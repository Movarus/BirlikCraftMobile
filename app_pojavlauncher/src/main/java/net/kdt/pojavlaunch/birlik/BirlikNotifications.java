package net.kdt.pojavlaunch.birlik;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import net.kdt.pojavlaunch.R;

/**
 * BirlikCraft look for notifications. Android always draws the small status icon as a
 * single-colour silhouette, so the colour comes from the gold accent and the full-colour
 * BirlikCraft logo shown as the large icon.
 */
public final class BirlikNotifications {
    public static final int GOLD = 0xFFD4A33B;
    private static Bitmap sLogo;

    private BirlikNotifications() {}

    public static NotificationCompat.Builder style(@NonNull Context context, @NonNull NotificationCompat.Builder builder) {
        if (sLogo == null) {
            sLogo = BitmapFactory.decodeResource(context.getResources(), R.drawable.birlikcraft_logo);
        }
        return builder.setSmallIcon(R.drawable.notif_icon)
                .setColor(GOLD)
                .setLargeIcon(sLogo);
    }
}
