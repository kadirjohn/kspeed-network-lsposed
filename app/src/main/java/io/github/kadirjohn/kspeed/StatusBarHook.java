package io.github.kadirjohn.kspeed;

import android.view.Gravity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
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
                            java.util.List<View> hits = new ArrayList<>();
                            collectStatusIcons(root, 0, hits);
                            for (View hit : hits) {
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
        java.util.List<View> found = new java.util.ArrayList<>();
        collectStatusIcons(root, 0, found);
        for (View statusIcons : found) {
            onStatusIcons(statusIcons);
        }
    }

    /** Collects EVERY StatusIconContainer in the subtree, not just the first — on some ROMs
     *  (measured on a Pixel A17 ROM) the main bar and the lock bar each own one instance,
     *  and inserting only into the first-found leaves the real icon cluster unserved. */
    private static void collectStatusIcons(View v, int depth, java.util.List<View> out) {
        if (depth > 40 || v == null) {
            return;
        }
        if ("com.android.systemui.statusbar.phone.StatusIconContainer"
                .equals(v.getClass().getName())) {
            out.add(v);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            // Skip our own indicator to avoid self-recursion.
            if (NetSpeedView.VIEW_TAG.equals(vg.getTag())) {
                return;
            }
            for (int i = 0; i < vg.getChildCount(); i++) {
                collectStatusIcons(vg.getChildAt(i), depth + 1, out);
            }
        }
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
        if (!(parent instanceof LinearLayout)
                || ((LinearLayout) parent).getOrientation() != LinearLayout.HORIZONTAL) {
            // A sibling index only reserves space in a flowing horizontal container. On an
            // unrecognised ROM layout, leave the stock bar alone instead of drawing over it.
            return;
        }

        // Insert position and container choice, three concerns in one:
        // 1. The user's requested order is "speed - icons - battery". On the reference ROM
        //    the statusIcons parent holds [.., statusIcons, battery, ..] — inserting
        //    BEFORE statusIcons lands the indicator left of the whole right cluster.
        // 2. On ROMs whose statusIcons parent contains ONLY the icon container (measured:
        //    Pixel A17 ROM — parent system_icons has a single statusIcons child; the
        //    battery lives in an outer classic LinearLayout, while this parent itself is
        //    embedded in a Compose interop chain where our classic view measures 0 and
        //    never renders), the indicator must escape upward: the first ancestor
        //    horizontal LinearLayout (status_bar_contents) is a classic container where
        //    the view renders. There the indicator is inserted BEFORE the child that
        //    contains the icon cluster (end_side_container), reproducing the same
        //    "speed - icons - battery" order the classic insert produces on the
        //    reference ROM — NOT appended after the battery.
        // 3. Post-layout the same escape applies when the icon cluster turns out to sit
        //    on the clock side of the bar (left cluster ROMs).
        ViewGroup target = parent;
        int idx = parent.indexOfChild(statusIcons);
        if (noVisibleSiblingAfter(statusIcons, parent)
                || insideComposeInterop(parent) || isLeftSideCluster(statusIcons)) {
            ViewGroup upper = findBarSpanningHorizontal((View) vp);
            if (upper != null && upper != parent && !hasOurChild(upper)) {
                target = upper;
                idx = ancestorIndexOf(upper, statusIcons);
                if (idx < 0) {
                    idx = target.getChildCount();
                }
            }
        }

        NetSpeedView view = new NetSpeedView(target.getContext(), surface);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        view.setLayoutParams(lp);
        target.addView(view, idx);
        XposedBridge.log(Constants.LOG_TAG + " inserted on '" + surface + "' at index " + idx
                + (target == parent ? "" : " (right-side append)"));
        // The cluster side is only knowable after layout; re-evaluate once laid out and
        // relocate to the right-side container when this cluster is on the clock side.
        relocateIfLeftSideCluster(statusIcons, view, surface);
        XposedBridge.log(Constants.LOG_TAG + " insert parent children [" + surface + "]: "
                + childNames(target));
        XposedBridge.log(Constants.LOG_TAG + " insert ancestry [" + surface + "]: "
                + ancestryChain(statusIcons));
        // Beacon at insert time: proves module activation to the setup card immediately,
        // independent of attach/visibility timing on any ROM surface.
        view.sendHello();
    }

    /**
     * Post-layout re-evaluation of the insert side. At hook time the tree may not be laid
     * out (widths 0), so the initial insert keeps the classic before-statusIcons index.
     * Once laid out, if the icon cluster sits on the clock (left) side of the bar — as on
     * the Pixel A17 ROM — the indicator is moved to the nearest bar-spanning horizontal
     * ancestor as the LAST child, placing it right before the battery (right side).
     */
    private static void relocateIfLeftSideCluster(final View statusIcons, final NetSpeedView view,
                                                  final String surface) {
        final ViewTreeObserver vto = statusIcons.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                try {
                    if (!isLeftSideCluster(statusIcons)) {
                        return;
                    }
                    ViewParent vp = statusIcons.getParent();
                    if (!(vp instanceof LinearLayout)) {
                        return;
                    }
                    ViewGroup upper = findBarSpanningHorizontal((View) vp);
                    if (upper == null || hasOurChild(upper)) {
                        return;
                    }
                    // Move: detach from the clock-side cluster and append to the bar's
                    // right-side horizontal container (just before the battery).
                    if (view.getParent() == upper) {
                        return;
                    }
                    if (view.getParent() instanceof ViewGroup) {
                        ((ViewGroup) view.getParent()).removeView(view);
                    }
                    upper.addView(view, upper.getChildCount());
                    XposedBridge.log(Constants.LOG_TAG + " relocated to right side on '"
                            + surface + "'; parent now: " + childNames(upper));
                } catch (Throwable t) {
                    XposedBridge.log(Constants.LOG_TAG + " relocate failed: " + t);
                } finally {
                    // One-shot: the listener removes itself after the first layout pass.
                    ViewTreeObserver current = statusIcons.getViewTreeObserver();
                    if (current.isAlive()) {
                        current.removeOnGlobalLayoutListener(this);
                    }
                }
            }
        });
    }

    /**
     * Index in {@code outer} of the child whose subtree contains {@code descendant} — i.e.
     * where to insert so the inserted view lands immediately BEFORE that whole subtree.
     * Returns -1 when the descendant is not under this container.
     */
    private static int ancestorIndexOf(ViewGroup outer, View descendant) {
        View cur = descendant;
        for (int depth = 0; depth < 8 && cur != null; depth++) {
            ViewParent p = cur.getParent();
            if (p == outer) {
                return outer.indexOfChild(cur);
            }
            if (p instanceof View) {
                cur = (View) p;
            } else {
                return -1;
            }
        }
        return -1;
    }

    /**
     * True when the statusIcons container is the last visible child of its parent — no
     * battery or other cluster member follows it in the same container. On the reference
     * ROM the battery sits right after statusIcons in this parent; on the Pixel A17 ROM
     * the parent holds the single icon container while the battery lives one classic
     * level higher. Used to decide the insert target.
     */
    private static boolean noVisibleSiblingAfter(View statusIcons, ViewGroup parent) {
        int idx = parent.indexOfChild(statusIcons);
        for (int i = idx + 1; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i).getVisibility() == View.VISIBLE) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when the given container is inside a Compose interop chain — i.e. walking up
     * from it reaches a Compose view/handler before any known classic status-bar view.
     * On such ROMs classic views inserted into the interop subtree can measure 0 and
     * never render, so the insert must escape to the first classic ancestor LinearLayout.
     */
    private static boolean insideComposeInterop(ViewGroup container) {
        View cur = container;
        for (int depth = 0; depth < 10 && cur != null; depth++) {
            String cls = cur.getClass().getName();
            if (cls.contains("ComposeView") || cls.contains("AndroidComposeView")
                    || cls.contains("AndroidViewsHandler") || cls.contains("ViewFactoryHolder")) {
                return true;
            }
            if (cls.endsWith("PhoneStatusBarView")) {
                return false; // classic bar root reached below any Compose layer
            }
            ViewParent p = cur.getParent();
            if (p instanceof View) {
                cur = (View) p;
            } else {
                break;
            }
        }
        return false;
    }

    /**
     * True when the statusIcons cluster renders on the LEFT half of the bar (clock side) —
     * on such ROMs inserting before statusIcons puts the indicator next to the clock.
     * Requires a laid-out tree; callers must re-check post-layout.
     */
    private static boolean isLeftSideCluster(View statusIcons) {
        View root = statusIcons.getRootView();
        if (root == null || root.getWidth() <= 0 || statusIcons.getWidth() <= 0) {
            return false;
        }
        float clusterCenter = statusIcons.getX() + statusIcons.getWidth() / 2f;
        return clusterCenter < root.getWidth() / 2f;
    }

    /**
     * Nearest ancestor horizontal LinearLayout of the statusIcons parent that spans (nearly)
     * the whole bar width — appending there puts the indicator before the right-side
     * cluster (battery). Returns null when no wider horizontal container is found.
     */
    private static ViewGroup findBarSpanningHorizontal(View from) {
        // First ancestor horizontal LinearLayout above the statusIcons cluster; width is
        // not required (called pre-layout too — the relocate listener re-checks after).
        View cur = from;
        for (int depth = 0; depth < 8 && cur != null; depth++) {
            ViewParent p = cur.getParent();
            if (!(p instanceof LinearLayout)) {
                return null;
            }
            LinearLayout ll = (LinearLayout) p;
            if (ll.getOrientation() == LinearLayout.HORIZONTAL) {
                return ll;
            }
            cur = ll;
        }
        return null;
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

    /** Debug helper: class#id chain from the given view up to the root (max 14 levels). */
    private static String ancestryChain(View v) {
        StringBuilder sb = new StringBuilder();
        View cur = v;
        for (int depth = 0; depth < 14 && cur != null; depth++) {
            String idName = "";
            try {
                if (cur.getId() != View.NO_ID) {
                    idName = "#" + cur.getResources().getResourceEntryName(cur.getId());
                }
            } catch (Throwable ignored) {
            }
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(cur.getClass().getSimpleName()).append(idName);
            ViewParent p = cur.getParent();
            if (p instanceof View) {
                cur = (View) p;
            } else {
                cur = null;
            }
        }
        return sb.toString();
    }

    /** Debug helper: sibling names of the insert parent. */
    private static String childNames(ViewGroup parent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View c = parent.getChildAt(i);
            String idName = "";
            try {
                if (c.getId() != View.NO_ID) {
                    idName = "#" + c.getResources().getResourceEntryName(c.getId());
                }
            } catch (Throwable ignored) {
            }
            sb.append(i).append(':').append(c.getClass().getSimpleName()).append(idName).append(' ');
        }
        return sb.toString();
    }

    private StatusBarHook() {}
}
