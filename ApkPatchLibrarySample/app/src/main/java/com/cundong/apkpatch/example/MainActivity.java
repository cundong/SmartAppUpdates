package com.cundong.apkpatch.example;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.cundong.utils.PatchUtils;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ApkPatchLibrary 使用演示。
 *
 * 完整流程（点击 Start 后）：
 *  1. 将打包在 assets/ 里的 old.apk 与 update.patch 解压到应用私有目录；
 *  2. 校验输入文件 SHA-256，确保内置测试夹具未损坏；
 *  3. 调用 {@link PatchUtils#patch} 将 old apk + 差分包合成为新 apk（子线程）；
 *  4. 校验输出 SHA-256，确认合成结果与预期 new.apk 逐字节一致。
 *  5. 校验成功后自动创建 PackageInstaller 会话并拉起系统安装确认页。
 *
 * 现代化说明（相对 1.x 版本）：
 *  - 纯 framework，无 appcompat/support 依赖；
 *  - AsyncTask → ExecutorService + Handler；
 *  - 文件放应用私有目录（分区存储，免存储权限）；
 *  - 不依赖网络、adb push 或预装旧版 App；安装仍由系统界面要求用户确认。
 *
 * @author Cundong
 * @version 3.0
 */
public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_UNKNOWN_APP_SOURCES = 1001;
    private static final String STATE_PENDING_INSTALL_PATH = "pending_install_path";
    /** 防止 Activity 重建后两个任务并发覆盖同一组 old/patch/new 文件。 */
    private static final AtomicBoolean PATCH_IN_PROGRESS = new AtomicBoolean(false);

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private Button mStartButton;
    private TextView mResultView;
    private long mBeginTime;
    private String mPendingInstallPath;
    private volatile boolean mDestroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        content.requestApplyInsets();

        mResultView = findViewById(R.id.result_view);

        mStartButton = findViewById(R.id.start_btn);
        Button githubButton = findViewById(R.id.github_btn);
        mStartButton.setOnClickListener(this);
        githubButton.setOnClickListener(this);

        if (savedInstanceState != null) {
            mPendingInstallPath = savedInstanceState.getString(STATE_PENDING_INSTALL_PATH);
            if (mPendingInstallPath != null) {
                InstallResultReceiver.restoreInstallInProgress();
                mStartButton.setEnabled(false);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_INSTALL_PATH, mPendingInstallPath);
    }

    @Override
    protected void onResume() {
        super.onResume();
        InstallResultReceiver.onActivityResumed(this);
        if (InstallResultReceiver.isInstallInProgress()) {
            mStartButton.setEnabled(false);
        }
        if (mPendingInstallPath != null
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls())) {
            String pendingPath = mPendingInstallPath;
            mPendingInstallPath = null;
            stageAndCommitInstall(new File(pendingPath));
        }
    }

    @Override
    protected void onPause() {
        InstallResultReceiver.onActivityPaused(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mDestroyed = true;
        mMainHandler.removeCallbacksAndMessages(null);
        mExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.start_btn) {
            if (InstallResultReceiver.isInstallInProgress()) {
                showToast(getString(R.string.toast_install_in_progress));
                return;
            }
            if (!PATCH_IN_PROGRESS.compareAndSet(false, true)) {
                showToast(getString(R.string.toast_patch_in_progress));
                return;
            }
            mStartButton.setEnabled(false);
            mResultView.setText(R.string.status_copying_assets);
            mBeginTime = System.currentTimeMillis();
            try {
                mExecutor.execute(this::patchInBackground);
            } catch (RejectedExecutionException e) {
                PATCH_IN_PROGRESS.set(false);
                mStartButton.setEnabled(true);
                Log.e(TAG, "patch executor rejected task", e);
                showToast(getString(R.string.toast_patch_start_failure));
            }
        } else if (v.getId() == R.id.github_btn) {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/cundong/SmartAppUpdates"));
            startActivity(intent);
        }
    }

    /** 子线程执行合成，结果切回主线程处理 */
    private void patchInBackground() {
        LocalPatchResult patchResult;
        try {
            patchResult = doPatch();
        } catch (RuntimeException e) {
            Log.e(TAG, "unexpected patch failure", e);
            patchResult = LocalPatchResult.failure("合成任务异常："
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            PATCH_IN_PROGRESS.set(false);
        }
        if (mDestroyed) {
            return;
        }

        LocalPatchResult result = patchResult;
        mMainHandler.post(() -> {
            if (!mDestroyed) {
                onPatchResult(result);
            }
        });
    }

    /**
     * 合成主流程（运行在子线程）。
     */
    private LocalPatchResult doPatch() {
        // 1. 从 assets 解压 old.apk / update.patch 到私有目录
        File oldApkFile = Constants.oldApkFile(this);
        File patchFile = Constants.patchFile(this);
        try {
            copyAsset(Constants.assetOldApk(), oldApkFile);
            copyAsset(Constants.assetPatch(), patchFile);
        } catch (IOException e) {
            Log.e(TAG, "copy asset failed", e);
            return LocalPatchResult.failure("复制 assets 失败：" + e.getMessage());
        }

        // 2. 校验内置输入，避免损坏/错配的测试夹具进入 native 层
        publishStatus(R.string.status_verifying_inputs);
        try {
            String oldSha256 = HashUtils.sha256(oldApkFile);
            if (!Constants.OLD_APK_SHA256.equals(oldSha256)) {
                return LocalPatchResult.failure("old.apk SHA-256 不匹配：" + oldSha256);
            }
            String patchSha256 = HashUtils.sha256(patchFile);
            if (!Constants.PATCH_APK_SHA256.equals(patchSha256)) {
                return LocalPatchResult.failure("update.patch SHA-256 不匹配：" + patchSha256);
            }
        } catch (IOException e) {
            Log.e(TAG, "verify input failed", e);
            return LocalPatchResult.failure("读取输入文件失败：" + e.getMessage());
        }

        // 3. 合成：old.apk + update.patch → new.apk
        publishStatus(R.string.status_patching);
        File newApkFile = Constants.newApkFile(this);
        if (newApkFile.exists() && !newApkFile.delete()) {
            return LocalPatchResult.failure("无法清理上一次合成结果："
                    + newApkFile.getAbsolutePath());
        }

        int patchResult;
        try {
            patchResult = PatchUtils.patch(oldApkFile.getAbsolutePath(),
                    newApkFile.getAbsolutePath(), patchFile.getAbsolutePath());
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "load native library failed", e);
            return LocalPatchResult.failure("当前设备 ABI 无法加载 libApkPatchLibrary.so");
        }
        if (patchResult != PatchUtils.SUCCESS) {
            Log.e(TAG, "patch failed, error code = " + patchResult);
            deleteInvalidOutput(newApkFile);
            return LocalPatchResult.failure("native 合成失败，错误码：" + patchResult);
        }

        // 4. 校验输出。只有与 Apks/淘宝v10.65.20.apk 的基准摘要一致才算演示成功。
        publishStatus(R.string.status_verifying_output);
        try {
            String newSha256 = HashUtils.sha256(newApkFile);
            if (!Constants.NEW_APK_SHA256.equals(newSha256)) {
                deleteInvalidOutput(newApkFile);
                return LocalPatchResult.failure("输出 SHA-256 不匹配：" + newSha256);
            }
            return LocalPatchResult.success(newApkFile, newSha256);
        } catch (IOException e) {
            Log.e(TAG, "verify output failed", e);
            deleteInvalidOutput(newApkFile);
            return LocalPatchResult.failure("读取合成结果失败：" + e.getMessage());
        }
    }

    /** 主线程处理合成结果 */
    private void onPatchResult(LocalPatchResult result) {
        long elapsed = System.currentTimeMillis() - mBeginTime;

        if (result.success) {
            String text = getString(R.string.result_success,
                    elapsed,
                    formatMiB(result.outputFile.length()),
                    result.outputFile.getAbsolutePath(),
                    result.sha256);
            mResultView.setText(text);
            showToast(getString(R.string.toast_patch_success));
            requestSystemInstall(result.outputFile);
        } else {
            mStartButton.setEnabled(true);
            mResultView.setText(getString(R.string.result_failure, elapsed, result.message));
            showToast(getString(R.string.toast_patch_failure));
        }
    }

    /**
     * Android 8.0+ 首次侧载需要用户明确允许当前 Demo 作为安装来源；授权后自动继续。
     */
    private void requestSystemInstall(File apkFile) {
        if (!InstallResultReceiver.tryBeginInstall()) {
            mStartButton.setEnabled(false);
            showToast(getString(R.string.toast_install_in_progress));
            return;
        }
        mStartButton.setEnabled(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            mPendingInstallPath = apkFile.getAbsolutePath();
            mStartButton.setEnabled(false);
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivityForResult(settingsIntent, REQUEST_UNKNOWN_APP_SOURCES);
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.e(TAG, "unknown-app-sources settings is unavailable", e);
                mPendingInstallPath = null;
                InstallResultReceiver.abortInstall();
                mStartButton.setEnabled(true);
                showToast(getString(R.string.toast_install_settings_unavailable));
            }
            return;
        }

        stageAndCommitInstall(apkFile);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_UNKNOWN_APP_SOURCES) {
            return;
        }

        String pendingPath = mPendingInstallPath;
        mPendingInstallPath = null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls()) {
            if (pendingPath != null) {
                stageAndCommitInstall(new File(pendingPath));
            }
        } else {
            InstallResultReceiver.abortInstall();
            mStartButton.setEnabled(true);
            showToast(getString(R.string.toast_install_permission_denied));
        }
    }

    /** 将大 APK 写入 PackageInstaller 会话，避免在主线程复制约百兆文件。 */
    private void stageAndCommitInstall(File apkFile) {
        if (mDestroyed) {
            return;
        }
        if (!InstallResultReceiver.isInstallInProgress()) {
            InstallResultReceiver.restoreInstallInProgress();
        }
        mStartButton.setEnabled(false);
        mResultView.append("\n" + getString(R.string.status_preparing_install));
        try {
            mExecutor.execute(() -> {
                try {
                    ApkUtils.installApk(getApplicationContext(), apkFile);
                    Log.i(TAG, "PackageInstaller session committed");
                } catch (IOException | RuntimeException e) {
                    Log.e(TAG, "prepare package installer session failed", e);
                    InstallResultReceiver.abortInstall();
                    if (!mDestroyed) {
                        mMainHandler.post(() -> {
                            if (!mDestroyed) {
                                mStartButton.setEnabled(true);
                                showToast(getString(R.string.toast_install_prepare_failure,
                                        e.getMessage() == null
                                            ? e.getClass().getSimpleName()
                                            : e.getMessage()));
                            }
                        });
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            InstallResultReceiver.abortInstall();
            mStartButton.setEnabled(true);
            Log.e(TAG, "install executor rejected task", e);
            showToast(getString(R.string.toast_install_prepare_failure,
                    e.getClass().getSimpleName()));
        }
    }

    /** 仅由 InstallResultReceiver 在当前 Activity 可见时调用。 */
    void launchInstallConfirmation(Intent confirmationIntent) {
        if (mDestroyed) {
            throw new IllegalStateException("Activity 已销毁");
        }
        startActivity(confirmationIntent);
        mResultView.append("\n" + getString(R.string.status_install_confirmation_launched));
    }

    /** 将 PackageInstaller 最终结果同步回前台页面。 */
    void onInstallFinished(boolean success, String detail) {
        if (mDestroyed) {
            return;
        }
        mStartButton.setEnabled(true);
        if (success) {
            mResultView.append("\n" + getString(R.string.status_install_success));
        } else {
            mResultView.append("\n" + getString(R.string.status_install_failure, detail));
        }
    }

    private void publishStatus(int stringResId) {
        if (mDestroyed) {
            return;
        }
        mMainHandler.post(() -> {
            if (!mDestroyed) {
                mResultView.setText(stringResId);
            }
        });
    }

    private static String formatMiB(long sizeBytes) {
        return String.format(Locale.US, "%.2f MiB", sizeBytes / (1024d * 1024d));
    }

    private static void deleteInvalidOutput(File outputFile) {
        if (outputFile.exists() && !outputFile.delete()) {
            Log.w(TAG, "failed to delete invalid output: " + outputFile);
        }
    }

    /** 从 assets 拷贝文件到目标路径 */
    private void copyAsset(String assetName, File dest) throws IOException {
        try (java.io.InputStream in = getAssets().open(assetName);
                java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
            byte[] buffer = new byte[64 * 1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }

    private void showToast(final String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private static final class LocalPatchResult {
        final boolean success;
        final String message;
        final File outputFile;
        final String sha256;

        private LocalPatchResult(boolean success, String message, File outputFile, String sha256) {
            this.success = success;
            this.message = message;
            this.outputFile = outputFile;
            this.sha256 = sha256;
        }

        static LocalPatchResult success(File outputFile, String sha256) {
            return new LocalPatchResult(true, null, outputFile, sha256);
        }

        static LocalPatchResult failure(String message) {
            return new LocalPatchResult(false, message, null, null);
        }
    }
}
