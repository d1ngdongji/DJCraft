package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import otto.djgun.djcraft.data.BeatCategory;

class DJMaceThrowRulesTest {
    @Test
    void exposesSpecifiedThrowTuning() {
        assertEquals(16.0F, DJMaceThrowRules.BASE_DAMAGE);
        assertEquals(1.75F, DJMaceThrowRules.INITIAL_SPEED);
        assertEquals(0.75F, DJMaceThrowRules.COLLISION_DIAMETER);
        assertEquals(0.07, DJMaceThrowRules.GRAVITY);
        assertEquals(60, DJMaceThrowRules.MAX_LIFETIME_TICKS);
        assertEquals(1.6, DJMaceThrowRules.VERTICAL_KNOCKBACK);
        assertEquals(720.0F, DJMaceThrowRules.ROTATION_DEGREES_PER_TICK * 20.0F);
    }

    @Test
    void smashDownbeatRaisesBaseDamageToTwentyFour() {
        float multiplier = DJBeatDamageRules.multiplier(BeatCategory.DOWNBEAT, false, true);
        assertEquals(24.0F, DJMaceThrowRules.BASE_DAMAGE * multiplier);
    }
}
