package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;

import androidx.preference.Preference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

/** BirlikCraft settings > Developer options: share logs, open the game folder. */
public class BirlikDeveloperPreferenceFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_birlik_developer);

        Preference shareLogs = findPreference("birlik_share_logs");
        if (shareLogs != null) {
            shareLogs.setOnPreferenceClickListener(p -> {
                Tools.shareLog(requireContext());
                return true;
            });
        }

        Preference openFolder = findPreference("birlik_open_game_folder");
        if (openFolder != null) {
            openFolder.setOnPreferenceClickListener(p -> {
                Tools.openPath(requireContext(), getCurrentGameDir(), false);
                return true;
            });
        }
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
