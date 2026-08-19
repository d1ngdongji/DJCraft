package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class TridentAssetTest {
    @Test
    void rendIconIsTransparentEighteenPixelTexture() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/djcraft/textures/mob_effect/rend.png")) {
            assertNotNull(input);
            var image = ImageIO.read(input);
            assertNotNull(image);
            assertEquals(18, image.getWidth());
            assertEquals(18, image.getHeight());
            assertEquals(0, image.getRGB(0, 0) >>> 24);
            assertTrue(image.getRGB(9, 9) >>> 24 > 0);
        }
    }

    @Test
    void suppliedRedirectSoundIsPlayableOggVorbis() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/assets/djcraft/sounds/weapons/trident_redirect.ogg")) {
            assertNotNull(input);
            assertTrue(TrackPackAudioValidator.isPlayableOggVorbis(input));
        }
    }

    @Test
    void soundAndLanguageResourcesExposeRendAndRedirect() throws Exception {
        assertJsonValue("/assets/djcraft/lang/en_us.json", "effect.djcraft.rend", "Rend");
        assertJsonValue("/assets/djcraft/lang/zh_cn.json", "effect.djcraft.rend", "撕裂");
        try (var input = getClass().getResourceAsStream("/assets/djcraft/sounds.json")) {
            assertNotNull(input);
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(root.has("weapon.trident_redirect"));
        }
    }

    @Test
    void mixinConfigurationIncludesTridentFlightHooks() throws Exception {
        try (var input = getClass().getResourceAsStream("/djcraft.mixins.json")) {
            assertNotNull(input);
            var mixins = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("mixins");
            assertTrue(mixins.asList().stream()
                    .anyMatch(value -> value.getAsString().equals("ThrownTridentMixin")));
            assertTrue(mixins.asList().stream()
                    .anyMatch(value -> value.getAsString().equals("EntityThrownTridentAttackMixin")));
        }
    }

    private void assertJsonValue(String path, String key, String expected) throws Exception {
        try (var input = getClass().getResourceAsStream(path)) {
            assertNotNull(input);
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals(expected, root.get(key).getAsString());
        }
    }
}
