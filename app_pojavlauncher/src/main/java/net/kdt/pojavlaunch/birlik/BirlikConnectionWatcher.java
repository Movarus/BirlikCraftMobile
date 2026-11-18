package net.kdt.pojavlaunch.birlik;

import android.app.Activity;
import android.widget.Toast;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

/**
 * BirlikCraft is a server-only client: the game joins oyna.birlikcraft.com directly and must
 * never fall back to Minecraft's title screen. This watches the game log and closes the game
 * when joining the server fails or the connection ends (kick, timeout, server stop, quitting).
 */
public final class BirlikConnectionWatcher {
    /** Log lines Minecraft writes when a server connection fails or ends. */
    private static final String[] FAILURE_MARKERS = {
            "Couldn't connect to server",
            "Failed to connect to server",
            "Client disconnected with reason",
            "Disconnected from server",
            "Lost connection",
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
                        Tools.MAIN_HANDLER.postDelayed(Tools::fullyExit, EXIT_DELAY_MS);
                    });
                    return;
                }
            }
        });
    }
}
