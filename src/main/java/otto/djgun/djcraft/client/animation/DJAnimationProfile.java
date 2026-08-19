package otto.djgun.djcraft.client.animation;

/** Semantic playback defaults combined with an optional resource-supplied curve. */
public record DJAnimationProfile(
        DJAnimationSemantic semantic,
        Channel channel,
        int priority,
        double defaultDurationBeats,
        float translationYBlocks,
        float translationZBlocks,
        float rotationXDegrees,
        float rotationZDegrees,
        DJAnimationCurve curve) {

    public enum Channel { ACTION, IMPULSE, TRANSITION }

    public DJAnimationProfile {
        if (semantic == null || channel == null || priority < 0 || priority > 100
                || !Double.isFinite(defaultDurationBeats) || defaultDurationBeats < 0.0
                || !Float.isFinite(translationYBlocks) || !Float.isFinite(translationZBlocks)
                || !Float.isFinite(rotationXDegrees) || !Float.isFinite(rotationZDegrees)) {
            throw new IllegalArgumentException("Invalid animation profile");
        }
    }

    public static DJAnimationProfile fallback(DJAnimationSemantic semantic) {
        DJAnimationCurve curve = null;
        if (semantic == DJAnimationSemantic.MELEE_STRIKE) {
            curve = DJAnimationClips.curve(DJAnimationClips.MELEE_STRIKE);
        } else if (semantic == DJAnimationSemantic.MELEE_THRUST) {
            curve = DJAnimationClips.curve(DJAnimationClips.MELEE_THRUST);
        } else if (semantic == DJAnimationSemantic.MELEE_SWEEP) {
            curve = DJAnimationClips.curve(DJAnimationClips.MELEE_SWEEP);
        } else if (semantic == DJAnimationSemantic.MELEE_CRITICAL) {
            curve = DJAnimationClips.curve(DJAnimationClips.MELEE_CRITICAL);
        } else if (semantic == DJAnimationSemantic.PARRY) {
            curve = DJAnimationClips.curve(DJAnimationClips.PARRY);
        } else if (semantic == DJAnimationSemantic.UNEQUIP_START) {
            curve = DJAnimationClips.curve(DJAnimationClips.UNEQUIP);
        } else if (semantic == DJAnimationSemantic.EQUIP_START) {
            curve = DJAnimationClips.curve(DJAnimationClips.EQUIP);
        } else if (semantic == DJAnimationSemantic.USE) {
            curve = DJAnimationClips.curve(DJAnimationClips.USE);
        }
        return new DJAnimationProfile(semantic, semantic.channel(), semantic.priority(),
                semantic.defaultDurationBeats(), semantic.translationYBlocks(),
                semantic.translationZBlocks(), semantic.rotationXDegrees(),
                semantic.rotationZDegrees(), curve);
    }

    public static DJAnimationProfile forEvent(DJAnimationEvent event) {
        return DJAnimationLibrary.getInstance().resolve(event).profile();
    }

    DJAnimationProfile withCurve(DJAnimationCurve replacement) {
        return new DJAnimationProfile(semantic, channel, priority, defaultDurationBeats,
                translationYBlocks, translationZBlocks, rotationXDegrees, rotationZDegrees, replacement);
    }
}
