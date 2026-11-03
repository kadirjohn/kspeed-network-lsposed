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
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
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

    private long lastRx, lastTx, lastTs;
    private String lastSource;
    private boolean running;
    private boolean baselineSeeded;
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
        long[] s = sampleBytes(sourceKind(getString(Constants.KEY_SOURCE, Constants.DEF_SOURCE)));
        lastRx = s[0];
        lastTx = s[1];
        lastTs = SystemClock.elapsedRealtime();
        lastSource = getString(Constants.KEY_SOURCE, Constants.DEF_SOURCE);
        baselineSeeded = true;
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
        long[] s = sampleBytes(sourceKind(source));
        long rx = s[0];
        long tx = s[1];

        double rxps;
        double txps;
        long dt = now - lastTs;
        long dRx = rx - lastRx;
        long dTx = tx - lastTx;
        boolean sourceChanged = !source.equals(lastSource);
        if (!baselineSeeded || sourceChanged || dt <= 0 || dRx < 0 || dTx < 0) {
            rxps = 0;
            txps = 0;
        } else {
            rxps = dRx * 1000.0 / dt;
            txps = dTx * 1000.0 / dt;
        }
        lastRx = rx;
        lastTx = tx;
        lastTs = now;
        lastSource = source;
        baselineSeeded = true;

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
        if (icon != null) {
            int alpha = (dimZero && bps <= 0.0) ? DIM_ALPHA : 255;
            icon.setBounds(0, 0, iconSz, iconSz);
            icon.setTint(color, alpha);
            tv.setCompoundDrawables(icon, null, null, null);
        } else {
            tv.setCompoundDrawables(null, null, null, null);
        }
        tv.setText(format(bps, unitMode, showUnit));
        tv.setVisibility(VISIBLE);
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

    private long[] sampleBytes(int kind) {
        if (kind == SRC_WIFI || kind == SRC_CELL) {
            return procSum(kind);
        }
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {
            return procSum(SRC_TOTAL);
        }
        return new long[]{rx, tx};
    }

    /** Sum rx/tx bytes from /proc/net/dev for interfaces matching the requested class. */
    private static long[] procSum(int kind) {
        long rx = 0;
        long tx = 0;
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.FileReader("/proc/net/dev"))) {
            String l;
            while ((l = br.readLine()) != null) {
                int colon = l.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String iface = l.substring(0, colon).trim();
                if (!ifaceMatches(iface, kind)) {
                    continue;
                }
                String[] f = l.substring(colon + 1).trim().split("\\s+");
                if (f.length > 8) {
                    rx += Long.parseLong(f[0]);
                    tx += Long.parseLong(f[8]);
                }
            }
        } catch (Throwable ignored) {
        }
        return new long[]{rx, tx};
    }

    private static boolean ifaceMatches(String iface, int kind) {
        switch (kind) {
            case SRC_WIFI:
                return iface.startsWith("wlan");
            case SRC_CELL:
                return iface.startsWith("rmnet") || iface.startsWith("ccmni") || iface.startsWith("pdp");
            case SRC_TOTAL:
            default:
                // physical radios only — exclude loopback, tunnels/VPN and other virtual ifaces
                return iface.startsWith("wlan") || iface.startsWith("rmnet")
                        || iface.startsWith("ccmni") || iface.startsWith("eth")
                        || iface.startsWith("usb") || iface.startsWith("rndis");
        }
    }

    // ---- formatting ----

    private static String format(double bytesPerSec, String unitMode, boolean showUnit) {
        if (unitMode == null) {
            unitMode = "auto_bytes";
        }
        if (unitMode.startsWith("auto")) {
            boolean bits = "auto_bits".equals(unitMode);
            double value = bits ? bytesPerSec * 8.0 : bytesPerSec;
            double base = bits ? 1000.0 : 1024.0;
            String[] units = bits
                    ? new String[]{"bit/s", "Kbit/s", "Mbit/s", "Gbit/s"}
                    : new String[]{"B/s", "KB/s", "MB/s", "GB/s"};
            int i = 0;
            while (value >= base && i < units.length - 1) {
                value /= base;
                i++;
            }
            String num = (i == 0 || value >= 10)
                    ? String.format(Locale.US, "%.0f", value)
                    : String.format(Locale.US, "%.1f", value);
            return num + " " + units[i];
        }

        double value;
        String label;
        switch (unitMode) {
            case "mbs":   value = bytesPerSec / 1048576.0;       label = "MB/s";   break;
            case "kbits": value = bytesPerSec * 8.0 / 1000.0;    label = "Kbit/s"; break;
            case "mbits": value = bytesPerSec * 8.0 / 1000000.0; label = "Mbit/s"; break;
            case "kbs":
            default:      value = bytesPerSec / 1024.0;          label = "KB/s";   break;
        }
        String num = value >= 10
                ? String.format(Locale.US, "%.0f", value)
                : String.format(Locale.US, "%.1f", value);
        return showUnit ? num + " " + label : num;
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

    /** A small filled triangle drawn in the text color: pointing down (download) or up (upload). */
    private static final class ArrowDrawable extends Drawable {
        private final boolean down;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ArrowDrawable(boolean down) {
            this.down = down;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
        }

        void setTint(int color, int alpha) {
            paint.setColor(color);
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void draw(@androidx.annotation.NonNull Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) {
                return;
            }
            float padX = b.width() * 0.16f;
            float padY = b.height() * 0.22f;
            Path p = new Path();
            if (down) {
                p.moveTo(b.left + padX, b.top + padY);
                p.lineTo(b.right - padX, b.top + padY);
                p.lineTo(b.exactCenterX(), b.bottom - padY);
            } else {
                p.moveTo(b.left + padX, b.bottom - padY);
                p.lineTo(b.right - padX, b.bottom - padY);
                p.lineTo(b.exactCenterX(), b.top + padY);
            }
            p.close();
            canvas.drawPath(p, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@androidx.annotation.Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
