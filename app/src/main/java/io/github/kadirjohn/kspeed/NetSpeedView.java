package io.github.kadirjohn.kspeed;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import de.robv.android.xposed.XposedBridge;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The indicator view: a tiny two-line LinearLayout (download / upload, each with a drawn ▼/▲ icon)
 * placed before the system-icon cluster on one status-bar surface. Runs its own refresh loop on the SystemUI
 * main thread while attached AND visible; reads settings from {@link ConfigProvider} over binder.
 */
@SuppressLint("ViewConstructor")
public class NetSpeedView extends LinearLayout {

    public static final String VIEW_TAG = "netspeed_view";

    public static final String SURFACE_COLLAPSED = "collapsed";
    public static final String SURFACE_SHADE = "shade";
    public static final String SURFACE_KEYGUARD = "keyguard";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final int DIM_ALPHA = 90;

    // Interface classes for per-source sampling.
    private static final int SRC_TOTAL = 0;
    private static final int SRC_WIFI = 1;
    private static final int SRC_CELL = 2;

    private final String surface;
    private final TextView line1;
    private final TextView line2;
    private final ArrowDrawable downArrow = new ArrowDrawable(true);
    private final ArrowDrawable upArrow = new ArrowDrawable(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StatusBarSpacing spacing = new StatusBarSpacing(this);

    private Bundle config = new Bundle();
    private ContentObserver observer;

    private final NetworkTrafficSampler trafficSampler;
    private boolean running;
    private View cachedClock;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long interval = 1000L;
            try {
                interval = intervalMs();
                update();
            } catch (Throwable t) {
                if (Log.isLoggable("KSpeed", Log.DEBUG)) {
                    Log.d("KSpeed", "surface=" + surface + " update failed", t);
                }
            }
            if (running) {
                handler.postDelayed(this, interval);
            }
        }
    };

    public NetSpeedView(Context c, String surface) {
        super(c);
        this.surface = surface;
        this.trafficSampler = new NetworkTrafficSampler(c);
        setTag(VIEW_TAG);
        setOrientation(VERTICAL);
        setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        setClipChildren(false);
        int pad = dp(c, 4);
        setPadding(pad, 0, pad, 0);

        line1 = makeLine(c);
        line2 = makeLine(c);
        addView(line1, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(line2, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    private TextView makeLine(Context c) {
        TextView t = new TextView(c);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        // Each row measures its own text plus drawable. MATCH_PARENT + END leaves the
        // drawable at the far edge of the wider row while the short number moves away.
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        t.setCompoundDrawablePadding(dp(c, 1));
        t.setLineSpacing(0f, 0.9f);
        return t;
    }

    // ---- lifecycle ----

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Beacon first: proves module activation to the setup card even on surfaces whose
        // per-surface setting keeps this view GONE (e.g. lock screen off) — attach order and
        // visibility must not gate the activation signal.
        sendHello();
        registerObserver();
        refreshConfig();
        seedBaseline();
        if (getWindowVisibility() == VISIBLE) {
            startLoop();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopLoop();
        unregisterObserver();
        cachedClock = null;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            refreshConfig();
            seedBaseline();
            startLoop();
        } else {
            stopLoop();
        }
    }

    private void startLoop() {
        if (!running) {
            running = true;
            handler.post(tick);
        }
    }

    private void stopLoop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private void seedBaseline() {
        trafficSampler.sample(sourceKind(getString(Constants.KEY_SOURCE, Constants.DEF_SOURCE)),
                SystemClock.elapsedRealtime());
    }

    // ---- settings over binder ----

    private void registerObserver() {
        if (observer != null) {
            return;
        }
        observer = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                refreshConfig();
            }
        };
        try {
            getContext().getContentResolver().registerContentObserver(ConfigProvider.URI, true, observer);
        } catch (Throwable ignored) {
        }
    }

    /**
     * One check-in per attach: proves to the settings app that this module is enabled in
     * LSPosed with the SystemUI scope (the settings app cannot detect this any other way —
     * its own process is not hooked). Runs on the IO executor; failures are ignored.
     */
    void sendHello() {
        final Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        // Try immediately on the calling thread first: attach-time binder call is cheap and
        // this is the setup card's only activation signal. If it throws, retry on the IO
        // executor so a transient provider unavailability does not lose the beacon.
        try {
            ctx.getContentResolver().call(ConfigProvider.URI,
                    LSPosedStatus.METHOD_HELLO, surface, null);
            XposedBridge.log(Constants.LOG_TAG + " hello sent (sync) for surface=" + surface);
        } catch (Throwable syncError) {
            XposedBridge.log(Constants.LOG_TAG + " hello sync failed surface=" + surface
                    + " (" + syncError.getClass().getSimpleName() + "); retrying on IO");
            IO.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        ctx.getContentResolver().call(ConfigProvider.URI,
                                LSPosedStatus.METHOD_HELLO, surface, null);
                        XposedBridge.log(Constants.LOG_TAG + " hello sent (io) for surface=" + surface);
                    } catch (Throwable t) {
                        // Persistent: this beacon is the setup card's only activation signal.
                        XposedBridge.log(Constants.LOG_TAG + " hello failed surface=" + surface
                                + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
                    }
                }
            });
        }
    }

    private void unregisterObserver() {
        if (observer != null) {
            try {
                getContext().getContentResolver().unregisterContentObserver(observer);
            } catch (Throwable ignored) {
            }
            observer = null;
        }
    }

    private void refreshConfig() {
        final Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bundle b = ctx.getContentResolver()
                            .call(ConfigProvider.URI, ConfigProvider.METHOD_GET, null, null);
                    if (b != null) {
                        b.size();
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                config = b;
                                StatusBarHook.updateIconConfig(b);
                            }
                        });
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    // ---- core update ----

    private void update() {
        if (!getBool(Constants.KEY_ENABLED, Constants.DEF_ENABLED) || !surfaceEnabled()) {
            setIndicatorVisibility(GONE, "disabled/surface");
            return;
        }

        String source = getString(Constants.KEY_SOURCE, Constants.DEF_SOURCE);
        long now = SystemClock.elapsedRealtime();
        double[] rates = trafficSampler.sample(sourceKind(source), now);
        double rxps = rates[0];
        double txps = rates[1];

        String mode = getString(Constants.KEY_MODE, Constants.DEF_MODE);
        String unitMode = getString(Constants.KEY_UNIT, Constants.DEF_UNIT);
        boolean showUnit = getBool(Constants.KEY_SHOW_UNIT, Constants.DEF_SHOW_UNIT);

        if (getBool(Constants.KEY_HIDE_IDLE, Constants.DEF_HIDE_IDLE)) {
            double thresholdBps = getInt(Constants.KEY_IDLE_THRESHOLD, Constants.DEF_IDLE_THRESHOLD) * 1024.0;
            double activity = "down".equals(mode) ? rxps : (rxps + txps);
            if (activity < thresholdBps) {
                setIndicatorVisibility(GONE, "idle");
                return;
            }
        }
        float sizeSp = getInt(Constants.KEY_TEXT_SIZE, Constants.DEF_TEXT_SIZE);
        line1.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        line2.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        int iconSz = Math.round(line1.getTextSize() * 0.82f);

        int dlColor = resolveColor(false);
        int ulColor = resolveColor(true);
        boolean dimZero = getBool(Constants.KEY_DIM_ZERO, Constants.DEF_DIM_ZERO);
        boolean upTop = getBool(Constants.KEY_UP_ON_TOP, Constants.DEF_UP_ON_TOP);

        switch (mode) {
            case "combined":
                renderEntry(line1, null, rxps + txps, dlColor, iconSz, unitMode, showUnit, false);
                line2.setVisibility(GONE);
                break;
            case "down":
                renderEntry(line1, downArrow, rxps, dlColor, iconSz, unitMode, showUnit, dimZero);
                line2.setVisibility(GONE);
                break;
            case "both":
            default:
                if (upTop) {
                    renderEntry(line1, upArrow, txps, ulColor, iconSz, unitMode, showUnit, dimZero);
                    renderEntry(line2, downArrow, rxps, dlColor, iconSz, unitMode, showUnit, dimZero);
                } else {
                    renderEntry(line1, downArrow, rxps, dlColor, iconSz, unitMode, showUnit, dimZero);
                    renderEntry(line2, upArrow, txps, ulColor, iconSz, unitMode, showUnit, dimZero);
                }
                break;
        }
        // Publish visibility after the row content is ready. Spacing is resolved
        // after layout, before drawing, rather than against the previous hidden frame.
        setIndicatorVisibility(VISIBLE, "traffic");
    }

    private void setIndicatorVisibility(int visibility, String reason) {
        boolean changed = getVisibility() != visibility;
        setVisibility(visibility);
        if (changed && Log.isLoggable("KSpeed", Log.DEBUG)) {
            Log.d("KSpeed", "surface=" + surface + " visibility=" + visibility
                    + " reason=" + reason + " idleThresholdKBps="
                    + getInt(Constants.KEY_IDLE_THRESHOLD, Constants.DEF_IDLE_THRESHOLD));
        }
    }

    private void renderEntry(TextView tv, ArrowDrawable icon, double bps, int color, int iconSz,
                             String unitMode, boolean showUnit, boolean dimZero) {
        tv.setTextColor(color);
        // Readability shadow: draw a soft dark-gray shadow only when a light text color sits
        // on a light status bar (e.g. yellow custom color on a white bar). Dark bars — or
        // dark colors on light bars — stay clean and shadowless.
        boolean needShadow = isStatusBarLight() && isLightColor(color);
        float radius = dp2(getContext(), 1f);
        setTextViewShadowLayer(tv, needShadow, radius);
        if (icon != null) {
            int alpha = (dimZero && bps <= 0.0) ? DIM_ALPHA : 255;
            icon.setShadowEnabled(needShadow);
            icon.setBounds(0, 0, iconSz, iconSz);
            icon.setTint(color, alpha);
            tv.setCompoundDrawables(icon, null, null, null);
        } else {
            tv.setCompoundDrawables(null, null, null, null);
        }
        tv.setText(format(bps, unitMode, showUnit));
        tv.setVisibility(VISIBLE);
    }

    /** Applies or clears the text shadow without leaving a stale layer when disabled. */
    private static void setTextViewShadowLayer(TextView tv, boolean enabled, float radius) {
        if (enabled) {
            tv.setShadowLayer(radius, radius * 0.6f, radius * 0.6f, 0x42000000);
        } else {
            tv.setShadowLayer(0f, 0f, 0f, 0x00000000);
        }
    }

    private static float dp2(Context c, float d) {
        return d * c.getResources().getDisplayMetrics().density;
    }

    private boolean surfaceEnabled() {
        if (SURFACE_SHADE.equals(surface)) {
            return getBool(Constants.KEY_SHOW_SHADE, Constants.DEF_SHOW_SHADE);
        }
        if (SURFACE_KEYGUARD.equals(surface)) {
            return getBool(Constants.KEY_SHOW_LOCKSCREEN, Constants.DEF_SHOW_LOCKSCREEN);
        }
        return true; // collapsed status bar: always (subject to the global enable)
    }

    // ---- color / tint ----

    /**
     * True when the indicator sits on a LIGHT status bar, derived from the mirrored clock
     * color: SystemUI renders the clock dark on light bars (and vice versa), so a dark
     * clock means a light bar. Conservative default: no shadow (assumed dark bar).
     */
    private boolean isStatusBarLight() {
        Integer clock = mirrorClockColor();
        if (clock == null) {
            return false;
        }
        return luminance(clock) < 0.5f;
    }

    private static float luminance(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        return (float) (0.2126 * r + 0.7152 * g + 0.0722 * b);
    }

    /** True when this color would be hard to read on the given bar lightness. */
    private static boolean isLightColor(int color) {
        return luminance(color) >= 0.5f;
    }

    private int resolveColor(boolean upload) {
        if ("custom".equals(getString(Constants.KEY_COLOR_MODE, Constants.DEF_COLOR_MODE))) {
            String hex = upload
                    ? getString(Constants.KEY_CUSTOM_COLOR_UL, Constants.DEF_CUSTOM_COLOR_UL)
                    : getString(Constants.KEY_CUSTOM_COLOR, Constants.DEF_CUSTOM_COLOR);
            try {
                return Color.parseColor(hex);
            } catch (Throwable t) {
                return Color.WHITE;
            }
        }
        Integer mirrored = mirrorClockColor();
        return mirrored != null ? mirrored : Color.WHITE;
    }

    private Integer mirrorClockColor() {
        try {
            if (cachedClock == null) {
                View root = getRootView();
                int id = getResources().getIdentifier("clock", "id", Constants.SYSTEMUI_PKG);
                if (id != 0 && root != null) {
                    cachedClock = root.findViewById(id);
                }
            }
            if (cachedClock instanceof TextView) {
                return ((TextView) cachedClock).getCurrentTextColor();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ---- traffic ----

    private static int sourceKind(String source) {
        if ("wifi".equals(source)) {
            return SRC_WIFI;
        }
        if ("cellular".equals(source)) {
            return SRC_CELL;
        }
        return SRC_TOTAL;
    }

    // ---- formatting ----

    private static String format(double bytesPerSec, String unitMode, boolean showUnit) {
        return SpeedFormatter.format(bytesPerSec, unitMode, showUnit);
    }

    // ---- config accessors ----

    private boolean getBool(String key, boolean def) {
        try {
            return config.containsKey(key) ? config.getBoolean(key, def) : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private int getInt(String key, int def) {
        try {
            return config.containsKey(key) ? config.getInt(key, def) : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private String getString(String key, String def) {
        try {
            String v = config.getString(key);
            return v != null ? v : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private long intervalMs() {
        try {
            long v = Long.parseLong(getString(Constants.KEY_INTERVAL, Constants.DEF_INTERVAL));
            return Math.max(500L, Math.min(10000L, v));
        } catch (Throwable t) {
            return 1000L;
        }
    }

    private static int dp(Context c, int d) {
        return Math.round(d * c.getResources().getDisplayMetrics().density);
    }
}
