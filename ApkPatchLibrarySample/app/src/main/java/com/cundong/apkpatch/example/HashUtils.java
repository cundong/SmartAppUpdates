package com.cundong.apkpatch.example;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 文件摘要工具。 */
public final class HashUtils {

	private HashUtils() {
		// 工具类，禁止实例化
	}

	/** 流式计算文件 SHA-256，避免把大 APK 整体读入 Java 堆。 */
	public static String sha256(File file) throws IOException {
		if (file == null || !file.isFile()) {
			throw new IOException("file does not exist: " + file);
		}

		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}

		try (FileInputStream input = new FileInputStream(file)) {
			byte[] buffer = new byte[64 * 1024];
			int count;
			while ((count = input.read(buffer)) != -1) {
				digest.update(buffer, 0, count);
			}
		}

		return toHex(digest.digest());
	}

	private static String toHex(byte[] bytes) {
		char[] hex = new char[bytes.length * 2];
		final char[] digits = "0123456789abcdef".toCharArray();
		for (int i = 0, j = 0; i < bytes.length; i++) {
			int value = bytes[i] & 0xff;
			hex[j++] = digits[value >>> 4];
			hex[j++] = digits[value & 0x0f];
		}
		return new String(hex);
	}
}
