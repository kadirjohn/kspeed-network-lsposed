package io.github.kadirjohn.kspeed;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SpeedFormatterTest {

    @Test
    public void testAutoBytes() {
        assertEquals("0 B/s", SpeedFormatter.format(0, "auto_bytes", true));
        assertEquals("500 B/s", SpeedFormatter.format(500, "auto_bytes", false));
        assertEquals("1.5 KB/s", SpeedFormatter.format(1536, "auto_bytes", true));
        assertEquals("20 KB/s", SpeedFormatter.format(20 * 1024, "auto_bytes", false));
        assertEquals("1.2 MB/s", SpeedFormatter.format(1.2 * 1024 * 1024, "auto_bytes", true));
        assertEquals("15 MB/s", SpeedFormatter.format(15 * 1024 * 1024, "auto_bytes", false));
    }

    @Test
    public void testAutoBits() {
        assertEquals("0 bit/s", SpeedFormatter.format(0, "auto_bits", true));
        assertEquals("8.0 Kbit/s", SpeedFormatter.format(1000, "auto_bits", true));
        assertEquals("80 Mbit/s", SpeedFormatter.format(10000000, "auto_bits", false));
    }

    @Test
    public void testFixedUnits() {
        // KB/s
        assertEquals("340 KB/s", SpeedFormatter.format(340 * 1024, "kbs", true));
        assertEquals("340", SpeedFormatter.format(340 * 1024, "kbs", false));
        // MB/s
        assertEquals("1.2 MB/s", SpeedFormatter.format(1.2 * 1048576, "mbs", true));
        assertEquals("1.2", SpeedFormatter.format(1.2 * 1048576, "mbs", false));
    }
}
