package otto.djgun.djcraft.combat.client;

import javax.annotation.Nullable;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.ItemAbilities;
import otto.djgun.djcraft.client.animation.DJAnimationEvent;

/** Maps an attack intent to the same base predicates used by Player#attack in Minecraft 1.21.1. */
public final class DJMeleeAttackClassifier {
    private DJMeleeAttackClassifier() {
    }

    public static DJAnimationEvent.Kind classify(Player player, @Nullable HitResult hitResult) {
        Entity target = hitResult instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        boolean strongAttack = player.getAttackStrengthScale(0.5F) > 0.9F;
        boolean criticalAttack = strongAttack
                && player.fallDistance > 0.0F
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger()
                && target instanceof LivingEntity
                && !player.isSprinting();
        double walkedThisTick = player.walkDist - player.walkDistO;
        return classifyVanillaFlags(
                target != null && target.isAttackable(),
                strongAttack,
                player.isSprinting(),
                criticalAttack,
                player.onGround(),
                walkedThisTick,
                player.getSpeed(),
                player.getMainHandItem().canPerformAction(ItemAbilities.SWORD_SWEEP));
    }

    static DJAnimationEvent.Kind classifyVanillaFlags(boolean attackableTarget, boolean strongAttack,
            boolean sprinting, boolean criticalAttack, boolean onGround, double walkedThisTick,
            float speed, boolean canSweep) {
        if (!attackableTarget) {
            return DJAnimationEvent.Kind.MELEE_STRIKE;
        }
        if (strongAttack && sprinting) {
            return DJAnimationEvent.Kind.MELEE_THRUST;
        }
        if (criticalAttack) {
            return DJAnimationEvent.Kind.MELEE_CRITICAL;
        }
        if (strongAttack && onGround && walkedThisTick < speed && canSweep) {
            return DJAnimationEvent.Kind.MELEE_SWEEP;
        }
        return DJAnimationEvent.Kind.MELEE_STRIKE;
    }
}
