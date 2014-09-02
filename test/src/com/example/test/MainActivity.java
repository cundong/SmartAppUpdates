package com.example.test;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.cundong.apkpatch.utils.ApkUtils;
import com.cundong.apkpatch.utils.SignUtils;
import com.cundong.utils.PatchUtils;

public class MainActivity extends Activity {

	private static final String TEST_PACKAGENAME = "com.sina.weibo";
	private static final String PATH = Environment
			.getExternalStorageDirectory() + File.separator;

	private String mOldApkName = "weiboV4.5.0.apk";
	private String mNewApkName = "weiboOldtoNew.apk";
	private String mPatchPath = "weiboPatch.apk";
	
	private ProgressBar mProgressBar;
	private TextView mResultView;
	private Button mStartButton, mGithubButton;

	private long mBeginTime = 0;
	private long mEndTime = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mProgressBar = (ProgressBar) findViewById(R.id.progressView1);
		mResultView = (TextView) findViewById(R.id.textview4);
		mStartButton = (Button) findViewById(R.id.start_btn);
		mGithubButton = (Button) findViewById(R.id.github_btn);
		
		mStartButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {

				mStartButton.setClickable(false);
				mResultView.setText("");
				mProgressBar.setVisibility(View.VISIBLE);
				
				Toast.makeText(MainActivity.this, "新apk合成中，请等待...", Toast.LENGTH_LONG).show();
				
				mBeginTime = System.currentTimeMillis();
				
				new PatchThread().start();
			}
		});
		
		mGithubButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent("android.intent.action.VIEW", Uri.parse("https://github.com/cundong/SmartAppUpdates"));
				startActivity(intent);
			}
		});
	}

	class PatchThread extends Thread {

		@Override
		public void run() {

			boolean copyApkSuccess = ApkUtils.copySourceApk(TEST_PACKAGENAME, PATH + mOldApkName);

			if (copyApkSuccess) {

				int patchResult = PatchUtils.patch(PATH + mOldApkName, PATH + mNewApkName, PATH + mPatchPath);

				if (patchResult == 0) {
					mHandler.sendEmptyMessage(0);
				} else {
					mHandler.sendEmptyMessage(-1);
				}
			} else {
				mHandler.sendEmptyMessage(-9999);
			}
		}
	}

	Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);

			int what = msg.what;

			mStartButton.setClickable(true);
			mProgressBar.setVisibility(View.GONE);

			if (what == -9999) {
				
				Toast.makeText(MainActivity.this,
						"无法获取packageName为" + TEST_PACKAGENAME,
						Toast.LENGTH_LONG).show();
				
			} else if (what == 0) {

				String s1 = SignUtils.getUnInstalledApkSignature(PATH
						+ mNewApkName);
				
				String s2 = SignUtils.InstalledApkSignature(MainActivity.this,
						TEST_PACKAGENAME);

				if (s1 != null && s1.equals(s2)) {
					Toast.makeText(MainActivity.this,
							"新apk已合成成功：" + PATH + mNewApkName, Toast.LENGTH_LONG)
							.show();
					
					mEndTime = System.currentTimeMillis();
					mResultView.setText("耗时: " + (mEndTime-mBeginTime) + "ms");
					ApkUtils.installApk(MainActivity.this, PATH + mNewApkName);
				} else {
					Toast.makeText(MainActivity.this, "新apk已合成失败，签名不一致",
							Toast.LENGTH_LONG).show();
				}
			} else {
				Toast.makeText(MainActivity.this, "新apk已合成失败",
						Toast.LENGTH_LONG).show();
			}
		}
	};

	static {
		System.loadLibrary("ApkPatchLibrary");
	}
}