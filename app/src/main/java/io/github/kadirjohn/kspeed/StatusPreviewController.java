package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Controller for the live status-bar preview card in SettingsActivity.
 * Pinned at the top of the settings screen and reflects preference changes immediately.
 */
public final class StatusPreviewController {

    private static final double SAMPLE_RX_BPS = 340.0 * 1024.0;         // ~340 KB/s
    private static final double SAMPLE_TX_BPS = 1.2 * 1024.0 * 1024.0;    // ~1.2 MB/s
    private static final int DIM_ALPHA = 90;

    private final View cardRoot;
    private final TextView mockClock;
    private final LinearLayout indicatorContainer;
    private final TextView line1;
    private final TextView line2;

    private final ArrowDrawable downArrow = new ArrowDrawable(true);
    private final ArrowDrawable upArrow = new ArrowDrawable(false);

    public StatusPreviewController(View cardRoot) {
        this.cardRoot = cardRoot;
        this.mockClock = cardRoot.findViewById(R.id.mock_statusbar_clock);
        this.indicatorContainer = cardRoot.findViewById(R.id.preview_indicator_container);
        this.line1 = cardRoot.findViewById(R.id.preview_line1);
        this.line2 = cardRoot.findViewById(R.id.preview_line2);

        if (mockClock != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            mockClock.setText(sdf.format(new Date()));
        }
    }

    public void update(SharedPreferences sp) {
        if (sp == null || cardRoot == null) {
            return;
        }

        Context ctx = cardRoot.getContext();
        boolean enabled = sp.getBoolean(Constants.KEY_ENABLED, Constants.DEF_ENABLED);
        String mode = sp.getString(Constants.KEY_MODE, Constants.DEF_MODE);
        String unitMode = sp.getString(Constants.KEY_UNIT, Constants.DEF_UNIT);
        boolean showUnit = sp.getBoolean(Constants.KEY_SHOW_UNIT, Constants.DEF_SHOW_UNIT);
        boolean upTop = sp.getBoolean(Constants.KEY_UP_ON_TOP, Constants.DEF_UP_ON_TOP);
        boolean dimZero = sp.getBoolean(Constants.KEY_DIM_ZERO, Constants.DEF_DIM_ZERO);
        int textSizeSp = sp.getInt(Constants.KEY_TEXT_SIZE, Constants.DEF_TEXT_SIZE);
        String colorMode = sp.getString(Constants.KEY_COLOR_MODE, Constants.DEF_COLOR_MODE);
        String customDlHex = sp.getString(Constants.KEY_CUSTOM_COLOR, Constants.DEF_CUSTOM_COLOR);
        String customUlHex = sp.getString(Constants.KEY_CUSTOM_COLOR_UL, Constants.DEF_CUSTOM_COLOR_UL);

        int defaultColor = resolveThemeColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        int dlColor = defaultColor;
        int ulColor = defaultColor;

        if ("custom".equals(colorMode)) {
            try {
                dlColor = Color.parseColor(customDlHex);
            } catch (Throwable ignored) {
                dlColor = defaultColor;
            }
            try {
                ulColor = Color.parseColor(customUlHex);
            } catch (Throwable ignored) {
                ulColor = defaultColor;
            }
        }

        if (!enabled) {
            indicatorContainer.setVisibility(View.GONE);
            return;
        }

        indicatorContainer.setVisibility(View.VISIBLE);

        line1.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        line2.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        // Bold text mirrors the real indicator: independent of size.
        boolean bold = sp.getBoolean(Constants.KEY_BOLD_TEXT, Constants.DEF_BOLD_TEXT);
        android.graphics.Typeface typeface = bold
                ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT;
        line1.setTypeface(typeface);
        line2.setTypeface(typeface);
        int iconSz = Math.round(line1.getTextSize() * 0.82f);

        switch (mode != null ? mode : Constants.DEF_MODE) {
            case "combined":
                renderEntry(line1, null, SAMPLE_RX_BPS + SAMPLE_TX_BPS, dlColor, iconSz, unitMode, showUnit, false);
                line2.setVisibility(View.GONE);
                break;
            case "down":
                renderEntry(line1, downArrow, SAMPLE_RX_BPS, dlColor, iconSz, unitMode, showUnit, dimZero);
                line2.setVisibility(View.GONE);
                break;
            case "both":
            default:
                if (upTop) {
                    renderEntry(line1, upArrow, SAMPLE_TX_BPS, ulColor, iconSz, unitMode, showUnit, dimZero);
                    renderEntry(line2, downArrow, SAMPLE_RX_BPS, dlColor, iconSz, unitMode, showUnit, dimZero);
                } else {
                    renderEntry(line1, downArrow, SAMPLE_RX_BPS, dlColor, iconSz, unitMode, showUnit, dimZero);
                    renderEntry(line2, upArrow, SAMPLE_TX_BPS, ulColor, iconSz, unitMode, showUnit, dimZero);
                }
                break;
        }
    }

    private void renderEntry(TextView tv, ArrowDrawable icon, double bps, int color, int iconSz,
                             String unitMode, boolean showUnit, boolean dimZero) {
        tv.setTextColor(color);
        if (icon != null) {
            int alpha = (dimZero && bps <= 0.0) ? DIM_ALPHA : 255;
            icon.setBounds(0, 0, iconSz, iconSz);
            icon.setTint(color, alpha);
            tv.setCompoundDrawables(icon, null, null, null);
            int pad = Math.round(1 * tv.getContext().getResources().getDisplayMetrics().density);
            tv.setCompoundDrawablePadding(pad);
        } else {
            tv.setCompoundDrawables(null, null, null, null);
        }
        tv.setText(SpeedFormatter.format(bps, unitMode, showUnit));
        tv.setVisibility(View.VISIBLE);
    }

    @ColorInt
    private static int resolveThemeColor(Context context, @AttrRes int attr, @ColorInt int fallback) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        return fallback;
    }
}
