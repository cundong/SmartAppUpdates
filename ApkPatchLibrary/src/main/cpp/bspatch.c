/*-
 * Copyright 2003-2005 Colin Percival
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted providing that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * bspatch.c — BSDIFF40 格式补丁的合成实现（纯算法层，不含 JNI）。
 *
 * 源自 Colin Percival 的 bsdiff/bspatch。和原始命令行实现相比，本版本：
 *  - 通过错误码返回失败，不会 exit() 杀死宿主 App；
 *  - 用固定宽度整数解析补丁头，并检查所有长度、偏移与加法；
 *  - 限制每个 bzip2 流只能消费头部声明的压缩分段；
 *  - 完整处理 EINTR/短读/短写，并以同目录临时文件原子提交输出。
 *
 * 文件格式（patch 文件布局）：
 *   0        8   "BSDIFF40" 魔数
 *   8        8   X = 控制块长度
 *   16       8   Y = 差异块长度
 *   24       8   新文件大小
 *   32       X   bzip2(控制块)：一组三元组 (x,y,z)
 *   32+X     Y   bzip2(差异块)
 *   32+X+Y   ?   bzip2(新增块)
 */
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include "bspatch.h"
#include "bzip2/bzlib.h"

#define BSDIFF_HEADER_SIZE 32
#define BSDIFF_INT_SIZE 8

/* bsdiff 的整数编码是 63 位绝对值 + 最高位符号位。 */
static int64_t offtin(const uint8_t *buf) {
	uint64_t value = (uint64_t)(buf[7] & 0x7FU);
	int index;

	for (index = 6; index >= 0; --index)
		value = value * 256U + buf[index];

	return (buf[7] & 0x80U) ? -(int64_t)value : (int64_t)value;
}

static int add_i64(int64_t left, int64_t right, int64_t *result) {
	if ((right > 0 && left > INT64_MAX - right) ||
			(right < 0 && left < INT64_MIN - right))
		return 0;
	*result = left + right;
	return 1;
}

static int read_fully(int fd, uint8_t *buffer, size_t length) {
	size_t offset = 0;

	while (offset < length) {
		size_t remaining = length - offset;
		size_t request = remaining > (size_t)SSIZE_MAX ? (size_t)SSIZE_MAX : remaining;
		ssize_t count = read(fd, buffer + offset, request);
		if (count > 0) {
			offset += (size_t)count;
			continue;
		}
		if (count < 0 && errno == EINTR)
			continue;
		return 0;
	}
	return 1;
}

static int write_fully(int fd, const uint8_t *buffer, size_t length) {
	size_t offset = 0;

	while (offset < length) {
		size_t remaining = length - offset;
		size_t request = remaining > (size_t)SSIZE_MAX ? (size_t)SSIZE_MAX : remaining;
		ssize_t count = write(fd, buffer + offset, request);
		if (count > 0) {
			offset += (size_t)count;
			continue;
		}
		if (count < 0 && errno == EINTR)
			continue;
		return 0;
	}
	return 1;
}

static int same_file(const struct stat *left, const struct stat *right) {
	return left->st_dev == right->st_dev && left->st_ino == right->st_ino;
}

/*
 * BZ2_bzRead() 的长度参数是 int。这里分块读取，既避免从 int64_t/size_t
 * 静默截断，也接受合法的短读，但不接受提前结束或无进展。
 */
static int bz_read_exact(BZFILE *stream, int *bzerror, int *stream_ended,
		uint8_t *buffer, size_t length) {
	size_t offset = 0;

	if (*stream_ended)
		return length == 0;

	while (offset < length) {
		size_t remaining = length - offset;
		int request = remaining > (size_t)INT_MAX ? INT_MAX : (int)remaining;
		int count = BZ2_bzRead(bzerror, stream, buffer + offset, request);

		if (*bzerror != BZ_OK && *bzerror != BZ_STREAM_END)
			return 0;
		if (count <= 0)
			return 0;

		offset += (size_t)count;
		if (*bzerror == BZ_STREAM_END) {
			*stream_ended = 1;
			if (offset != length)
				return 0;
		}
	}
	return 1;
}

/*
 * 断言解压数据恰好结束，并用 FILE 当前物理位置减去 bz2 未消费缓存，得到
 * 流实际消费的压缩字节边界。这样恶意流无法跨入下一个 BSDIFF 分段。
 */
static int bz_expect_end_at(BZFILE *stream, FILE *file, int *bzerror,
		int *stream_ended, int64_t expected_offset) {
	uint8_t extra;
	void *unused = NULL;
	int unused_count = 0;
	off_t physical_offset;
	int64_t consumed_offset;

	if (!*stream_ended) {
		int count = BZ2_bzRead(bzerror, stream, &extra, 1);
		if (*bzerror != BZ_STREAM_END || count != 0)
			return 0;
		*stream_ended = 1;
	}

	BZ2_bzReadGetUnused(bzerror, stream, &unused, &unused_count);
	if (*bzerror != BZ_OK || unused_count < 0)
		return 0;

	physical_offset = ftello(file);
	if (physical_offset < 0 || (int64_t)physical_offset < unused_count)
		return 0;
	consumed_offset = (int64_t)physical_offset - unused_count;
	return consumed_offset == expected_offset;
}

static int open_bz_stream(const char *patchfile, int64_t offset,
		const struct stat *expected_patch, FILE **file, BZFILE **stream,
		int *bzerror) {
	struct stat actual_patch;

	*file = fopen(patchfile, "rb");
	if (*file == NULL)
		return BSPATCH_ERR_OPEN_PATCH;
	if (fstat(fileno(*file), &actual_patch) != 0 ||
			!same_file(expected_patch, &actual_patch) ||
			actual_patch.st_size != expected_patch->st_size ||
			(int64_t)(off_t)offset != offset ||
			fseeko(*file, (off_t)offset, SEEK_SET) != 0)
		return BSPATCH_ERR_CORRUPT_PATCH;

	*stream = BZ2_bzReadOpen(bzerror, *file, 0, 0, NULL, 0);
	if (*stream == NULL || *bzerror != BZ_OK)
		return BSPATCH_ERR_BZIP2;
	return BSPATCH_SUCCESS;
}

static int stat_output_alias(const char *newfile, const struct stat *old_stat,
		const struct stat *patch_stat) {
	struct stat output_stat;

	if (stat(newfile, &output_stat) != 0)
		return errno == ENOENT ? BSPATCH_SUCCESS : BSPATCH_ERR_OPEN_NEW;
	if (same_file(&output_stat, old_stat) || same_file(&output_stat, patch_stat))
		return BSPATCH_ERR_INVALID_ARGUMENT;
	return BSPATCH_SUCCESS;
}

int bspatch(const char *oldfile, const char *newfile, const char *patchfile) {
	FILE *header_file = NULL, *cpf = NULL, *dpf = NULL, *epf = NULL;
	BZFILE *cpfbz2 = NULL, *dpfbz2 = NULL, *epfbz2 = NULL;
	int cbz2err = BZ_OK, dbz2err = BZ_OK, ebz2err = BZ_OK;
	int ctrl_ended = 0, diff_ended = 0, extra_ended = 0;
	int old_fd = -1, output_fd = -1;
	struct stat patch_stat, old_stat;
	int64_t patch_size, ctrl_length, diff_length, new_size64;
	int64_t diff_offset, extra_offset;
	size_t old_size, new_size;
	uint8_t header[BSDIFF_HEADER_SIZE], ctrl_buffer[BSDIFF_INT_SIZE];
	uint8_t *old_data = NULL, *new_data = NULL;
	int64_t old_pos = 0, new_pos = 0, ctrl[3];
	char *temporary_path = NULL;
	int temporary_created = 0;
	int result = BSPATCH_SUCCESS;
	int index;

	if (oldfile == NULL || newfile == NULL || patchfile == NULL)
		return BSPATCH_ERR_INVALID_ARGUMENT;

	header_file = fopen(patchfile, "rb");
	if (header_file == NULL)
		return BSPATCH_ERR_OPEN_PATCH;
	if (fstat(fileno(header_file), &patch_stat) != 0 || patch_stat.st_size < BSDIFF_HEADER_SIZE) {
		result = BSPATCH_ERR_CORRUPT_PATCH;
		goto cleanup;
	}
	patch_size = (int64_t)patch_stat.st_size;
	if ((off_t)patch_size != patch_stat.st_size ||
			fread(header, 1, sizeof(header), header_file) != sizeof(header) ||
			memcmp(header, "BSDIFF40", 8) != 0) {
		result = BSPATCH_ERR_CORRUPT_PATCH;
		goto cleanup;
	}

	ctrl_length = offtin(header + 8);
	diff_length = offtin(header + 16);
	new_size64 = offtin(header + 24);
	if (ctrl_length < 0 || diff_length < 0 || new_size64 < 0 ||
			ctrl_length > patch_size - BSDIFF_HEADER_SIZE) {
		result = BSPATCH_ERR_CORRUPT_PATCH;
		goto cleanup;
	}
	diff_offset = BSDIFF_HEADER_SIZE + ctrl_length;
	if (diff_length > patch_size - diff_offset) {
		result = BSPATCH_ERR_CORRUPT_PATCH;
		goto cleanup;
	}
	extra_offset = diff_offset + diff_length;
	if ((uint64_t)new_size64 > (uint64_t)SIZE_MAX) {
		result = BSPATCH_ERR_NO_MEMORY;
		goto cleanup;
	}
	new_size = (size_t)new_size64;

	result = open_bz_stream(patchfile, BSDIFF_HEADER_SIZE, &patch_stat,
			&cpf, &cpfbz2, &cbz2err);
	if (result != BSPATCH_SUCCESS)
		goto cleanup;
	result = open_bz_stream(patchfile, diff_offset, &patch_stat,
			&dpf, &dpfbz2, &dbz2err);
	if (result != BSPATCH_SUCCESS)
		goto cleanup;
	result = open_bz_stream(patchfile, extra_offset, &patch_stat,
			&epf, &epfbz2, &ebz2err);
	if (result != BSPATCH_SUCCESS)
		goto cleanup;

	old_fd = open(oldfile, O_RDONLY);
	if (old_fd < 0) {
		result = BSPATCH_ERR_OPEN_OLD;
		goto cleanup;
	}
	if (fstat(old_fd, &old_stat) != 0 || old_stat.st_size < 0 ||
			(uint64_t)old_stat.st_size > (uint64_t)SIZE_MAX ||
			lseek(old_fd, 0, SEEK_SET) < 0) {
		result = BSPATCH_ERR_READ_OLD;
		goto cleanup;
	}
	old_size = (size_t)old_stat.st_size;
	old_data = (uint8_t *)malloc(old_size == 0 ? 1 : old_size);
	if (old_data == NULL) {
		result = BSPATCH_ERR_NO_MEMORY;
		goto cleanup;
	}
	if (!read_fully(old_fd, old_data, old_size)) {
		result = BSPATCH_ERR_READ_OLD;
		goto cleanup;
	}
	close(old_fd);
	old_fd = -1;

	new_data = (uint8_t *)malloc(new_size == 0 ? 1 : new_size);
	if (new_data == NULL) {
		result = BSPATCH_ERR_NO_MEMORY;
		goto cleanup;
	}

	while (new_pos < new_size64) {
		int64_t old_after_diff;

		for (index = 0; index < 3; ++index) {
			if (!bz_read_exact(cpfbz2, &cbz2err, &ctrl_ended,
					ctrl_buffer, sizeof(ctrl_buffer))) {
				result = BSPATCH_ERR_CORRUPT_PATCH;
				goto cleanup;
			}
			ctrl[index] = offtin(ctrl_buffer);
		}

		/* (0, 0, z) 是 bsdiff 用于只移动 old 指针的合法控制项；
		 * 只有 (0, 0, 0) 完全无进展，必须拒绝。 */
		if (ctrl[0] < 0 || ctrl[1] < 0 ||
				(ctrl[0] == 0 && ctrl[1] == 0 && ctrl[2] == 0) ||
				ctrl[0] > new_size64 - new_pos) {
			result = BSPATCH_ERR_CORRUPT_PATCH;
			goto cleanup;
		}
		if (!add_i64(old_pos, ctrl[0], &old_after_diff)) {
			result = BSPATCH_ERR_CORRUPT_PATCH;
			goto cleanup;
		}
		if (!bz_read_exact(dpfbz2, &dbz2err, &diff_ended,
				new_data + (size_t)new_pos, (size_t)ctrl[0])) {
			result = BSPATCH_ERR_CORRUPT_PATCH;
			goto cleanup;
		}

		for (int64_t i = 0; i < ctrl[0]; ++i) {
			int64_t source_pos = old_pos + i;
			if (source_pos >= 0 && (uint64_t)source_pos < (uint64_t)old_size)
				new_data[(size_t)(new_pos + i)] += old_data[(size_t)source_pos];
		}
		new_pos += ctrl[0];
		old_pos = old_after_diff;

		if (ctrl[1] > new_size64 - new_pos ||
				!bz_read_exact(epfbz2, &ebz2err, &extra_ended,
					new_data + (size_t)new_pos, (size_t)ctrl[1])) {
			result = BSPATCH_ERR_CORRUPT_PATCH;
			goto cleanup;
		}
		new_pos += ctrl[1];
		if (!add_i64(old_pos, ctrl[2], &old_pos)) {
			result = BSPATCH_ERR_CORRUPT_PATCH;
			goto cleanup;
		}
	}

	if (!bz_expect_end_at(cpfbz2, cpf, &cbz2err, &ctrl_ended, diff_offset) ||
			!bz_expect_end_at(dpfbz2, dpf, &dbz2err, &diff_ended, extra_offset) ||
			!bz_expect_end_at(epfbz2, epf, &ebz2err, &extra_ended, patch_size)) {
		result = BSPATCH_ERR_CORRUPT_PATCH;
		goto cleanup;
	}

	result = stat_output_alias(newfile, &old_stat, &patch_stat);
	if (result != BSPATCH_SUCCESS)
		goto cleanup;
	if (strlen(newfile) > SIZE_MAX - sizeof(".tmp.XXXXXX")) {
		result = BSPATCH_ERR_NO_MEMORY;
		goto cleanup;
	}
	temporary_path = (char *)malloc(strlen(newfile) + sizeof(".tmp.XXXXXX"));
	if (temporary_path == NULL) {
		result = BSPATCH_ERR_NO_MEMORY;
		goto cleanup;
	}
	snprintf(temporary_path, strlen(newfile) + sizeof(".tmp.XXXXXX"),
			"%s.tmp.XXXXXX", newfile);
	output_fd = mkstemp(temporary_path);
	if (output_fd < 0) {
		result = BSPATCH_ERR_OPEN_NEW;
		goto cleanup;
	}
	temporary_created = 1;
	if (!write_fully(output_fd, new_data, new_size) || fsync(output_fd) != 0) {
		result = BSPATCH_ERR_WRITE_NEW;
		goto cleanup;
	}
	if (close(output_fd) != 0) {
		output_fd = -1;
		result = BSPATCH_ERR_WRITE_NEW;
		goto cleanup;
	}
	output_fd = -1;

	/* 缩小 stat 与 rename 的竞态窗口，并再次阻止覆盖输入 inode。 */
	result = stat_output_alias(newfile, &old_stat, &patch_stat);
	if (result != BSPATCH_SUCCESS)
		goto cleanup;
	if (rename(temporary_path, newfile) != 0) {
		result = BSPATCH_ERR_WRITE_NEW;
		goto cleanup;
	}
	temporary_created = 0;

cleanup:
	if (cpfbz2 != NULL) BZ2_bzReadClose(&cbz2err, cpfbz2);
	if (dpfbz2 != NULL) BZ2_bzReadClose(&dbz2err, dpfbz2);
	if (epfbz2 != NULL) BZ2_bzReadClose(&ebz2err, epfbz2);
	if (header_file != NULL) fclose(header_file);
	if (cpf != NULL) fclose(cpf);
	if (dpf != NULL) fclose(dpf);
	if (epf != NULL) fclose(epf);
	if (old_fd >= 0) close(old_fd);
	if (output_fd >= 0) close(output_fd);
	if (temporary_created && temporary_path != NULL) unlink(temporary_path);
	free(temporary_path);
	free(new_data);
	free(old_data);
	return result;
}
