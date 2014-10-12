package com.example.test;

import java.io.File;

import android.os.Environment;

public class Constants {

	//用于测试的packageName
	public static final String TEST_PACKAGENAME = "com.sina.weibo";
	
	public static final String PATH = Environment.getExternalStorageDirectory() + File.separator;

	//旧版本微博客户端的目录
	public static final String OLD_APK_PATH = PATH + "weiboV4.5.0.apk";
	
	//合成得到的新版微博
	public static final String NEW_APK_PATH = PATH + "weiboOldtoNew.apk";
	
	//从服务器下载来的查分包
	public static final String PATCH_PATH = PATH + "weibo.patch";
}