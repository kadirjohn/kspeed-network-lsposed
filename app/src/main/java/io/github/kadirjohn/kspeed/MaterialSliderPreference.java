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
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;

/**
 * Material 3 Slider Preference.
 * Displays title, icon, unit label (e.g. "8 sp", "100 KB/s"), value tooltip bubble,
 * and tactile haptic feedback.
 */
public class MaterialSliderPreference extends Preference {

    private int min = 0;
    private int max = 100;
    private int value = 0;
    private String unitSuffix = "";

    public MaterialSliderPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_material_slider);
        setSelectable(false);

        // Read android:max and app:min if present
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, new int[]{
                    android.R.attr.max,
                    androidx.preference.R.attr.min,
                    android.R.attr.defaultValue
            });
            max = a.getInt(0, 100);
            min = a.getInt(1, 0);
            value = a.getInt(2, min);
            a.recycle();
        }

        initDefaults();
    }

    private void initDefaults() {
        if (Constants.KEY_TEXT_SIZE.equals(getKey())) {
            min = 5;
            max = 14;
            unitSuffix = " sp";
        } else if (Constants.KEY_IDLE_THRESHOLD.equals(getKey())) {
            min = 0;
            max = 100;
            unitSuffix = " KB/s";
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, min);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        if (defaultValue instanceof Integer) {
            value = getPersistedInt((Integer) defaultValue);
        } else {
            value = getPersistedInt(min);
        }
    }

    public void setValue(int newValue) {
        int clamped = Math.max(min, Math.min(max, newValue));
        if (value != clamped) {
            value = clamped;
            persistInt(clamped);
            notifyChanged();
        }
    }

    public int getValue() {
        return value;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ImageView iconView = (ImageView) holder.findViewById(R.id.slider_icon);
        if (iconView != null) {
            if (getIcon() != null) {
                iconView.setImageDrawable(getIcon());
                iconView.setVisibility(View.VISIBLE);
            } else {
                iconView.setVisibility(View.GONE);
            }
        }

        TextView titleView = (TextView) holder.findViewById(R.id.slider_title);
        if (titleView != null) {
            titleView.setText(getTitle());
        }

        TextView summaryView = (TextView) holder.findViewById(R.id.slider_summary);
        if (summaryView != null) {
            if (getSummary() != null && getSummary().length() > 0) {
                summaryView.setText(getSummary());
                summaryView.setVisibility(View.VISIBLE);
            } else {
                summaryView.setVisibility(View.GONE);
            }
        }

        final TextView valueView = (TextView) holder.findViewById(R.id.slider_value_text);
        if (valueView != null) {
            valueView.setText(value + unitSuffix);
        }

        final Slider slider = (Slider) holder.findViewById(R.id.material_slider);
        if (slider != null) {
            slider.setValueFrom(min);
            slider.setValueTo(max);
            slider.setStepSize(1.0f);
            slider.setValue(Math.max(min, Math.min(max, value)));
            slider.setEnabled(isEnabled());

            slider.setLabelFormatter(val -> Math.round(val) + unitSuffix);

            slider.addOnChangeListener((s, val, fromUser) -> {
                int rounded = Math.round(val);
                if (valueView != null) {
                    valueView.setText(rounded + unitSuffix);
                }
                if (fromUser) {
                    s.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    if (callChangeListener(rounded)) {
                        value = rounded;
                        persistInt(rounded);
                    }
                }
            });
        }
    }
}
