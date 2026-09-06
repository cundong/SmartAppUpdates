package com.cundong.apkpatch.example;

import android.content.Context;

import java.io.File;

/**
 * 演示用常量与文件路径。
 *
 * 本示例演示增量更新完整链路：old.apk 与 update.patch 都打包进应用 assets/（见
 * build/generated/fixtures/assets/），启动时拷贝到应用私有目录，再调用 bspatch 合成 new.apk。
 * 产物放在应用私有外部目录（context.getExternalFilesDir(null)，即
 * /sdcard/Android/data/<package>/files/），Android 10+ 分区存储下无需任何权限。
 *
 * 三个 SHA-256 是仓库 Apks/fixtures.json 和构建生成的差分包 的测试基准。
 * 正式环境应由可信服务端下发并校验签名，不能把固定摘要当成生产方案。
 *
 * @author Cundong
 * @date 2013-9-6
 * @version 3.0
 */
public final class Constants {

	public static final boolean DEBUG = true;

	/** assets 里的旧 apk 与差分包文件名 */
	private static final String ASSET_OLD_APK = "old.apk";
	private static final String ASSET_PATCH = "update.patch";

	/** Apks/ 中淘宝 10.65.10 → 10.65.20 测试输入的 SHA-256 基准。 */
	public static final String OLD_APK_SHA256 = FixtureMetadata.OLD_APK_SHA256;
	public static final String PATCH_APK_SHA256 = FixtureMetadata.PATCH_APK_SHA256;
	public static final String NEW_APK_SHA256 = FixtureMetadata.NEW_APK_SHA256;

	/** 合成得到的新 apk 文件名 */
	private static final String NEW_APK_NAME = "taobao-10.65.20.apk";

	private Constants() {
		// 工具类，禁止实例化
	}

	/** assets 里的旧 apk 源文件名 */
	public static String assetOldApk() {
		return ASSET_OLD_APK;
	}

	/** assets 里的差分包源文件名 */
	public static String assetPatch() {
		return ASSET_PATCH;
	}

	/** 运行期旧 apk 文件（从 assets 解压到私有目录） */
	public static File oldApkFile(Context context) {
		return new File(context.getFilesDir(), "old.apk");
	}

	/** 运行期差分包文件（从 assets 解压到私有目录） */
	public static File patchFile(Context context) {
		return new File(context.getFilesDir(), "update.patch");
	}

	/** 合成输出的新 apk 文件 */
	public static File newApkFile(Context context) {
		File dir = context.getExternalFilesDir(null);
		if (dir == null) {
			dir = context.getFilesDir();
		}
		return new File(dir, NEW_APK_NAME);
	}
}
