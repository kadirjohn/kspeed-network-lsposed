package io.github.kadirjohn.kspeed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** LSPosed/Xposed entry point (declared in assets/xposed_init). */
public class XposedEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!Constants.SYSTEMUI_PKG.equals(lpparam.packageName)) {
            return;
        }
        try {
            StatusBarHook.install(lpparam);
        } catch (Throwable t) {
            XposedBridge.log(Constants.LOG_TAG + " install failed: " + t);
        }
    }
}
