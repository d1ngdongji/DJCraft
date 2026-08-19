package otto.djgun.djcraft.sound;

import java.util.Locale;

public enum DJWeaponSoundSemantic {
    MELEE_STRIKE,
    MELEE_THRUST,
    MELEE_SWEEP,
    MELEE_CRITICAL,
    TRIGGER_IMPACT,
    CHARGE_START,
    CHARGE_RELEASE,
    UNEQUIP_START,
    EQUIP_START,
    RELOAD_START,
    INSPECT_START,
    USE,
    USE_START,
    USE_RELEASE,
    READY,
    CANCEL,
    DASH,
    DOUBLE_JUMP,
    PARRY,
    TARGET_HIT;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DJWeaponSoundSemantic parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
