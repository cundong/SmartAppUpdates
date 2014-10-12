package com.example.test;

import java.io.File;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.cundong.utils.PatchUtils;
import com.example.test.util.ApkUtils;
import com.example.test.util.SignUtils;
import com.example.test2.R;

/**
 * 类说明：	ApkPatchLibrary使用Demo
 * 
 * @author 	cundong
 * @version 1.4
 */
public class MainActivity extends Activity {
	
	private Context mContext = null;
	
	private ProgressDialog mProgressDialog;
	private TextView mResultView;
	private Button mStartButton, mGithubButton;

	private long mBeginTime = 0;
	private long mEndTime = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mContext = this.getApplicationContext();
		
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);// 设置风格为圆形进度条
		mProgressDialog.setMessage("新版Apk合成中，请稍候...");
		mProgressDialog.setCancelable(false);
		
		mResultView = (TextView) findViewById(R.id.textview4);
		mStartButton = (Button) findViewById(R.id.start_btn);
		mGithubButton = (Button) findViewById(R.id.github_btn);

		mStartButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				
				File patchFile = new File( Constants.PATCH_PATH );
				
				if(!ApkUtils.isInstalled(mContext, Constants.TEST_PACKAGENAME)) {
					Toast.makeText(mContext,
							getString(R.string.demo_info1),
							Toast.LENGTH_LONG).show();
				} else if (!patchFile.exists()) {
					Toast.makeText(mContext,
							getString(R.string.demo_info2),
							Toast.LENGTH_LONG).show();
				} else {
					mResultView.setText("");
					mProgressDialog.show();
					
					mBeginTime = System.currentTimeMillis();
					
					new PatchThread().start();
				}
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

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
		}
	}
	
	/**
	 * apk合成，线程
	 */
	class PatchThread extends Thread {

		@Override
		public void run() {
			
			String oldApkSource = ApkUtils.getSourceApkPath(mContext, Constants.TEST_PACKAGENAME);
			
			if (!TextUtils.isEmpty(oldApkSource)) {

				int patchResult = PatchUtils.patch(Constants.OLD_APK_PATH, Constants.NEW_APK_PATH, Constants.PATCH_PATH);

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
			
			if (mProgressDialog.isShowing()) {
				mProgressDialog.dismiss();
			}
			
			switch(what) {
				case -9999:
					Toast.makeText(mContext,
							"无法获取packageName为" + Constants.TEST_PACKAGENAME + "的源apk文件",
							Toast.LENGTH_LONG).show();
					break;
				case 0:
					String signatureNew = SignUtils.getUnInstalledApkSignature(Constants.NEW_APK_PATH);
					
					String signatureSource = SignUtils.InstalledApkSignature(mContext,
							Constants.TEST_PACKAGENAME);

					if ( !TextUtils.isEmpty(signatureNew) && !TextUtils.isEmpty(signatureSource) && signatureNew.equals(signatureSource)) {
						Toast.makeText(mContext,
								"新apk已合成成功：" + Constants.NEW_APK_PATH,
								Toast.LENGTH_LONG).show();

						mEndTime = System.currentTimeMillis();
						mResultView
								.setText("耗时: " + (mEndTime - mBeginTime) + "ms");
						ApkUtils.installApk(MainActivity.this, Constants.NEW_APK_PATH);
					} else {
					Toast.makeText(mContext, "新apk已合成失败，签名不一致",
							Toast.LENGTH_LONG).show();
					}
					break;
				default:
				Toast.makeText(mContext, "新apk已合成失败", Toast.LENGTH_LONG).show();
			}
		}
	};

	static {
		System.loadLibrary("ApkPatchLibrary");
	}
}