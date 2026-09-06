package com.cundong.utils;

/**
 * APK 增量更新合成工具类（bspatch 客户端）。
 *
 * <p>功能：用服务器下发的差分补丁（BSDIFF40 格式，由 bsdiff 生成），
 * 将本机已安装的旧版 apk 合成为新版 apk，从而只需下载差分包即可完成整包更新。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * String oldApk = context.getPackageManager()
 *         .getApplicationInfo(context.getPackageName(), 0).sourceDir;
 * int result = PatchUtils.patch(oldApk, "/sdcard/new.apk", "/sdcard/update.patch");
 * if (result == PatchUtils.SUCCESS) {
 *     // 合成成功，校验 MD5/签名后引导安装
 * }
 * }</pre>
 *
 * <p>注意：本方法为耗时 IO + 计算操作，必须在子线程调用。</p>
 *
 * <p>native 实现见 ApkPatchLibrary/src/main/cpp/（bspatch.c 为算法层，
 * apk_patch_jni.c 为 JNI 桥接层）。</p>
 *
 * @author Cundong
 * @date 2013-9-6
 * @version 2.0
 */
public final class PatchUtils {

	/** 合成成功 */
	public static final int SUCCESS = 0;
	/** 参数为空 */
	public static final int ERR_INVALID_ARGUMENT = -1;
	/** 无法打开 patch 文件 */
	public static final int ERR_OPEN_PATCH = -2;
	/** patch 文件损坏或格式非法（非 BSDIFF40） */
	public static final int ERR_CORRUPT_PATCH = -3;
	/** 无法打开旧版 apk 文件 */
	public static final int ERR_OPEN_OLD = -4;
	/** 读取旧版 apk 文件失败 */
	public static final int ERR_READ_OLD = -5;
	/** 内存分配失败 */
	public static final int ERR_NO_MEMORY = -6;
	/** 无法创建输出文件 */
	public static final int ERR_OPEN_NEW = -7;
	/** 写入输出文件失败 */
	public static final int ERR_WRITE_NEW = -8;
	/** bzip2 解压流初始化失败 */
	public static final int ERR_BZIP2 = -9;

	static {
		System.loadLibrary("ApkPatchLibrary");
	}

	private PatchUtils() {
		// 工具类，禁止实例化
	}

	/**
	 * 使用补丁包合成新版 apk。
	 *
	 * @param oldApkPath 旧版 apk 路径，示例：/data/app/xxx/base.apk
	 * @param newApkPath 合成输出路径，示例：/sdcard/Android/data/xxx/files/new.apk
	 * @param patchPath  差分补丁路径，示例：/sdcard/Android/data/xxx/files/update.patch
	 * @return {@link #SUCCESS}(0) 表示成功；负值为上述 ERR_* 错误码之一
	 */
	public static native int patch(String oldApkPath, String newApkPath,
			String patchPath);
}
