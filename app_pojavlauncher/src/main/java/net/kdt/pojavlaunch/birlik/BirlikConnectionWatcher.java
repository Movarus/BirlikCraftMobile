package net.kdt.pojavlaunch.birlik;

import android.app.Activity;
import android.widget.Toast;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

/**
 * BirlikCraft is a server-only client: the game joins oyna.birlikcraft.com directly and must
 * never fall back to Minecraft's title screen. This watches the game log and, when joining the
 * server fails, closes the game and reopens the launcher.
 */
public final class BirlikConnectionWatcher {
    /**
     * Log lines Minecraft writes when joining the server fails. Disconnects after joining are
     * left alone (the disconnect screen stays visible, useful for testing); the BirlikCraft mod
     * closes the game when the player then reaches the title screen.
     */
    private static final String[] FAILURE_MARKERS = {
            "Couldn't connect to server",
            "Failed to connect to server",
    };
    private static final long EXIT_DELAY_MS = 1500;

    private static volatile boolean sExiting;

    private BirlikConnectionWatcher() {}

    public static void attach(@NonNull Activity activity) {
        Logger.addLogListener(text -> {
            if (sExiting || text == null) return;
            for (String marker : FAILURE_MARKERS) {
                if (text.contains(marker)) {
                    sExiting = true;
                    Tools.runOnUiThread(() -> {
                        Toast.makeText(activity, R.string.birlik_connection_closed, Toast.LENGTH_LONG).show();
                        Tools.MAIN_HANDLER.postDelayed(() -> {
                            net.kdt.pojavlaunch.ExitActivity.returnToLauncher(activity);
                            Tools.fullyExit();
                        }, EXIT_DELAY_MS);
                    });
                    return;
                }
            }
        });
    }
}
