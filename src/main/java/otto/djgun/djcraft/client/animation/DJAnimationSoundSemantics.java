package otto.djgun.djcraft.client.animation;

import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;

public final class DJAnimationSoundSemantics {
    private DJAnimationSoundSemantics() {
    }

    /** Returns {@code null} when an addon semantic has no built-in weapon-sound counterpart. */
    public static DJWeaponSoundSemantic from(DJAnimationSemantic semantic) {
        if (!"djcraft".equals(semantic.id().getNamespace())) {
            return null;
        }
        try {
            return DJWeaponSoundSemantic.parse(semantic.id().getPath());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** @deprecated Use {@link #from(DJAnimationSemantic)}. */
    @Deprecated(forRemoval = false)
    public static DJWeaponSoundSemantic from(DJAnimationEvent.Kind kind) {
        return from(kind.semantic());
    }
}
