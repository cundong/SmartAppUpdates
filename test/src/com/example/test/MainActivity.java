package com.example.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import com.cundong.utils.PatchUtils;

/**
 * 一个例子： 假设手机上已经安装了WeiboV3，点击“升级检测”按钮
 * 
 */
public class MainActivity extends Activity {

	private String PATH = Environment.getExternalStorageDirectory()
			+ File.separator;
	private String oldapk_filepath = "WeiboV3.apk";

	private String newapk_savepath = "cundong.apk";

	private String patchpath = "weibopatch.apk";

	private Button mButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//
		mButton = (Button) findViewById(R.id.button1);
		mButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				Toast.makeText(MainActivity.this, "新apk合成中，请等待...",
						Toast.LENGTH_LONG).show();
				new Thread(new PatchThread()).start();
			}
		});

		Log.i("@Cundong", "-->" + PATH);
	}

	Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);

			int what = msg.what;

			if (what == 0) {
				Toast.makeText(MainActivity.this,
						"新apk已合成成功，" + PATH + newapk_savepath,
						Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(MainActivity.this, "新apk已合成失败",
						Toast.LENGTH_LONG).show();
			}

			installAPK(PATH + "WeiboV3.apk");
		}
	};

	class PatchThread implements Runnable {

		@Override
		public void run() {

			backupApplication("com.sina.weibo", PATH + "WeiboV3.apk");

			int ret = PatchUtils.patch(PATH + oldapk_filepath, PATH
					+ newapk_savepath, PATH + patchpath);

			mHandler.sendEmptyMessage(ret);
		}
	}

	static {
		System.loadLibrary("apkpatch");
	}

	/**
	 * 安装APK
	 * 
	 * @param apkUrl
	 */
	private void installAPK(String apkUrl) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(Uri.parse("file://" + apkUrl),
				"application/vnd.android.package-archive");
		startActivity(intent);// 安装
	}

	/**
	 * 
	 * @Description 将app由data/app目录拷贝到sd卡下的指定目录中
	 * 
	 * @param appId
	 *            应用程序的ID号，如com.wondertek.jttxl
	 * 
	 * @param dest
	 *            需要将应用程序拷贝的目标位置
	 * 
	 * @date 2013-7-24 下午3:32:12
	 */
	private String backupApplication(String packageName, String dest) {

		if (packageName == null || packageName.length() == 0

		|| dest == null || dest.length() == 0) {
			return "illegal parameters";
		}

		// check file /data/app/appId-1.apk exists

		String apkPath = "/data/app/" + packageName + "-1.apk";

		File apkFile = new File(apkPath);

		if (apkFile.exists() == false) {
			return apkPath + " doesn't exist!";
		}

		FileInputStream in = null;

		try {
			in = new FileInputStream(apkFile);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return e.getMessage();
		}

		// create dest folder if necessary

		int i = dest.lastIndexOf('/');

		if (i != -1) {
			File dirs = new File(dest.substring(0, i));
			dirs.mkdirs();
			dirs = null;
		}

		// do file copy operation

		byte[] c = new byte[1024];

		int slen;

		FileOutputStream out = null;

		try {
			out = new FileOutputStream(dest);

			while ((slen = in.read(c, 0, c.length)) != -1)
				out.write(c, 0, slen);
		} catch (IOException e) {
			e.printStackTrace();
			return e.getMessage();
		} finally {
			if (out != null)
				try {
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
					return e.getMessage();
				}
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
					return e.getMessage();
				}
			}
		}

		return "success";

	}
}