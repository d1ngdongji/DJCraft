package otto.djgun.djcraft.network.server;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.api.combat.DJItemBehaviorDefinition;
import otto.djgun.djcraft.api.combat.DJTriggerBehaviorContext;
import otto.djgun.djcraft.combat.DJActionContext;
import otto.djgun.djcraft.combat.DJAttackExecutionRules;
import otto.djgun.djcraft.combat.DJActionSource;
import otto.djgun.djcraft.combat.DJAutoChargeManager;
import otto.djgun.djcraft.combat.DJChargeJudgmentCache;
import otto.djgun.djcraft.combat.DJCombatHandler;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;
import otto.djgun.djcraft.combat.DJMeleeAttackWindowManager;
import otto.djgun.djcraft.combat.DJMeleeBehaviorResolver;
import otto.djgun.djcraft.combat.DJRayWeaponCombatService;
import otto.djgun.djcraft.combat.DJRayWeaponManager;
import otto.djgun.djcraft.combat.DJTridentCombatService;
import otto.djgun.djcraft.combat.DJMaceCombatService;
import otto.djgun.djcraft.combat.DJJudgmentVerifier;
import otto.djgun.djcraft.combat.DJJudgmentVerifier.Verification;
import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.network.packet.DJAttackClientPayload;
import otto.djgun.djcraft.network.packet.DJAutoChargeStartPayload;
import otto.djgun.djcraft.network.packet.DJChargeReleasePayload;
import otto.djgun.djcraft.network.packet.DJChargeStartPayload;
import otto.djgun.djcraft.network.packet.DJTriggerFirePayload;
import otto.djgun.djcraft.network.packet.DJTridentFirePayload;
import otto.djgun.djcraft.network.packet.DJMaceThrowPayload;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.util.BeatGridUtil;

public final class DJCombatRequestHandler {
    private DJCombatRequestHandler() {
    }

    public static void handleAttack(DJAttackClientPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack actionStack = payload.source().resolveAtActionStart(player, payload.hand());
        Verification verification = DJJudgmentVerifier.verify(
                player, payload.proof(),
                DJMeleeBehaviorResolver.resolveJudgmentBehavior(actionStack).disabledByCanAttack());
        if (verification.session() == null) {
            return;
        }
        if (actionStack.isEmpty()) {
            DJCraft.LOGGER.debug("Rejected DJ melee source for {}: sequence={}, sourceSlot={}, selectedSlot={}",
                    player.getName().getString(), payload.proof().actionSequence(), payload.source().slot(),
                    player.getInventory().selected);
            return;
        }
        boolean validAction = verification.accepted()
                && payload.hand() == InteractionHand.MAIN_HAND
                && !player.getCooldowns().isOnCooldown(actionStack.getItem());
        HitResult result = validAction ? verification.result() : HitResult.miss(payload.proof().clientTimeMs());
        recordMissIfNeeded(verification, payload.proof().actionSequence(), result);
        int offBeatDamagePercent = verification.session().getOffBeatAttackDamagePercent();
        boolean damageAuthorized = DJAttackExecutionRules.canExecute(validAction, result, offBeatDamagePercent);
        if (damageAuthorized
                && !verification.session().tryConsumeEnergy(DJItemCooldownManager.getAttackEnergyCost(actionStack))) {
            damageAuthorized = false;
            verification.session().sendResourceState();
        }
        if (damageAuthorized && result.isHit()) {
            verification.session().ignoreCurrentBeatForComboReset();
        }
        if (verification.accepted()) {
            applyCooldown(player, actionStack, verification, false);
        }
        if (damageAuthorized) {
            DJActionContext action = DJActionContext.create(player,
                    verification.session().getSessionId(), payload.proof().actionSequence(), payload.hand(),
                    payload.source(), actionStack, result, verification.stopAfterAction(), true,
                    offBeatDamagePercent);
            if (payload.source().slot() != player.getInventory().selected) {
                DJCraft.LOGGER.debug("Executing pre-switch DJ melee for {}: sequence={}, sourceSlot={}, currentSlot={}",
                        player.getName().getString(), payload.proof().actionSequence(), payload.source().slot(),
                        player.getInventory().selected);
            }
            DJMeleeAttackWindowManager.activate(player, verification.session(), action,
                    DJMeleeBehaviorResolver.resolveExecutor(actionStack));
        } else {
            stopAfterAction(player, verification);
        }
    }

    public static void handleChargeRelease(DJChargeReleasePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        DJActionSource actionSource = payload.source();
        ItemStack stack = actionSource.resolveAtActionStart(player, payload.hand());
        if (stack.isEmpty() && player.isUsingItem() && player.getUsedItemHand() == payload.hand()
                && DJItemBehaviorManager.resolve(player.getUseItem()).isCharge()) {
            stack = player.getUseItem();
            actionSource = DJActionSource.capture(player, payload.hand(), stack);
        }
        DJItemBehaviorDefinition behavior = DJItemBehaviorManager.resolveDefinition(stack);
        Verification verification = DJJudgmentVerifier.verify(
                player, payload.proof(), behavior.disabledByCanAttack());
        if (verification.session() == null) {
            return;
        }
        boolean validWeapon = verification.accepted() && !stack.isEmpty() && behavior.isCharge()
                && actionSource.equals(payload.source());
        HitResult result = validWeapon
                ? verification.result()
                : HitResult.miss(payload.proof().clientTimeMs());
        recordMissIfNeeded(verification, payload.proof().actionSequence(), result);
        int offBeatDamagePercent = verification.session().getOffBeatAttackDamagePercent();
        boolean damageAuthorized = DJAttackExecutionRules.canExecute(validWeapon, result, offBeatDamagePercent);
        if (damageAuthorized
                && !verification.session().tryConsumeEnergy(DJItemCooldownManager.getUseEnergyCost(stack))) {
            damageAuthorized = false;
            verification.session().sendResourceState();
        }
        if (!stack.isEmpty()) {
            DJChargeJudgmentCache.store(player.getUUID(), DJActionContext.create(player,
                    verification.session().getSessionId(), payload.proof().actionSequence(), payload.hand(),
                    actionSource, stack, result, verification.stopAfterAction(), damageAuthorized,
                    offBeatDamagePercent));
        }
    }

    public static void handleTriggerFire(DJTriggerFirePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        InteractionHand hand = payload.hand();
        ItemStack stack = payload.source().resolveAtActionStart(player, hand);
        DJItemBehaviorDefinition definition = DJItemBehaviorManager.resolveDefinition(stack);
        Verification verification = DJJudgmentVerifier.verify(
                player, payload.proof(), definition.disabledByCanAttack());
        if (verification.session() == null) {
            return;
        }
        DJItemBehavior behavior = definition.family();
        boolean validAction = verification.accepted() && behavior.isTrigger()
                && !player.getCooldowns().isOnCooldown(stack.getItem());
        HitResult result = validAction ? verification.result() : HitResult.miss(payload.proof().clientTimeMs());
        recordMissIfNeeded(verification, payload.proof().actionSequence(), result);
        int offBeatDamagePercent = verification.session().getOffBeatAttackDamagePercent();
        boolean damageAuthorized = DJAttackExecutionRules.canExecute(validAction, result, offBeatDamagePercent);
        if (!damageAuthorized) {
            stopAfterAction(player, verification);
            return;
        }

        if (!verification.session().tryConsumeEnergy(DJItemCooldownManager.getUseEnergyCost(stack))) {
            verification.session().sendResourceState();
            stopAfterAction(player, verification);
            return;
        }
        DJActionContext action = DJActionContext.create(player, verification.session().getSessionId(),
                payload.proof().actionSequence(), hand, payload.source(), stack, result,
                verification.stopAfterAction(), true, offBeatDamagePercent);
        var rayProfile = DJRayWeaponManager.resolve(stack).orElse(null);
        if (rayProfile != null) {
            DJRayWeaponCombatService.fire(player, action, rayProfile);
        } else if (behavior == DJItemBehavior.CROSSBOW && stack.getItem() instanceof CrossbowItem crossbow) {
            ItemStack projectile = player.getProjectile(stack);
            if (projectile.isEmpty() && !player.hasInfiniteMaterials()) {
                verification.session().recordJudgmentMiss(payload.proof().actionSequence());
                stopAfterAction(player, verification);
                return;
            }
            ItemStack toLoad = projectile.isEmpty() ? new ItemStack(Items.ARROW) : projectile.copyWithCount(1);
            if (!projectile.isEmpty() && !player.hasInfiniteMaterials()) {
                projectile.shrink(1);
            }
            stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(List.of(toLoad)));
            DJCombatHandler.beginProjectileFire(player, action);
            try {
                crossbow.performShooting(player.level(), player, hand, stack, 3.15f, 1.0f, null);
            } finally {
                DJCombatHandler.endProjectileFire(player.getUUID());
            }
        } else {
            if (!executeDelegatedUse(player, action, stack, definition)) {
                verification.session().recordJudgmentMiss(payload.proof().actionSequence());
                stopAfterAction(player, verification);
                return;
            }
        }
        applyCooldown(player, action.stackSnapshot(), verification, true);
        stopAfterAction(player, verification);
    }

    public static void handleTridentFire(DJTridentFirePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = payload.source().resolveAtActionStart(player, payload.hand());
        DJItemBehaviorDefinition behavior = DJItemBehaviorManager.resolveDefinition(stack);
        Verification verification = DJJudgmentVerifier.verify(
                player, payload.proof(), behavior.disabledByCanAttack());
        if (verification.session() == null) {
            return;
        }
        boolean valid = verification.accepted()
                && DJItemBehaviorManager.is(stack, DJItemBehavior.TRIDENT)
                && !player.getCooldowns().isOnCooldown(stack.getItem())
                && stack.getDamageValue() < stack.getMaxDamage() - 1;
        HitResult result = valid ? verification.result() : HitResult.miss(payload.proof().clientTimeMs());
        recordMissIfNeeded(verification, payload.proof().actionSequence(), result);
        if (verification.accepted()) {
            applyCooldown(player, stack, verification, true);
        }
        int offBeatDamagePercent = verification.session().getOffBeatAttackDamagePercent();
        boolean damageAuthorized = DJAttackExecutionRules.canExecute(valid, result, offBeatDamagePercent);
        if (damageAuthorized) {
            if (verification.session().tryConsumeEnergy(DJItemCooldownManager.getUseEnergyCost(stack))) {
                DJActionContext action = DJActionContext.create(player, verification.session().getSessionId(),
                        payload.proof().actionSequence(), payload.hand(), payload.source(), stack, result,
                        verification.stopAfterAction(), true, offBeatDamagePercent);
                DJTridentCombatService.fire(player, action);
            } else {
                verification.session().sendResourceState();
            }
        }
        stopAfterAction(player, verification);
    }

    public static void handleMaceThrow(DJMaceThrowPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = payload.source().resolveAtActionStart(player, payload.hand());
        DJItemBehaviorDefinition behavior = DJItemBehaviorManager.resolveDefinition(stack);
        Verification verification = DJJudgmentVerifier.verify(
                player, payload.proof(), behavior.disabledByCanAttack());
        if (verification.session() == null) {
            return;
        }
        boolean valid = verification.accepted()
                && DJItemBehaviorManager.is(stack, DJItemBehavior.MACE)
                && !player.getCooldowns().isOnCooldown(stack.getItem());
        HitResult result = valid ? verification.result() : HitResult.miss(payload.proof().clientTimeMs());
        recordMissIfNeeded(verification, payload.proof().actionSequence(), result);
        if (verification.accepted()) {
            applyCooldown(player, stack, verification, true);
        }
        int offBeatDamagePercent = verification.session().getOffBeatAttackDamagePercent();
        boolean damageAuthorized = DJAttackExecutionRules.canExecute(valid, result, offBeatDamagePercent);
        if (damageAuthorized) {
            if (verification.session().tryConsumeEnergy(DJItemCooldownManager.getUseEnergyCost(stack))) {
                DJActionContext action = DJActionContext.create(player, verification.session().getSessionId(),
                        payload.proof().actionSequence(), payload.hand(), payload.source(), stack, result,
                        verification.stopAfterAction(), true, offBeatDamagePercent);
                DJMaceCombatService.throwMace(player, action);
            } else {
                verification.session().sendResourceState();
            }
        }
        stopAfterAction(player, verification);
    }

    public static void handleChargeStart(DJChargeStartPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = payload.source().resolveAtActionStart(player, payload.hand());
        DJItemBehaviorDefinition behavior = DJItemBehaviorManager.resolveDefinition(stack);
        Verification verification = DJJudgmentVerifier.verify(
                player, payload.proof(), behavior.disabledByCanAttack());
        if (verification.session() == null) {
            return;
        }
        boolean valid = verification.accepted() && !stack.isEmpty() && behavior.family().isCharge();
        HitResult result = valid ? verification.result() : HitResult.miss(payload.proof().clientTimeMs());
        recordMissIfNeeded(verification, payload.proof().actionSequence(), result);
        stopAfterAction(player, verification);
    }

    public static void handleAutoChargeStart(DJAutoChargeStartPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = payload.source().resolveAtActionStart(player, payload.hand());
        DJItemBehaviorDefinition definition = DJItemBehaviorManager.resolveDefinition(stack);
        Verification verification = DJJudgmentVerifier.verify(
                player, payload.proof(), definition.disabledByCanAttack());
        if (verification.session() == null) {
            return;
        }
        var profile = DJRayWeaponManager.resolve(stack).orElse(null);
        boolean validWeapon = !stack.isEmpty() && definition.family().isTrigger()
                && profile != null && profile.autoChargeBeats() > 0;
        boolean wasOnCooldown = validWeapon && player.getCooldowns().isOnCooldown(stack.getItem());
        HitResult result = verification.accepted() && validWeapon && !wasOnCooldown
                ? verification.result() : HitResult.miss(payload.proof().clientTimeMs());

        // A legal auto-charge weapon attempt consumes cooldown even when its proof is a Miss/rejected.
        if (validWeapon && !wasOnCooldown) {
            applyCooldown(player, stack, verification, true);
        }
        int offBeatDamagePercent = verification.session().getOffBeatAttackDamagePercent();
        boolean damageAuthorized = DJAttackExecutionRules.canExecute(
                verification.accepted() && validWeapon && !wasOnCooldown, result, offBeatDamagePercent);
        if (!result.isHit()) {
            recordMissIfNeeded(verification, payload.proof().actionSequence(), result);
        }
        var beats = verification.session().getTrackPack().timeline().combatLine();
        var schedule = damageAuthorized ? otto.djgun.djcraft.combat.DJAutoChargeTiming.schedule(
                payload.proof().clientTimeMs(), beats, profile.autoChargeBeats()) : null;
        double currentVirtualBeat = otto.djgun.djcraft.util.BeatGridUtil.getVirtualBeat(
                verification.session().getCurrentTimeMs(), beats);
        boolean schedulable = damageAuthorized
                && otto.djgun.djcraft.combat.DJAutoChargeTiming.isSchedulable(schedule, currentVirtualBeat,
                        verification.session().getTrackPack().getTotalDurationMs(),
                        verification.session().getTrackPack().getOffsetMs());
        if (!schedulable) {
            recordMissIfNeeded(verification, payload.proof().actionSequence(),
                    HitResult.miss(payload.proof().clientTimeMs()));
            stopAfterAction(player, verification);
            return;
        }
        if (!verification.session().tryConsumeEnergy(DJItemCooldownManager.getUseEnergyCost(stack))) {
            verification.session().sendResourceState();
            stopAfterAction(player, verification);
            return;
        }
        DJActionContext action = DJActionContext.create(player, verification.session().getSessionId(),
                payload.proof().actionSequence(), payload.hand(), payload.source(), stack, result,
                verification.stopAfterAction(), true, offBeatDamagePercent);
        if (!DJAutoChargeManager.begin(player, verification.session(), action, schedule.targetVirtualBeat())) {
            DJCraft.LOGGER.debug("Rejected stale auto charge for {}: sequence={}, targetVirtualBeat={}",
                    player.getName().getString(), payload.proof().actionSequence(), schedule.targetVirtualBeat());
        }
    }

    private static boolean executeDelegatedUse(ServerPlayer player, DJActionContext action, ItemStack stack,
            DJItemBehaviorDefinition behavior) {
        DJCombatHandler.beginProjectileFire(player, action);
        InteractionResultHolder<ItemStack> useResult;
        try {
            useResult = behavior.triggerExecutor()
                    .map(executor -> executor.execute(new DJTriggerBehaviorContext(
                            player, action.hand(), stack, action.result(), action.sequence())))
                    .orElseGet(() -> stack.use(player.level(), player, action.hand()));
        } catch (RuntimeException exception) {
            DJCraft.LOGGER.error("DJ trigger behavior {} failed for {}",
                    behavior.id(), player.getName().getString(), exception);
            return false;
        } finally {
            DJCombatHandler.endProjectileFire(player.getUUID());
        }
        if (useResult == null) {
            DJCraft.LOGGER.error("DJ trigger behavior {} returned null for {}",
                    behavior.id(), player.getName().getString());
            return false;
        }
        if (useResult.getObject() != stack) {
            action.source().replace(player, action.hand(), useResult.getObject());
        }
        return true;
    }

    private static void applyCooldown(ServerPlayer player, ItemStack stack, Verification verification,
            boolean useAction) {
        int beatCooldown = useAction
                ? DJItemCooldownManager.getUseBeatCooldown(stack)
                : DJItemCooldownManager.getBeatCooldown(stack);
        double waitBeats = Math.max(0, beatCooldown - 0.4);
        int ticks = BeatGridUtil.getDurationTicks(verification.session().getCurrentTimeMs(),
                verification.session().getTrackPack().timeline().combatLine(), waitBeats);
        if (ticks > 0) {
            player.getCooldowns().addCooldown(stack.getItem(), ticks);
        }
    }

    private static void stopAfterAction(ServerPlayer player, Verification verification) {
        if (verification.stopAfterAction()) {
            player.sendSystemMessage(Component.translatable("message.djcraft.clock_desync"));
            DJModeManager.getInstance().stopSession(player, StopReason.CLOCK_DESYNC);
            DJCraft.LOGGER.warn("Stopped DJ session for {} after five clock anomalies",
                    player.getName().getString());
        }
    }

    private static void recordMissIfNeeded(Verification verification, long actionSequence, HitResult result) {
        if (verification.accepted() && !result.isHit()) {
            verification.session().recordJudgmentMiss(actionSequence);
        }
    }
}
