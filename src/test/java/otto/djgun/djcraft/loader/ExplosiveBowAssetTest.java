package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class ExplosiveBowAssetTest {
    @Test
    void suppliedStagesAreFourValidSixteenPixelTextures() throws Exception {
        int previousOpaquePixels = -1;
        for (int stage = 0; stage < 4; stage++) {
            try (var input = getClass().getResourceAsStream(
                    "/assets/djcraft/textures/item/explosive_bow_stage" + stage + ".png")) {
                assertNotNull(input, "stage " + stage);
                var image = ImageIO.read(input);
                assertNotNull(image, "stage " + stage);
                assertEquals(16, image.getWidth());
                assertEquals(16, image.getHeight());
                int opaquePixels = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        if ((image.getRGB(x, y) >>> 24) != 0) {
                            opaquePixels++;
                        }
                    }
                }
                assertTrue(opaquePixels > previousOpaquePixels,
                        "stage " + stage + " must be larger than the previous supplied stage");
                previousOpaquePixels = opaquePixels;
            }
        }
    }

    @Test
    void suppliedSoundsAreCompleteOggVorbisStreams() throws Exception {
        for (String name : new String[] { "explosive_bow_charge.ogg", "explosive_bow_shoot.ogg" }) {
            try (var input = getClass().getResourceAsStream("/assets/djcraft/sounds/weapons/" + name)) {
                assertNotNull(input, name);
                assertTrue(TrackPackAudioValidator.isPlayableOggVorbis(input), name);
            }
        }
    }

    @Test
    void modelUsesQuarterBeatAutomaticStages() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/djcraft/models/item/explosive_bow.json")) {
            assertNotNull(input);
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            var overrides = root.getAsJsonArray("overrides");
            assertEquals(6, overrides.size());
            assertEquals(0.25F, overrides.get(3).getAsJsonObject().getAsJsonObject("predicate")
                    .get("djcraft:auto_charge").getAsFloat());
            assertEquals(0.5F, overrides.get(4).getAsJsonObject().getAsJsonObject("predicate")
                    .get("djcraft:auto_charge").getAsFloat());
            assertEquals(0.75F, overrides.get(5).getAsJsonObject().getAsJsonObject("predicate")
                    .get("djcraft:auto_charge").getAsFloat());
        }
    }
}
