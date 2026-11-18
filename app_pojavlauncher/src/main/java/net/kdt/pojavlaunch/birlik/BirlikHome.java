package net.kdt.pojavlaunch.birlik;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.Locale;

/** Binds the BirlikCraft top bar and home page (player info, version switch, renderer picker). */
public final class BirlikHome {
    private BirlikHome() {}

    /** Top bar of activity_pojav_launcher: signed-in player and sign-out button. */
    public static void bindTopBar(@NonNull Activity activity) {
        BirlikAuth.Session session = BirlikAuth.getSession(activity);
        TextView avatar = activity.findViewById(R.id.birlik_player_avatar);
        TextView name = activity.findViewById(R.id.birlik_player_name);
        TextView credit = activity.findViewById(R.id.birlik_player_credit);
        View chip = activity.findViewById(R.id.birlik_player_chip);
        View logout = activity.findViewById(R.id.birlik_logout_button);

        boolean loggedIn = session != null;
        if (chip != null) chip.setVisibility(loggedIn ? View.VISIBLE : View.INVISIBLE);
        if (logout != null) {
            logout.setVisibility(loggedIn ? View.VISIBLE : View.INVISIBLE);
            logout.setOnClickListener(v -> BirlikLoginController.confirmLogout(activity));
        }
        if (!loggedIn) return;
        if (avatar != null) avatar.setText(session.username.substring(0, 1).toUpperCase(Locale.ROOT));
        if (name != null) name.setText(session.username);
        if (credit != null) credit.setText(activity.getString(R.string.birlik_credit, session.credit));
    }

    /**
     * Switch the top bar between the home look (logo, player, settings, sign out) and the page
     * look used by settings and other inner pages (back button and page title).
     */
    public static void updateTopBar(@NonNull Activity activity, @NonNull androidx.fragment.app.Fragment page) {
        boolean home = page instanceof net.kdt.pojavlaunch.fragments.MainMenuFragment;
        View back = activity.findViewById(R.id.birlik_back_button);
        View logo = activity.findViewById(R.id.birlik_top_logo);
        TextView title = activity.findViewById(R.id.birlik_top_title);
        View chip = activity.findViewById(R.id.birlik_player_chip);
        View settings = activity.findViewById(R.id.setting_button);
        View logout = activity.findViewById(R.id.birlik_logout_button);
        if (back == null || title == null) return;

        back.setVisibility(home ? View.GONE : View.VISIBLE);
        back.setOnClickListener(v -> activity.onBackPressed());
        if (logo != null) logo.setVisibility(home ? View.VISIBLE : View.GONE);
        if (settings != null) settings.setVisibility(home ? View.VISIBLE : View.GONE);
        if (home) {
            title.setText(R.string.birlik_brand_title);
            title.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(activity, R.font.minecraft_ten));
            bindTopBar(activity);
        } else {
            title.setText(getPageTitle(page));
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            if (chip != null) chip.setVisibility(View.GONE);
            if (logout != null) logout.setVisibility(View.GONE);
        }
    }

    private static int getPageTitle(androidx.fragment.app.Fragment page) {
        if (page instanceof net.kdt.pojavlaunch.prefs.screens.BirlikDeveloperPreferenceFragment)
            return R.string.birlik_pref_category_developer;
        if (page instanceof net.kdt.pojavlaunch.prefs.screens.BirlikAboutPreferenceFragment)
            return R.string.pref_category_about;
        if (page instanceof net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceControlFragment)
            return R.string.birlik_touch_settings_title;
        if (page instanceof net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment)
            return R.string.birlik_settings;
        if (page instanceof net.kdt.pojavlaunch.fragments.SearchModFragment) {
            android.os.Bundle args = page.getArguments();
            String type = args == null ? null : args.getString(net.kdt.pojavlaunch.fragments.SearchModFragment.ARG_PROJECT_TYPE);
            return net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi.TYPE_SHADER.equals(type)
                    ? R.string.birlik_menu_shaderpacks : R.string.birlik_menu_resourcepacks;
        }
        return R.string.app_short_name;
    }

    /** Home page: welcome name and the 26.3 / 26.2 version switch. */
    public static void bindHome(@NonNull View root) {
        BirlikAuth.Session session = BirlikAuth.getSession(root.getContext());
        TextView player = root.findViewById(R.id.birlik_home_player);
        if (player != null) player.setText(session == null ? "" : session.username);

        TextView v263 = root.findViewById(R.id.birlik_version_263);
        TextView v262 = root.findViewById(R.id.birlik_version_262);
        if (v263 == null || v262 == null) return;
        Runnable refresh = () -> {
            String current = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, BirlikSetup.DEFAULT_PROFILE);
            v263.setSelected(!BirlikSetup.PROFILE_26_2.equals(current));
            v262.setSelected(BirlikSetup.PROFILE_26_2.equals(current));
        };
        v263.setOnClickListener(v -> { selectProfile(BirlikSetup.PROFILE_26_3); refresh.run(); });
        v262.setOnClickListener(v -> { selectProfile(BirlikSetup.PROFILE_26_2); refresh.run(); });
        refresh.run();
    }

    private static void selectProfile(String profileKey) {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                .apply();
    }

    /**
     * Pick the renderer of the selected BirlikCraft version. Amethyst stores the renderer per
     * profile, and profile editing is hidden, so this is the only place to change it.
     */
    public static void showRendererPicker(@NonNull Activity activity) {
        String profileKey = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, BirlikSetup.DEFAULT_PROFILE);
        LauncherProfiles.load();
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(profileKey);
        if (profile == null) return;

        Tools.RenderersList renderers = Tools.getCompatibleRenderers(activity);
        String recommended = BirlikSetup.getRecommendedRenderer(activity);
        int count = renderers.rendererIds.size();
        String[] names = new String[count + 1];
        int recommendedIndex = recommended == null ? -1 : renderers.rendererIds.indexOf(recommended);
        names[0] = recommendedIndex >= 0
                ? activity.getString(R.string.birlik_renderer_recommended, renderers.rendererDisplayNames[recommendedIndex])
                : activity.getString(R.string.birlik_renderer_auto);
        System.arraycopy(renderers.rendererDisplayNames, 0, names, 1, count);
        int checked = 0;
        if (profile.pojavRendererName != null && !profile.pojavRendererName.equals(recommended)) {
            int index = renderers.rendererIds.indexOf(profile.pojavRendererName);
            if (index >= 0) checked = index + 1;
        }
        String version = BirlikSetup.getGameVersion(profileKey);

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.birlik_graphics_title, version))
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    LauncherProfiles.load();
                    MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(profileKey);
                    if (p != null) {
                        p.pojavRendererName = which == 0 ? recommended : renderers.rendererIds.get(which - 1);
                        LauncherProfiles.write();
                        BirlikSetup.markRendererChosen(profileKey);
                    }
                    Toast.makeText(activity, activity.getString(R.string.birlik_graphics_applied, names[which]),
                            Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
