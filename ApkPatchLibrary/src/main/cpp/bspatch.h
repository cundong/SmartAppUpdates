/*
 * bspatch.h — bspatch 二进制补丁合成算法的纯 C 接口。
 *
 * 本层与 JNI/Android 完全解耦，只依赖标准 C 库与 bzip2，
 * 可单独在桌面环境编译调试（如单元测试、命令行工具）。
 *
 * JNI 桥接层见 apk_patch_jni.c；Java 入口见 com.cundong.utils.PatchUtils。
 */
#ifndef BSPATCH_H
#define BSPATCH_H

#ifdef __cplusplus
extern "C" {
#endif

/*
 * 返回值约定（同时是 Java 层 PatchUtils.patch() 的返回值契约）：
 *
 *   BSPATCH_SUCCESS               0  合成成功
 *   BSPATCH_ERR_INVALID_ARGUMENT -1  参数为空（old/new/patch 路径为 NULL）
 *   BSPATCH_ERR_OPEN_PATCH       -2  无法打开 patch 文件
 *   BSPATCH_ERR_CORRUPT_PATCH    -3  patch 文件损坏或格式非法（非 BSDIFF40）
 *   BSPATCH_ERR_OPEN_OLD         -4  无法打开 old 文件
 *   BSPATCH_ERR_READ_OLD         -5  读取 old 文件失败
 *   BSPATCH_ERR_NO_MEMORY        -6  内存分配失败
 *   BSPATCH_ERR_OPEN_NEW         -7  无法创建/打开 new 输出文件
 *   BSPATCH_ERR_WRITE_NEW        -8  写入 new 文件失败
 *   BSPATCH_ERR_BZIP2            -9  bzip2 解压流初始化失败
 *
 * 注意：历史版本在出错时调用 err()/errx() 直接 exit()，会杀死整个 App
 * 进程；现已全部改为返回错误码，由调用方决定如何处理。
 */
#define BSPATCH_SUCCESS               0
#define BSPATCH_ERR_INVALID_ARGUMENT -1
#define BSPATCH_ERR_OPEN_PATCH       -2
#define BSPATCH_ERR_CORRUPT_PATCH    -3
#define BSPATCH_ERR_OPEN_OLD         -4
#define BSPATCH_ERR_READ_OLD         -5
#define BSPATCH_ERR_NO_MEMORY        -6
#define BSPATCH_ERR_OPEN_NEW         -7
#define BSPATCH_ERR_WRITE_NEW        -8
#define BSPATCH_ERR_BZIP2            -9

/*
 * 用 patch 文件将 oldfile 合成为 newfile（bspatch 算法，BSDIFF40 格式）。
 *
 * @param oldfile   旧版本文件路径（客户端通常为已安装 apk 的 sourceDir）
 * @param newfile   合成结果输出路径（已存在则截断覆盖）
 * @param patchfile 差分补丁文件路径（由服务端 bsdiff 生成）
 * @return 上述 BSPATCH_* 错误码之一；0 表示成功
 */
int bspatch(const char *oldfile, const char *newfile, const char *patchfile);

#ifdef __cplusplus
}
#endif

#endif /* BSPATCH_H */
