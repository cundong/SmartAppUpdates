package com.cundong.apkpatch;

import com.cundong.common.Constants;
import com.cundong.utils.PatchUtils;

/**
 * 类说明：  使用旧版apk包+差分包，合并新包实例
 * 
 * @author  Cundong
 * @date    2014-9-6
 * @version 1.2
 */
public class ApkPatchDemo {

	public static void main(String[] args) {

		long start = System.currentTimeMillis();
		
		System.out.println("开始合成新包，请等待...");
		
		start = System.currentTimeMillis();

		int patchResult = PatchUtils.patch(Constants.OLD_APK, Constants.OLD_2_NEW_APK, Constants.PATCH_FILE);

		long end = System.currentTimeMillis();

		System.out.println("合成新包成功：" + Constants.NEW_APK + "，耗时：" + (end - start)
				/ 1000 + "秒，patchResult=" + patchResult);
	}

	static {
		System.loadLibrary("ApkPatchLibraryServer");
	}
}