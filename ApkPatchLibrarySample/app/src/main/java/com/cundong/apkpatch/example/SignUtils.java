package com.cundong.apkpatch.example;

import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;

/**
 * 文件 MD5 校验工具类。
 *
 * 增量更新流程中用于两处：
 *  1. 合成前：校验本机已安装旧 apk 的 MD5 是否与官方一致（识别二次打包篡改）；
 *  2. 合成后：校验合成出的新 apk 的 MD5 是否与官方新版一致（验证合成正确性）。
 *
 * @author Cundong
 * @date 2015-12-20
 * @version 1.1
 */
public final class SignUtils {

	private static final String TAG = "SignUtils";

	private SignUtils() {
		// 工具类，禁止实例化
	}

	private static String bytes2Hex(byte[] src) {
		char[] res = new char[src.length * 2];
		final char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7',
				'8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
		for (int i = 0, j = 0; i < src.length; i++) {
			res[j++] = hexDigits[src[i] >>> 4 & 0x0f];
			res[j++] = hexDigits[src[i] & 0x0f];
		}
		return new String(res);
	}

	/** 计算文件的 MD5（32 位小写十六进制），失败返回 null */
	private static String getMd5ByFile(File file) {
		String value = null;
		FileInputStream in = null;
		try {
			in = new FileInputStream(file);
			MessageDigest digester = MessageDigest.getInstance("MD5");
			byte[] bytes = new byte[8192];
			int byteCount;
			while ((byteCount = in.read(bytes)) > 0) {
				digester.update(bytes, 0, byteCount);
			}
			value = bytes2Hex(digester.digest());
		} catch (Exception e) {
			Log.e(TAG, "getMd5ByFile failed", e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
		return value;
	}

	/**
	 * 判断文件的 MD5 是否等于指定值。
	 *
	 * @param file 待校验文件
	 * @param md5  期望的 MD5（不能为空）
	 * @return 一致返回 true
	 */
	public static boolean checkMd5(File file, String md5) {
		if (TextUtils.isEmpty(md5)) {
			throw new IllegalArgumentException("md5 cannot be empty");
		}

		String fileMd5 = getMd5ByFile(file);

		if (Constants.DEBUG) {
			Log.d(TAG, String.format("file's md5=%s, expected md5=%s", fileMd5, md5));
		}

		return md5.equals(fileMd5);
	}

	/**
	 * 判断指定路径文件的 MD5 是否等于指定值。
	 */
	public static boolean checkMd5(String filePath, String md5) {
		return checkMd5(new File(filePath), md5);
	}
}
