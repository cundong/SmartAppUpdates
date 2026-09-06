package com.cundong.apkpatch.example;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Apk 相关工具类：查询安装信息、取已安装 apk 源文件路径、发起安装。
 *
 * @author Cundong
 * @version 2.0
 * @date 2013-9-6
 */
public final class ApkUtils {


	private ApkUtils() {
		// 工具类，禁止实例化
	}

	/**
	 * 获取已安装应用的 PackageInfo；未安装或不可见（Android 11+ 包可见性）时返回 null。
	 *
	 * 注：int flags 重载在 API 33 被标记废弃，但在所有版本上行为一致；
	 * 沿用旧接口以兼容 minSdk 21。
	 */
	@SuppressWarnings("deprecation")
	public static PackageInfo getInstalledApkPackageInfo(Context context, String packageName) {
		try {
			return context.getPackageManager().getPackageInfo(packageName, 0);
		} catch (PackageManager.NameNotFoundException e) {
			return null;
		}
	}

	/**
	 * 判断指定包名的应用是否已安装。
	 */
	public static boolean isInstalled(Context context, String packageName) {
		return getInstalledApkPackageInfo(context, packageName) != null;
	}

	/**
	 * 获取已安装应用的源 apk 文件路径，如 /data/app/xxx/base.apk。
	 * 该路径对本应用可读，可直接作为 bspatch 的 old 文件。
	 */
	@SuppressWarnings("deprecation")
	public static String getSourceApkPath(Context context, String packageName) {
		if (TextUtils.isEmpty(packageName)) {
			return null;
		}

		try {
			ApplicationInfo appInfo = context.getPackageManager()
					.getApplicationInfo(packageName, 0);
			return appInfo.sourceDir;
		} catch (PackageManager.NameNotFoundException e) {
			return null;
		}
	}

	/**
	 * 通过 PackageInstaller 安装 apk（系统会弹出安装确认界面）。
	 *
	 * <p>不依赖 FileProvider / androidx，纯 framework 实现。会先解析 apk 的真实包名，
	 * 再把文件写入安装会话；状态由 {@link InstallResultReceiver} 处理。</p>
	 *
	 * @param context 上下文
	 * @param apkFile 待安装的 apk 文件
	 * @throws IOException 读取 apk 或写入安装会话失败
	 */
	@SuppressWarnings("deprecation")
	public static void installApk(Context context, File apkFile) throws IOException {
		if (apkFile == null || !apkFile.isFile() || apkFile.length() <= 0) {
			throw new IOException("待安装 APK 不存在或为空");
		}

		PackageInfo archiveInfo = context.getPackageManager()
				.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
		if (archiveInfo == null || TextUtils.isEmpty(archiveInfo.packageName)) {
			throw new IOException("无法解析待安装 APK 的包名");
		}

		if (!FixtureMetadata.PACKAGE_NAME.equals(archiveInfo.packageName)
                || !FixtureMetadata.NEW_VERSION_NAME.equals(archiveInfo.versionName)
                || (Build.VERSION.SDK_INT >= 28 ? archiveInfo.getLongVersionCode() : archiveInfo.versionCode)
                    != FixtureMetadata.NEW_VERSION_CODE) {
            throw new IOException("合成 APK 的包名或版本与淘宝测试输入不一致");
        }

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();

		PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
				PackageInstaller.SessionParams.MODE_FULL_INSTALL);
		params.setAppPackageName(archiveInfo.packageName);

		int sessionId = installer.createSession(params);
		boolean committed = false;
		try {
			try (PackageInstaller.Session session = installer.openSession(sessionId)) {
				try (InputStream in = new FileInputStream(apkFile);
						OutputStream out = session.openWrite("base.apk", 0, apkFile.length())) {
					byte[] buffer = new byte[64 * 1024];
					int len;
					while ((len = in.read(buffer)) != -1) {
						out.write(buffer, 0, len);
					}
					session.fsync(out);
				}

				Intent callbackIntent = new Intent(context, InstallResultReceiver.class);
				callbackIntent.setAction(InstallResultReceiver.ACTION_INSTALL_STATUS);
				callbackIntent.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
				int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
				if (Build.VERSION.SDK_INT >= 31) {
					// PackageInstaller 需要向回调补充 status/confirmation Intent，必须可变。
					pendingIntentFlags |= PendingIntent.FLAG_MUTABLE;
				}
				PendingIntent statusReceiver = PendingIntent.getBroadcast(context,
						sessionId, callbackIntent, pendingIntentFlags);
				session.commit(statusReceiver.getIntentSender());
				committed = true;
			}
		} finally {
			if (!committed) {
				installer.abandonSession(sessionId);
			}
		}
	}
}
