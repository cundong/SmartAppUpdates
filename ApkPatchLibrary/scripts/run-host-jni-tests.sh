#!/bin/sh
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
build_dir=${APKPATCH_JNI_TEST_BUILD_DIR:-"$project_dir/build/host-jni-tests"}
compiler=${CC:-cc}
jni_java_home=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.home = //p')
case "$(uname -s)" in
    Darwin) jni_platform=darwin ;;
    Linux) jni_platform=linux ;;
    *) echo "Host JNI checks require macOS or Linux" >&2; exit 1 ;;
esac
mkdir -p "$build_dir"
"$compiler" -std=c99 -Wall -Wextra -Werror -Wno-unused-parameter     -I"$jni_java_home/include" -I"$jni_java_home/include/$jni_platform"     -I"$project_dir/src/main/cpp"     "$project_dir/src/test/native/jni_failure_test.c"     "$project_dir/src/main/cpp/apk_patch_jni.c" -o "$build_dir/jni_failure_test"
"$build_dir/jni_failure_test"
