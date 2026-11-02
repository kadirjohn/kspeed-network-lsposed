package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/**
 * Settings UI. Material 3 + dynamic color (Monet) when the device supports it, baseline M3
 * palette otherwise. The window is edge-to-edge (forced by the OS on this targetSdk); real
 * WindowInsets are applied here once as padding — top (status bar + cutout) to the app bar,
 * bottom (navigation bar / gesture area) to the list container — so no preference text can
 * draw under the system clock/icons, and bottom rows stay reachable above the nav bar.
 */
public class SettingsActivity extends androidx.appcompat.app.AppCompatActivity {

    private StatusPreviewController previewController;
    private SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sharedPreferences, key) -> updatePreview();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);

        applyInsets();

        prefs = getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE);
        View previewCard = findViewById(R.id.settings_preview_card);
        if (previewCard != null) {
            previewController = new StatusPreviewController(previewCard);
            updatePreview();
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    public void updatePreview() {
        if (previewController != null && prefs != null) {
            previewController.update(prefs);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null) {
            prefs.registerOnSharedPreferenceChangeListener(prefListener);
            updatePreview();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
    }

    /**
     * Apply the real system insets. Top (status bar + display cutout) pads the root view, so
     * the toolbar keeps its natural actionBarSize and its title can never be squeezed by
     * inset padding (padding the toolbar itself clipped the title on the measured device).
     * Bottom (navigation bar / gesture area + IME) pads the list container, so bottom rows
     * stay reachable and the hex field is visible with the keyboard open.
     */
    private void applyInsets() {
        final View root = findViewById(R.id.settings_root);
        final View container = findViewById(R.id.settings_container);
        root.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            WindowInsetsCompat insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets);
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()).top;
            int left = insets.getInsets(WindowInsetsCompat.Type.systemBars()).left;
            int right = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime()).bottom;
            root.setPadding(left, top, right, root.getPaddingBottom());
            container.setPadding(container.getPaddingLeft(), container.getPaddingTop(),
                    container.getPaddingRight(), bottom);
            return WindowInsetsCompat.CONSUMED.toWindowInsets();
        });
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

            AboutCardPreference aboutCard = findPreference("about_card");
            if (aboutCard != null) {
                aboutCard.setCallback(new AboutCardPreference.Callback() {
                    @Override
                    public void onCheckUpdate() {
                        runUpdateCheck(true);
                    }

                    @Override
                    public void onViewSource() {
                        openUrl(Constants.REPO_PAGE);
                    }
                });
            }

            bindColorPreference(findPreference(Constants.KEY_CUSTOM_COLOR));
            bindColorPreference(findPreference(Constants.KEY_CUSTOM_COLOR_UL));

            Toast.makeText(ctx, getString(R.string.hint_restart), Toast.LENGTH_LONG).show();
        }

        /** Hex color entry: prefill with the stored value, show a live color preview chip in the summary. */
        private void bindColorPreference(final Preference pref) {
            if (pref == null) {
                return;
            }
            SharedPreferences sp = getPreferenceManager().getSharedPreferences();
            final String key = pref.getKey();
            String hex = sp != null ? sp.getString(key, "#FFFFFF") : "#FFFFFF";
            pref.setSummary(hex);
            pref.setOnPreferenceChangeListener((p, newValue) -> {
                // Edits are fully owned by showHexDialog (below), which validates and writes
                // the normalized value itself. Reaching here means a non-interactive write;
                // normalize-and-write ourselves and block the framework's raw write.
                String v = normalizeHex(newValue == null ? "" : newValue.toString());
                if (v == null) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), R.string.invalid_hex, Toast.LENGTH_SHORT).show();
                    }
                    return false; // invalid input: keep the old value
                }
                SharedPreferences sp2 = getPreferenceManager().getSharedPreferences();
                if (sp2 != null) {
                    sp2.edit().putString(key, v).apply();
                    notifyHook();
                }
                p.setSummary(v);
                return false; // already written by us; block the framework's un-normalized write
            });
            // Rich entry: tap opens a dialog pre-filled with the current value.
            pref.setOnPreferenceClickListener(p -> {
                showHexDialog(pref, key);
                return true;
            });
        }

        private String normalizeHex(String raw) {
            if (raw == null) return null;
            String s = raw.trim();
            if (s.isEmpty()) return null;
            if (!s.startsWith("#")) s = "#" + s;
            if (s.length() == 4) { // #RGB -> #RRGGBB
                s = "#" + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2)
                        + s.charAt(3) + s.charAt(3);
            }
            try {
                Color.parseColor(s);
            } catch (Throwable t) {
                return null;
            }
            return s.toUpperCase(Locale.US);
        }

        private void showHexDialog(final Preference pref, final String key) {
            Context ctx = getContext();
            if (ctx == null) return;
            SharedPreferences sp = getPreferenceManager().getSharedPreferences();
            String current = sp != null ? sp.getString(key, "#FFFFFF") : "#FFFFFF";
            final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx);
            builder.setTitle(pref.getTitle());
            final EditText input = new EditText(ctx);
            input.setText(current);
            input.setSelection(input.length());
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});
            builder.setView(input);
            builder.setPositiveButton(android.R.string.ok, (d, w) -> {
                String v = normalizeHex(input.getText().toString());
                if (v == null) {
                    Toast.makeText(ctx, R.string.invalid_hex, Toast.LENGTH_SHORT).show();
                    return;
                }
                SharedPreferences sp2 = getPreferenceManager().getSharedPreferences();
                if (sp2 != null) {
                    sp2.edit().putString(key, v).apply();
                    notifyHook();
                    if (getActivity() instanceof SettingsActivity) {
                        ((SettingsActivity) getActivity()).updatePreview();
                    }
                }
                pref.setSummary(v);
            });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
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
            if (getActivity() instanceof SettingsActivity) {
                ((SettingsActivity) getActivity()).updatePreview();
            }
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
            new MaterialAlertDialogBuilder(getActivity())
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
