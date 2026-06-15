package com.nogrep.netspeed;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * Exposes the module's settings to the SystemUI hook over binder (a ContentProvider), because on
 * Android 16 the SystemUI process cannot read our private prefs file directly (DAC + SELinux app
 * isolation block cross-app file reads, and XSharedPreferences/MODE_WORLD_READABLE no longer works
 * on this framework). The hook calls {@link #METHOD_GET} to pull all values as a Bundle, and
 * observes {@link #URI} to be notified when the user changes a setting.
 */
public class ConfigProvider extends ContentProvider {

    public static final String AUTHORITY = "com.nogrep.netspeed.config";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/prefs");
    public static final String METHOD_GET = "get";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (!METHOD_GET.equals(method)) {
            return null;
        }
        Bundle out = new Bundle();
        Context ctx = getContext();
        if (ctx == null) {
            return out;
        }
        SharedPreferences sp = ctx.getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean) {
                out.putBoolean(e.getKey(), (Boolean) v);
            } else if (v instanceof Integer) {
                out.putInt(e.getKey(), (Integer) v);
            } else if (v instanceof Long) {
                out.putLong(e.getKey(), (Long) v);
            } else if (v instanceof Float) {
                out.putFloat(e.getKey(), (Float) v);
            } else if (v instanceof String) {
                out.putString(e.getKey(), (String) v);
            }
        }
        return out;
    }

    // No row-based access is used.
    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}
