package net.kdt.pojavlaunch.birlik;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;

/**
 * Full-screen BirlikCraft loading screen shown when the launcher opens.
 * It stays up while the launcher prepares its components (unpacking files, Java runtimes...)
 * and for at least {@link #MIN_VISIBLE_MS}, then fades out.
 */
public final class BirlikSplashController implements TaskCountListener {
    private static final long MIN_VISIBLE_MS = 1800;
    private static final long MAX_VISIBLE_MS = 25000;
    private static final long FADE_MS = 450;

    /** Only show the splash once per app process (not on every return to the launcher). */
    private static boolean sShownThisProcess;

    private final View mOverlay;
    private final View mContent;
    private final TextView mStatus;
    private final long mStartTime = SystemClock.uptimeMillis();
    private final Runnable mFinishRunnable = this::finish;
    private boolean mFinished;

    private BirlikSplashController(View overlay) {
        mOverlay = overlay;
        mContent = overlay.findViewById(R.id.birlik_launch_splash_content);
        mStatus = overlay.findViewById(R.id.birlik_launch_splash_status);
    }

    public static void attach(Activity activity) {
        View overlay = activity.findViewById(R.id.birlik_launch_splash);
        if (overlay == null) return;
        if (sShownThisProcess) {
            overlay.setVisibility(View.GONE);
            return;
        }
        sShownThisProcess = true;
        overlay.setVisibility(View.VISIBLE);
        new BirlikSplashController(overlay).start();
    }

    private void start() {
        // Logo and title rise in
        if (mContent != null) {
            mContent.setAlpha(0f);
            mContent.setScaleX(0.92f);
            mContent.setScaleY(0.92f);
            mContent.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start();
        }
        // Safety net: never block the launcher for too long
        mOverlay.postDelayed(mFinishRunnable, MAX_VISIBLE_MS);
        ProgressKeeper.addTaskCountListener(this, true);
    }

    @Override
    public void onUpdateTaskCount(int taskCount) {
        Tools.runOnUiThread(() -> {
            if (mFinished) return;
            if (taskCount > 0) {
                // Work started (again): wait for it
                mOverlay.removeCallbacks(mFinishRunnable);
                mOverlay.postDelayed(mFinishRunnable, MAX_VISIBLE_MS);
                if (mStatus != null) mStatus.setText(R.string.birlik_splash_status_preparing);
            } else {
                long elapsed = SystemClock.uptimeMillis() - mStartTime;
                mOverlay.removeCallbacks(mFinishRunnable);
                mOverlay.postDelayed(mFinishRunnable, Math.max(300, MIN_VISIBLE_MS - elapsed));
            }
        });
    }

    private void finish() {
        if (mFinished) return;
        mFinished = true;
        ProgressKeeper.removeTaskCountListener(this);
        mOverlay.removeCallbacks(mFinishRunnable);
        if (mStatus != null) mStatus.setText(R.string.birlik_splash_status_ready);
        mOverlay.animate()
                .alpha(0f)
                .setStartDelay(150)
                .setDuration(FADE_MS)
                .withEndAction(() -> mOverlay.setVisibility(View.GONE))
                .start();
    }
}
