package otto.djgun.djcraft.client.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;

class DJAnimationSoundSemanticsTest {
    @Test
    void everyAnimationSemanticHasExactlyOneSoundSemantic() {
        EnumSet<DJWeaponSoundSemantic> mapped = EnumSet.noneOf(DJWeaponSoundSemantic.class);
        for (DJAnimationEvent.Kind kind : DJAnimationEvent.Kind.values()) {
            DJWeaponSoundSemantic semantic = DJAnimationSoundSemantics.from(kind);
            assertNotEquals(DJWeaponSoundSemantic.TARGET_HIT, semantic);
            mapped.add(semantic);
        }
        assertEquals(DJAnimationEvent.Kind.values().length, mapped.size());
        assertEquals(DJWeaponSoundSemantic.values().length - 3, mapped.size());
        assertFalse(mapped.contains(DJWeaponSoundSemantic.DASH));
        assertFalse(mapped.contains(DJWeaponSoundSemantic.DOUBLE_JUMP));
    }
}
