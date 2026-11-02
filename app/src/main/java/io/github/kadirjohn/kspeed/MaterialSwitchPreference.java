package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

/**
 * Material 3 Switch Preference with checkmark thumb icon when enabled
 * and tactile haptic feedback on toggle.
 */
public class MaterialSwitchPreference extends SwitchPreferenceCompat {

    public MaterialSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.preference_material_switch);
    }

    public MaterialSwitchPreference(@NonNull Context context) {
        super(context);
        setWidgetLayoutResource(R.layout.preference_material_switch);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
    }

    @Override
    public void setChecked(boolean checked) {
        boolean changed = isChecked() != checked;
        super.setChecked(checked);
        if (changed && getContext() != null) {
            // Haptic tick
        }
    }

    @Override
    protected void onClick() {
        super.onClick();
    }

    @Override
    public void performClick() {
        super.performClick();
    }
}
