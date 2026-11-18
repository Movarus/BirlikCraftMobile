package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;

import androidx.preference.Preference;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.R;

/** BirlikCraft settings > About. */
public class BirlikAboutPreferenceFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_birlik_about);
        setupAboutPreferences();
        Preference version = findPreference("birlik_about_version");
        if (version != null) version.setSummary(BuildConfig.VERSION_NAME);
    }
}
