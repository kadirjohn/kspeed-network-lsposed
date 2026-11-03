package io.github.kadirjohn.kspeed;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Settings UI. Material 3 + dynamic color (Monet) when the device supports it, baseline M3
 * palette otherwise. Edge-to-edge window insets are handled smoothly.
 */
public class SettingsActivity extends androidx.appcompat.app.AppCompatActivity {

    private StatusPreviewController previewController;
    private SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sharedPreferences, key) -> updatePreview();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        prefs = getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE);
        boolean amoled = prefs.getBoolean(Constants.KEY_AMOLED_BLACK, Constants.DEF_AMOLED_BLACK);
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (amoled && nightMode == Configuration.UI_MODE_NIGHT_YES) {
            setTheme(R.style.AppTheme_Amoled);
        } else {
            DynamicColors.applyToActivitiesIfAvailable(getApplication());
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);

        applyInsets();

        View previewCard = findViewById(R.id.settings_preview_card);
        if (previewCard != null) {
            previewController = new StatusPreviewController(previewCard);
            updatePreview();
            AppBarLayout appbar = findViewById(R.id.settings_appbar);
            appbar.addOnOffsetChangedListener((bar, offset) -> {
                float fadeRange = bar.getTotalScrollRange() * 0.7f;
                float alpha = fadeRange > 0 ? Math.max(0f, 1f - Math.abs(offset) / fadeRange) : 1f;
                previewCard.setAlpha(alpha);
                previewCard.setImportantForAccessibility(alpha == 0f
                        ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            });
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

    private void applyInsets() {
        final View root = findViewById(R.id.settings_root);
        final View appbar = findViewById(R.id.settings_appbar);
        final View container = findViewById(R.id.settings_container);
        root.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            WindowInsetsCompat insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets);
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()).top;
            int left = insets.getInsets(WindowInsetsCompat.Type.systemBars()).left;
            int right = insets.getInsets(WindowInsetsCompat.Type.systemBars()).right;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime()).bottom;

            if (appbar != null) {
                appbar.setPadding(left, top, right, appbar.getPaddingBottom());
            } else {
                root.setPadding(left, top, right, root.getPaddingBottom());
            }
            if (container != null) {
                container.setPadding(container.getPaddingLeft(), container.getPaddingTop(),
                        container.getPaddingRight(), bottom);
            }
            return WindowInsetsCompat.CONSUMED.toWindowInsets();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_settings, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.menu_search));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        filterSettings(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        filterSettings(newText);
                        return true;
                    }
                });
            }
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
                    filterSettings("");
                    return true;
                }
            });
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_reset) {
            showResetDialog();
            return true;
        } else if (id == R.id.action_export) {
            exportSettingsJson();
            return true;
        } else if (id == R.id.action_import) {
            importSettingsJson();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void filterSettings(String query) {
        androidx.fragment.app.Fragment f = getSupportFragmentManager().findFragmentById(R.id.settings_container);
        if (f instanceof SettingsFragment) {
            ((SettingsFragment) f).filterPreferences(query);
        }
    }

    private void showResetDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_confirm_title)
                .setMessage(R.string.reset_confirm_msg)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (prefs != null) {
                        prefs.edit().clear().apply();
                    }
                    PreferenceManager.setDefaultValues(this, Constants.PREFS, Context.MODE_PRIVATE, R.xml.prefs, true);
                    notifyHook();
                    recreate();
                    Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void exportSettingsJson() {
        try {
            Map<String, ?> all = prefs.getAll();
            JSONObject json = new JSONObject();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            String formatted = json.toString(2);

            final TextView tv = new TextView(this);
            tv.setText(formatted);
            tv.setTextIsSelectable(true);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            tv.setPadding(pad, pad, pad, pad);

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.export_title)
                    .setView(tv)
                    .setPositiveButton(R.string.copy_json, (dialog, which) -> {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("KSpeed Settings", formatted));
                            Toast.makeText(this, R.string.json_copied, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Throwable t) {
            Toast.makeText(this, "Export error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importSettingsJson() {
        final EditText input = new EditText(this);
        input.setHint(R.string.import_hint);
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        input.setPadding(pad, pad, pad, pad);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String raw = input.getText().toString().trim();
                    try {
                        JSONObject json = new JSONObject(raw);
                        SharedPreferences.Editor ed = prefs.edit();
                        Iterator<String> keys = json.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            Object val = json.get(k);
                            if (val instanceof Boolean) {
                                ed.putBoolean(k, (Boolean) val);
                            } else if (val instanceof Integer) {
                                ed.putInt(k, (Integer) val);
                            } else if (val instanceof Long) {
                                ed.putLong(k, (Long) val);
                            } else if (val instanceof String) {
                                ed.putString(k, (String) val);
                            }
                        }
                        ed.apply();
                        notifyHook();
                        recreate();
                        Toast.makeText(this, R.string.import_done, Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) {
                        Toast.makeText(this, R.string.import_invalid, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void notifyHook() {
        try {
            getContentResolver().notifyChange(ConfigProvider.URI, null);
        } catch (Throwable ignored) {
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

            Toast.makeText(ctx, getString(R.string.hint_restart), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setDivider(null);
            setDividerHeight(0);
            RecyclerView rv = getListView();
            rv.setClipToPadding(false);

            // Generous internal margin so SVGs have plenty of room from the outline
            final int padHorizontal = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 22, getResources().getDisplayMetrics());

            rv.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(@NonNull View child) {
                    child.setPaddingRelative(padHorizontal, child.getPaddingTop(),
                            padHorizontal, child.getPaddingBottom());
                }

                @Override
                public void onChildViewDetachedFromWindow(@NonNull View child) {}
            });

            rv.addItemDecoration(new CategoryCardItemDecoration(requireContext()));
            updateDependencies();
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
            updateDependencies();
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
            if (Constants.KEY_AMOLED_BLACK.equals(key)) {
                if (getActivity() != null) {
                    getActivity().recreate();
                }
                return;
            }
            updateDependencies();
            if (getActivity() instanceof SettingsActivity) {
                ((SettingsActivity) getActivity()).updatePreview();
            }
        }

        public void updateDependencies() {
            SharedPreferences sp = getPreferenceManager().getSharedPreferences();
            if (sp == null) return;

            boolean enabled = sp.getBoolean(Constants.KEY_ENABLED, Constants.DEF_ENABLED);
            boolean hideIdle = sp.getBoolean(Constants.KEY_HIDE_IDLE, Constants.DEF_HIDE_IDLE);
            String unit = sp.getString(Constants.KEY_UNIT, Constants.DEF_UNIT);
            String colorMode = sp.getString(Constants.KEY_COLOR_MODE, Constants.DEF_COLOR_MODE);

            // 1. When enabled is false, disable all sub-settings
            Preference modePref = findPreference(Constants.KEY_MODE);
            Preference intervalPref = findPreference(Constants.KEY_INTERVAL);
            Preference sourcePref = findPreference(Constants.KEY_SOURCE);
            PreferenceCategory displayCat = findPreference("cat_display");
            PreferenceCategory appearanceCat = findPreference("cat_appearance");
            PreferenceCategory surfacesCat = findPreference("cat_surfaces");

            if (modePref != null) modePref.setEnabled(enabled);
            if (intervalPref != null) intervalPref.setEnabled(enabled);
            if (sourcePref != null) sourcePref.setEnabled(enabled);
            if (displayCat != null) displayCat.setEnabled(enabled);
            if (appearanceCat != null) appearanceCat.setEnabled(enabled);
            if (surfacesCat != null) surfacesCat.setEnabled(enabled);

            // 2. Hide when idle: animate visibility of idle_threshold_kb
            Preference idleThreshold = findPreference(Constants.KEY_IDLE_THRESHOLD);
            if (idleThreshold != null) {
                idleThreshold.setVisible(hideIdle);
            }

            // 3. Auto unit: disable show_unit
            Preference showUnitPref = findPreference(Constants.KEY_SHOW_UNIT);
            if (showUnitPref != null) {
                boolean isAuto = unit != null && unit.startsWith("auto");
                showUnitPref.setEnabled(!isAuto);
                if (isAuto) {
                    showUnitPref.setSummary(R.string.pref_show_unit_auto_note);
                } else {
                    showUnitPref.setSummary(R.string.pref_show_unit_summary);
                }
            }

            // 4. Color mode: auto disables custom colors
            boolean isCustomColor = "custom".equals(colorMode);
            Preference customDl = findPreference(Constants.KEY_CUSTOM_COLOR);
            Preference customUl = findPreference(Constants.KEY_CUSTOM_COLOR_UL);
            if (customDl != null) customDl.setEnabled(isCustomColor);
            if (customUl != null) customUl.setEnabled(isCustomColor);
        }

        public void filterPreferences(String query) {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) return;
            boolean hasQuery = query != null && !query.trim().isEmpty();
            String q = hasQuery ? query.toLowerCase(Locale.getDefault()).trim() : "";

            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                Preference p = screen.getPreference(i);
                if (p instanceof PreferenceCategory) {
                    PreferenceCategory cat = (PreferenceCategory) p;
                    int matchCount = 0;
                    for (int j = 0; j < cat.getPreferenceCount(); j++) {
                        Preference child = cat.getPreference(j);
                        boolean match = !hasQuery || matches(child, q);
                        child.setVisible(match);
                        if (match) matchCount++;
                    }
                    cat.setVisible(!hasQuery || matchCount > 0);
                } else {
                    p.setVisible(!hasQuery || matches(p, q));
                }
            }
        }

        private static boolean matches(Preference p, String q) {
            CharSequence title = p.getTitle();
            if (title != null && title.toString().toLowerCase(Locale.getDefault()).contains(q)) {
                return true;
            }
            CharSequence summary = p.getSummary();
            return summary != null && summary.toString().toLowerCase(Locale.getDefault()).contains(q);
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
