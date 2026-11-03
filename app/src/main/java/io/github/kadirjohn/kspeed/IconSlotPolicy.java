package io.github.kadirjohn.kspeed;

import java.util.HashSet;
import java.util.Set;

/** Reversible ownership of per-container exclusions; never removes pre-existing ROM exclusions. */
final class IconSlotPolicy {
    private final Set<String> owned = new HashSet<>();

    boolean hasOwned() { return !owned.isEmpty(); }

    boolean sync(Set<String> ignored, Set<String> desired) {
        boolean changed = false;
        for (String slot : new HashSet<>(owned)) {
            if (!desired.contains(slot)) {
                changed |= ignored.remove(slot);
                owned.remove(slot);
            }
        }
        for (String slot : desired) {
            if (ignored.add(slot)) {
                owned.add(slot);
                changed = true;
            }
        }
        return changed;
    }
}
