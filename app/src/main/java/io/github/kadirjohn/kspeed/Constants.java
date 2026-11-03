package io.github.kadirjohn.kspeed;

/** Shared keys/defaults between the settings UI and the SystemUI hook. */
public final class Constants {
    private Constants() {}

    public static final String PKG = "io.github.kadirjohn.kspeed";
    public static final String SYSTEMUI_PKG = "com.android.systemui";

    /** SharedPreferences file name (must match on both write and XSharedPreferences read sides). */
    public static final String PREFS = "netspeed_prefs";

    public static final String LOG_TAG = "[KSpeed]";

    // Update check (GitHub Releases)
    public static final String REPO = "kadirjohn/kspeed-network-lsposed";
    public static final String RELEASES_API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    public static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases/latest";
    public static final String REPO_PAGE = "https://github.com/" + REPO;
    public static final String APP_PREFS = "netspeed_app"; // app-only state (last update check)
    public static final String KEY_LAST_UPDATE_CHECK = "last_update_check";

    // Preference keys
    public static final String KEY_ENABLED        = "enabled";
    public static final String KEY_MODE           = "mode";            // both | combined | down
    public static final String KEY_INTERVAL       = "interval_ms";     // String list value
    public static final String KEY_UNIT           = "unit";            // auto_bytes|auto_bits|kbs|mbs|kbits|mbits
    public static final String KEY_SHOW_UNIT      = "show_unit";
    public static final String KEY_HIDE_IDLE      = "hide_idle";
    public static final String KEY_IDLE_THRESHOLD = "idle_threshold_kb"; // int, KB/s
    public static final String KEY_TEXT_SIZE      = "text_size_sp";    // int, sp
    public static final String KEY_BOLD_TEXT      = "bold_text";       // bool — independent of size
    public static final String KEY_COLOR_MODE     = "color_mode";      // auto | custom
    public static final String KEY_CUSTOM_COLOR   = "custom_color";    // #RRGGBB — download
    public static final String KEY_CUSTOM_COLOR_UL = "custom_color_ul"; // #RRGGBB — upload
    public static final String KEY_DIM_ZERO       = "dim_zero";        // dim the arrow when idle
    public static final String KEY_UP_ON_TOP      = "up_on_top";       // swap UL/DL order
    public static final String KEY_SOURCE         = "source";          // total | wifi | cellular
    public static final String KEY_SHOW_SHADE     = "show_on_shade";   // show in the notification shade
    public static final String KEY_SHOW_LOCKSCREEN = "show_on_lockscreen";
    public static final String KEY_AMOLED_BLACK   = "amoled_black";

    // Defaults (kept in sync with res/xml/prefs.xml)
    public static final boolean DEF_ENABLED        = true;
    public static final String  DEF_MODE           = "both";
    public static final String  DEF_INTERVAL       = "1000";
    public static final String  DEF_UNIT           = "auto_bytes";
    public static final boolean DEF_SHOW_UNIT      = true;
    public static final boolean DEF_HIDE_IDLE      = false;
    public static final int     DEF_IDLE_THRESHOLD = 1;
    public static final int     DEF_TEXT_SIZE      = 8;
    public static final boolean DEF_BOLD_TEXT      = false;
    public static final String  DEF_COLOR_MODE     = "auto";
    public static final String  DEF_CUSTOM_COLOR   = "#FFFFFF";
    public static final String  DEF_CUSTOM_COLOR_UL = "#FFFFFF";
    public static final boolean DEF_DIM_ZERO       = true;
    public static final boolean DEF_UP_ON_TOP      = false;
    public static final String  DEF_SOURCE         = "total";
    public static final boolean DEF_SHOW_SHADE     = false;
    public static final boolean DEF_SHOW_LOCKSCREEN = false;
    public static final boolean DEF_AMOLED_BLACK   = false;
}
