package otto.djgun.djcraft.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DJComboTextureColorTest {
    @Test
    void findsModeAcrossVisiblePixelsAndIgnoresAlphaVariance() {
        int redOpaque = 0xFF0000FF;
        int redHalfAlpha = 0x800000FF;
        int blueOpaque = 0xFFFF0000;

        assertEquals(0xFF0000, DJComboTextureColor.dominantVisibleRgb(new int[] {
                redOpaque, blueOpaque, redHalfAlpha
        }));
    }

    @Test
    void ignoresFullyTransparentPixels() {
        assertEquals(0x123456, DJComboTextureColor.dominantVisibleRgb(new int[] {
                0x00000000, 0x00FFFFFF, 0xFF563412
        }));
        assertEquals(-1, DJComboTextureColor.dominantVisibleRgb(new int[] {
                0x00000000, 0x00FFFFFF
        }));
    }
}
