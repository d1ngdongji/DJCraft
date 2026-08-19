package otto.djgun.djcraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;

class DJWeaponSoundRuntimeTest {
    @Test
    void firingReleaseAndCancellationStopTrackedChargeSound() {
        assertTrue(DJWeaponSoundRuntime.shouldStopCharge(DJWeaponSoundSemantic.TRIGGER_IMPACT));
        assertTrue(DJWeaponSoundRuntime.shouldStopCharge(DJWeaponSoundSemantic.CHARGE_RELEASE));
        assertTrue(DJWeaponSoundRuntime.shouldStopCharge(DJWeaponSoundSemantic.CANCEL));
        assertFalse(DJWeaponSoundRuntime.shouldStopCharge(DJWeaponSoundSemantic.CHARGE_START));
        assertFalse(DJWeaponSoundRuntime.shouldStopCharge(DJWeaponSoundSemantic.TARGET_HIT));
    }
}
