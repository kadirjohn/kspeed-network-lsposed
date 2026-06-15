# Xposed entry point must keep its name (referenced from assets/xposed_init).
-keep class com.nogrep.netspeed.XposedEntry { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }

# Keep the custom status-bar view (instantiated reflectively inside SystemUI).
-keep class com.nogrep.netspeed.NetSpeedView { *; }

# Do not warn about the (compileOnly) Xposed API.
-dontwarn de.robv.android.xposed.**
