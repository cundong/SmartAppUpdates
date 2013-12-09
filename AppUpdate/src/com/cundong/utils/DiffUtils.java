package com.cundong.utils;

/**
 * 类说明： APK工具类
 * 
 * @author Cundong
 * @date 2013-9-6
 * @version 1.0
 */
public class DiffUtils {

	/**
	 * 本地方法 比较路径为oldPath的apk与newPath的apk之间差异，并生成patch包，存储于patchPath
	 * 
	 * @param oldPath
	 * @param newPath
	 * @param patchPath
	 * @return
	 */
	public static native int genDiff(String oldApkPath, String newApkPath,
			String patchPath);
}