package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class AssaultCrossbowAssetTest {
    @Test
    void suppliedTexturesAndChargedModelArePresent() throws Exception {
        for (String suffix : new String[] { "", "_charged" }) {
            try (var input = getClass().getResourceAsStream(
                    "/assets/djcraft/textures/item/assault_crossbow" + suffix + ".png")) {
                assertNotNull(input);
                var image = ImageIO.read(input);
                assertNotNull(image);
                assertEquals(20, image.getWidth());
                assertEquals(20, image.getHeight());
            }
        }

        var model = loadJson("/assets/djcraft/models/item/assault_crossbow.json");
        assertEquals("djcraft:item/assault_crossbow_charged",
                model.getAsJsonArray("overrides").get(0).getAsJsonObject().get("model").getAsString());
    }

    @Test
    void suppliedShotSoundIsPlayableAndRegistered() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/assets/djcraft/sounds/weapons/assault_crossbow_shoot.ogg")) {
            assertNotNull(input);
            assertTrue(TrackPackAudioValidator.isPlayableOggVorbis(input));
        }
        assertTrue(loadJson("/assets/djcraft/sounds.json").has("weapon.assault_crossbow_shoot"));
        assertNotNull(getClass().getResourceAsStream(
                "/assets/djcraft/djcraft/weapon_sounds/assault_crossbow.json"));
    }

    private static com.google.gson.JsonObject loadJson(String path) throws Exception {
        try (var input = AssaultCrossbowAssetTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
