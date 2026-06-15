package com.nogrep.netspeed;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            // Stored privately; the SystemUI hook reads these over the ConfigProvider (binder),
            // not from the file, so MODE_PRIVATE is correct and sufficient.
            PreferenceManager pm = getPreferenceManager();
            pm.setSharedPreferencesName(Constants.PREFS);
            pm.setSharedPreferencesMode(Context.MODE_PRIVATE);
            setPreferencesFromResource(R.xml.prefs, rootKey);

            Toast.makeText(getContext(), getString(R.string.hint_restart), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onResume() {
            super.onResume();
            SharedPreferences sp = getPreferenceManager().getSharedPreferences();
            if (sp != null) {
                sp.registerOnSharedPreferenceChangeListener(this);
            }
            notifyHook();
        }

        @Override
        public void onPause() {
            super.onPause();
            SharedPreferences sp = getPreferenceManager().getSharedPreferences();
            if (sp != null) {
                sp.unregisterOnSharedPreferenceChangeListener(this);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
            notifyHook();
        }

        /** Tell the SystemUI hook (which observes the ConfigProvider URI) to re-read settings. */
        private void notifyHook() {
            try {
                Context c = getContext();
                if (c != null) {
                    c.getContentResolver().notifyChange(ConfigProvider.URI, null);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
