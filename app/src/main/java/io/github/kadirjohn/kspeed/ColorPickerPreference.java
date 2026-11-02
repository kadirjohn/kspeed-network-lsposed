package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/**
 * Material 3 Color Picker Preference.
 * Displays a solid color preview circle on the right and opens a dialog with
 * preset swatches and a validated #RRGGBB hex input field.
 */
public class ColorPickerPreference extends Preference {

    private static final String[] PRESET_ROW_1 = {
            "#FFFFFF", "#F44336", "#FF9800", "#FFEB3B", "#4CAF50", "#00BCD4"
    };
    private static final String[] PRESET_ROW_2 = {
            "#2196F3", "#3F51B5", "#9C27B0", "#E91E63", "#76FF03", "#80C0FF"
    };

    private String colorHex = "#FFFFFF";

    public ColorPickerPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_color_picker);
    }

    public ColorPickerPreference(@NonNull Context context) {
        super(context);
        setLayoutResource(R.layout.preference_color_picker);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        String def = defaultValue instanceof String ? (String) defaultValue : "#FFFFFF";
        colorHex = normalizeHex(getPersistedString(def));
        if (colorHex == null) {
            colorHex = "#FFFFFF";
        }
        setSummary(colorHex);
    }

    public void setColor(String hex) {
        String normalized = normalizeHex(hex);
        if (normalized != null && !normalized.equals(colorHex)) {
            colorHex = normalized;
            persistString(normalized);
            setSummary(normalized);
            notifyChanged();
        }
    }

    public String getColor() {
        return colorHex;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ImageView iconView = (ImageView) holder.findViewById(R.id.color_pref_icon);
        if (iconView != null) {
            if (getIcon() != null) {
                iconView.setImageDrawable(getIcon());
                iconView.setVisibility(View.VISIBLE);
            } else {
                iconView.setVisibility(View.GONE);
            }
        }

        TextView titleView = (TextView) holder.findViewById(R.id.color_pref_title);
        if (titleView != null) {
            titleView.setText(getTitle());
        }

        TextView summaryView = (TextView) holder.findViewById(R.id.color_pref_summary);
        if (summaryView != null) {
            summaryView.setText(colorHex);
        }

        View circleView = holder.findViewById(R.id.color_preview_circle);
        if (circleView != null) {
            applyCircleColor(circleView, parseColorSafe(colorHex));
            circleView.setAlpha(isEnabled() ? 1.0f : 0.4f);
        }
    }

    @Override
    protected void onClick() {
        showColorDialog();
    }

    private void showColorDialog() {
        final Context ctx = getContext();
        if (ctx == null) return;

        View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_color_picker, null);
        final View previewView = dialogView.findViewById(R.id.dialog_color_preview);
        final EditText hexInput = dialogView.findViewById(R.id.dialog_hex_input);
        LinearLayout row1 = dialogView.findViewById(R.id.swatches_row_1);
        LinearLayout row2 = dialogView.findViewById(R.id.swatches_row_2);

        applyCircleColor(previewView, parseColorSafe(colorHex));
        hexInput.setText(colorHex);
        hexInput.setSelection(colorHex.length());

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String normalized = normalizeHex(s != null ? s.toString() : "");
                if (normalized != null) {
                    applyCircleColor(previewView, parseColorSafe(normalized));
                }
            }
        });

        populateSwatches(ctx, row1, PRESET_ROW_1, hexInput);
        populateSwatches(ctx, row2, PRESET_ROW_2, hexInput);

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(getTitle())
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String entered = hexInput.getText().toString();
                    String normalized = normalizeHex(entered);
                    if (normalized == null) {
                        Toast.makeText(ctx, R.string.invalid_hex, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (callChangeListener(normalized)) {
                        setColor(normalized);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void populateSwatches(Context ctx, LinearLayout row, String[] colors, EditText hexInput) {
        if (row == null) return;
        row.removeAllViews();
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, ctx.getResources().getDisplayMetrics());
        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, ctx.getResources().getDisplayMetrics());

        for (String hex : colors) {
            View swatch = new View(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            swatch.setLayoutParams(lp);
            applyCircleColor(swatch, parseColorSafe(hex));
            swatch.setOnClickListener(v -> {
                hexInput.setText(hex);
                hexInput.setSelection(hex.length());
            });
            row.addView(swatch);
        }
    }

    private void applyCircleColor(View view, int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        int strokeWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2,
                view.getContext().getResources().getDisplayMetrics());
        gd.setStroke(strokeWidth, 0x44888888);
        view.setBackground(gd);
    }

    private static int parseColorSafe(String hex) {
        try {
            return Color.parseColor(hex);
        } catch (Throwable t) {
            return Color.WHITE;
        }
    }

    public static String normalizeHex(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (!s.startsWith("#")) s = "#" + s;
        if (s.length() == 4) { // #RGB -> #RRGGBB
            s = "#" + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2)
                    + s.charAt(3) + s.charAt(3);
        }
        if (s.length() != 7) {
            return null;
        }
        try {
            Color.parseColor(s);
        } catch (Throwable t) {
            return null;
        }
        return s.toUpperCase(Locale.US);
    }
}
