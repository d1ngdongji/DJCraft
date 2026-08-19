package otto.djgun.djcraft.client.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BuiltInBeatTextureTest {
    @Test
    void suppliedBeatTexturesShipAtTheirOriginalArgbDimensions() throws Exception {
        assertBuiltInTexture("blue_beat.png");
        assertBuiltInTexture("green_beat.png");
        assertBuiltInTexture("white_beat.png");
    }

    private static void assertBuiltInTexture(String name) throws Exception {
        String path = "/assets/djcraft/textures/gui/beats/" + name;
        try (InputStream input = BuiltInBeatTextureTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            var animation = BeatImageDecoder.decode(input, name);
            assertEquals(32, animation.width());
            assertEquals(16, animation.height());
            assertEquals(1, animation.frames().size());
            int[] pixels = animation.frames().getFirst().argbPixels();
            assertTrue(Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) == 0));
            assertTrue(Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) != 0));
        }
    }
}
