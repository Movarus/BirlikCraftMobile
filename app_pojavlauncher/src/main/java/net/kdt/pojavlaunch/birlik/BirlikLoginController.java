package net.kdt.pojavlaunch.birlik;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import java.io.IOException;

/**
 * Drives the BirlikCraft login screen (layout/birlik_login_overlay.xml).
 * The screen covers the launcher until the player signs in with a BirlikCraft account.
 */
public final class BirlikLoginController {
    private static final String REGISTER_URL = "https://movarus.info";

    private final Activity mActivity;
    private final View mOverlay;
    private final EditText mUsername;
    private final EditText mPassword;
    private final TextView mError;
    private final View mButton;
    private final View mProgress;
    private boolean mBusy;

    private BirlikLoginController(Activity activity, View overlay) {
        mActivity = activity;
        mOverlay = overlay;
        mUsername = overlay.findViewById(R.id.birlik_login_username);
        mPassword = overlay.findViewById(R.id.birlik_login_password);
        mError = overlay.findViewById(R.id.birlik_login_error);
        mButton = overlay.findViewById(R.id.birlik_login_button);
        mProgress = overlay.findViewById(R.id.birlik_login_progress);

        mButton.setOnClickListener(v -> submit());
        mPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        overlay.findViewById(R.id.birlik_login_register)
                .setOnClickListener(v -> Tools.openURL(activity, REGISTER_URL));
    }

    /** Show the login screen if the player is not signed in. Call after setContentView(). */
    public static void attach(@NonNull Activity activity) {
        View overlay = activity.findViewById(R.id.birlik_login_overlay);
        if (overlay == null) return;
        if (BirlikAuth.isLoggedIn(activity)) {
            overlay.setVisibility(View.GONE);
            return;
        }
        // A stale session (account file deleted, other account selected...) is not valid
        BirlikAuth.logout(activity);
        show(activity);
    }

    /** Show the login screen, e.g. when the player tries to play without a session. */
    public static void show(@NonNull Activity activity) {
        View overlay = activity.findViewById(R.id.birlik_login_overlay);
        if (overlay == null) return;
        if (overlay.getTag() == null) overlay.setTag(new BirlikLoginController(activity, overlay));
        overlay.setAlpha(1f);
        overlay.setVisibility(View.VISIBLE);
    }

    /** Ask the player whether to sign out, then show the login screen. */
    public static void confirmLogout(@NonNull Activity activity) {
        BirlikAuth.Session session = BirlikAuth.getSession(activity);
        String name = session == null ? "" : session.username;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.birlik_logout_title)
                .setMessage(activity.getString(R.string.birlik_logout_message, name))
                .setPositiveButton(R.string.birlik_logout_button, (d, w) -> {
                    BirlikAuth.logout(activity);
                    activity.recreate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void submit() {
        if (mBusy) return;
        String username = mUsername.getText().toString().trim();
        String password = mPassword.getText().toString();
        if (username.isEmpty() || password.isEmpty()) {
            showError(mActivity.getString(R.string.birlik_login_empty));
            return;
        }
        setBusy(true);
        hideKeyboard();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                BirlikAuth.Session session = BirlikAuth.login(username, password);
                BirlikAuth.saveSession(mActivity, session);
                Tools.runOnUiThread(() -> onLoggedIn(session));
            } catch (BirlikAuth.AuthException e) {
                Tools.runOnUiThread(() -> { setBusy(false); showError(e.getMessage()); });
            } catch (IOException e) {
                Tools.runOnUiThread(() -> {
                    setBusy(false);
                    showError(mActivity.getString(R.string.birlik_login_network_error) + "\n(" + e.getMessage() + ")");
                });
            }
        });
    }

    private void onLoggedIn(BirlikAuth.Session session) {
        if (mActivity.isFinishing()) return;
        mPassword.setText("");
        Toast.makeText(mActivity, mActivity.getString(R.string.birlik_login_welcome, session.username),
                Toast.LENGTH_SHORT).show();
        mOverlay.animate().alpha(0f).setDuration(350).withEndAction(() -> {
            mOverlay.setVisibility(View.GONE);
            // Reload the launcher so the account bar and menus pick up the new account
            mActivity.recreate();
        }).start();
    }

    private void setBusy(boolean busy) {
        mBusy = busy;
        mButton.setEnabled(!busy);
        mUsername.setEnabled(!busy);
        mPassword.setEnabled(!busy);
        mProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) mError.setVisibility(View.GONE);
    }

    private void showError(String message) {
        mError.setText(message);
        mError.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(mPassword.getWindowToken(), 0);
    }
}
