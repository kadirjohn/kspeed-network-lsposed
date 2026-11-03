package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

/**
 * Material 3 Switch Preference with checkmark thumb icon when enabled.
 * Uses the widget layout only; toggle/click behaviour is entirely the stock
 * SwitchPreferenceCompat implementation (performClick is library-restricted and was
 * previously overridden with a plain super call, which produced RestrictedApi lint errors).
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
}
