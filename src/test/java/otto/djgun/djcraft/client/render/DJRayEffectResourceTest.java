package otto.djgun.djcraft.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class DJRayEffectResourceTest {
    @Test
    void genericFallbackIsAnOrdinaryRayWithoutShockwave() throws Exception {
        DJRayEffectProfile profile = parse("generic");
        assertEquals(0L, profile.shockwaveLifetimeMs());
        assertEquals(0.0F, profile.shockwaveStartRadius());
        assertEquals(0.0F, profile.endBurstScale());
    }

    @Test
    void shaderDescriptorMatchesRendererUniforms() throws Exception {
        try (var input = DJRayEffectResourceTest.class.getResourceAsStream(
                "/assets/djcraft/shaders/core/ray_effect.json")) {
            assertNotNull(input);
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals("djcraft:ray_effect", root.get("vertex").getAsString());
            assertEquals("djcraft:ray_effect", root.get("fragment").getAsString());
            Set<String> uniforms = new HashSet<>();
            root.getAsJsonArray("uniforms").forEach(element -> {
                var uniform = element.getAsJsonObject();
                String name = uniform.get("name").getAsString();
                uniforms.add(name);
                assertEquals(uniform.get("count").getAsInt(),
                        uniform.getAsJsonArray("values").size(),
                        name + " default value count");
            });
            assertTrue(uniforms.containsAll(Set.of(
                    "ModelViewMat", "ProjMat", "ColorModulator", "EffectMode", "Progress", "Time")));
        }
    }

    @Test
    void builtInEffectUsesRequestedDimensionsAndLifetimes() throws Exception {
        try (var input = DJRayEffectResourceTest.class.getResourceAsStream(
                "/assets/djcraft/djcraft/ray_effects/laser_crossbow.json")) {
            assertNotNull(input);
            var profile = DJRayEffectProfile.parse(JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)));
            assertEquals(0.035F, profile.coreWidth());
            assertEquals(0.11F, profile.haloWidth());
            assertEquals(180L, profile.beamLifetimeMs());
            assertEquals(240L, profile.burstLifetimeMs());
            assertEquals(0.12F, profile.burstStartRadius());
            assertEquals(0.75F, profile.burstEndRadius());
            assertTrue((profile.coreColor() >>> 8 & 0xFF) > (profile.coreColor() >>> 16 & 0xFF));
            assertTrue((profile.haloColor() >>> 8 & 0xFF) > (profile.haloColor() & 0xFF));
            assertTrue(profile.coreColor() >>> 24 < 0xFF);
            assertTrue(profile.haloColor() >>> 24 < profile.coreColor() >>> 24);
            assertEquals(0.0, profile.firstPersonMainMuzzle().x);
            assertEquals(-0.22, profile.firstPersonOffhandMuzzle().x);
        }
    }

    @Test
    void magicEffectKeepsTheBeamSubtleAndTheAuthoritativeContactProminent() throws Exception {
        try (var input = DJRayEffectResourceTest.class.getResourceAsStream(
                "/assets/djcraft/djcraft/ray_effects/magic_crossbow.json")) {
            assertNotNull(input);
            var profile = DJRayEffectProfile.parse(JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)));
            assertTrue(profile.coreWidth() < 0.01F);
            assertTrue(profile.haloWidth() < 0.03F);
            assertTrue(profile.beamLifetimeMs() <= 100L);
            assertTrue(profile.burstEndRadius() >= 1.0F);
            assertEquals(1.0F, profile.contactBurstScale());
            assertTrue(profile.muzzleBurstScale() < profile.contactBurstScale());
            assertTrue(profile.endBurstScale() < profile.contactBurstScale());
        }
    }

    @Test
    void legacyFirstPersonMuzzleStillMirrorsAcrossHands() {
        var profile = DJRayEffectProfile.parse(JsonParser.parseString("""
                {
                  "core_color": "#FFFFFFFF",
                  "halo_color": "#FFFFFFFF",
                  "core_width": 0.01,
                  "halo_width": 0.02,
                  "beam_lifetime_ms": 1,
                  "burst_lifetime_ms": 1,
                  "burst_start_radius": 0.0,
                  "burst_end_radius": 0.0,
                  "pulse_speed": 1.0,
                  "first_person_muzzle": [0.2, -0.1, 0.4],
                  "third_person_muzzle": [0.2, -0.1, 0.4]
                }
                """));

        assertEquals(0.2, profile.firstPersonMainMuzzle().x);
        assertEquals(-0.2, profile.firstPersonOffhandMuzzle().x);
    }

    @Test
    void explosiveBowUsesThreeDimensionalShockwaveProfile() throws Exception {
        try (var input = DJRayEffectResourceTest.class.getResourceAsStream(
                "/assets/djcraft/djcraft/ray_effects/explosive_bow.json")) {
            assertNotNull(input);
            var profile = DJRayEffectProfile.parse(JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)));
            assertEquals(450L, profile.shockwaveLifetimeMs());
            assertEquals(0.25F, profile.shockwaveStartRadius());
            assertEquals(0.0F, profile.contactBurstScale());
            assertEquals(0.0F, profile.endBurstScale());
        }
    }

    private static DJRayEffectProfile parse(String name) throws Exception {
        try (var input = DJRayEffectResourceTest.class.getResourceAsStream(
                "/assets/djcraft/djcraft/ray_effects/" + name + ".json")) {
            assertNotNull(input, name);
            return DJRayEffectProfile.parse(JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)));
        }
    }
}
