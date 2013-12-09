package com.cundong.apkupdate;

import com.cundong.utils.DiffUtils;
import com.cundong.utils.PatchUtils;

/**
 * 类说明： Test
 * 
 * @author Cundong
 * @date 2013-9-6
 * @version 1.0
 */
public class APKTest {

	static {
		System.loadLibrary("AppUpdate");
	}

	public static void main(String[] args) {

		long start = System.currentTimeMillis();

		// 旧版本新浪微博，V3.0
		String oldPath = "/Users/cundong/Downloads/weiboV3.apk";

		// 新版本新浪微博，V4.0
		String newPath = "/Users/cundong/Downloads/weiboV4.apk";

		// patch包存储路径
		String patchPath = "/Users/cundong/Downloads/weiboPatch.apk";

		//使用旧版本新浪微博+patch包，合成的新版微博
		String thenewPath = "/Users/cundong/Desktop/weiboOld2New.apk";
		
		//1.
		System.out.println("开始生成差异包，请等待...");

		DiffUtils.genDiff(oldPath, newPath, patchPath);

		long end = System.currentTimeMillis();

		System.out.println("生成差异包成功，" + patchPath + "，耗时：" + (end - start)
				/ 1000 + "秒！！");

		//2.
		System.out.println("开始合成新包.");
		start = System.currentTimeMillis();
		
		PatchUtils.patch(oldPath, thenewPath, patchPath);

		end = System.currentTimeMillis();

		System.out.println("合成新包成功，" + thenewPath + "，耗时：" + (end - start)
				/ 1000 + "秒！！");
	}
}