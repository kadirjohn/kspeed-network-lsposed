package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.view.View;
import java.util.ArrayList;
import java.util.WeakHashMap;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Opt-in AOSP artwork for the ordinary main-bar VPN icon. Retains stock tint and semantics. */
final class VpnIconStyle {
    static final String KEY = "compact_aosp_vpn_icon";
    private static boolean enabled;
    private static final WeakHashMap<View, Boolean> views = new WeakHashMap<>();
    private static Context moduleContext;

    static void updateConfig(Bundle config) {
        boolean value = config.getBoolean(KEY, false);
        if (enabled == value) return;
        enabled = value;
        for (View view : new ArrayList<>(views.keySet())) {
            if (view == null) continue;
            try {
                XposedHelpers.callMethod(view, "updateDrawable", false);
                XposedHelpers.callMethod(view, "maybeUpdateIconScaleDimens");
                view.invalidate();
            } catch (Throwable ignored) { }
        }
    }

    static void install(LoadPackageParam param) {
        try {
            Class<?> cls = XposedHelpers.findClass("com.android.systemui.statusbar.StatusBarIconView", param.classLoader);
            // Both methods and mIconScale were read from this device's SystemUI APK.
            XposedBridge.hookAllMethods(cls, "getIcon", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        View view = (View) p.thisObject;
                        if (!"vpn".equals(XposedHelpers.callMethod(view, "getSlot"))) return;
                        views.put(view, false);
                        if (!enabled || !NetSpeedView.SURFACE_COLLAPSED.equals(StatusBarHook.detectSurface(view))) return;
                        Icon icon = (Icon) XposedHelpers.getObjectField(p.args[0], "icon");
                        if (icon == null || icon.getType() != Icon.TYPE_RESOURCE) return;
                        // Preserve branded VPN artwork and no-internet warning variants.
                        if (!"stat_sys_vpn_ic".equals(view.getResources().getResourceEntryName(icon.getResId()))) return;
                        if (moduleContext == null) moduleContext = view.getContext().createPackageContext(Constants.PKG, Context.CONTEXT_IGNORE_SECURITY);
                        p.setResult(moduleContext.getDrawable(R.drawable.ic_vpn_aosp));
                        views.put(view, true);
                    } catch (Throwable ignored) { }
                }
            });
            XposedBridge.hookAllMethods(cls, "maybeUpdateIconScaleDimens", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        View view = (View) p.thisObject;
                        if (!enabled || !Boolean.TRUE.equals(views.get(view))) return;
                        float scale = XposedHelpers.getFloatField(view, "mIconScale");
                        XposedHelpers.setFloatField(view, "mIconScale", scale * 0.75f);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(Constants.LOG_TAG + " compact VPN icon unavailable: " + t);
        }
    }
}
