package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import otto.djgun.djcraft.data.BeatCategory;

class DJBeatDamageRulesTest {

    @Test
    void weakbeatHalvesDamageUnlessWeaponIsSwift() {
        assertEquals(0.5F, DJBeatDamageRules.multiplier(BeatCategory.WEAKBEAT, false, false));
        assertEquals(1.0F, DJBeatDamageRules.multiplier(BeatCategory.WEAKBEAT, true, false));
    }

    @Test
    void downbeatBoostsOnlySmashWeapons() {
        assertEquals(1.0F, DJBeatDamageRules.multiplier(BeatCategory.DOWNBEAT, false, false));
        assertEquals(1.5F, DJBeatDamageRules.multiplier(BeatCategory.DOWNBEAT, false, true));
    }

    @Test
    void normalBeatDoesNotModifyDamage() {
        assertEquals(1.0F, DJBeatDamageRules.multiplier(BeatCategory.NORMAL, true, true));
    }

    @Test
    void categoryMatchRequiresTheMatchingSpecializedWeaponTag() {
        assertTrue(DJBeatDamageRules.isCategoryMatched(
                BeatCategory.WEAKBEAT, true, false));
        assertFalse(DJBeatDamageRules.isCategoryMatched(
                BeatCategory.WEAKBEAT, false, true));
        assertTrue(DJBeatDamageRules.isCategoryMatched(
                BeatCategory.DOWNBEAT, false, true));
        assertFalse(DJBeatDamageRules.isCategoryMatched(
                BeatCategory.DOWNBEAT, true, false));
        assertFalse(DJBeatDamageRules.isCategoryMatched(
                BeatCategory.NORMAL, true, true));
    }
}
