package com.cundong.apkpatch.utils;

import java.io.File;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;

/**
 * apk 工具类
 * 
 */
public class ApkUtils {

	public static boolean isInstalled(Context context, String packageName) {
		PackageManager pm = context.getPackageManager();
		boolean installed = false;
		try {
			pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
			installed = true;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return installed;
	}

	/**
	 * 根据packageName，获取当前app的源apk，并拷贝到指定目录下
	 * 
	 * @param packageName
	 * @param destPath
	 * @return
	 */
	public static boolean copySourceApk(String packageName, String destPath) {

		if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(destPath))
			return false;

		String sourceApkPath = getSourceApkPath(packageName);

		if (TextUtils.isEmpty(sourceApkPath)) {
			return false;
		}

		return FileUtils.copyFile(sourceApkPath, destPath);
	}

	public static String getSourceApkPath(String packageName) {
		if (TextUtils.isEmpty(packageName))
			return null;

		String sourceApkPath = new StringBuilder().append("/data/app/")
				.append(packageName).append("-1.apk").toString();

		File apkFile = new File(sourceApkPath);
		if (!apkFile.exists()) {
			sourceApkPath = new StringBuilder().append("/data/app/")
					.append(packageName).append("-2.apk").toString();
			apkFile = new File(sourceApkPath);

			if (apkFile.exists()) {
				return sourceApkPath;
			}
		} else {
			return sourceApkPath;
		}

		return null;
	}

	/**
	 * 安装APK
	 * 
	 * @param context
	 * @param apkPath
	 */
	public static void installApk(Context context, String apkPath) {

		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(Uri.parse("file://" + apkPath),
				"application/vnd.android.package-archive");

		context.startActivity(intent);
	}
}