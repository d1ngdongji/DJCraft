package otto.djgun.djcraft.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.combat.access.PlayerAttackStrengthAccess;

/** Scoped compatibility state for item hooks while a registered area attack hits one target. */
public final class DJAreaMeleeCombatService {
    private static final ThreadLocal<Boolean> AREA_ATTACK = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> PRIMARY_TARGET = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<ItemStack> AREA_STACK = new ThreadLocal<>();
    private static final ThreadLocal<Entity> ACTIVE_TARGET = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DAMAGE_CONFIRMED = ThreadLocal.withInitial(() -> false);

    private DJAreaMeleeCombatService() {
    }

    public static boolean isAreaAttackActive(LivingEntity attacker) {
        return attacker instanceof ServerPlayer && AREA_ATTACK.get();
    }

    public static boolean isPrimaryTargetEffect() {
        return PRIMARY_TARGET.get();
    }

    public static boolean isAreaAttackStack(LivingEntity attacker, ItemStack stack) {
        return isAreaAttackActive(attacker) && AREA_STACK.get() == stack;
    }

    public static void confirmDamage(LivingEntity target) {
        if (AREA_ATTACK.get() && ACTIVE_TARGET.get() == target) {
            DAMAGE_CONFIRMED.set(true);
        }
    }

    static void attackTarget(ServerPlayer player, DJActionContext action, Entity target,
            int attackStrengthTicks, boolean primaryTarget) {
        PlayerAttackStrengthAccess accessor = (PlayerAttackStrengthAccess) player;
        int liveAttackStrengthTicks = accessor.djcraft$getAttackStrengthTicker();
        AREA_ATTACK.set(true);
        PRIMARY_TARGET.set(primaryTarget);
        AREA_STACK.set(action.resolveLiveStack(player));
        ACTIVE_TARGET.set(target);
        DAMAGE_CONFIRMED.set(false);
        try {
            accessor.djcraft$setAttackStrengthTicker(attackStrengthTicks);
            DJCombatHandler.receivePendingJudgment(player, action);
            DJCombatHandler.attackAuthorized(player, target);
            if (DAMAGE_CONFIRMED.get() && target instanceof LivingEntity livingTarget
                    && DJItemBehaviorManager.is(action.stackSnapshot(), DJItemBehavior.TRIDENT)) {
                livingTarget.setDeltaMovement(DJTridentRules.radialKnockbackVelocity(
                        livingTarget.getDeltaMovement(), player.getEyePosition(),
                        livingTarget.getBoundingBox().getCenter(), DJTridentRules.MELEE_RADIAL_KNOCKBACK,
                        livingTarget.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
                livingTarget.hasImpulse = true;
                livingTarget.hurtMarked = true;
            }
        } finally {
            DJCombatHandler.endVanillaAttack(player);
            AREA_ATTACK.set(false);
            PRIMARY_TARGET.set(false);
            AREA_STACK.remove();
            ACTIVE_TARGET.remove();
            DAMAGE_CONFIRMED.remove();
            accessor.djcraft$setAttackStrengthTicker(liveAttackStrengthTicks);
            DJCombatHandler.discardPendingJudgment(player.getUUID());
        }
    }
}
