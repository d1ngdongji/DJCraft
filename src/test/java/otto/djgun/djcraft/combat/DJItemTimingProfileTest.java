package otto.djgun.djcraft.combat;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DJItemTimingProfileTest {
    @Test
    void parsesCompleteAndPartialProfiles() {
        DJItemTimingProfile complete = parse("""
                {
                  "beat_cooldown": 4,
                  "use_beat_cooldown": 2,
                  "switch_warmup": 1,
                  "attack_energy_cost": 2.5,
                  "use_energy_cost": 3
                }
                """);
        assertEquals(4, complete.beatCooldown());
        assertEquals(2, complete.useBeatCooldown());
        assertEquals(1, complete.switchWarmup());
        assertEquals(2.5, complete.attackEnergyCost());
        assertEquals(3.0, complete.useEnergyCost());
        assertEquals(2.5, complete.resolveAttackEnergyCost());
        assertEquals(3.0, complete.resolveUseEnergyCost());
        assertEquals(2, complete.resolveUseBeatCooldown(4));

        DJItemTimingProfile cooldownOnly = parse("""
                {"beat_cooldown": 0}
                """);
        assertEquals(0, cooldownOnly.beatCooldown());
        assertNull(cooldownOnly.useBeatCooldown());
        assertEquals(0, cooldownOnly.resolveUseBeatCooldown(0));
        assertNull(cooldownOnly.switchWarmup());
        assertEquals(0.0, cooldownOnly.resolveAttackEnergyCost());
        assertEquals(0.0, cooldownOnly.resolveUseEnergyCost());

        DJItemTimingProfile warmupOnly = parse("""
                {"switch_warmup": 0}
                """);
        assertNull(warmupOnly.beatCooldown());
        assertEquals(0, warmupOnly.switchWarmup());

        DJItemTimingProfile energyOnly = parse("""
                {"attack_energy_cost": 0, "use_energy_cost": 1.25}
                """);
        assertEquals(0.0, energyOnly.attackEnergyCost());
        assertEquals(1.25, energyOnly.useEnergyCost());
    }

    @Test
    void rejectsInvalidProfiles() {
        assertInvalid("{}");
        assertInvalid("[]");
        assertInvalid("{\"beat_cooldown\": -1}");
        assertInvalid("{\"use_beat_cooldown\": -1}");
        assertInvalid("{\"switch_warmup\": 0.5}");
        assertInvalid("{\"beat_cooldown\": \"2\"}");
        assertInvalid("{\"use_beat_cooldown\": 0.5}");
        assertInvalid("{\"attack_energy_cost\": -1}");
        assertInvalid("{\"use_energy_cost\": \"2\"}");
        assertInvalid("{\"use_energy_cost\": 1e400}");
        assertInvalid("{\"beat_cooldown\": 2, \"extra\": true}");
    }

    @Test
    void builtInTridentProfilePreservesEnergyDefaults() throws Exception {
        var stream = DJItemTimingProfileTest.class.getResourceAsStream(
                "/data/minecraft/djcraft/item_timing/trident.json");
        assertNotNull(stream);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            DJItemTimingProfile profile = DJItemTimingProfile.parse(JsonParser.parseReader(reader));
            assertEquals(5.0, profile.resolveAttackEnergyCost());
            assertEquals(10.0, profile.resolveUseEnergyCost());
            assertEquals(2, profile.resolveUseBeatCooldown(profile.resolveBeatCooldown(1)));
        }
    }

    @Test
    void builtInMaceProfileDefinesAreaAttackEnergyCost() throws Exception {
        var stream = DJItemTimingProfileTest.class.getResourceAsStream(
                "/data/minecraft/djcraft/item_timing/mace.json");
        assertNotNull(stream);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            DJItemTimingProfile profile = DJItemTimingProfile.parse(JsonParser.parseReader(reader));
            assertEquals(8.0, profile.resolveAttackEnergyCost());
            assertEquals(2, profile.resolveUseBeatCooldown(profile.resolveBeatCooldown(1)));
        }
    }

    @Test
    void builtInRightClickWeaponsDeclareUseCooldowns() throws Exception {
        Map<String, Integer> expected = Map.of(
                "/data/minecraft/djcraft/item_timing/bow.json", 2,
                "/data/minecraft/djcraft/item_timing/crossbow.json", 4,
                "/data/minecraft/djcraft/item_timing/shield.json", 2,
                "/data/minecraft/djcraft/item_timing/trident.json", 2,
                "/data/minecraft/djcraft/item_timing/mace.json", 2,
                "/data/djcraft/djcraft/item_timing/laser_crossbow.json", 2,
                "/data/djcraft/djcraft/item_timing/magic_crossbow.json", 2,
                "/data/djcraft/djcraft/item_timing/assault_crossbow.json", 1,
                "/data/djcraft/djcraft/item_timing/explosive_bow.json", 2);
        for (var entry : expected.entrySet()) {
            var stream = DJItemTimingProfileTest.class.getResourceAsStream(entry.getKey());
            assertNotNull(stream, entry.getKey());
            try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                DJItemTimingProfile profile = DJItemTimingProfile.parse(JsonParser.parseReader(reader));
                assertEquals(entry.getValue(), profile.useBeatCooldown(), entry.getKey());
            }
        }
    }

    private static DJItemTimingProfile parse(String json) {
        return DJItemTimingProfile.parse(JsonParser.parseString(json));
    }

    private static void assertInvalid(String json) {
        assertThrows(IllegalArgumentException.class, () -> parse(json));
    }
}
