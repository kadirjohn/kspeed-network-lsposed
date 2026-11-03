package io.github.kadirjohn.kspeed;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class IconSlotPolicyTest {
    @Test public void disablingPreservesRomExclusionsAndOtherOwners() {
        Set<String> ignored = new HashSet<>(Set.of("wifi", "alarm_clock"));
        IconSlotPolicy policy = new IconSlotPolicy();
        assertTrue(policy.sync(ignored, Set.of("wifi", "mobile")));
        ignored.add("vpn");
        assertTrue(policy.sync(ignored, Set.of()));
        assertEquals(Set.of("wifi", "alarm_clock", "vpn"), ignored);
        assertFalse(policy.hasOwned());
    }

    @Test public void repeatedMeasureDoesNotChangeOrAcquireExistingSlots() {
        Set<String> ignored = new HashSet<>();
        IconSlotPolicy policy = new IconSlotPolicy();
        assertFalse(policy.sync(ignored, Set.of()));
        assertTrue(policy.sync(ignored, Set.of("mobile", "stacked_mobile")));
        assertFalse(policy.sync(ignored, Set.of("mobile", "stacked_mobile")));
        assertTrue(policy.sync(ignored, Set.of("mobile")));
        assertEquals(Set.of("mobile"), ignored);
    }

    @Test public void mainBarPolicyDoesNotModifyShadeSet() {
        Set<String> main = new HashSet<>();
        Set<String> shade = new HashSet<>();
        IconSlotPolicy policy = new IconSlotPolicy();
        policy.sync(main, Set.of("mobile"));
        assertTrue(shade.isEmpty());
        policy.sync(main, Set.of());
        assertTrue(main.isEmpty());
        assertTrue(shade.isEmpty());
    }
}
