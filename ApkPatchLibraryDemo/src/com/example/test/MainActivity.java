package com.example.test;

import java.io.File;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.cundong.utils.PatchUtils;
import com.example.test.util.ApkUtils;
import com.example.test.util.SignUtils;

/**
 * 类说明： ApkPatchLibrary使用Demo
 * 
 * @author  Cundong
 * @version 1.5
 */
public class MainActivity extends Activity {

	// 合成成功
	private static final int WHAT_SUCCESS = 1;

	// 合成的APK签名和已安装的签名不一致
	private static final int WHAT_FAIL_SING = -1;

	// 合成失败
	private static final int WHAT_FAIL_ERROR = -2;

	// 获取源文件失败
	private static final int WHAT_FAIL_GET_SOURCE = -3;

	private Context mContext = null;

	private ProgressDialog mProgressDialog;
	private TextView mResultView;
	private Button mStartButton, mGithubButton;

	private long mBeginTime, mEndTime;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mContext = getApplicationContext();

		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		mProgressDialog.setMessage("doing..");
		mProgressDialog.setCancelable(false);

		mResultView = (TextView) findViewById(R.id.textview4);
		mStartButton = (Button) findViewById(R.id.start_btn);
		mGithubButton = (Button) findViewById(R.id.github_btn);

		mStartButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {

				File patchFile = new File(Constants.PATCH_PATH);
				
				if (!ApkUtils.isInstalled(mContext, Constants.TEST_PACKAGENAME)) {
					Toast.makeText(mContext, getString(R.string.demo_info1),
							Toast.LENGTH_LONG).show();
				} else if (!patchFile.exists()) {
					Toast.makeText(mContext, getString(R.string.demo_info2),
							Toast.LENGTH_LONG).show();
				} else {
					new PatchApkTask().execute();
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
	
	private class PatchApkTask extends AsyncTask<String, Void, Integer> {

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			mProgressDialog.show();
			
			mResultView.setText("");
			mBeginTime = System.currentTimeMillis();
		}

		@Override
		protected Integer doInBackground(String... params) {

			String oldApkSource = ApkUtils.getSourceApkPath(mContext,
					Constants.TEST_PACKAGENAME);

			if (!TextUtils.isEmpty(oldApkSource)) {

				int patchResult = PatchUtils.patch(oldApkSource,
						Constants.NEW_APK_PATH, Constants.PATCH_PATH);

				if (patchResult == 0) {

					String signatureNew = SignUtils
							.getUnInstalledApkSignature(Constants.NEW_APK_PATH);

					String signatureSource = SignUtils
							.getInstalledApkSignature(mContext,
									Constants.TEST_PACKAGENAME);
					
					if (!TextUtils.isEmpty(signatureNew)
							&& !TextUtils.isEmpty(signatureSource)
							&& signatureNew.equals(signatureSource)) {
						return WHAT_SUCCESS;
					} else {
						return WHAT_FAIL_SING;
					}
				} else {
					return WHAT_FAIL_ERROR;
				}
			} else {
				return WHAT_FAIL_GET_SOURCE;
			}
		}
		
		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			
			if (mProgressDialog.isShowing()) {
				mProgressDialog.dismiss();
			}
			
			mEndTime = System.currentTimeMillis();
			mResultView.setText("耗时: " + (mEndTime - mBeginTime) + "ms");
			
			switch (result) {
				case WHAT_SUCCESS: {
					
					String text = "新apk已合成成功：" + Constants.NEW_APK_PATH;
					showShortToast(text);
					
					ApkUtils.installApk(MainActivity.this, Constants.NEW_APK_PATH);
					break;
				}
				case WHAT_FAIL_SING: {
					String text = "新apk已合成失败，签名不一致";
					showShortToast(text);
					break;
				}
				case WHAT_FAIL_ERROR: {
					String text = "新apk已合成失败";
					showShortToast(text);
					break;
				}
				case WHAT_FAIL_GET_SOURCE: {
					String text = "无法获取packageName为" + Constants.TEST_PACKAGENAME
							+ "的源apk文件，只能整包更新了！";
					showShortToast(text);
					break;
				}
			}
		}
	}
	
	private void showShortToast(final String text) {

		Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
	}
	
	static {
		System.loadLibrary("ApkPatchLibrary");
	}
}