package com.cundong.apkpatch.example;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PackageInstaller 会话结果回调与前台确认页协调器。
 *
 * 普通应用无法静默安装。收到 STATUS_PENDING_USER_ACTION 时，仅在 MainActivity 位于
 * 前台时拉起系统确认页；否则暂存确认 Intent，等用户回到 Demo 后立即继续。
 */
public final class InstallResultReceiver extends BroadcastReceiver {

	static final String ACTION_INSTALL_STATUS =
			"com.cundong.apkpatch.example.action.INSTALL_STATUS";
	private static final String TAG = "InstallResult";
	private static final AtomicBoolean INSTALL_IN_PROGRESS = new AtomicBoolean(false);
	private static final Object ACTIVITY_LOCK = new Object();

	private static WeakReference<MainActivity> sResumedActivity =
			new WeakReference<>(null);
	private static PendingConfirmation sPendingConfirmation;

	static boolean tryBeginInstall() {
		return INSTALL_IN_PROGRESS.compareAndSet(false, true);
	}

	static void restoreInstallInProgress() {
		INSTALL_IN_PROGRESS.set(true);
	}

	static boolean isInstallInProgress() {
		return INSTALL_IN_PROGRESS.get();
	}

	static void abortInstall() {
		INSTALL_IN_PROGRESS.set(false);
		synchronized (ACTIVITY_LOCK) {
			sPendingConfirmation = null;
		}
	}

	static void onActivityResumed(MainActivity activity) {
		PendingConfirmation pending;
		synchronized (ACTIVITY_LOCK) {
			sResumedActivity = new WeakReference<>(activity);
			pending = sPendingConfirmation;
			sPendingConfirmation = null;
		}
		if (pending != null) {
			launchConfirmation(activity, pending);
		}
	}

	static void onActivityPaused(MainActivity activity) {
		synchronized (ACTIVITY_LOCK) {
			if (sResumedActivity.get() == activity) {
				sResumedActivity.clear();
			}
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public void onReceive(Context context, Intent intent) {
		if (intent == null || !ACTION_INSTALL_STATUS.equals(intent.getAction())) {
			return;
		}

		int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
				PackageInstaller.STATUS_FAILURE);
		int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
		String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
		if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
			Intent confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
			if (confirmationIntent == null) {
				abandonSessionQuietly(context, sessionId);
				reportFailure(context, status, "系统未返回安装确认 Intent");
				return;
			}

			PendingConfirmation pending = new PendingConfirmation(confirmationIntent, sessionId);
			MainActivity activity;
			synchronized (ACTIVITY_LOCK) {
				activity = sResumedActivity.get();
				if (activity == null) {
					sPendingConfirmation = pending;
					Log.i(TAG, "install confirmation deferred until activity resumes");
					return;
				}
			}
			launchConfirmation(activity, pending);
			return;
		}

		if (status == PackageInstaller.STATUS_SUCCESS) {
			finishInstall(true, "");
			Log.i(TAG, "package install succeeded");
			Toast.makeText(context, R.string.toast_install_success, Toast.LENGTH_LONG).show();
			return;
		}

		reportFailure(context, status, message);
	}

	private static void launchConfirmation(MainActivity activity, PendingConfirmation pending) {
		try {
			activity.launchInstallConfirmation(pending.intent);
			Log.i(TAG, "system install confirmation launched");
		} catch (RuntimeException e) {
			Context context = activity.getApplicationContext();
			Log.e(TAG, "failed to launch system install confirmation", e);
			abandonSessionQuietly(context, pending.sessionId);
			reportFailure(context, PackageInstaller.STATUS_PENDING_USER_ACTION,
					e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
		}
	}

	private static void reportFailure(Context context, int status, String message) {
		String detail = TextUtils.isEmpty(message)
				? String.valueOf(status)
				: status + ": " + message;
		finishInstall(false, detail);
		Log.e(TAG, "package install failed: " + detail);
		Toast.makeText(context,
				context.getString(R.string.toast_install_failure, detail),
				Toast.LENGTH_LONG).show();
	}

	private static void finishInstall(boolean success, String detail) {
		INSTALL_IN_PROGRESS.set(false);
		MainActivity activity;
		synchronized (ACTIVITY_LOCK) {
			sPendingConfirmation = null;
			activity = sResumedActivity.get();
		}
		if (activity != null) {
			activity.onInstallFinished(success, detail);
		}
	}

	private static void abandonSessionQuietly(Context context, int sessionId) {
		if (sessionId < 0) {
			return;
		}
		try {
			context.getPackageManager().getPackageInstaller().abandonSession(sessionId);
		} catch (RuntimeException e) {
			Log.w(TAG, "failed to abandon session " + sessionId, e);
		}
	}

	private static final class PendingConfirmation {
		final Intent intent;
		final int sessionId;

		PendingConfirmation(Intent intent, int sessionId) {
			this.intent = intent;
			this.sessionId = sessionId;
		}
	}
}
