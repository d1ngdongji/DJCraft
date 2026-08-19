package otto.djgun.djcraft.client.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.sound.DJActionOutcome;

class DJFirstPersonAnimatorTest {
    private static final Object MAIN_ITEM = new Object();
    private static final Object OFF_ITEM = new Object();

    @Test
    void physicalArmMappingPreservesMainAndOffHandForBothPlayerSettings() {
        assertEquals(DJAnimationHand.MAIN, DJAnimationHand.fromPhysicalArm(true, true));
        assertEquals(DJAnimationHand.OFF, DJAnimationHand.fromPhysicalArm(false, true));
        assertEquals(DJAnimationHand.MAIN, DJAnimationHand.fromPhysicalArm(false, false));
        assertEquals(DJAnimationHand.OFF, DJAnimationHand.fromPhysicalArm(true, false));
    }

    @BeforeEach
    void loadAnimationResources() {
        Map<String, DJAnimationCurve> curves = Map.of(
                DJAnimationClips.EQUIP, new DJAnimationCurve(
                        key(0.0f, -0.0875f, -0.96875f, -0.175f, -42.0f, 27.0f, 20.0f),
                        key(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)),
                DJAnimationClips.UNEQUIP, new DJAnimationCurve(
                        key(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                        key(1.0f, -0.0875f, -0.96875f, -0.175f, -42.0f, 27.0f, 20.0f)),
                DJAnimationClips.MELEE_STRIKE, new DJAnimationCurve(
                        key(0.0f, -0.2375f, -0.1125f, 0.125f, 0.0f, -8.0f, 62.0f),
                        key(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)),
                DJAnimationClips.MELEE_THRUST, new DJAnimationCurve(
                        key(0.0f, 0.0f, -0.15f, 0.375f, 0.0f, -5.0f, 90.0f),
                        key(0.5f, -0.009375f, -0.0875f, 0.2375f, 1.0f, -3.0f, 82.0f),
                        key(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)),
                DJAnimationClips.MELEE_SWEEP, new DJAnimationCurve(
                        key(0.0f, -0.3f, -0.1f, 0.09375f, 6.0f, -14.0f, 78.0f),
                        key(0.5f, -0.1375f, -0.034375f, 0.05625f, 3.0f, -7.0f, 42.0f),
                        key(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)),
                DJAnimationClips.MELEE_CRITICAL, new DJAnimationCurve(
                        key(0.0f, 0.01875f, -0.275f, 0.14375f, 4.0f, -3.0f, 168.0f),
                        key(0.5f, 0.00625f, -0.15f, 0.08125f, 3.0f, -2.0f, 118.0f),
                        key(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)),
                DJAnimationClips.TRIDENT_THRUST, new DJAnimationCurve(
                        layeredKey(0.0f, -1.5f, 45.0f),
                        layeredKey(0.3158f, -1.125f, 44.0f),
                        key(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)),
                DJAnimationClips.PARRY, new DJAnimationCurve(
                        key(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                        key(1.0f / 6.0f, -0.075f, 0.025f, 0.09375f, -4.0f, 3.0f, -6.0f),
                        key(2.0f / 6.0f, 0.0875f, -0.01875f, 0.05f, 5.0f, -4.0f, 7.0f),
                        key(3.0f / 6.0f, -0.0625f, 0.0125f, 0.06875f, -3.0f, 3.0f, -5.0f),
                        key(4.0f / 6.0f, 0.04375f, -0.00625f, 0.03125f, 2.0f, -2.0f, 3.0f),
                        key(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)),
                DJAnimationClips.USE, new DJAnimationCurve(
                        key(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                        key(0.45f, 0.0f, -0.15f, -0.1f, -18.0f, 0.0f, -12.0f),
                        key(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)));
        DJAnimationLibrary.getInstance().installForTests(curves, List.of());
    }

    @Test
    void attackStartsAtImpactAndRecoversByVirtualBeat() {
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.MELEE_SWEEP, 4.0, 1.0));

        DJAnimationPose impact = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.0));
        DJAnimationPose middle = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.5));
        DJAnimationPose recovered = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 5.0));

        assertEquals(0.09375f, impact.translationZBlocks(), 0.0001f);
        assertTrue(Math.abs(middle.translationZBlocks()) < Math.abs(impact.translationZBlocks()));
        assertEquals(0.0f, recovered.translationZBlocks(), 0.0001f);
    }

    @Test
    void switchOutOverlaysAttackAndHoldsIncomingItemUntilHalfway() {
        Object incomingItem = new Object();
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.MELEE_SWEEP, 4.0, 1.0));
        animator.accept(event(2, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.UNEQUIP_START, 4.0, 2.0));
        animator.accept(event(3, 1, DJAnimationHand.MAIN, incomingItem,
                DJAnimationEvent.Kind.EQUIP_START, 5.0, 1.0));

        DJAnimationPose outgoingImpact = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.0));
        DJAnimationPose incomingBeforeHandoff = animator.sample(
                DJAnimationHand.MAIN, incomingItem, snapshot(1, 4.5));
        DJAnimationPose outgoingAtHandoff = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 5.0));
        DJAnimationPose incomingRecovered = animator.sample(
                DJAnimationHand.MAIN, incomingItem, snapshot(1, 6.0));

        assertEquals(0.09375f, outgoingImpact.translationZBlocks(), 0.0001f);
        assertEquals(-0.96875f + 0.035f, incomingBeforeHandoff.translationYBlocks(), 0.0001f);
        assertEquals(-0.96875f - 0.035f, outgoingAtHandoff.translationYBlocks(), 0.0001f);
        assertEquals(-0.035f, incomingRecovered.translationYBlocks(), 0.0001f);
    }

    @Test
    void attackDuringInstantSwitchStartsOnCurrentlyRenderedOutgoingItem() {
        Object incomingItem = new Object();
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.UNEQUIP_START, 4.0, 2.0));
        animator.accept(event(2, 1, DJAnimationHand.MAIN, incomingItem,
                DJAnimationEvent.Kind.EQUIP_START, 5.0, 1.0));
        animator.accept(event(3, 1, DJAnimationHand.MAIN, incomingItem,
                DJAnimationEvent.Kind.MELEE_SWEEP, 4.0, 2.0));

        DJAnimationPose outgoingAttack = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.0));
        DJAnimationPose firstIncomingFrame = animator.sample(
                DJAnimationHand.MAIN, incomingItem, snapshot(1, 5.0));

        assertEquals(78.0f, outgoingAttack.rotationZDegrees(), 0.0001f);
        assertEquals(20.0f, firstIncomingFrame.rotationZDegrees(), 0.0001f);
    }

    @Test
    void retargetingPendingIncomingItemKeepsOutgoingPoseContinuous() {
        Object firstTarget = new Object();
        Object redirectedTarget = new Object();
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.UNEQUIP_START, 4.0, 2.0));
        animator.accept(event(2, 1, DJAnimationHand.MAIN, firstTarget,
                DJAnimationEvent.Kind.EQUIP_START, 5.0, 1.0));

        DJAnimationPose beforeRedirect = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.5));
        animator.accept(event(3, 1, DJAnimationHand.MAIN, redirectedTarget,
                DJAnimationEvent.Kind.EQUIP_START, 5.0, 1.0));
        DJAnimationPose afterRedirect = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.5));
        DJAnimationPose obsoleteTarget = animator.sample(
                DJAnimationHand.MAIN, firstTarget, snapshot(1, 4.5));
        DJAnimationPose redirected = animator.sample(
                DJAnimationHand.MAIN, redirectedTarget, snapshot(1, 4.5));

        assertEquals(beforeRedirect, afterRedirect);
        assertEquals(0.035f, obsoleteTarget.translationYBlocks(), 0.0001f);
        assertEquals(-0.96875f + 0.035f, redirected.translationYBlocks(), 0.0001f);
    }

    @Test
    void interruptedEquipBecomesContinuousStartPoseForNextUnequip() {
        Object firstTarget = new Object();
        Object redirectedTarget = new Object();
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.UNEQUIP_START, 4.0, 2.0));
        animator.accept(event(2, 1, DJAnimationHand.MAIN, firstTarget,
                DJAnimationEvent.Kind.EQUIP_START, 5.0, 1.0));

        DJAnimationPose interruptedPose = animator.sample(
                DJAnimationHand.MAIN, firstTarget, snapshot(1, 5.4));
        animator.accept(event(3, 1, DJAnimationHand.MAIN, firstTarget,
                DJAnimationEvent.Kind.UNEQUIP_START, 5.4, 2.0));
        animator.accept(event(4, 1, DJAnimationHand.MAIN, redirectedTarget,
                DJAnimationEvent.Kind.EQUIP_START, 6.4, 1.0));
        DJAnimationPose redirectedStart = animator.sample(
                DJAnimationHand.MAIN, firstTarget, snapshot(1, 5.4));

        assertEquals(interruptedPose, redirectedStart);
    }

    @Test
    void lateRendererHandoffRestartsEquipAtItsActualFirstFrame() {
        Object incomingItem = new Object();
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, incomingItem,
                DJAnimationEvent.Kind.EQUIP_START, 5.0, 1.0));
        animator.accept(event(2, 1, DJAnimationHand.MAIN, incomingItem,
                DJAnimationEvent.Kind.EQUIP_START, 6.0, 1.0));

        DJAnimationPose actualFirstFrame = animator.sample(
                DJAnimationHand.MAIN, incomingItem, snapshot(1, 6.0));

        assertEquals(-0.96875f - 0.035f, actualFirstFrame.translationYBlocks(), 0.0001f);
        assertEquals(20.0f, actualFirstFrame.rotationZDegrees(), 0.0001f);
    }

    @Test
    void lowFrameRateStillShowsOneImpactSample() {
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.MELEE_SWEEP, 2.0, 0.25));

        DJAnimationPose firstVisibleFrame = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.0));
        DJAnimationPose followingFrame = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.01));

        assertEquals(0.09375f, firstVisibleFrame.translationZBlocks(), 0.0001f);
        assertEquals(0.0f, followingFrame.translationZBlocks(), 0.0001f);
    }

    @Test
    void consecutiveAttacksReplaceInsteadOfAddingTheirFullTransforms() {
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.MELEE_SWEEP, 4.0, 1.0));
        animator.accept(event(2, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.MELEE_SWEEP, 4.1, 1.0));

        DJAnimationPose pose = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.1));

        assertEquals(0.09375f, pose.translationZBlocks(), 0.0001f);
        assertEquals(78.0f, pose.rotationZDegrees(), 0.0001f);
    }

    @Test
    void resourceDurationOverridesEventDurationDuringPlayback() {
        DJAnimationCurve sweep = DJAnimationClips.curve(DJAnimationClips.MELEE_SWEEP);
        DJAnimationLibrary.LoadedProfile profile = new DJAnimationLibrary.LoadedProfile(
                net.minecraft.resources.ResourceLocation.parse("example:main"),
                100,
                java.util.Set.of(net.minecraft.resources.ResourceLocation.parse("djcraft:main")),
                java.util.Set.of(),
                Map.of(DJAnimationSemantic.MELEE_SWEEP,
                        new DJAnimationLibrary.Binding(sweep, 2.0)));
        DJAnimationLibrary.getInstance().installForTests(
                Map.of(DJAnimationClips.MELEE_SWEEP, sweep), List.of(profile));
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.MELEE_SWEEP, 4.0, 0.25));

        animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.0));
        DJAnimationPose halfway = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 5.0));

        assertEquals(0.05625f, halfway.translationZBlocks(), 0.0001f);
        assertEquals(42.0f, halfway.rotationZDegrees(), 0.0001f);
    }

    @Test
    void tridentThrustUsesDedicatedHorizontalLargeDisplacementClip() {
        DJAnimationCurve tridentCurve = DJAnimationClips.curve(DJAnimationClips.TRIDENT_THRUST);
        DJAnimationLibrary.LoadedProfile tridentProfile = new DJAnimationLibrary.LoadedProfile(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("djcraft", "trident"),
                100,
                java.util.Set.of(net.minecraft.resources.ResourceLocation.parse("minecraft:trident")),
                java.util.Set.of(),
                Map.of(DJAnimationSemantic.MELEE_THRUST,
                        new DJAnimationLibrary.Binding(tridentCurve, 2.0)));
        DJAnimationLibrary.getInstance().installForTests(
                Map.of(DJAnimationClips.TRIDENT_THRUST, tridentCurve), List.of(tridentProfile));
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM, "minecraft:trident",
                DJAnimationEvent.Kind.MELEE_THRUST, 4.0, 2.0));

        DJAnimationPose impact = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.0));

        assertEquals(-1.5f, impact.translationZBlocks(), 0.0001f);
        assertEquals(0.0f, impact.translationXBlocks(), 0.0001f);
        assertEquals(0.0f, impact.rotationZDegrees(), 0.0001f);
        assertEquals(45.0f, impact.itemCenterSpace().rotationZDegrees(), 0.0001f);
    }

    @Test
    void crossbowTriggerUsesDedicatedOneBeatRecoilClip() {
        DJAnimationCurve crossbowCurve = new DJAnimationCurve(
                key(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                key(0.08f, 0.0f, 0.05f, -0.2f, 12.0f, 0.0f, 2.0f),
                key(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        DJAnimationLibrary.LoadedProfile crossbowProfile = new DJAnimationLibrary.LoadedProfile(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("djcraft", "crossbow"),
                100,
                java.util.Set.of(net.minecraft.resources.ResourceLocation.parse("minecraft:crossbow")),
                java.util.Set.of(),
                Map.of(DJAnimationSemantic.TRIGGER_IMPACT,
                        new DJAnimationLibrary.Binding(crossbowCurve, 1.0)));
        DJAnimationLibrary.getInstance().installForTests(
                Map.of("animation.djcraft.first_person.crossbow_fire", crossbowCurve),
                List.of(crossbowProfile));
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM, "minecraft:crossbow",
                DJAnimationEvent.Kind.TRIGGER_IMPACT, 4.0, 4.0));

        animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.0));
        DJAnimationPose recoil = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.08));
        DJAnimationPose recovered = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 5.0));

        assertEquals(-0.2f, recoil.translationZBlocks(), 0.0001f);
        assertEquals(12.0f, recoil.rotationXDegrees(), 0.0001f);
        assertEquals(2.0f, recoil.rotationZDegrees(), 0.0001f);
        assertEquals(0.0f, recovered.translationZBlocks(), 0.0001f);
        assertEquals(0.0f, recovered.rotationXDegrees(), 0.0001f);
    }

    @Test
    void parryUsesTheImpulseChannelAndRecovers() {
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.PARRY, 4.0, 0.35));

        DJAnimationPose initial = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.0));
        DJAnimationPose impact = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM,
                snapshot(1, 4.0 + 0.35 / 6.0));
        DJAnimationPose recovered = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.35));

        assertEquals(0.0f, initial.translationXBlocks(), 0.0001f);
        assertEquals(-0.075f, impact.translationXBlocks(), 0.0001f);
        assertEquals(0.09375f, impact.translationZBlocks(), 0.0001f);
        assertEquals(-6.0f, impact.rotationZDegrees(), 0.0001f);
        assertEquals(0.0f, recovered.translationZBlocks(), 0.0001f);
    }

    @Test
    void animatorComposesAndMirrorsHandAndItemCenterSpacesIndependently() {
        DJAnimationPose peak = new DJAnimationPose(
                new DJAnimationTransform(0.25f, -0.1f, 0.2f, 4.0f, 6.0f, 8.0f, 1.0f),
                new DJAnimationTransform(0.125f, 0.05f, -0.1f, -3.0f, 5.0f, 7.0f, 1.0f));
        DJAnimationCurve curve = new DJAnimationCurve(
                new DJAnimationCurve.Keyframe(0.0f, peak),
                new DJAnimationCurve.Keyframe(1.0f, DJAnimationPose.IDENTITY));
        DJAnimationLibrary.getInstance().installForTests(
                Map.of(DJAnimationClips.MELEE_SWEEP, curve), List.of());
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.OFF, OFF_ITEM,
                DJAnimationEvent.Kind.MELEE_SWEEP, 4.0, 1.0));

        DJAnimationPose pose = animator.sample(
                DJAnimationHand.OFF, OFF_ITEM, snapshot(1, 4.0));

        assertEquals(-0.25f, pose.handSpace().translationXBlocks(), 0.0001f);
        assertEquals(-6.0f, pose.handSpace().rotationYDegrees(), 0.0001f);
        assertEquals(-0.125f, pose.itemCenterSpace().translationXBlocks(), 0.0001f);
        assertEquals(-7.0f, pose.itemCenterSpace().rotationZDegrees(), 0.0001f);
    }

    @Test
    void useRunsForOneVirtualBeatAndDoesNotAffectTheOtherHand() {
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.USE, 4.0, 1.0));

        DJAnimationPose mainPeak = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.45));
        DJAnimationPose offPeak = animator.sample(
                DJAnimationHand.OFF, OFF_ITEM, snapshot(1, 4.45));
        DJAnimationPose recovered = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 5.0));

        assertEquals(-0.1f, mainPeak.translationZBlocks(), 0.0001f);
        assertEquals(-18.0f, mainPeak.rotationXDegrees(), 0.0001f);
        assertEquals(-12.0f, mainPeak.rotationZDegrees(), 0.0001f);
        assertEquals(0.0f, offPeak.translationZBlocks(), 0.0001f);
        assertEquals(0.0f, offPeak.rotationXDegrees(), 0.0001f);
        assertEquals(0.0f, offPeak.rotationZDegrees(), 0.0001f);
        assertEquals(0.0f, recovered.translationZBlocks(), 0.0001f);
        assertEquals(0.0f, recovered.rotationXDegrees(), 0.0001f);
        assertEquals(0.0f, recovered.rotationZDegrees(), 0.0001f);
    }

    @Test
    void handsKeepIndependentActionAndImpulseChannels() {
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.EQUIP_START, 8.0, 2.0));
        animator.accept(event(2, 1, DJAnimationHand.OFF, OFF_ITEM,
                DJAnimationEvent.Kind.MELEE_SWEEP, 8.0, 1.0));

        DJAnimationPose main = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 8.0));
        DJAnimationPose off = animator.sample(DJAnimationHand.OFF, OFF_ITEM, snapshot(1, 8.0));

        assertEquals(-0.96875f - 0.035f, main.translationYBlocks(), 0.0001f);
        assertEquals(-0.175f, main.translationZBlocks(), 0.0001f);
        assertEquals(0.09375f, off.translationZBlocks(), 0.0001f);
    }

    @Test
    void handSwapRunsIndependentUnequipTransitionsForBothHands() {
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.UNEQUIP_START, 8.0, 2.0));
        animator.accept(event(2, 1, DJAnimationHand.OFF, OFF_ITEM,
                DJAnimationEvent.Kind.UNEQUIP_START, 8.0, 2.0));

        DJAnimationPose main = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 8.5));
        DJAnimationPose off = animator.sample(DJAnimationHand.OFF, OFF_ITEM, snapshot(1, 8.5));

        assertEquals(-main.translationXBlocks(), off.translationXBlocks(), 0.0001f);
        assertEquals(main.translationYBlocks(), off.translationYBlocks(), 0.0001f);
        assertEquals(-main.rotationYDegrees(), off.rotationYDegrees(), 0.0001f);
        assertEquals(-main.rotationZDegrees(), off.rotationZDegrees(), 0.0001f);
    }

    @Test
    void mainHandEquipDoesNotRaiseOffHand() {
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.EQUIP_START, 8.0, 2.0));

        DJAnimationPose main = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 8.0));
        DJAnimationPose off = animator.sample(DJAnimationHand.OFF, OFF_ITEM, snapshot(1, 8.0));

        assertEquals(-0.96875f - 0.035f, main.translationYBlocks(), 0.0001f);
        assertEquals(-0.035f, off.translationYBlocks(), 0.0001f);
        assertEquals(0.0f, off.translationZBlocks(), 0.0001f);
        assertEquals(0.0f, off.rotationXDegrees(), 0.0001f);
    }

    @Test
    void mainHandAttackDoesNotTransformOffHand() {
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.MELEE_SWEEP, 8.0, 1.0));

        DJAnimationPose main = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 8.0));
        DJAnimationPose off = animator.sample(DJAnimationHand.OFF, OFF_ITEM, snapshot(1, 8.0));

        assertEquals(0.09375f, main.translationZBlocks(), 0.0001f);
        assertEquals(0.0f, off.translationXBlocks(), 0.0001f);
        assertEquals(0.0f, off.translationZBlocks(), 0.0001f);
        assertEquals(0.0f, off.rotationXDegrees(), 0.0001f);
        assertEquals(0.0f, off.rotationYDegrees(), 0.0001f);
        assertEquals(0.0f, off.rotationZDegrees(), 0.0001f);
    }

    @Test
    void generationChangeClearsNonRebuildableState() {
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.CHARGE_START, 3.0, 1.0));

        DJAnimationPose beforeReset = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 4.0));
        DJAnimationPose afterReset = animator.sample(
                DJAnimationHand.MAIN, MAIN_ITEM, snapshot(2, 4.0));

        assertTrue(beforeReset.translationZBlocks() < 0.0f);
        assertEquals(0.0f, afterReset.translationZBlocks(), 0.0001f);
    }

    @Test
    void duplicateSequenceIsIdempotent() {
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(7, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.CHARGE_START, 2.0, 1.0));
        animator.accept(event(7, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.CANCEL, 2.0, 0.0));

        DJAnimationPose pose = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 3.0));

        assertTrue(pose.translationZBlocks() < 0.0f);
    }

    @Test
    void explicitCancelClearsAllChannelsForOnlyThatHand() {
        DJFirstPersonAnimator animator = new DJFirstPersonAnimator();
        animator.accept(event(1, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.CHARGE_START, 2.0, 1.0));
        animator.accept(event(2, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.MELEE_SWEEP, 2.0, 1.0));
        animator.accept(event(3, 1, DJAnimationHand.MAIN, MAIN_ITEM,
                DJAnimationEvent.Kind.CANCEL, 2.0, 0.0));

        DJAnimationPose pose = animator.sample(DJAnimationHand.MAIN, MAIN_ITEM, snapshot(1, 3.0));

        assertEquals(0.0f, pose.translationZBlocks(), 0.0001f);
        assertEquals(0.0f, pose.rotationXDegrees(), 0.0001f);
    }

    @Test
    void clockSnapshotMapsAcrossVariableBeatIntervals() {
        List<BeatEvent> beats = List.of(
                new BeatEvent(0, "beat"),
                new BeatEvent(500, "beat"),
                new BeatEvent(1500, "beat"),
                new BeatEvent(2000, "beat"));
        DJAnimationClock.ClockSnapshot snapshot = new DJAnimationClock.ClockSnapshot(
                1000, 1.5, 0.5, true, false, 1, false, beats);

        assertEquals(1500, snapshot.timeAtVirtualBeat(2.0));
        assertEquals(1.5, snapshot.virtualBeatAt(1000), 0.000001);
    }

    @Test
    void easingClampsAndKeepsDocumentedEndpoints() {
        assertEquals(0.0f, DJFirstPersonAnimator.smoothstep(-1.0f));
        assertEquals(0.5f, DJFirstPersonAnimator.smoothstep(0.5f));
        assertEquals(1.0f, DJFirstPersonAnimator.smoothstep(2.0f));
        assertEquals(0.0f, DJFirstPersonAnimator.clamp01(Double.NaN));
    }

    @Test
    void blockbenchResourceCurvesHaveStableEndpointsAndAxisConversion() {
        DJAnimationPose drawStart = DJAnimationClips.curve(DJAnimationClips.EQUIP).sample(0.0f);
        DJAnimationPose drawEnd = DJAnimationClips.curve(DJAnimationClips.EQUIP).sample(1.0f);
        DJAnimationPose putAwayStart = DJAnimationClips.curve(DJAnimationClips.UNEQUIP).sample(0.0f);
        DJAnimationPose putAwayEnd = DJAnimationClips.curve(DJAnimationClips.UNEQUIP).sample(1.0f);
        DJAnimationPose thrustImpact = DJAnimationClips.curve(DJAnimationClips.MELEE_THRUST).sample(0.0f);
        DJAnimationPose sweepImpact = DJAnimationClips.curve(DJAnimationClips.MELEE_SWEEP).sample(0.0f);
        DJAnimationPose criticalImpact = DJAnimationClips.curve(DJAnimationClips.MELEE_CRITICAL).sample(0.0f);
        DJAnimationPose tridentImpact = DJAnimationClips.curve(DJAnimationClips.TRIDENT_THRUST).sample(0.0f);

        assertEquals(-0.96875f, drawStart.translationYBlocks(), 0.0001f);
        assertEquals(DJAnimationPose.IDENTITY, drawEnd);
        assertEquals(DJAnimationPose.IDENTITY, putAwayStart);
        assertEquals(drawStart, putAwayEnd);
        assertEquals(0.375f, thrustImpact.translationZBlocks(), 0.0001f);
        assertEquals(90.0f, thrustImpact.rotationZDegrees(), 0.0001f);
        assertEquals(-0.3f, sweepImpact.translationXBlocks(), 0.0001f);
        assertEquals(78.0f, sweepImpact.rotationZDegrees(), 0.0001f);
        assertEquals(-0.275f, criticalImpact.translationYBlocks(), 0.0001f);
        assertEquals(168.0f, criticalImpact.rotationZDegrees(), 0.0001f);
        assertEquals(-1.5f, tridentImpact.handSpace().translationZBlocks(), 0.0001f);
        assertEquals(0.0f, tridentImpact.handSpace().rotationZDegrees(), 0.0001f);
        assertEquals(45.0f, tridentImpact.itemCenterSpace().rotationZDegrees(), 0.0001f);
    }

    private static DJAnimationEvent event(long sequence, long generation, DJAnimationHand hand, Object item,
            DJAnimationEvent.Kind kind, double virtualBeat, double durationBeats) {
        return event(sequence, generation, hand, item,
                hand == DJAnimationHand.MAIN ? "djcraft:main" : "djcraft:off",
                kind, virtualBeat, durationBeats);
    }

    private static DJAnimationEvent event(long sequence, long generation, DJAnimationHand hand, Object item,
            String itemIdentity, DJAnimationEvent.Kind kind, double virtualBeat, double durationBeats) {
        return new DJAnimationEvent(sequence, generation, hand, item, itemIdentity,
                kind, 1_000L, virtualBeat, durationBeats, DJActionOutcome.NOT_JUDGED);
    }

    private static DJAnimationCurve.Keyframe key(float phase, float x, float y, float z,
            float rotationX, float rotationY, float rotationZ) {
        return new DJAnimationCurve.Keyframe(phase,
                new DJAnimationPose(x, y, z, rotationX, rotationY, rotationZ, 1.0f));
    }

    private static DJAnimationCurve.Keyframe layeredKey(
            float phase, float handTranslationZ, float itemRotationZ) {
        return new DJAnimationCurve.Keyframe(phase, new DJAnimationPose(
                new DJAnimationTransform(0.0f, 0.0f, handTranslationZ, 0.0f, 0.0f, 0.0f, 1.0f),
                new DJAnimationTransform(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, itemRotationZ, 1.0f)));
    }

    private static DJAnimationClock.ClockSnapshot snapshot(long generation, double virtualBeat) {
        return new DJAnimationClock.ClockSnapshot(1_000L, virtualBeat,
                virtualBeat - Math.floor(virtualBeat), true, false, generation, true, List.of());
    }
}
