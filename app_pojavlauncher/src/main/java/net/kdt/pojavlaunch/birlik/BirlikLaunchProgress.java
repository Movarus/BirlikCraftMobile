package net.kdt.pojavlaunch.birlik;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shows the launcher's background work as the wave bar in place of the PLAY button:
 * "Installing the version" while game files / Fabric / mods are prepared, "Starting" once
 * everything is ready and the game is opening.
 */
public final class BirlikLaunchProgress {
    /** Progress records, most relevant first. */
    private static final String[] RECORDS = {
            ProgressLayout.DOWNLOAD_MINECRAFT, ProgressLayout.INSTALL_MODPACK, ProgressLayout.UNPACK_RUNTIME,
            ProgressLayout.EXTRACT_COMPONENTS, ProgressLayout.EXTRACT_SINGLE_FILES,
            ProgressLayout.DOWNLOAD_VERSION_LIST, ProgressLayout.AUTHENTICATE_MICROSOFT};
    private static final long LAUNCH_TIMEOUT_MS = 20000;

    /** Set when PLAY is pressed; cleared if nothing happens (refused launch, error dialog...). */
    private static volatile long sLaunchRequestedAt;

    private final View mPlay;
    private final View mBox;
    private final TextView mLabel;
    private final BirlikWaveProgressView mBar;
    private final Map<String, Integer> mActive = new LinkedHashMap<>();
    private final Map<String, ProgressListener> mListeners = new LinkedHashMap<>();
    private final Runnable mTimeout = this::refresh;

    private BirlikLaunchProgress(View root) {
        mPlay = root.findViewById(R.id.play_button);
        mBox = root.findViewById(R.id.birlik_launch_progress);
        mLabel = root.findViewById(R.id.birlik_launch_progress_label);
        mBar = root.findViewById(R.id.birlik_launch_progress_bar);
    }

    public static void onPlayPressed() {
        sLaunchRequestedAt = System.currentTimeMillis();
    }

    /** The launch was refused (not available, mod check, setup error...). */
    public static void cancelLaunch() {
        sLaunchRequestedAt = 0;
    }

    /** Attach to the home page; returns a handle to detach in onDestroyView. */
    public static BirlikLaunchProgress attach(@NonNull View root) {
        BirlikLaunchProgress progress = new BirlikLaunchProgress(root);
        if (progress.mBox == null) return progress;
        for (String record : RECORDS) {
            ProgressListener listener = new ProgressListener() {
                @Override public void onProgressStarted() { update(record, -1); }
                @Override public void onProgressUpdated(int value, int resid, Object... va) { update(record, value); }
                @Override public void onProgressEnded() { remove(record); }
                private void update(String key, int value) { Tools.runOnUiThread(() -> { progress.mActive.put(key, value); progress.refresh(); }); }
                private void remove(String key) { Tools.runOnUiThread(() -> { progress.mActive.remove(key); progress.refresh(); }); }
            };
            progress.mListeners.put(record, listener);
            ProgressKeeper.addListener(record, listener);
        }
        progress.refresh();
        return progress;
    }

    public void detach() {
        for (Map.Entry<String, ProgressListener> entry : mListeners.entrySet()) {
            ProgressKeeper.removeListener(entry.getKey(), entry.getValue());
        }
        mListeners.clear();
        if (mBox != null) mBox.removeCallbacks(mTimeout);
    }

    /** Refresh after PLAY so "Starting" shows up even before the first task begins. */
    public void refresh() {
        if (mBox == null) return;
        boolean launching = System.currentTimeMillis() - sLaunchRequestedAt < LAUNCH_TIMEOUT_MS;
        if (mActive.isEmpty() && !launching) {
            mBox.setVisibility(View.GONE);
            mPlay.setVisibility(View.VISIBLE);
            return;
        }
        mPlay.setVisibility(View.INVISIBLE);
        mBox.setVisibility(View.VISIBLE);

        String record = null;
        for (String key : RECORDS) if (mActive.containsKey(key)) { record = key; break; }
        if (record == null) {
            mLabel.setText(R.string.birlik_progress_starting);
            mBar.setProgress(-1);
        } else {
            boolean version = ProgressLayout.DOWNLOAD_MINECRAFT.equals(record)
                    || ProgressLayout.INSTALL_MODPACK.equals(record) || ProgressLayout.UNPACK_RUNTIME.equals(record);
            int value = mActive.get(record);
            String label = mLabel.getContext().getString(version ? R.string.birlik_progress_installing : R.string.birlik_progress_preparing);
            mLabel.setText(value > 0 ? label + "  " + value + "%" : label);
            mBar.setProgress(value > 0 ? value : -1);
        }
        mBox.removeCallbacks(mTimeout);
        if (launching) mBox.postDelayed(mTimeout, LAUNCH_TIMEOUT_MS);
    }
}
