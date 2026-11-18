package net.kdt.pojavlaunch.birlik;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceControlFragment;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** BirlikCraft's own settings screen: category sidebar on the left, styled cards on the right. */
public class BirlikSettingsFragment extends Fragment {
    public static final String TAG = "BirlikSettingsFragment";

    private LinearLayout mContent;
    private View mScroll;
    private int mCategory;

    public BirlikSettingsFragment() {
        super(R.layout.fragment_birlik_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mContent = view.findViewById(R.id.birlik_settings_content);
        mScroll = view.findViewById(R.id.birlik_settings_scroll);
        LinearLayout categories = view.findViewById(R.id.birlik_settings_categories);

        int[][] entries = {
                {R.string.birlik_pref_category_game, R.drawable.ic_birlik_graphics},
                {R.string.birlik_settings_controls, R.drawable.ic_menu_custom_controls},
                {R.string.birlik_pref_category_performance, R.drawable.ic_setting_ram},
                {R.string.birlik_settings_account, R.drawable.ic_edit_profile},
                {R.string.birlik_pref_category_permissions, R.drawable.ic_setting_misc},
                {R.string.birlik_pref_category_developer, R.drawable.ic_folder},
                {R.string.pref_category_about, R.drawable.ic_menu_news}};
        List<View> items = new ArrayList<>();
        for (int i = 0; i < entries.length; i++) {
            final int index = i;
            View item = categoryItem(categories, entries[i][0], entries[i][1]);
            items.add(item);
            item.setOnClickListener(v -> {
                for (View other : items) other.setSelected(other == v);
                showCategory(index);
            });
        }
        if (savedInstanceState != null) mCategory = savedInstanceState.getInt("category", 0);
        items.get(mCategory).setSelected(true);
        showCategory(mCategory);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("category", mCategory);
    }

    private void showCategory(int category) {
        mCategory = category;
        mContent.removeAllViews();
        mScroll.scrollTo(0, 0);
        switch (category) {
            case 0: buildGame(); break;
            case 1: buildControls(); break;
            case 2: buildPerformance(); break;
            case 3: buildAccount(); break;
            case 4: buildPermissions(); break;
            case 5: buildDeveloper(); break;
            default: buildAbout(); break;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------------------------------

    private void buildGame() {
        header(R.string.birlik_pref_category_game);
        action(R.drawable.ic_birlik_graphics, getString(R.string.birlik_menu_graphics),
                currentRendererSummary(), v -> {
                    BirlikHome.showRendererPicker(requireActivity());
                    // The picker writes the profile; refresh the summary when coming back
                    v.postDelayed(() -> { if (isAdded() && mCategory == 0) showCategory(0); }, 800);
                });
        toggle(R.string.birlik_settings_shaders_title, R.string.birlik_settings_shaders_summary,
                BirlikSetup.PREF_SHADERS_ENABLED, true);
        action(R.drawable.ic_menu_custom_controls, getString(R.string.birlik_edit_controls_title),
                getString(R.string.birlik_edit_controls_summary),
                v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
    }

    private void buildControls() {
        header(R.string.birlik_settings_controls);
        slider(R.string.mcl_setting_title_buttonscale, "buttonscale", 80, 250, 5, 100, " %");
        slider(R.string.mcl_setting_title_mousespeed, "mousespeed", 25, 300, 5, 100, " %");
        toggle(R.string.mcl_disable_gestures, R.string.mcl_disable_gestures_subtitle, "disableGestures", false);
        toggle(R.string.preference_enable_gyro_title, R.string.preference_enable_gyro_description, "enableGyro", false);
        slider(R.string.preference_gyro_sensitivity_title, "gyroSensitivity", 25, 300, 5, 100, " %");
        action(R.drawable.ic_setting_gesture_time, getString(R.string.birlik_settings_advanced_controls),
                getString(R.string.birlik_touch_settings_summary),
                v -> Tools.swapFragment(requireActivity(), LauncherPreferenceControlFragment.class, null, null));
    }

    private void buildPerformance() {
        header(R.string.birlik_pref_category_performance);
        slider(R.string.mcl_setting_title_resolution_scaler, "resolutionRatio", 25, 100, 5,
                (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100), " %");
        int deviceRam = Tools.getTotalDeviceMemory(requireContext());
        int maxRam = (Architecture.is32BitsDevice() || deviceRam < 2048) ? Math.min(1024, deviceRam)
                : deviceRam - (deviceRam < 3064 ? 800 : 1024);
        slider(R.string.mcl_memory_allocation, "allocation", 256, maxRam, 128,
                LauncherPreferences.PREF_RAM_ALLOCATION, " MB");
        note(R.string.birlik_settings_performance_note);
    }

    private void buildAccount() {
        header(R.string.birlik_settings_account);
        BirlikAuth.Session session = BirlikAuth.getSession(requireContext());
        if (session != null) {
            action(R.drawable.ic_edit_profile, session.username,
                    getString(R.string.birlik_credit, session.credit), null);
        }
        action(R.drawable.ic_birlik_logout, getString(R.string.birlik_logout_button),
                getString(R.string.birlik_settings_logout_summary),
                v -> BirlikLoginController.confirmLogout(requireActivity()));
    }

    private void buildPermissions() {
        header(R.string.birlik_pref_category_permissions);
        if (!(getActivity() instanceof LauncherActivity)) return;
        LauncherActivity activity = (LauncherActivity) getActivity();
        boolean notifications = activity.checkForNotificationPermission();
        boolean microphone = activity.checkForMicrophonePermission();
        action(R.drawable.ic_setting_misc, getString(R.string.preference_ask_for_notification_title),
                getString(notifications ? R.string.birlik_permission_granted : R.string.preference_ask_for_notification_description),
                notifications ? null : v -> activity.askForNotificationPermission(() -> showCategory(4)));
        action(R.drawable.ic_setting_misc, getString(R.string.preference_ask_for_microphone_title),
                getString(microphone ? R.string.birlik_permission_granted : R.string.preference_ask_for_microphone_description),
                microphone ? null : v -> activity.askForMicrophonePermission(() -> showCategory(4)));
    }

    private void buildDeveloper() {
        header(R.string.birlik_pref_category_developer);
        toggle(R.string.birlik_test_mode_title, R.string.birlik_test_mode_summary, BirlikSetup.PREF_TEST_MODE, false);
        action(android.R.drawable.ic_menu_share, getString(R.string.main_share_logs),
                getString(R.string.birlik_share_logs_summary), v -> Tools.shareLog(requireContext()));
        action(R.drawable.ic_folder, getString(R.string.mcl_button_open_directory),
                getString(R.string.birlik_open_game_folder_summary),
                v -> Tools.openPath(requireContext(), getCurrentGameDir(), false));
    }

    private void buildAbout() {
        header(R.string.pref_category_about);
        action(R.drawable.ic_edit_profile, getString(R.string.pref_about_author_title),
                getString(R.string.pref_about_author_summary), null);
        action(R.drawable.ic_birlik_globe, getString(R.string.pref_about_website_title),
                getString(R.string.pref_about_website_summary), v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        action(R.drawable.ic_menu_home, getString(R.string.pref_about_server_title),
                getString(R.string.server_ip), v -> {
                    ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    String ip = getString(R.string.server_ip);
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("BirlikCraft", ip));
                    Toast.makeText(requireContext(), getString(R.string.birlik_ip_copied, ip), Toast.LENGTH_SHORT).show();
                });
        action(R.drawable.ic_discord, getString(R.string.pref_about_discord_title),
                getString(R.string.discord_invite), v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));
        action(R.drawable.birlikcraft_logo, getString(R.string.birlik_about_version_title), BuildConfig.VERSION_NAME, null);
    }

    // ------------------------------------------------------------------------------------------
    // Row builders
    // ------------------------------------------------------------------------------------------

    private View categoryItem(LinearLayout parent, @StringRes int title, @DrawableRes int icon) {
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setBackgroundResource(R.drawable.birlik_category_bg);
        int pad = dp(R.dimen._8sdp);
        item.setPadding(pad, dp(R.dimen._7sdp), pad, dp(R.dimen._7sdp));
        ImageView image = new ImageView(requireContext());
        image.setImageResource(icon);
        image.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.birlik_gold_light)));
        item.addView(image, new LinearLayout.LayoutParams(dp(R.dimen._16sdp), dp(R.dimen._16sdp)));
        TextView text = text(getString(title), R.dimen._10ssp, R.color.primary_text, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(dp(R.dimen._8sdp));
        item.addView(text, lp);
        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemLp.bottomMargin = dp(R.dimen._3sdp);
        parent.addView(item, itemLp);
        return item;
    }

    private void header(@StringRes int title) {
        TextView header = text(getString(title), R.dimen._16ssp, R.color.birlik_gold_light, true);
        header.setPadding(0, 0, 0, dp(R.dimen._6sdp));
        mContent.addView(header);
    }

    private void note(@StringRes int message) {
        TextView note = text(getString(message), R.dimen._8ssp, R.color.secondary_text, false);
        note.setPadding(dp(R.dimen._4sdp), dp(R.dimen._4sdp), dp(R.dimen._4sdp), 0);
        mContent.addView(note);
    }

    /** A card with an icon, a title and a summary. A null listener makes it informational. */
    private void action(@DrawableRes int icon, String title, String summary, @Nullable View.OnClickListener listener) {
        LinearLayout row = row();
        ImageView image = new ImageView(requireContext());
        image.setImageResource(icon);
        image.setBackgroundResource(R.drawable.birlik_tile_icon_bg);
        int pad = dp(R.dimen._5sdp);
        image.setPadding(pad, pad, pad, pad);
        if (icon != R.drawable.birlikcraft_logo) {
            image.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.birlik_gold_light)));
        }
        row.addView(image, new LinearLayout.LayoutParams(dp(R.dimen._28sdp), dp(R.dimen._28sdp)));
        row.addView(texts(title, summary), textsParams());
        if (listener != null) row.setOnClickListener(listener);
        else row.setClickable(false);
    }

    private void toggle(@StringRes int title, @StringRes int summary, String key, boolean defaultValue) {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        LinearLayout row = row();
        row.addView(texts(getString(title), getString(summary)), textsParams());
        SwitchCompat toggle = new SwitchCompat(requireContext());
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setThumbTintList(ContextCompat.getColorStateList(requireContext(), R.color.birlik_switch_thumb));
        toggle.setTrackTintList(ContextCompat.getColorStateList(requireContext(), R.color.birlik_switch_track));
        toggle.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            LauncherPreferences.loadPreferences(requireContext());
        });
        row.addView(toggle);
        row.setOnClickListener(v -> toggle.toggle());
    }

    private void slider(@StringRes int title, String key, int min, int max, int step, int defaultValue, String suffix) {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        LinearLayout row = row();
        row.setOrientation(LinearLayout.VERTICAL);
        row.setClickable(false);
        LinearLayout top = new LinearLayout(requireContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = text(getString(title), R.dimen._10ssp, R.color.primary_text, true);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int current = Math.max(min, Math.min(max, prefs.getInt(key, defaultValue)));
        TextView value = text(current + suffix, R.dimen._10ssp, R.color.birlik_gold_light, true);
        top.addView(value);
        row.addView(top);

        SeekBar seek = new SeekBar(requireContext());
        seek.setMax((max - min) / step);
        seek.setProgress((current - min) / step);
        seek.setProgressDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.birlik_seekbar_progress));
        seek.setThumb(ContextCompat.getDrawable(requireContext(), R.drawable.birlik_seekbar_thumb));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                value.setText((min + progress * step) + suffix);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                prefs.edit().putInt(key, min + s.getProgress() * step).apply();
                LauncherPreferences.loadPreferences(requireContext());
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(R.dimen._4sdp);
        row.addView(seek, lp);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.birlik_settings_row_bg);
        int pad = dp(R.dimen._10sdp);
        row.setPadding(pad, dp(R.dimen._8sdp), pad, dp(R.dimen._8sdp));
        row.setMinimumHeight(dp(R.dimen._42sdp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(R.dimen._5sdp);
        mContent.addView(row, lp);
        return row;
    }

    private LinearLayout texts(String title, @Nullable String summary) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(title, R.dimen._10ssp, R.color.primary_text, true));
        if (summary != null && !summary.isEmpty()) {
            box.addView(text(summary, R.dimen._8ssp, R.color.secondary_text, false));
        }
        return box;
    }

    private LinearLayout.LayoutParams textsParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(dp(R.dimen._10sdp));
        lp.setMarginEnd(dp(R.dimen._6sdp));
        return lp;
    }

    private TextView text(String value, int sizeRes, int colorRes, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(sizeRes));
        view.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(int dimenRes) {
        return getResources().getDimensionPixelSize(dimenRes);
    }

    private String currentRendererSummary() {
        String key = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, BirlikSetup.DEFAULT_PROFILE);
        LauncherProfiles.load();
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(key);
        String renderer = profile == null ? null : profile.pojavRendererName;
        if (renderer == null) renderer = BirlikSetup.getRecommendedRenderer(requireContext());
        String name = getString(R.string.birlik_renderer_auto);
        if (renderer != null) {
            Tools.RenderersList list = Tools.getCompatibleRenderers(requireContext());
            int index = list.rendererIds.indexOf(renderer);
            if (index >= 0) name = list.rendererDisplayNames[index];
        }
        return getString(R.string.birlik_settings_renderer_summary, BirlikSetup.getGameVersion(key), name);
    }

    private static File getCurrentGameDir() {
        try {
            String key = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            LauncherProfiles.load();
            MinecraftProfile profile = key == null ? null : LauncherProfiles.mainProfileJson.profiles.get(key);
            if (profile != null) return Tools.getGameDirPath(profile);
        } catch (RuntimeException ignored) {}
        return new File(Tools.DIR_GAME_NEW);
    }
}
