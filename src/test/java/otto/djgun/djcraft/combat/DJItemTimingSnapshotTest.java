package otto.djgun.djcraft.combat;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DJItemTimingSnapshotTest {
    @Test
    void copiesReplacementMapsAndSupportsRemoval() {
        Map<String, DJItemTimingProfile> byId = new HashMap<>();
        Map<Integer, DJItemTimingProfile> byKey = new HashMap<>();
        DJItemTimingProfile profile = new DJItemTimingProfile(4, null);
        byId.put("example:weapon", profile);
        byKey.put(7, profile);

        DJItemTimingSnapshot<String, Integer> loaded = new DJItemTimingSnapshot<>(byId, byKey);
        byId.clear();
        byKey.clear();

        assertTrue(loaded.byId().containsKey("example:weapon"));
        assertEquals(4, loaded.byKey().get(7).resolveBeatCooldown(1));
        assertEquals(4, loaded.byKey().get(7).resolveSwitchWarmup(4));

        DJItemTimingSnapshot<String, Integer> removed = DJItemTimingSnapshot.empty();
        assertFalse(removed.byId().containsKey("example:weapon"));
        assertTrue(removed.byKey().isEmpty());
    }

    @Test
    void resolvesEachOptionalFieldIndependently() {
        DJItemTimingProfile cooldownOnly = new DJItemTimingProfile(3, null);
        assertEquals(3, cooldownOnly.resolveBeatCooldown(1));
        assertEquals(3, cooldownOnly.resolveSwitchWarmup(3));

        DJItemTimingProfile warmupOnly = new DJItemTimingProfile(null, 0);
        assertEquals(2, warmupOnly.resolveBeatCooldown(2));
        assertEquals(0, warmupOnly.resolveSwitchWarmup(2));
    }
}
