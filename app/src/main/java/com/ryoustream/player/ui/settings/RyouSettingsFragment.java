package com.ryoustream.player.ui.settings;

import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import com.ryoustream.player.BuildConfig;
import com.ryoustream.player.R;

/**
 * Settings fragment using AndroidX Preference library.
 */
public class RyouSettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // App version
        Preference versionPref = findPreference("pref_version");
        if (versionPref != null) {
            versionPref.setSummary(BuildConfig.VERSION_NAME
                    + " (build " + BuildConfig.BUILD_NUMBER + ")");
        }

        // Build info
        Preference buildInfoPref = findPreference("pref_build_info");
        if (buildInfoPref != null) {
            buildInfoPref.setSummary("Date: " + BuildConfig.BUILD_DATE
                    + "\nCommit: " + BuildConfig.GIT_HASH);
        }

        // Cache clear
        Preference clearCachePref = findPreference("pref_clear_cache");
        if (clearCachePref != null) {
            clearCachePref.setOnPreferenceClickListener(pref -> {
                clearCache();
                return true;
            });
        }
    }

    private void clearCache() {
        requireActivity().getCacheDir().delete();
        requireActivity().runOnUiThread(() -> {
            if (getContext() != null) {
                android.widget.Toast.makeText(getContext(),
                        R.string.cache_cleared, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }
}
