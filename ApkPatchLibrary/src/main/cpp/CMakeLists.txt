# ApkPatchLibrary native 构建脚本
#
# 产物：libApkPatchLibrary.so（armeabi-v7a / arm64-v8a / x86_64，ABI 由
# build.gradle 的 ndk.abiFilters 控制）。
#
# 代码分层：
#   bspatch.c        纯 C 的 bspatch 算法实现（可独立桌面编译调试）
#   apk_patch_jni.c  JNI 桥接层（jstring 转换 + 调用 bspatch）
#   bzip2/           第三方 bzip2 源码（bsdiff 格式依赖其解压）
cmake_minimum_required(VERSION 3.18.1)
project(apk_patch_library C)

# bzip2 源码清单。注意必须排除带 main() 的程序入口文件：
#   bzip2.c          —— bzip2 命令行工具的 main()
#   bzip2recover.c   —— bzip2recover 命令行工具的 main()
set(BZIP2_SOURCES
    bzip2/bzlib.c
    bzip2/compress.c
    bzip2/decompress.c
    bzip2/crctable.c
    bzip2/randtable.c
    bzip2/blocksort.c
    bzip2/huffman.c)

add_library(ApkPatchLibrary SHARED
    apk_patch_jni.c
    bspatch.c
    ${BZIP2_SOURCES})

# 说明：不要加 _FILE_OFFSET_BITS=64 —— 32 位 ABI 上它会把 fseeko 重定向到
# fseeko64（仅 API 24+ 可用），minSdk 21 下直接编译失败。
# 不加该宏时：64 位 ABI 的 off_t 天然 64 位；32 位 ABI 支持 <2GB 的 apk，足够。
# 若确需 >2GB apk 的 32 位支持，需同时将 minSdk 提升到 24 再加此宏。

# bzip2 是上世纪 90 年代的第三方 C 代码，警告噪音大，只保留核心告警
target_compile_options(ApkPatchLibrary PRIVATE
    -Wall
    -Wno-unused-parameter
    -Wno-unused-variable
    -Wno-unused-function)

find_library(log_lib log)

target_link_libraries(ApkPatchLibrary ${log_lib} z)

# Android 15+ 要求 16KB 页对齐（arm64-v8a / x86_64 必须，32 位 ABI 加上无害）
target_link_options(ApkPatchLibrary PRIVATE "-Wl,-z,max-page-size=16384")
