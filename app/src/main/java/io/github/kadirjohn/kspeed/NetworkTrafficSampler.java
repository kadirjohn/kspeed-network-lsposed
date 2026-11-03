package io.github.kadirjohn.kspeed;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.util.*;

/** Counts the underlying internet links once, never adding VPN/CLAT tunnel counters. */
final class NetworkTrafficSampler {
    static final int TOTAL = 0, WIFI = 1, CELLULAR = 2, OTHER = 4;
    static final class Link {
        final String name;
        final int kind;
        final boolean vpn;
        Link(String name, int kind, boolean vpn) {
            this.name = name; this.kind = kind; this.vpn = vpn;
        }
    }
    interface Backend {
        List<Link> links() throws Exception;
        Map<String, long[]> freshCounters(); // null if proc counters are inaccessible
        long[] apiCounters(String iface);
    }
    private final Backend backend;
    private String lastKey;
    private long lastRx, lastTx, lastTime;
    private double[] rate = {0, 0};

    NetworkTrafficSampler(Backend backend) { this.backend = backend; }
    NetworkTrafficSampler(Context context) { this(new AndroidBackend(context)); }

    double[] sample(int kind, long now) {
        try {
            TreeSet<String> names = new TreeSet<>();
            for (Link link : backend.links()) {
                if (!link.vpn && link.name != null && (kind == TOTAL || link.kind == kind)) {
                    names.add(link.name);
                }
            }
            Map<String, long[]> fresh = backend.freshCounters();
            boolean cachedApi = fresh == null;
            long rx = 0, tx = 0;
            TreeSet<String> counted = new TreeSet<>();
            for (String name : names) {
                long[] bytes = cachedApi ? backend.apiCounters(name) : fresh.get(name);
                if (bytes == null || bytes[0] < 0 || bytes[1] < 0) continue;
                counted.add(name);
                rx += bytes[0]; tx += bytes[1];
            }
            String key = kind + ":" + (cachedApi ? "api:" : "proc:") + counted;
            long dt = now - lastTime;
            if (key.equals(lastKey) && cachedApi && dt > 0 && dt < 1000) {
                // Modern TrafficStats caches results. A 500ms UI tick must not turn the same
                // cached sample into a false zero. Keep the previous rate until a fresh window.
                return rate.clone();
            }
            if (!key.equals(lastKey) || dt <= 0 || rx < lastRx || tx < lastTx) {
                rate = new double[]{0, 0};
            } else {
                rate = new double[]{(rx - lastRx) * 1000.0 / dt, (tx - lastTx) * 1000.0 / dt};
            }
            lastKey = key; lastRx = rx; lastTx = tx; lastTime = now;
            return rate.clone();
        } catch (Exception ignored) {
            // Never fall back to global totals: that would reintroduce VPN double accounting.
            lastKey = null;
            rate = new double[]{0, 0};
            return rate.clone();
        }
    }

    static Map<String, long[]> parseCounters(Reader reader) throws Exception {
        Map<String, long[]> result = new HashMap<>();
        BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        String line;
        while ((line = br.readLine()) != null) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String[] fields = line.substring(colon + 1).trim().split("\\s+");
            if (fields.length < 9) continue;
            try {
                result.put(line.substring(0, colon).trim(), new long[]{Long.parseLong(fields[0]), Long.parseLong(fields[8])});
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private static final class AndroidBackend implements Backend {
        private final ConnectivityManager connectivity;
        AndroidBackend(Context context) { connectivity = context.getSystemService(ConnectivityManager.class); }
        @Override public List<Link> links() {
            List<Link> result = new ArrayList<>();
            if (connectivity == null) return result;
            for (Network network : connectivity.getAllNetworks()) {
                NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
                LinkProperties props = connectivity.getLinkProperties(network);
                if (caps == null || props == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                // VPN networks can advertise WIFI/CELLULAR as well: check VPN first.
                boolean vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                int kind = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? WIFI
                        : caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? CELLULAR : OTHER;
                result.add(new Link(props.getInterfaceName(), kind, vpn));
            }
            return result;
        }
        @Override public Map<String, long[]> freshCounters() {
            try (FileReader reader = new FileReader("/proc/net/dev")) {
                return parseCounters(reader);
            } catch (Exception ignored) { return null; }
        }
        @Override public long[] apiCounters(String iface) {
            return new long[]{TrafficStats.getRxBytes(iface), TrafficStats.getTxBytes(iface)};
        }
    }
}
