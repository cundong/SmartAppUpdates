package com.cundong.apkpatch.utils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.DisplayMetrics;

/**
 * apk 签名工具类
 * 
 */
public class SignUtils {

	/**
	 * 获取未安装Apk的签名
	 * 
	 * @param apkPath
	 * @return
	 */
	public static String getUnInstalledApkSignature(String apkPath) {
		String PATH_PackageParser = "android.content.pm.PackageParser";
		try {
			// apk包的文件路径
			// 这是一个Package 解释器, 是隐藏的
			// 构造函数的参数只有一个, apk文件的路径
			Class<?> pkgParserCls = Class.forName(PATH_PackageParser);
			Class<?>[] typeArgs = new Class[1];
			typeArgs[0] = String.class;
			Constructor<?> pkgParserCt = pkgParserCls.getConstructor(typeArgs);
			Object[] valueArgs = new Object[1];
			valueArgs[0] = apkPath;
			Object pkgParser = pkgParserCt.newInstance(valueArgs);

			// 这个是与显示有关的, 里面涉及到一些像素显示等等, 我们使用默认的情况
			DisplayMetrics metrics = new DisplayMetrics();
			metrics.setToDefaults();
			// PackageParser.Package mPkgInfo = packageParser.parsePackage(new
			// File(apkPath), apkPath,
			// metrics, 0);
			typeArgs = new Class[4];
			typeArgs[0] = File.class;
			typeArgs[1] = String.class;
			typeArgs[2] = DisplayMetrics.class;
			typeArgs[3] = Integer.TYPE;
			Method pkgParser_parsePackageMtd = pkgParserCls.getDeclaredMethod(
					"parsePackage", typeArgs);
			valueArgs = new Object[4];
			valueArgs[0] = new File(apkPath);
			valueArgs[1] = apkPath;
			valueArgs[2] = metrics;
			valueArgs[3] = PackageManager.GET_SIGNATURES;
			Object pkgParserPkg = pkgParser_parsePackageMtd.invoke(pkgParser,
					valueArgs);

			typeArgs = new Class[2];
			typeArgs[0] = pkgParserPkg.getClass();
			typeArgs[1] = Integer.TYPE;
			Method pkgParser_collectCertificatesMtd = pkgParserCls
					.getDeclaredMethod("collectCertificates", typeArgs);
			valueArgs = new Object[2];
			valueArgs[0] = pkgParserPkg;
			valueArgs[1] = PackageManager.GET_SIGNATURES;
			pkgParser_collectCertificatesMtd.invoke(pkgParser, valueArgs);
			// 应用程序信息包, 这个公开的, 不过有些函数, 变量没公开
			Field packageInfoFld = pkgParserPkg.getClass().getDeclaredField(
					"mSignatures");
			Signature[] info = (Signature[]) packageInfoFld.get(pkgParserPkg);
			return info[0].toCharsString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}

	/**
	 * 获取已安装apk签名
	 * 
	 * @param context
	 * @param packageName
	 * @return
	 */
	public static String InstalledApkSignature(Context context, String packageName) {
		PackageManager pm = context.getPackageManager();
		List<PackageInfo> apps = pm
				.getInstalledPackages(PackageManager.GET_SIGNATURES);

		Iterator<PackageInfo> iter = apps.iterator();
		while (iter.hasNext()) {
			PackageInfo packageinfo = iter.next();
			String thisName = packageinfo.packageName;
			if (thisName.equals(packageName)) {
				return packageinfo.signatures[0].toCharsString();
			}
		}
		
		return null;
	}
}
