package com.cundong.apkpatch.example;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.cundong.utils.PatchUtils;

import java.io.File;

/**
 * 类说明：   ApkPatchLibrary 使用Sample
 *
 * @author  Cundong
 * @version 1.5
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = Constants.DEBUG ? "MainActivity" : MainActivity.class.getSimpleName();

    // 成功
    private static final int WHAT_SUCCESS = 1;

    // 本地安装的微博MD5不正确
    private static final int WHAT_FAIL_OLD_MD5 = -1;

    // 新生成的微博MD5不正确
    private static final int WHAT_FAIL_GEN_MD5 = -2;

    // 合成失败
    private static final int WHAT_FAIL_PATCH = -3;

    // 获取源文件失败
    private static final int WHAT_FAIL_GET_SOURCE = -4;

    // 未知错误
    private static final int WHAT_FAIL_UNKNOWN = -5;

    private Context mContext = null;

    private ProgressDialog mProgressDialog;
    private TextView mResultView;
    private Button mStartButton, mGithubButton;

    private long mBeginTime, mEndTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mContext = getApplicationContext();

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setMessage("doing..");
        mProgressDialog.setCancelable(false);

        mResultView = (TextView) findViewById(R.id.textview4);

        mStartButton = (Button) findViewById(R.id.start_btn);
        mGithubButton = (Button) findViewById(R.id.github_btn);
        mStartButton.setOnClickListener(this);
        mGithubButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v == mStartButton) {
            File patchFile = new File(Constants.PATCH_PATH);

            if (!ApkUtils.isInstalled(mContext, Constants.TEST_PACKAGENAME)) {
                Toast.makeText(mContext, getString(R.string.demo_info1), Toast.LENGTH_LONG).show();
            } else if (!patchFile.exists()) {
                Toast.makeText(mContext, getString(R.string.demo_info2), Toast.LENGTH_LONG).show();
            } else {
                new PatchApkTask().execute();
            }
        } else if (v == mGithubButton) {
            Intent intent = new Intent("android.intent.action.VIEW", Uri.parse("https://github.com/cundong/SmartAppUpdates"));
            startActivity(intent);
        }
    }

    private String mCurentRealMD5, mNewRealMD5;

    /**
     * 模拟请求服务器，根据当前安装微博客户端的versionCode、versionName，来获取其文件的正确MD5，防止本地安装的是被篡改的版本
     */
    private void requestOldMD5(int versionCode, String versionName) {
        mCurentRealMD5 = Constants.WEIBO_OLD_MD5;
        mNewRealMD5 = Constants.WEIBO_NEW_MD5;
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

            PackageInfo packageInfo = ApkUtils.getInstalledApkPackageInfo(mContext, Constants.TEST_PACKAGENAME);

            if (packageInfo != null) {

                requestOldMD5(packageInfo.versionCode, packageInfo.versionName);

                String oldApkSource = ApkUtils.getSourceApkPath(mContext, Constants.TEST_PACKAGENAME);

                if (!TextUtils.isEmpty(oldApkSource)) {

                    // 校验一下本地安装APK的MD5是不是和真实的MD5一致
                    if (SignUtils.checkMd5(oldApkSource, mCurentRealMD5)) {
                        int patchResult = PatchUtils.patch(oldApkSource, Constants.NEW_APK_PATH, Constants.PATCH_PATH);

                        if (patchResult == 0) {

                            if (SignUtils.checkMd5(Constants.NEW_APK_PATH, mNewRealMD5)) {
                                return WHAT_SUCCESS;
                            } else {
                                return WHAT_FAIL_GEN_MD5;
                            }
                        } else {
                            return WHAT_FAIL_PATCH;
                        }
                    } else {
                        return WHAT_FAIL_OLD_MD5;
                    }
                } else {
                    return WHAT_FAIL_GET_SOURCE;
                }
            } else {
                return WHAT_FAIL_UNKNOWN;
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
                    showToast(text);

                    ApkUtils.installApk(MainActivity.this, Constants.NEW_APK_PATH);
                    break;
                }
                case WHAT_FAIL_OLD_MD5: {
                    String text = "现在安装的WeiboV5.5的MD5不对";
                    showToast(text);
                    break;
                }
                case WHAT_FAIL_GEN_MD5: {
                    String text = "合成完毕，但是合成得到的APK MD5不对";
                    showToast(text);
                    break;
                }
                case WHAT_FAIL_PATCH: {
                    String text = "新APK已合成失败";
                    showToast(text);
                    break;
                }
                case WHAT_FAIL_GET_SOURCE: {
                    String text = "无法获取微博客户端的源apk文件，只能整包更新了！";
                    showToast(text);
                    break;
                }
            }
        }
    }

    private void showToast(final String text) {
        Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    static {
        System.loadLibrary("ApkPatchLibrary");
    }
}