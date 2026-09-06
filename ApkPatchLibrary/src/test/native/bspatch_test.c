#define _XOPEN_SOURCE 700
#define _DARWIN_C_SOURCE 1

#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include "bspatch.h"
#include "bzip2/bzlib.h"

typedef struct {
	uint8_t *bytes;
	size_t size;
} buffer_t;

static int failures = 0;

#define CHECK(condition, message) do { \
	if (!(condition)) { \
		fprintf(stderr, "FAIL: %s (line %d)\n", message, __LINE__); \
		failures++; \
	} \
} while (0)

static void encode_offt(int64_t value, uint8_t output[8]) {
	uint64_t magnitude = value < 0 ? (uint64_t)(-value) : (uint64_t)value;
	int index;

	for (index = 0; index < 8; ++index) {
		output[index] = (uint8_t)(magnitude & 0xFFU);
		magnitude >>= 8U;
	}
	if (value < 0)
		output[7] |= 0x80U;
}

static buffer_t compress_block(const uint8_t *source, size_t source_size) {
	buffer_t result = {NULL, 0};
	unsigned int compressed_size;
	unsigned int source_length;
	int bz_result;

	CHECK(source_size <= UINT32_MAX, "test source fits bzip2 API");
	source_length = (unsigned int)source_size;
	compressed_size = source_length + source_length / 100U + 601U;
	result.bytes = (uint8_t *)malloc(compressed_size);
	CHECK(result.bytes != NULL, "allocate compressed block");
	if (result.bytes == NULL)
		return result;

	bz_result = BZ2_bzBuffToBuffCompress((char *)result.bytes, &compressed_size,
			(char *)(source_size == 0 ? (const uint8_t *)"" : source),
			source_length, 9, 0, 30);
	CHECK(bz_result == BZ_OK, "compress test block");
	if (bz_result != BZ_OK) {
		free(result.bytes);
		result.bytes = NULL;
		return result;
	}
	result.size = compressed_size;
	return result;
}

static buffer_t make_patch_from_controls(const uint8_t *controls, size_t controls_size,
		const uint8_t *diff, size_t diff_size,
		const uint8_t *extra, size_t extra_size, int64_t new_size) {
	buffer_t ctrl_compressed;
	buffer_t diff_compressed;
	buffer_t extra_compressed;
	buffer_t patch = {NULL, 0};
	size_t offset;

	ctrl_compressed = compress_block(controls, controls_size);
	diff_compressed = compress_block(diff, diff_size);
	extra_compressed = compress_block(extra, extra_size);
	if (ctrl_compressed.bytes == NULL || diff_compressed.bytes == NULL ||
			extra_compressed.bytes == NULL)
		goto cleanup;

	patch.size = 32 + ctrl_compressed.size + diff_compressed.size + extra_compressed.size;
	patch.bytes = (uint8_t *)malloc(patch.size);
	CHECK(patch.bytes != NULL, "allocate test patch");
	if (patch.bytes == NULL) {
		patch.size = 0;
		goto cleanup;
	}

	memcpy(patch.bytes, "BSDIFF40", 8);
	encode_offt((int64_t)ctrl_compressed.size, patch.bytes + 8);
	encode_offt((int64_t)diff_compressed.size, patch.bytes + 16);
	encode_offt(new_size, patch.bytes + 24);
	offset = 32;
	memcpy(patch.bytes + offset, ctrl_compressed.bytes, ctrl_compressed.size);
	offset += ctrl_compressed.size;
	memcpy(patch.bytes + offset, diff_compressed.bytes, diff_compressed.size);
	offset += diff_compressed.size;
	memcpy(patch.bytes + offset, extra_compressed.bytes, extra_compressed.size);

cleanup:
	free(ctrl_compressed.bytes);
	free(diff_compressed.bytes);
	free(extra_compressed.bytes);
	return patch;
}

static buffer_t make_patch(int64_t ctrl0, int64_t ctrl1, int64_t ctrl2,
		const uint8_t *diff, size_t diff_size,
		const uint8_t *extra, size_t extra_size, int64_t new_size) {
	uint8_t controls[24];

	encode_offt(ctrl0, controls);
	encode_offt(ctrl1, controls + 8);
	encode_offt(ctrl2, controls + 16);
	return make_patch_from_controls(controls, sizeof(controls), diff, diff_size,
			extra, extra_size, new_size);
}

static int write_file(const char *path, const uint8_t *bytes, size_t size) {
	FILE *file = fopen(path, "wb");
	int ok;

	if (file == NULL)
		return 0;
	ok = fwrite(bytes, 1, size, file) == size;
	if (fclose(file) != 0)
		ok = 0;
	return ok;
}

static buffer_t read_file(const char *path) {
	buffer_t result = {NULL, 0};
	FILE *file = fopen(path, "rb");
	long size;

	if (file == NULL)
		return result;
	if (fseek(file, 0, SEEK_END) != 0 || (size = ftell(file)) < 0 ||
			fseek(file, 0, SEEK_SET) != 0) {
		fclose(file);
		return result;
	}
	result.bytes = (uint8_t *)malloc(size == 0 ? 1U : (size_t)size);
	if (result.bytes != NULL && fread(result.bytes, 1, (size_t)size, file) == (size_t)size)
		result.size = (size_t)size;
	else {
		free(result.bytes);
		result.bytes = NULL;
	}
	fclose(file);
	return result;
}

static int file_equals(const char *path, const uint8_t *expected, size_t size) {
	buffer_t actual = read_file(path);
	int equal = actual.bytes != NULL && actual.size == size &&
			memcmp(actual.bytes, expected, size) == 0;
	free(actual.bytes);
	return equal;
}

static void reset_output(const char *path) {
	static const uint8_t sentinel[] = "preserve-existing-output";
	CHECK(write_file(path, sentinel, sizeof(sentinel) - 1), "prepare output sentinel");
}

static void expect_corrupt_and_preserved(const char *old_path, const char *output_path,
		const char *patch_path, const uint8_t *patch, size_t patch_size,
		const char *case_name) {
	static const uint8_t sentinel[] = "preserve-existing-output";
	int result;

	CHECK(write_file(patch_path, patch, patch_size), "write corrupt patch");
	reset_output(output_path);
	result = bspatch(old_path, output_path, patch_path);
	if (result != BSPATCH_ERR_CORRUPT_PATCH) {
		fprintf(stderr, "FAIL: %s returned %d, expected %d\n", case_name,
				result, BSPATCH_ERR_CORRUPT_PATCH);
		failures++;
	}
	CHECK(file_equals(output_path, sentinel, sizeof(sentinel) - 1),
			"corrupt patch preserves existing output");
}

int main(void) {
	char directory[] = "/tmp/apkpatch-test.XXXXXX";
	char old_path[256], output_path[256], patch_path[256], missing_path[256];
	static const uint8_t old_bytes[] = "abc";
	static const uint8_t expected_new[] = "xyz";
	static const uint8_t diff[] = {(uint8_t)('x' - 'a')};
	static const uint8_t extra[] = "yz";
	static const uint8_t sentinel[] = "preserve-existing-output";
	buffer_t valid_patch;
	buffer_t position_only_patch;
	buffer_t zero_progress_patch;
	uint8_t position_controls[48];
	uint8_t position_diff[] = {(uint8_t)('x' - 'b')};
	uint8_t *mutated;
	int result;

	CHECK(mkdtemp(directory) != NULL, "create test directory");
	if (failures != 0)
		return EXIT_FAILURE;
	snprintf(old_path, sizeof(old_path), "%s/old.bin", directory);
	snprintf(output_path, sizeof(output_path), "%s/new.bin", directory);
	snprintf(patch_path, sizeof(patch_path), "%s/patch.bin", directory);
	snprintf(missing_path, sizeof(missing_path), "%s/missing/file.bin", directory);
	CHECK(write_file(old_path, old_bytes, sizeof(old_bytes) - 1), "write old file");

	valid_patch = make_patch(1, 2, 0, diff, sizeof(diff), extra,
			sizeof(extra) - 1, (int64_t)sizeof(expected_new) - 1);
	fprintf(stderr, "CASE valid patch\n");
	CHECK(valid_patch.bytes != NULL, "create valid BSDIFF40 patch");
	CHECK(write_file(patch_path, valid_patch.bytes, valid_patch.size), "write valid patch");
	result = bspatch(old_path, output_path, patch_path);
	CHECK(result == BSPATCH_SUCCESS, "valid patch returns success");
	CHECK(file_equals(output_path, expected_new, sizeof(expected_new) - 1),
			"valid patch creates expected new file");

	fprintf(stderr, "CASE position-only control tuple\n");
	encode_offt(0, position_controls);
	encode_offt(0, position_controls + 8);
	encode_offt(1, position_controls + 16);
	encode_offt(1, position_controls + 24);
	encode_offt(2, position_controls + 32);
	encode_offt(0, position_controls + 40);
	position_only_patch = make_patch_from_controls(position_controls,
			sizeof(position_controls), position_diff, sizeof(position_diff), extra,
			sizeof(extra) - 1, (int64_t)sizeof(expected_new) - 1);
	CHECK(position_only_patch.bytes != NULL, "create position-only control patch");
	CHECK(write_file(patch_path, position_only_patch.bytes, position_only_patch.size),
			"write position-only control patch");
	result = bspatch(old_path, output_path, patch_path);
	CHECK(result == BSPATCH_SUCCESS, "(0, 0, z) control tuple is valid");
	CHECK(file_equals(output_path, expected_new, sizeof(expected_new) - 1),
			"position-only control patch creates expected file");

	mutated = (uint8_t *)malloc(valid_patch.size + 1);
	CHECK(mutated != NULL, "allocate mutation buffer");
	if (mutated != NULL) {
		fprintf(stderr, "CASE bad magic\n");
		fprintf(stderr, "CASE segment bounds\n");
		memcpy(mutated, valid_patch.bytes, valid_patch.size);
		mutated[0] = 'X';
		expect_corrupt_and_preserved(old_path, output_path, patch_path, mutated,
				valid_patch.size, "bad magic");

		memcpy(mutated, valid_patch.bytes, valid_patch.size);
		encode_offt((int64_t)valid_patch.size, mutated + 8);
		expect_corrupt_and_preserved(old_path, output_path, patch_path, mutated,
				valid_patch.size, "control segment out of bounds");

		fprintf(stderr, "CASE truncated header\n");
		expect_corrupt_and_preserved(old_path, output_path, patch_path,
				valid_patch.bytes, 31, "truncated header");
		fprintf(stderr, "CASE truncated bzip2\n");
		expect_corrupt_and_preserved(old_path, output_path, patch_path,
				valid_patch.bytes, valid_patch.size - 1, "truncated bzip2 stream");

		fprintf(stderr, "CASE trailing bytes\n");
		memcpy(mutated, valid_patch.bytes, valid_patch.size);
		mutated[valid_patch.size] = 0x42;
		expect_corrupt_and_preserved(old_path, output_path, patch_path, mutated,
				valid_patch.size + 1, "trailing bytes beyond extra segment");
	}

	zero_progress_patch = make_patch(0, 0, 0, NULL, 0, NULL, 0, 1);
	fprintf(stderr, "CASE zero progress\n");
	CHECK(zero_progress_patch.bytes != NULL, "create zero-progress patch");
	if (zero_progress_patch.bytes != NULL)
		expect_corrupt_and_preserved(old_path, output_path, patch_path,
				zero_progress_patch.bytes, zero_progress_patch.size, "zero progress tuple");

	CHECK(write_file(patch_path, valid_patch.bytes, valid_patch.size), "restore valid patch");
	fprintf(stderr, "CASE aliases\n");
	result = bspatch(old_path, old_path, patch_path);
	CHECK(result == BSPATCH_ERR_INVALID_ARGUMENT, "output cannot alias old input");
	CHECK(file_equals(old_path, old_bytes, sizeof(old_bytes) - 1), "old input remains unchanged");
	result = bspatch(old_path, patch_path, patch_path);
	CHECK(result == BSPATCH_ERR_INVALID_ARGUMENT, "output cannot alias patch input");

	reset_output(output_path);
	fprintf(stderr, "CASE missing inputs/output parent\n");
	result = bspatch(old_path, output_path, missing_path);
	CHECK(result == BSPATCH_ERR_OPEN_PATCH, "missing patch returns open-patch error");
	CHECK(file_equals(output_path, sentinel, sizeof(sentinel) - 1),
			"missing patch preserves output");
	CHECK(write_file(patch_path, valid_patch.bytes, valid_patch.size), "restore valid patch again");
	result = bspatch(missing_path, output_path, patch_path);
	CHECK(result == BSPATCH_ERR_OPEN_OLD, "missing old returns open-old error");
	result = bspatch(old_path, missing_path, patch_path);
	CHECK(result == BSPATCH_ERR_OPEN_NEW, "missing output parent returns open-new error");

	CHECK(write_file(old_path, (const uint8_t *)"", 0), "prepare empty old file");
	fprintf(stderr, "CASE empty old\n");
	CHECK(write_file(patch_path, valid_patch.bytes, valid_patch.size), "restore patch for empty old");
	result = bspatch(old_path, output_path, patch_path);
	CHECK(result == BSPATCH_SUCCESS, "empty old file is a valid input");
	CHECK(file_equals(output_path, (const uint8_t *)"\027yz", 3),
			"empty old applies diff bytes without source contribution");

	free(mutated);
	free(position_only_patch.bytes);
	free(zero_progress_patch.bytes);
	free(valid_patch.bytes);
	unlink(output_path);
	unlink(patch_path);
	unlink(old_path);
	rmdir(directory);

	if (failures != 0) {
		fprintf(stderr, "%d native bspatch test(s) failed\n", failures);
		return EXIT_FAILURE;
	}
	printf("All native bspatch regression tests passed.\n");
	return EXIT_SUCCESS;
}
