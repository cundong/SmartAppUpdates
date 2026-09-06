#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
build_dir=${APKPATCH_HOST_TEST_BUILD_DIR:-"$project_dir/build/host-native-tests"}
compiler=${CC:-/usr/bin/cc}

mkdir -p "$build_dir"

# Compile each translation unit separately so the legacy bzip2 warning exception
# cannot hide accidental fallthrough in our parser or regression tests.
compile_object() {
    source_file=$1
    object_file=$2
    shift 2
    "$compiler" \
        -std=c99 -D_FILE_OFFSET_BITS=64 \
        -Wall -Wextra -Werror -Wimplicit-fallthrough \
        -Wno-unused-parameter -Wno-unused-variable -Wno-unused-function \
        -I"$project_dir/src/main/cpp" \
        "$@" -c "$source_file" -o "$object_file"
}

compile_object "$project_dir/src/test/native/bspatch_test.c" "$build_dir/bspatch_test.o"
compile_object "$project_dir/src/main/cpp/bspatch.c" "$build_dir/bspatch.o"

set -- "$build_dir/bspatch_test.o" "$build_dir/bspatch.o"
for unit in bzlib compress decompress crctable randtable blocksort huffman; do
    # bzip2's resumable decompression state machine intentionally falls through.
    compile_object "$project_dir/src/main/cpp/bzip2/$unit.c" "$build_dir/$unit.o" \
        -Wno-implicit-fallthrough
    set -- "$@" "$build_dir/$unit.o"
done

"$compiler" "$@" -o "$build_dir/bspatch_test"
"$build_dir/bspatch_test"
