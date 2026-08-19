package otto.djgun.djcraft.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackPackResourcesBeatTextureTest {
    @Test
    void mapsSafePngAndGifPathsToStableResources() {
        var png = TrackPackResources.beatTextureLocation("example", "beats/kicks/normal.png");
        var gif = TrackPackResources.beatTextureLocation("example", "beats/accent.gif");

        assertTrue(png.isPresent());
        assertTrue(gif.isPresent());
        assertTrue(png.get().getPath().startsWith("textures/gui/beats/trackpacks/"));
        assertTrue(png.get().getPath().endsWith("/kicks/normal.png"));
        assertTrue(gif.get().getPath().endsWith("/accent.gif"));
        assertEquals(png, TrackPackResources.beatTextureLocation("example", "beats/kicks/normal.png"));
    }

    @Test
    void rejectsUnsafeOrNonResourcePaths() {
        assertTrue(TrackPackResources.beatTextureLocation("example", "../outside.png").isEmpty());
        assertTrue(TrackPackResources.beatTextureLocation("example", "beats/../outside.png").isEmpty());
        assertTrue(TrackPackResources.beatTextureLocation("example", "beats/Upper.png").isEmpty());
        assertTrue(TrackPackResources.beatTextureLocation("example", "beats/readme.txt").isEmpty());
    }
}
