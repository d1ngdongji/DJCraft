package otto.djgun.djcraft.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DJRainbowColorTest {
    @Test
    void sharedCyclePreservesComboRainbowPhases() {
        DJRainbowColor.Rgb red = DJRainbowColor.sample(0L, 0L);
        DJRainbowColor.Rgb blue = DJRainbowColor.sample(900L, 0L);

        assertEquals(1.0F, red.red(), 0.001F);
        assertEquals(0.10F, red.green(), 0.001F);
        assertEquals(0.10F, red.blue(), 0.001F);
        assertEquals(0.10F, blue.red(), 0.001F);
        assertEquals(0.19F, blue.green(), 0.001F);
        assertEquals(1.0F, blue.blue(), 0.001F);
    }

    @Test
    void argbClampsAlphaAndPacksColor() {
        DJRainbowColor.Rgb color = new DJRainbowColor.Rgb(1.0F, 0.5F, 0.0F);

        assertEquals(0xFFFF8000, DJRainbowColor.argb(color, 2.0F));
        assertEquals(0x00FF8000, DJRainbowColor.argb(color, -1.0F));
    }
}
