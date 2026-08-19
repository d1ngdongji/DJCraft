package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class DJRayWeaponProfileTest {
    @Test
    void rayOverchargeAddsTwoDamagePerLevel() {
        assertEquals(0.0, DJRayWeaponCombatService.rayEnchantmentDamage(0));
        assertEquals(2.0, DJRayWeaponCombatService.rayEnchantmentDamage(1));
        assertEquals(8.0, DJRayWeaponCombatService.rayEnchantmentDamage(4));
    }

    @Test
    void parsesCompleteProfile() {
        DJRayWeaponProfile profile = DJRayWeaponProfile.parse(JsonParser.parseString("""
                {
                  "range": 64,
                  "base_damage": 10.0,
                  "pierce_entities": true,
                  "effect": "djcraft:laser_crossbow"
                }
                """));
        assertEquals(64.0, profile.range());
        assertEquals(10.0, profile.baseDamage());
        assertTrue(profile.pierceEntities());
        assertEquals(ResourceLocation.parse("djcraft:laser_crossbow"), profile.effect());
        assertEquals(7.0, profile.horizontalAimAssistPercent());
        assertEquals(7.0, profile.verticalAimAssistPercent());
    }

    @Test
    void rejectsMalformedProfiles() {
        assertInvalid("{}");
        assertInvalid("[]");
        assertInvalid("{\"range\":0,\"base_damage\":10,\"pierce_entities\":true,\"effect\":\"djcraft:x\"}");
        assertInvalid("{\"range\":64,\"base_damage\":-1,\"pierce_entities\":true,\"effect\":\"djcraft:x\"}");
        assertInvalid("{\"range\":64,\"base_damage\":10,\"pierce_entities\":1,\"effect\":\"djcraft:x\"}");
        assertInvalid("{\"range\":64,\"base_damage\":10,\"pierce_entities\":true,\"effect\":\"bad id\"}");
        assertInvalid("{\"range\":64,\"base_damage\":10,\"pierce_entities\":true,\"effect\":\"djcraft:x\",\"extra\":1}");
        assertInvalid("{\"range\":64,\"base_damage\":10,\"pierce_entities\":true,\"effect\":\"djcraft:x\",\"horizontal_aim_assist_percent\":-1}");
        assertInvalid("{\"range\":64,\"base_damage\":10,\"pierce_entities\":true,\"effect\":\"djcraft:x\",\"vertical_aim_assist_percent\":101}");
    }

    @Test
    void parsesAimAssistOverrides() {
        DJRayWeaponProfile profile = DJRayWeaponProfile.parse(JsonParser.parseString("""
                {
                  "range": 32,
                  "base_damage": 4,
                  "pierce_entities": false,
                  "effect": "djcraft:test",
                  "horizontal_aim_assist_percent": 3.5,
                  "vertical_aim_assist_percent": 9.0
                }
                """));
        assertEquals(3.5, profile.horizontalAimAssistPercent());
        assertEquals(9.0, profile.verticalAimAssistPercent());
    }

    @Test
    void builtInLaserCrossbowDefinesRequestedCombatValues() throws Exception {
        var stream = DJRayWeaponProfileTest.class.getResourceAsStream(
                "/data/djcraft/djcraft/ray_weapons/laser_crossbow.json");
        assertNotNull(stream);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            DJRayWeaponProfile profile = DJRayWeaponProfile.parse(JsonParser.parseReader(reader));
            assertEquals(96.0, profile.range());
            assertEquals(12.0, profile.baseDamage());
            assertTrue(profile.pierceEntities());
            assertEquals(7.0, profile.horizontalAimAssistPercent());
            assertEquals(7.0, profile.verticalAimAssistPercent());
        }
    }

    @Test
    void builtInLaserCrossbowDefinesRequestedTimingValues() throws Exception {
        var stream = DJRayWeaponProfileTest.class.getResourceAsStream(
                "/data/djcraft/djcraft/item_timing/laser_crossbow.json");
        assertNotNull(stream);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            DJItemTimingProfile profile = DJItemTimingProfile.parse(JsonParser.parseReader(reader));
            assertEquals(2, profile.beatCooldown());
            assertEquals(1, profile.switchWarmup());
            assertEquals(3.0, profile.resolveUseEnergyCost());
        }
    }

    @Test
    void builtInMagicCrossbowDefinesRequestedCombatAndTimingValues() throws Exception {
        DJRayWeaponProfile rayProfile = loadRayProfile("magic_crossbow.json");
        assertEquals(96.0, rayProfile.range());
        assertEquals(16.0, rayProfile.baseDamage());
        assertFalse(rayProfile.pierceEntities());
        assertEquals(ResourceLocation.parse("djcraft:magic_crossbow"), rayProfile.effect());

        DJItemTimingProfile timingProfile = loadTimingProfile("magic_crossbow.json");
        assertEquals(2, timingProfile.beatCooldown());
        assertEquals(1, timingProfile.switchWarmup());
        assertEquals(3.0, timingProfile.resolveUseEnergyCost());
    }

    @Test
    void builtInExplosiveBowDefinesAutomaticChargeAndExplosion() throws Exception {
        DJRayWeaponProfile rayProfile = loadRayProfile("explosive_bow.json");
        assertEquals(96.0, rayProfile.range());
        assertEquals(0.0, rayProfile.baseDamage());
        assertFalse(rayProfile.pierceEntities());
        assertEquals(1, rayProfile.autoChargeBeats());
        assertNotNull(rayProfile.explosion());
        assertEquals(5.0, rayProfile.explosion().radius());
        assertEquals(18.0, rayProfile.explosion().damage());
        assertEquals(8.0, rayProfile.explosion().airborneRadius());
        assertEquals(36.0, rayProfile.explosion().airborneDamage());
        assertTrue(rayProfile.explosion().explodeAtMaxRange());

        DJItemTimingProfile timingProfile = loadTimingProfile("explosive_bow.json");
        assertEquals(2, timingProfile.beatCooldown());
        assertEquals(1, timingProfile.switchWarmup());
        assertEquals(6.0, timingProfile.resolveUseEnergyCost());
    }

    private static DJRayWeaponProfile loadRayProfile(String fileName) throws Exception {
        var stream = DJRayWeaponProfileTest.class.getResourceAsStream(
                "/data/djcraft/djcraft/ray_weapons/" + fileName);
        assertNotNull(stream);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return DJRayWeaponProfile.parse(JsonParser.parseReader(reader));
        }
    }

    private static DJItemTimingProfile loadTimingProfile(String fileName) throws Exception {
        var stream = DJRayWeaponProfileTest.class.getResourceAsStream(
                "/data/djcraft/djcraft/item_timing/" + fileName);
        assertNotNull(stream);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return DJItemTimingProfile.parse(JsonParser.parseReader(reader));
        }
    }

    private static void assertInvalid(String json) {
        assertThrows(IllegalArgumentException.class,
                () -> DJRayWeaponProfile.parse(JsonParser.parseString(json)));
    }
}
