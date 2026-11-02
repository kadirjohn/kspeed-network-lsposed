# Xposed entry point must keep its name (referenced from assets/xposed_init).
-keep class io.github.kadirjohn.kspeed.XposedEntry { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }

# Keep the custom status-bar view (instantiated reflectively inside SystemUI).
-keep class io.github.kadirjohn.kspeed.NetSpeedView { *; }

# Do not warn about the (compileOnly) Xposed API.
-dontwarn de.robv.android.xposed.**
