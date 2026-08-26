package otto.djgun.djcraft.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class AssaultCrossbowItemTest {
    @Test
    void basePotionEffectsLastHalfAsLongAsVanillaTippedArrowEffects() {
        assertEquals(50, AssaultCrossbowPotionEffects.scaleDuration(800,
                AssaultCrossbowPotionEffects.BASE_POTION_DURATION_DIVISOR));
        assertEquals(50, AssaultCrossbowPotionEffects.scaleDuration(801,
                AssaultCrossbowPotionEffects.BASE_POTION_DURATION_DIVISOR));
    }

    @Test
    void customPotionEffectsUseHalfDurationRoundedDown() {
        assertEquals(50, AssaultCrossbowPotionEffects.scaleDuration(101,
                AssaultCrossbowPotionEffects.CUSTOM_EFFECT_DURATION_DIVISOR));
        assertEquals(50, AssaultCrossbowPotionEffects.scaleDuration(100,
                AssaultCrossbowPotionEffects.CUSTOM_EFFECT_DURATION_DIVISOR));
    }

    @Test
    void timedPotionEffectsAlwaysLastAtLeastOneTick() {
        assertEquals(1, AssaultCrossbowPotionEffects.scaleDuration(1,
                AssaultCrossbowPotionEffects.BASE_POTION_DURATION_DIVISOR));
        assertEquals(1, AssaultCrossbowPotionEffects.scaleDuration(0,
                AssaultCrossbowPotionEffects.CUSTOM_EFFECT_DURATION_DIVISOR));
    }

    @Test
    void infinitePotionEffectsRemainInfinite() {
        assertEquals(-1, AssaultCrossbowPotionEffects.scaleDuration(-1,
                AssaultCrossbowPotionEffects.BASE_POTION_DURATION_DIVISOR));
    }

    @Test
    void instantPotionEffectsUseFullStrength() {
        assertEquals(1.0, AssaultCrossbowPotionEffects.INSTANT_EFFECT_STRENGTH);
    }
}
