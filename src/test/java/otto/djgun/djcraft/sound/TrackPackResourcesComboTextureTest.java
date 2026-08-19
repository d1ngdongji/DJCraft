package otto.djgun.djcraft.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackPackResourcesComboTextureTest {
    @Test
    void parsesLegacyAndThresholdTexturePaths() {
        TrackPackResources.ComboTextureFile legacy = TrackPackResources
                .parseComboTextureFile("example", "combo/7.png")
                .orElseThrow();
        TrackPackResources.ComboTextureFile threshold = TrackPackResources
                .parseComboTextureFile("example", "combo/2147483647/9.png")
                .orElseThrow();

        assertEquals(1, legacy.threshold());
        assertEquals(7, legacy.digit());
        assertEquals("combo/7.png", legacy.fileName());
        assertEquals(2147483647, threshold.threshold());
        assertEquals(9, threshold.digit());
        assertTrue(threshold.location().getPath().endsWith("/2147483647/9.png"));
    }

    @Test
    void rejectsReservedMalformedAndOverflowThresholds() {
        assertTrue(TrackPackResources.parseComboTextureFile("example", "combo/1/0.png").isEmpty());
        assertTrue(TrackPackResources.parseComboTextureFile("example", "combo/01/0.png").isEmpty());
        assertTrue(TrackPackResources.parseComboTextureFile("example", "combo/0/0.png").isEmpty());
        assertTrue(TrackPackResources.parseComboTextureFile("example", "combo/nope/0.png").isEmpty());
        assertTrue(TrackPackResources.parseComboTextureFile("example", "combo/2147483648/0.png").isEmpty());
        assertTrue(TrackPackResources.parseComboTextureFile("example", "combo/50/10.png").isEmpty());
        assertTrue(TrackPackResources.parseComboTextureFile("example", "combo/50/nested/0.png").isEmpty());
    }
}
