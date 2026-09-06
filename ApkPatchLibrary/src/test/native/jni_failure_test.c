#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <jni.h>
#include "bspatch.h"
#include "com_cundong_utils_PatchUtils.h"

static int fail_at, calls, releases, pending, patched;
static const char *JNICALL get_chars(JNIEnv *env, jstring str, jboolean *copy) {
    assert(!pending); /* Calling conversion with a pending exception is illegal. */
    if (++calls == fail_at) {
        pending = 1;
        return NULL;
    }
    return "file";
}
static void JNICALL release_chars(JNIEnv *env, jstring str, const char *chars) {
    assert(chars != NULL);
    ++releases;
}
int bspatch(const char *oldfile, const char *newfile, const char *patchfile) {
    assert(!pending);
    ++patched;
    return BSPATCH_SUCCESS;
}
int main(void) {
    const struct JNINativeInterface_ table = {
        .GetStringUTFChars = get_chars,
        .ReleaseStringUTFChars = release_chars
    };
    JNIEnv env = &table;
    jstring path = (jstring)(uintptr_t)1;
    for (fail_at = 0; fail_at <= 3; ++fail_at) {
        calls = releases = pending = patched = 0;
        int result = Java_com_cundong_utils_PatchUtils_patch(&env, NULL, path, path, path);
        if (fail_at == 0) {
            assert(result == BSPATCH_SUCCESS && calls == 3 && releases == 3 && patched == 1);
        } else {
            assert(result == BSPATCH_ERR_INVALID_ARGUMENT && pending);
            assert(calls == fail_at && releases == fail_at - 1 && !patched);
        }
    }
    calls = releases = pending = patched = 0;
    assert(Java_com_cundong_utils_PatchUtils_patch(&env, NULL, NULL, path, path)
        == BSPATCH_ERR_INVALID_ARGUMENT);
    assert(!calls && !releases && !patched);
    puts("JNI failure checks passed: success, each conversion failure, null argument");
    return 0;
}
