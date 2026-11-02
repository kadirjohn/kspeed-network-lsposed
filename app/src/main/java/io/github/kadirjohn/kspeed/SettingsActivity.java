package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
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

        private static final long AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;

        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
            PreferenceManager pm = getPreferenceManager();
            pm.setSharedPreferencesName(Constants.PREFS);
            pm.setSharedPreferencesMode(Context.MODE_PRIVATE);
            setPreferencesFromResource(R.xml.prefs, rootKey);

            Context ctx = getContext();

            Preference check = findPreference("check_update");
            if (check != null && ctx != null) {
                check.setSummary("Installed: v" + UpdateChecker.currentVersion(ctx) + " · tap to check");
                check.setOnPreferenceClickListener(p -> {
                    runUpdateCheck(true);
                    return true;
                });
            }
            Preference source = findPreference("view_source");
            if (source != null) {
                source.setOnPreferenceClickListener(p -> {
                    openUrl(Constants.REPO_PAGE);
                    return true;
                });
            }

            Toast.makeText(ctx, getString(R.string.hint_restart), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onResume() {
            super.onResume();
            SharedPreferences sp = getPreferenceManager().getSharedPreferences();
            if (sp != null) {
                sp.registerOnSharedPreferenceChangeListener(this);
            }
            notifyHook();
            maybeAutoCheck();
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

        // ---- update check ----

        private void maybeAutoCheck() {
            Context ctx = getContext();
            if (ctx == null) {
                return;
            }
            SharedPreferences app = ctx.getSharedPreferences(Constants.APP_PREFS, Context.MODE_PRIVATE);
            long last = app.getLong(Constants.KEY_LAST_UPDATE_CHECK, 0L);
            if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) {
                return;
            }
            runUpdateCheck(false); // silent: only surfaces if an update exists
        }

        private void runUpdateCheck(final boolean manual) {
            final Context ctx = getContext();
            if (ctx == null) {
                return;
            }
            ctx.getSharedPreferences(Constants.APP_PREFS, Context.MODE_PRIVATE)
                    .edit().putLong(Constants.KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply();
            if (manual) {
                Toast.makeText(ctx, R.string.update_checking, Toast.LENGTH_SHORT).show();
            }
            UpdateChecker.checkAsync(ctx, new UpdateChecker.Callback() {
                @Override
                public void onResult(boolean updateAvailable, String latestTag, String currentVersion, String url) {
                    if (!isAdded()) {
                        return;
                    }
                    if (updateAvailable) {
                        showUpdateDialog(latestTag, currentVersion, url);
                    } else if (manual) {
                        Toast.makeText(getContext(),
                                getString(R.string.update_uptodate, currentVersion), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String message) {
                    if (manual && isAdded()) {
                        Toast.makeText(getContext(),
                                getString(R.string.update_failed, message), Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        private void showUpdateDialog(String latestTag, String currentVersion, final String url) {
            if (getActivity() == null) {
                return;
            }
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.update_available_title)
                    .setMessage(getString(R.string.update_available_msg, latestTag, currentVersion))
                    .setPositiveButton(R.string.update_download, (d, w) -> openUrl(url))
                    .setNegativeButton(R.string.update_later, null)
                    .show();
        }

        private void openUrl(String url) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Throwable t) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), url, Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
