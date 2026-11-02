package io.github.kadirjohn.kspeed;

import java.util.Locale;

/**
 * Common formatting logic for network speed values.
 * Shared between the real NetSpeedView indicator in SystemUI and the settings preview card.
 */
public final class SpeedFormatter {
    private SpeedFormatter() {}

    /**
     * Formats bytesPerSec according to unitMode and showUnit.
     *
     * @param bytesPerSec raw rate in bytes per second
     * @param unitMode    one of auto_bytes, auto_bits, kbs, mbs, kbits, mbits
     * @param showUnit    whether to append unit label for fixed units; ignored for auto_* (always true)
     * @return formatted string, e.g. "1.2 MB/s", "340 KB/s", "850"
     */
    public static String format(double bytesPerSec, String unitMode, boolean showUnit) {
        if (unitMode == null) {
            unitMode = Constants.DEF_UNIT;
        }
        if (unitMode.startsWith("auto")) {
            boolean bits = "auto_bits".equals(unitMode);
            double value = bits ? bytesPerSec * 8.0 : bytesPerSec;
            double base = bits ? 1000.0 : 1024.0;
            String[] units = bits
                    ? new String[]{"bit/s", "Kbit/s", "Mbit/s", "Gbit/s"}
                    : new String[]{"B/s", "KB/s", "MB/s", "GB/s"};
            int i = 0;
            while (value >= base && i < units.length - 1) {
                value /= base;
                i++;
            }
            String num = (i == 0 || value >= 10)
                    ? String.format(Locale.US, "%.0f", value)
                    : String.format(Locale.US, "%.1f", value);
            return num + " " + units[i];
        }

        double value;
        String label;
        switch (unitMode) {
            case "mbs":   value = bytesPerSec / 1048576.0;       label = "MB/s";   break;
            case "kbits": value = bytesPerSec * 8.0 / 1000.0;    label = "Kbit/s"; break;
            case "mbits": value = bytesPerSec * 8.0 / 1000000.0; label = "Mbit/s"; break;
            case "kbs":
            default:      value = bytesPerSec / 1024.0;          label = "KB/s";   break;
        }
        String num = value >= 10
                ? String.format(Locale.US, "%.0f", value)
                : String.format(Locale.US, "%.1f", value);
        return showUnit ? num + " " + label : num;
    }
}
