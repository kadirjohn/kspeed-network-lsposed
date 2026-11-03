package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Detects whether this module is actually enabled in LSPosed (or a compatible manager) with
 * the SystemUI scope, so the settings app can warn the user instead of silently doing nothing.
 *
 * Detection strategy: our app process is NOT hooked (the module's scope is SystemUI only),
 * so Xposed framework classes are not visible here and the manager's config files are not
 * readable by an app. The reliable public signal is a check-in beacon: the SystemUI-side hook
 * already calls our exported ConfigProvider over binder ({@link NetSpeedView#refreshConfig}),
 * and additionally reports {@link #METHOD_HELLO} once per surface attach. The provider
 * executes in this app's process and records the timestamp in app-only prefs. If a check-in
 * exists and is fresh, the module is demonstrably enabled and running in SystemUI.
 */
public final class LSPosedStatus {

    /** Provider method the SystemUI hook calls once per surface attach ("I am alive"). */
    public static final String METHOD_HELLO = "hello";
    /** App-only prefs keys (netspeed_app — not part of the netspeed_prefs provider contract). */
    public static final String KEY_HELLO_TS = "hook_hello_ts";
    public static final String KEY_HELLO_SURFACES = "hook_hello_surfaces";

    /** A check-in older than this is considered stale (SystemUI probably rebooted since). */
    private static final long STALE_MS = 6 * 60 * 60 * 1000L;

    private LSPosedStatus() {}

    /**
     * Milliseconds since the hook's last check-in, or null when it has never checked in.
     * A null result means "no evidence the module is enabled for SystemUI" — either the
     * module is disabled/unscoped, or SystemUI has not been restarted since it was enabled.
     */
    public static Long getHelloAgeMs(Context ctx) {
        try {
            SharedPreferences sp =
                    ctx.getSharedPreferences(Constants.APP_PREFS, Context.MODE_PRIVATE);
            if (!sp.contains(KEY_HELLO_TS)) {
                return null;
            }
            long ts = sp.getLong(KEY_HELLO_TS, 0L);
            if (ts <= 0L) {
                return null;
            }
            return System.currentTimeMillis() - ts;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Surfaces that have ever checked in (for diagnostics; may be an empty string). */
    public static String getHelloSurfaces(Context ctx) {
        try {
            SharedPreferences sp =
                    ctx.getSharedPreferences(Constants.APP_PREFS, Context.MODE_PRIVATE);
            return sp.getString(KEY_HELLO_SURFACES, "");
        } catch (Throwable t) {
            return "";
        }
    }

    /** True if the SystemUI hook has ever checked in through the provider. */
    public static boolean hasHookCheckedIn(Context ctx) {
        return getHelloAgeMs(ctx) != null;
    }

    /** Human-readable status line for the setup card. */
    public static CharSequence describeStatus(Context ctx) {
        Long age = getHelloAgeMs(ctx);
        if (age == null) {
            return ctx.getString(R.string.status_no_hook);
        }
        if (age > STALE_MS) {
            return ctx.getString(R.string.status_hook_stale);
        }
        return ctx.getString(R.string.status_hook_ok);
    }

    /** Records a check-in; called from ConfigProvider (our process) on METHOD_HELLO. */
    static void recordHello(Context ctx, String surface) {
        try {
            SharedPreferences sp =
                    ctx.getSharedPreferences(Constants.APP_PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            String surfaces = sp.getString(KEY_HELLO_SURFACES, "");
            if (surface != null && surface.length() > 0
                    && !(" " + surfaces + " ").contains(" " + surface + " ")) {
                surfaces = surfaces.length() == 0 ? surface : surfaces + " " + surface;
                sp.edit().putString(KEY_HELLO_SURFACES, surfaces).apply();
            }
            sp.edit().putLong(KEY_HELLO_TS, now).apply();
        } catch (Throwable ignored) {
        }
    }
}
