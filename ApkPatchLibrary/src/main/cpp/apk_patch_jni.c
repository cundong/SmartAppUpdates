/*
 * apk_patch_jni.c — JNI 桥接层。
 *
 * 只做三件事：参数校验、jstring → char* 转换、调用纯 C 算法层 bspatch()。
 * 算法实现见 bspatch.c；返回值契约见 bspatch.h（与 Java 层 PatchUtils 一致）。
 */
#include <jni.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif

#include "bspatch.h"
#include "com_cundong_utils_PatchUtils.h"

#ifdef __ANDROID__
#define LOG_TAG "ApkPatchLibrary"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#else
#define LOGI(...) ((void)0)
#define LOGE(...) ((void)0)
#endif

/*
 * Class:     com_cundong_utils_PatchUtils
 * Method:    patch
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_cundong_utils_PatchUtils_patch(JNIEnv *env,
		jclass clazz, jstring oldPath, jstring newPath, jstring patchPath) {
	(void) clazz; /* static 方法，无 this */

	if (oldPath == NULL || newPath == NULL || patchPath == NULL) {
		LOGE("patch called with null argument");
		return BSPATCH_ERR_INVALID_ARGUMENT;
	}

	const char *oldFile = NULL, *newFile = NULL, *patchFile = NULL;
	int result = BSPATCH_ERR_INVALID_ARGUMENT;

	/* A failed conversion leaves a pending JVM exception. Only cleanup JNI calls
	 * are legal until we return; do not attempt another conversion. */
	oldFile = (*env)->GetStringUTFChars(env, oldPath, NULL);
	if (oldFile == NULL) goto cleanup;
	newFile = (*env)->GetStringUTFChars(env, newPath, NULL);
	if (newFile == NULL) goto cleanup;
	patchFile = (*env)->GetStringUTFChars(env, patchPath, NULL);
	if (patchFile == NULL) goto cleanup;

	LOGI("old   = %s", oldFile);
	LOGI("new   = %s", newFile);
	LOGI("patch = %s", patchFile);

	result = bspatch(oldFile, newFile, patchFile);

	LOGI("bspatch result = %d", result);

cleanup:
	if (oldFile != NULL) (*env)->ReleaseStringUTFChars(env, oldPath, oldFile);
	if (newFile != NULL) (*env)->ReleaseStringUTFChars(env, newPath, newFile);
	if (patchFile != NULL) (*env)->ReleaseStringUTFChars(env, patchPath, patchFile);

	return result;
}
