package otto.djgun.djcraft.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** The sole writer of DJCraft first-person transforms to PoseStack. */
public final class DJFirstPersonPoseApplier {
    private DJFirstPersonPoseApplier() {
    }

    public static void applyHandSpace(DJAnimationPose pose, PoseStack poseStack) {
        apply(pose.handSpace(), poseStack);
    }

    public static void applyItemCenterSpace(DJAnimationPose pose, PoseStack poseStack) {
        apply(pose.itemCenterSpace(), poseStack);
    }

    private static void apply(DJAnimationTransform transform, PoseStack poseStack) {
        poseStack.translate(transform.translationXBlocks(),
                transform.translationYBlocks(), transform.translationZBlocks());
        poseStack.mulPose(Axis.XP.rotationDegrees(transform.rotationXDegrees()));
        poseStack.mulPose(Axis.YP.rotationDegrees(transform.rotationYDegrees()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(transform.rotationZDegrees()));
        poseStack.scale(transform.scaleRatio(), transform.scaleRatio(), transform.scaleRatio());
    }
}
