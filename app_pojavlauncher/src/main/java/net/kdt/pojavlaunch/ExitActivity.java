package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Tools.shareLog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

@Keep
public class ExitActivity extends AppCompatActivity {

    @SuppressLint("StringFormatInvalid") //invalid on some translations but valid on most, cant fix that atm
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int code = -1;
        boolean isSignal = false;
        Bundle extras = getIntent().getExtras();
        if(extras != null) {
            code = extras.getInt("code",-1);
            isSignal = extras.getBoolean("isSignal", false);
        }

        int message = isSignal ? R.string.mcn_signal_title : R.string.mcn_exit_title;

        new AlertDialog.Builder(this)
                .setMessage(getString(message,code))
                .setPositiveButton(R.string.main_share_logs, (dialog, which) -> shareLog(this))
                .setOnDismissListener(dialog -> ExitActivity.this.finish())
                .show();
    }

    /**
     * BirlikCraft: reopen the launcher (as if its icon was tapped). The launcher process ends
     * when the game starts, so without this closing the game would leave the app entirely.
     */
    public static void returnToLauncher(Context ctx) {
        try {
            Intent i = new Intent(ctx, TestStorageActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ctx.startActivity(i);
        } catch (RuntimeException e) {
            android.util.Log.w("ExitActivity", "Could not reopen the launcher", e);
        }
    }

    public static void showExitMessage(Context ctx, int code, boolean isSignal) {
        // A normal exit (code 0) goes straight back to the launcher
        if (code == 0 && !isSignal) {
            returnToLauncher(ctx);
            return;
        }
        Intent i = new Intent(ctx,ExitActivity.class);
        i.putExtra("code",code);
        i.putExtra("isSignal", isSignal);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

}
