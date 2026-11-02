package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/**
 * A Material 3 single-select Chip Group Preference.
 * Displays options as horizontal chips with one-touch selection, optional icons, and tactile haptic feedback.
 */
public class ChipGroupPreference extends Preference {

    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private String selectedValue;

    public ChipGroupPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_chip_group);
        setSelectable(false);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, new int[]{
                    android.R.attr.entries,
                    android.R.attr.entryValues,
                    android.R.attr.defaultValue
            });
            entries = a.getTextArray(0);
            entryValues = a.getTextArray(1);
            selectedValue = a.getString(2);
            a.recycle();
        }

        initDefaults();
    }

    public ChipGroupPreference(@NonNull Context context) {
        super(context);
        setLayoutResource(R.layout.preference_chip_group);
        setSelectable(false);
    }

    private void initDefaults() {
        if (entries == null || entryValues == null) {
            String key = getKey();
            if (Constants.KEY_SOURCE.equals(key)) {
                entries = getContext().getResources().getTextArray(R.array.source_entries);
                entryValues = getContext().getResources().getTextArray(R.array.source_values);
                if (selectedValue == null) selectedValue = Constants.DEF_SOURCE;
            } else if (Constants.KEY_MODE.equals(key)) {
                entries = getContext().getResources().getTextArray(R.array.mode_entries);
                entryValues = getContext().getResources().getTextArray(R.array.mode_values);
                if (selectedValue == null) selectedValue = Constants.DEF_MODE;
            } else if (Constants.KEY_UNIT.equals(key)) {
                entries = getContext().getResources().getTextArray(R.array.unit_entries);
                entryValues = getContext().getResources().getTextArray(R.array.unit_values);
                if (selectedValue == null) selectedValue = Constants.DEF_UNIT;
            } else if (Constants.KEY_INTERVAL.equals(key)) {
                entries = getContext().getResources().getTextArray(R.array.interval_entries);
                entryValues = getContext().getResources().getTextArray(R.array.interval_values);
                if (selectedValue == null) selectedValue = Constants.DEF_INTERVAL;
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        String def = defaultValue instanceof String ? (String) defaultValue : "";
        selectedValue = getPersistedString(def.isEmpty() ? selectedValue : def);
    }

    public void setValue(String value) {
        if (value != null && !value.equals(selectedValue)) {
            selectedValue = value;
            persistString(value);
            notifyChanged();
        }
    }

    public String getValue() {
        return selectedValue;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ImageView iconView = (ImageView) holder.findViewById(R.id.chip_pref_icon);
        if (iconView != null) {
            if (getIcon() != null) {
                iconView.setImageDrawable(getIcon());
                iconView.setVisibility(View.VISIBLE);
            } else {
                iconView.setVisibility(View.GONE);
            }
        }

        TextView titleView = (TextView) holder.findViewById(R.id.chip_pref_title);
        if (titleView != null) {
            titleView.setText(getTitle());
        }

        TextView summaryView = (TextView) holder.findViewById(R.id.chip_pref_summary);
        if (summaryView != null) {
            if (getSummary() != null && getSummary().length() > 0) {
                summaryView.setText(getSummary());
                summaryView.setVisibility(View.VISIBLE);
            } else {
                summaryView.setVisibility(View.GONE);
            }
        }

        ChipGroup group = (ChipGroup) holder.findViewById(R.id.chip_group);
        if (group != null && entries != null && entryValues != null) {
            group.removeAllViews();
            Context ctx = group.getContext();

            int matchedIndex = findBestMatchIndex(selectedValue);

            for (int i = 0; i < entries.length; i++) {
                final int index = i;
                final String val = entryValues[i].toString();
                Chip chip = new Chip(ctx);
                chip.setText(entries[i]);
                chip.setCheckable(true);
                chip.setClickable(isEnabled());
                chip.setEnabled(isEnabled());

                // Set optional icon
                int iconRes = getIconForValue(getKey(), val);
                if (iconRes != 0) {
                    chip.setChipIcon(ContextCompat.getDrawable(ctx, iconRes));
                    chip.setChipIconVisible(true);
                }

                if (index == matchedIndex) {
                    chip.setChecked(true);
                }

                chip.setOnClickListener(v -> {
                    chip.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    if (callChangeListener(val)) {
                        setValue(val);
                    }
                });

                group.addView(chip);
            }
        }
    }

    private int findBestMatchIndex(String currentVal) {
        if (currentVal == null || entryValues == null) return 0;
        for (int i = 0; i < entryValues.length; i++) {
            if (entryValues[i].toString().equals(currentVal)) {
                return i;
            }
        }
        // If not found (e.g. custom interval in ms), find closest numerical match
        try {
            long currentNum = Long.parseLong(currentVal);
            long closestDiff = Long.MAX_VALUE;
            int closestIdx = 0;
            for (int i = 0; i < entryValues.length; i++) {
                long candidate = Long.parseLong(entryValues[i].toString());
                long diff = Math.abs(candidate - currentNum);
                if (diff < closestDiff) {
                    closestDiff = diff;
                    closestIdx = i;
                }
            }
            return closestIdx;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int getIconForValue(String key, String val) {
        if (Constants.KEY_SOURCE.equals(key)) {
            switch (val) {
                case "total": return R.drawable.illus_source_total;
                case "wifi": return R.drawable.illus_source_wifi;
                case "cellular": return R.drawable.illus_source_cell;
            }
        } else if (Constants.KEY_MODE.equals(key)) {
            switch (val) {
                case "both": return R.drawable.illus_mode_both;
                case "combined": return R.drawable.illus_mode_combined;
                case "down": return R.drawable.illus_mode_down;
            }
        }
        return 0;
    }
}
