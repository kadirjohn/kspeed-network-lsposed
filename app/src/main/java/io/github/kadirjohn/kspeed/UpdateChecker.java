package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Checks the GitHub Releases API for a newer version. App-side only (needs INTERNET). */
public final class UpdateChecker {

    public interface Callback {
        void onResult(boolean updateAvailable, String latestTag, String currentVersion, String url);

        void onError(String message);
    }

    private UpdateChecker() {}

    public static void checkAsync(final Context appCtx, final Callback cb) {
        final Context ctx = appCtx.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(Constants.RELEASES_API).openConnection();
                    conn.setRequestProperty("Accept", "application/vnd.github+json");
                    conn.setRequestProperty("User-Agent", Constants.REPO);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);

                    int code = conn.getResponseCode();
                    if (code != 200) {
                        post(main, cb, "HTTP " + code);
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                    }
                    JSONObject o = new JSONObject(sb.toString());
                    final String tag = o.optString("tag_name", "");
                    final String url = o.optString("html_url", Constants.RELEASES_PAGE);
                    final String current = currentVersion(ctx);
                    final boolean newer = isNewer(stripV(tag), current);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onResult(newer, tag, current, url);
                        }
                    });
                } catch (final Exception e) {
                    post(main, cb, e.getClass().getSimpleName());
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        }, "netspeed-update-check").start();
    }

    private static void post(Handler main, final Callback cb, final String msg) {
        main.post(new Runnable() {
            @Override
            public void run() {
                cb.onError(msg);
            }
        });
    }

    public static String currentVersion(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    static String stripV(String tag) {
        if (tag == null) {
            return "";
        }
        return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
    }

    /** Numeric dotted-version comparison: is {@code latest} strictly newer than {@code current}? */
    static boolean isNewer(String latest, String current) {
        int[] a = parse(latest);
        int[] b = parse(current);
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int[] parse(String v) {
        if (v == null || v.isEmpty()) {
            return new int[]{0};
        }
        String core = v.split("[-+]")[0]; // drop pre-release/build metadata
        String[] parts = core.split("\\.");
        int[] r = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                r[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                r[i] = 0;
            }
        }
        return r;
    }
}
