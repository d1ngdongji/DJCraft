package otto.djgun.djcraft.client.animation;

/** Renderer-independent hand identity used by the deterministic animation core. */
public enum DJAnimationHand {
    MAIN,
    OFF;

    public static DJAnimationHand fromPhysicalArm(boolean renderedRightArm, boolean mainArmIsRight) {
        return renderedRightArm == mainArmIsRight ? MAIN : OFF;
    }
}
