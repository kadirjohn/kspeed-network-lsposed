package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A switch preference representing "Auto (match status bar)" color mode.
 * Persists "auto" when checked and "custom" when unchecked, preserving full compatibility
 * with Constants.KEY_COLOR_MODE in the SystemUI hook.
 */
public class ColorModeSwitchPreference extends MaterialSwitchPreference {

    public ColorModeSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ColorModeSwitchPreference(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        String current = getPersistedString(Constants.DEF_COLOR_MODE);
        setChecked("auto".equals(current));
    }

    @Override
    public boolean isChecked() {
        String current = getPersistedString(Constants.DEF_COLOR_MODE);
        return "auto".equals(current);
    }

    @Override
    public void setChecked(boolean checked) {
        String value = checked ? "auto" : "custom";
        if (!value.equals(getPersistedString(Constants.DEF_COLOR_MODE))) {
            persistString(value);
            notifyChanged();
        }
    }

    @Override
    protected void onClick() {
        boolean nextChecked = !isChecked();
        String nextValue = nextChecked ? "auto" : "custom";
        if (callChangeListener(nextValue)) {
            setChecked(nextChecked);
        }
    }
}
