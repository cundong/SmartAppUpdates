#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
build_dir=${APKPATCH_HOST_TEST_BUILD_DIR:-"$project_dir/build/host-native-tests"}
compiler=${CC:-/usr/bin/cc}

mkdir -p "$build_dir"

"$compiler" \
    -std=c99 \
    -D_FILE_OFFSET_BITS=64 \
    -Wall -Wextra -Werror \
    -Wno-unused-parameter -Wno-unused-variable -Wno-unused-function \
    -I"$project_dir/src/main/cpp" \
    "$project_dir/src/test/native/bspatch_test.c" \
    "$project_dir/src/main/cpp/bspatch.c" \
    "$project_dir/src/main/cpp/bzip2/bzlib.c" \
    "$project_dir/src/main/cpp/bzip2/compress.c" \
    "$project_dir/src/main/cpp/bzip2/decompress.c" \
    "$project_dir/src/main/cpp/bzip2/crctable.c" \
    "$project_dir/src/main/cpp/bzip2/randtable.c" \
    "$project_dir/src/main/cpp/bzip2/blocksort.c" \
    "$project_dir/src/main/cpp/bzip2/huffman.c" \
    -o "$build_dir/bspatch_test"

"$build_dir/bspatch_test"
