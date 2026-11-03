package io.github.kadirjohn.kspeed;

import android.view.Gravity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Installs the SystemUI hook and inserts the indicator before the system-icon cluster, on every
 * status-bar surface (collapsed bar, notification-shade header, lock screen).
 *
 * Unified anchor: every surface contains a {@code StatusIconContainer} (id {@code statusIcons}) as
 * the system-icon cluster, with the battery (a Compose view on A16) sitting right after it in the
 * same parent. So we hook StatusIconContainer, and for each instance insert our view into its parent
 * before it → left of DND, Data Saver and the battery. The surface is detected from the ancestor ids; each
 * surface gets its own reusable view instance that self-hides per the user's per-surface setting.
 */
public class StatusBarHook {
    static final String KEY_HIDE_CELLULAR = "hide_statusbar_cellular";
    static final String KEY_COMPACT_HIDDEN = "compact_hidden_status_icons";
    private static boolean hideCellular;
    private static boolean compactHidden;
    private static boolean warned;
    private static final WeakHashMap<ViewGroup, IconSlotPolicy> policies = new WeakHashMap<>();

    static void updateIconConfig(Bundle config) {
        VpnIconStyle.updateConfig(config);
        boolean hide = config.getBoolean(KEY_HIDE_CELLULAR, false);
        boolean compact = config.getBoolean(KEY_COMPACT_HIDDEN, false);
        if (hide == hideCellular && compact == compactHidden) return;
        hideCellular = hide;
        compactHidden = compact;
        for (ViewGroup container : new ArrayList<>(policies.keySet())) {
            if (container != null) container.requestLayout();
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyIconPolicy(ViewGroup container) {
        // Measured on Neoteric A17: onMeasure AND onLayout consult mIgnoredSlots. It is local
        // to this container, unlike icon_blacklist. Never apply this policy to shade/keyguard.
        if (!NetSpeedView.SURFACE_COLLAPSED.equals(detectSurface(container))) return;
        IconSlotPolicy policy = policies.get(container);
        if (policy == null) {
            policy = new IconSlotPolicy();
            policies.put(container, policy);
        }
        if (!hideCellular && !compactHidden && !policy.hasOwned()) return;
        try {
            Set<String> ignored = (Set<String>) XposedHelpers.getObjectField(container, "mIgnoredSlots");
            Set<String> desired = new HashSet<>();
            if (hideCellular) {
                desired.add("mobile");
                desired.add("stacked_mobile");
            }
            if (compactHidden) {
                for (int i = 0; i < container.getChildCount(); i++) {
                    View child = container.getChildAt(i);
                    ViewGroup.LayoutParams lp = child.getLayoutParams();
                    if (child.getVisibility() == View.GONE && lp != null && lp.width == 0 && lp.height == 0) {
                        Object slot = XposedHelpers.callMethod(child, "getSlot");
                        if (slot instanceof String) desired.add((String) slot);
                    }
                }
            }
            policy.sync(ignored, desired);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                XposedBridge.log(Constants.LOG_TAG + " icon policy unavailable: " + t);
            }
        }
    }

    static void install(LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;
        VpnIconStyle.install(lpparam);

        Class<?> sic = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.phone.StatusIconContainer", cl);
        if (sic == null) {
            XposedBridge.log(Constants.LOG_TAG + " StatusIconContainer not found — aborting");
            return;
        }

        XC_MethodHook ensure = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    applyIconPolicy((ViewGroup) param.thisObject);
                } catch (Throwable t) {
                    if (!warned) {
                        warned = true;
                        XposedBridge.log(Constants.LOG_TAG + " icon policy skipped: " + t);
                    }
                }
            }
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

        // ROM-independent catch-all: on some ROMs (measured on a Pixel A17 ROM) the main
        // bar's StatusIconContainer inflates and lays out before our hooks are installed,
        // and onMeasure never fires again on that static tree, so the method hooks alone
        // never reach it. Hooking View.onAttachedToWindow globally lets us notice EVERY
        // attach on the status-bar window and walk the freshly attached subtree for a
        // statusIcons container — covering trees that finished inflating before install.
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                View attached = (View) param.thisObject;
                                ViewParent vp = attached.getParent();
                                if (!(vp instanceof View)) {
                                    // Only window roots (decor attaches under non-View parents)
                                    // trigger a full sweep; child attaches are too frequent.
                                    sweepForStatusIcons(attached);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            XposedBridge.log(Constants.LOG_TAG + " attach sweep hook installed");
        } catch (Throwable t) {
            XposedBridge.log(Constants.LOG_TAG + " attach sweep unavailable: " + t);
        }

        XposedBridge.log(Constants.LOG_TAG + " hook installed on StatusIconContainer");

        // Trees that attached BEFORE install are only reachable through WindowManagerGlobal.
        // Schedule one deferred sweep (main thread) and retry briefly: at install time the
        // status bar window may not exist yet; a few short retries cover both orders.
        try {
            Class<?> wmg = XposedHelpers.findClass("android.view.WindowManagerGlobal", cl);
            final Object global = XposedHelpers.callStaticMethod(wmg, "getInstance");
            final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            final int[] attempts = {0};
            Runnable sweep = new Runnable() {
                @Override
                public void run() {
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.ArrayList<View> roots = new ArrayList<>(
                                (java.util.Collection<View>) XposedHelpers.getObjectField(global, "mViews"));
                        boolean found = false;
                        for (View root : roots) {
                            if (root == null) {
                                continue;
                            }
                            View hit = findStatusIcons(root, 0);
                            if (hit != null) {
                                onStatusIcons(hit);
                                found = true;
                            }
                        }
                        attempts[0]++;
                        if (!found && attempts[0] < 12) {
                            h.postDelayed(this, 1000L);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(Constants.LOG_TAG + " root sweep failed: " + t);
                    }
                }
            };
            h.postDelayed(sweep, 800L);
            XposedBridge.log(Constants.LOG_TAG + " root sweep scheduled");
        } catch (Throwable t) {
            XposedBridge.log(Constants.LOG_TAG + " root sweep unavailable: " + t);
        }
    }

    /**
     * Depth-limited search of the given subtree for a StatusIconContainer that is not yet
     * served, then routes it through the normal insert path. Called on window-root attaches
     * so trees that inflated before install are still found.
     */
    private static void sweepForStatusIcons(View root) {
        View found = findStatusIcons(root, 0);
        if (found != null) {
            onStatusIcons(found);
        }
    }

    private static View findStatusIcons(View v, int depth) {
        if (depth > 40 || v == null) {
            return null;
        }
        if ("com.android.systemui.statusbar.phone.StatusIconContainer"
                .equals(v.getClass().getName())) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            // Skip our own indicator to avoid self-recursion.
            if (NetSpeedView.VIEW_TAG.equals(vg.getTag())) {
                return null;
            }
            for (int i = 0; i < vg.getChildCount(); i++) {
                View hit = findStatusIcons(vg.getChildAt(i), depth + 1);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
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
        if (idx < 0 || !(parent instanceof LinearLayout)
                || ((LinearLayout) parent).getOrientation() != LinearLayout.HORIZONTAL) {
            // A sibling index only reserves space in a flowing horizontal container. On an
            // unrecognised ROM layout, leave the stock bar alone instead of drawing over it.
            return;
        }

        NetSpeedView view = new NetSpeedView(parent.getContext(), surface);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        view.setLayoutParams(lp);
        parent.addView(view, idx);
        XposedBridge.log(Constants.LOG_TAG + " inserted on '" + surface + "' at index " + idx);
        // Beacon at insert time: proves module activation to the setup card immediately,
        // independent of attach/visibility timing on any ROM surface.
        view.sendHello();
    }

    private static boolean hasOurChild(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (NetSpeedView.VIEW_TAG.equals(parent.getChildAt(i).getTag())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Identify which status-bar surface this statusIcons belongs to, by ancestor view types
     * and resource ids. Order matters: on some ROMs (measured on a Pixel A17 ROM whose
     * collapsed bar reuses the keyguard layout family) an ancestor id contains "keyguard"
     * even on the MAIN bar, which previously misclassified the collapsed bar as the lock
     * screen and hid it via show_on_lockscreen=false. The root ancestor's TYPE
     * disambiguates: PhoneStatusBarView roots the collapsed bar; a KeyguardStatusBarView
     * with no PhoneStatusBarView above it is the lock screen's own bar.
     */
    static String detectSurface(View v) {
        View cur = v;
        boolean sawKeyguardBar = false;
        for (int depth = 0; depth < 24 && cur != null; depth++) {
            String cls = cur.getClass().getName();
            if (cls.endsWith("PhoneStatusBarView")) {
                // Deterministic: this ancestor IS the collapsed status bar.
                return NetSpeedView.SURFACE_COLLAPSED;
            }
            if (cls.endsWith("KeyguardStatusBarView")) {
                sawKeyguardBar = true;
            }
            int id = cur.getId();
            if (id != View.NO_ID) {
                String name = null;
                try {
                    name = cur.getResources().getResourceEntryName(id);
                } catch (Throwable ignored) {
                }
                if (name != null) {
                    if (name.equals("split_shade_status_bar") || name.contains("shade_header")) {
                        return NetSpeedView.SURFACE_SHADE;
                    }
                    if (name.equals("status_bar")) {
                        return NetSpeedView.SURFACE_COLLAPSED;
                    }
                }
            }
            ViewParent p = cur.getParent();
            if (p instanceof View) {
                cur = (View) p;
            } else {
                cur = null;
            }
        }
        // A KeyguardStatusBarView root with no PhoneStatusBarView above it: the lock
        // screen's own bar. A bare "keyguard" substring in some ancestor id is NOT enough
        // on ROMs that reuse the keyguard layout family for the main bar.
        if (sawKeyguardBar) {
            return NetSpeedView.SURFACE_KEYGUARD;
        }
        return null;
    }

    private StatusBarHook() {}
}
