package com.nogrep.netspeed;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Installs the SystemUI hook and inserts the indicator immediately LEFT of the battery, on every
 * status-bar surface (collapsed bar, notification-shade header, lock screen).
 *
 * Unified anchor: every surface contains a {@code StatusIconContainer} (id {@code statusIcons}) as
 * the system-icon cluster, with the battery (a Compose view on A16) sitting right after it in the
 * same parent. So we hook StatusIconContainer, and for each instance insert our view into its parent
 * right after it → directly left of the battery. The surface is detected from the ancestor ids; each
 * surface gets its own reusable view instance that self-hides per the user's per-surface setting.
 */
public class StatusBarHook {

    static void install(LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;

        Class<?> sic = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.phone.StatusIconContainer", cl);
        if (sic == null) {
            XposedBridge.log(Constants.LOG_TAG + " StatusIconContainer not found — aborting");
            return;
        }

        XC_MethodHook ensure = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    onStatusIcons((View) param.thisObject);
                } catch (Throwable t) {
                    XposedBridge.log(Constants.LOG_TAG + " onStatusIcons failed: " + t);
                }
            }
        };
        // StatusIconContainer doesn't declare onAttachedToWindow; hook methods it does override.
        try { XposedHelpers.findAndHookMethod(sic, "onFinishInflate", ensure); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(sic, "onMeasure", int.class, int.class, ensure); } catch (Throwable ignored) {}

        XposedBridge.log(Constants.LOG_TAG + " hook installed on StatusIconContainer");
    }

    private static void onStatusIcons(View statusIcons) {
        ViewParent vp = statusIcons.getParent();
        if (!(vp instanceof ViewGroup)) {
            return;
        }
        ViewGroup parent = (ViewGroup) vp;
        if (hasOurChild(parent)) {
            return; // already inserted on this surface — cheap fast path (onMeasure fires often)
        }
        final String surface = detectSurface(statusIcons);
        if (surface == null) {
            return; // unknown / detached instance
        }
        final View si = statusIcons;
        // Defer: don't mutate the view tree during a measure/layout pass.
        statusIcons.post(new Runnable() {
            @Override
            public void run() {
                try {
                    insert(si, surface);
                } catch (Throwable t) {
                    XposedBridge.log(Constants.LOG_TAG + " insert failed: " + t);
                }
            }
        });
    }

    private static void insert(View statusIcons, String surface) {
        ViewParent vp = statusIcons.getParent();
        if (!(vp instanceof ViewGroup)) {
            return;
        }
        ViewGroup parent = (ViewGroup) vp;
        if (hasOurChild(parent)) {
            return;
        }
        int idx = parent.indexOfChild(statusIcons);
        idx = (idx < 0) ? parent.getChildCount() : idx + 1; // right after the icon cluster

        NetSpeedView view = new NetSpeedView(parent.getContext(), surface);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        view.setLayoutParams(lp);
        parent.addView(view, idx);
        XposedBridge.log(Constants.LOG_TAG + " inserted on '" + surface + "' at index " + idx);
    }

    private static boolean hasOurChild(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (NetSpeedView.VIEW_TAG.equals(parent.getChildAt(i).getTag())) {
                return true;
            }
        }
        return false;
    }

    /** Identify which status-bar surface this statusIcons belongs to, by ancestor resource ids. */
    private static String detectSurface(View v) {
        View cur = v;
        for (int depth = 0; depth < 24 && cur != null; depth++) {
            int id = cur.getId();
            if (id != View.NO_ID) {
                String name = null;
                try {
                    name = cur.getResources().getResourceEntryName(id);
                } catch (Throwable ignored) {
                }
                if (name != null) {
                    if (name.contains("keyguard")) {
                        return NetSpeedView.SURFACE_KEYGUARD;
                    }
                    if (name.equals("split_shade_status_bar") || name.contains("shade_header")) {
                        return NetSpeedView.SURFACE_SHADE;
                    }
                    if (name.equals("status_bar")) {
                        return NetSpeedView.SURFACE_COLLAPSED;
                    }
                }
            }
            ViewParent p = cur.getParent();
            cur = (p instanceof View) ? (View) p : null;
        }
        return null;
    }

    private StatusBarHook() {}
}
