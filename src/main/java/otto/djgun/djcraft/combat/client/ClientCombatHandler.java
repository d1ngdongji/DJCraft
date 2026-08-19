package otto.djgun.djcraft.combat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.combat.DJActionSource;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;
import otto.djgun.djcraft.combat.DJMeleeBehaviorResolver;
import otto.djgun.djcraft.api.combat.DJAreaMeleeBehavior;
import otto.djgun.djcraft.network.packet.DJAttackClientPayload;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;
import otto.djgun.djcraft.client.animation.DJAnimationRuntime;
import otto.djgun.djcraft.client.animation.DJAnimationSemantic;
import otto.djgun.djcraft.sound.DJActionOutcome;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.combat.DJMiningRules;
import otto.djgun.djcraft.combat.DJSweptMeleeVolume;
import otto.djgun.djcraft.combat.access.DJThrownTridentExtension;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.network.packet.DJMiningPayload;

/**
 * 客户端攻击输入监听器
 *
 * 用途：
 * 1. 在玩家点击攻击时，利用客户端 OpenAL 精确时间做节拍判定
 * 2. 立即将结果通知 DJCrosshairRenderer 显示颜色闪烁效果（零延迟视觉反馈）
 * 3. 发送 DJAttackClientPayload 到服务端，由服务端做宽松自洽核验并读取服务端倍率
 *
 * 重要：实体近战会取消独立的原版客户端攻击包，并把目标放进同一个 DJ payload；
 * 服务端验证后仍通过 Player#attack 和 DJCombatHandler 完成原版伤害流程。
 *
 * 注册方式：在 DJCraftClient 构造函数中手动注册到 NeoForge.EVENT_BUS
 */
@OnlyIn(Dist.CLIENT)
public class ClientCombatHandler {

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        // 只处理攻击按键（主手）
        if (!event.isAttack())
            return;

        var optSession = DJModeManagerClient.getInstance().getActiveSession();
        if (optSession.isEmpty())
            return;
        DJSessionClient session = optSession.get();

        if (Minecraft.getInstance().player == null)
            return;

        net.minecraft.world.entity.player.Player player = Minecraft.getInstance().player;
        var capturedAction = DJClientActionCapture.consumeAttack(player);
        if (Minecraft.getInstance().hitResult instanceof BlockHitResult blockHit
                && blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            var blockState = player.level().getBlockState(blockHit.getBlockPos());
            var miningStack = player.getMainHandItem();
            boolean miningIntent = DJMiningRules.isMiningIntent(miningStack, blockState);
            if (!capturedAction.physicalPress()) {
                if (!miningIntent) {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                }
                return;
            }
            if (miningIntent) {
                HitResult result = BeatJudgeFacade.judgeAndNotify(session, DJItemBehaviorRegistry.MINING);
                var proof = DJClientJudgmentProofs.createNonOffensive(session, result);
                DJAnimationRuntime.getInstance().emitVisualOnly(
                        DJAnimationSemantic.MELEE_STRIKE, InteractionHand.MAIN_HAND,
                        miningStack, session,
                        DJAnimationSemantic.MELEE_STRIKE.defaultDurationBeats(),
                        DJActionOutcome.judged(result, false));
                PacketDistributor.sendToServer(new DJMiningPayload(proof, blockHit.getBlockPos()));
                return;
            }
        }

        net.minecraft.world.item.ItemStack stack = capturedAction.stack();
        DJActionSource actionSource = capturedAction.source();
        if (stack.isEmpty()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (actionSource.slot() != player.getInventory().selected) {
            DJCraft.LOGGER.debug("Captured pre-switch DJ attack source for {}: sourceSlot={}, currentSlot={}, item={}",
                    player.getName().getString(), actionSource.slot(), player.getInventory().selected,
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }

        DJItemBehavior itemBehavior = DJItemBehaviorManager.resolve(stack);
        var meleeBehavior = DJMeleeBehaviorResolver.resolveExecutor(stack);
        if (meleeBehavior instanceof DJAreaMeleeBehavior areaBehavior) {
            handleAreaMelee(event, player, stack, actionSource, session, areaBehavior,
                    itemBehavior == DJItemBehavior.TRIDENT,
                    itemBehavior == DJItemBehavior.TRIDENT
                            ? otto.djgun.djcraft.client.animation.DJAnimationEvent.Kind.MELEE_THRUST
                            : otto.djgun.djcraft.client.animation.DJAnimationEvent.Kind.MELEE_STRIKE);
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(false);

        // 如果物品处于冷却中（或切换前摇中），不触发攻击判定和准星动画
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (session.getEnergy() < DJItemCooldownManager.getAttackEnergyCost(stack)) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        HitResult result = BeatJudgeFacade.judgeMeleeAttackAndNotify(session, stack);
        var proof = DJClientJudgmentProofs.create(session, result);
        DJAnimationRuntime.getInstance().emit(
                DJMeleeAttackClassifier.classify(player, Minecraft.getInstance().hitResult),
                InteractionHand.MAIN_HAND, stack, session, DJItemCooldownManager.getBeatCooldown(stack),
                DJActionOutcome.judged(result, true), proof.actionSequence(), result.judgedAtMs(), result.beatIndex());
        PacketDistributor.sendToServer(new DJAttackClientPayload(
                proof, InteractionHand.MAIN_HAND,
                actionSource));
        DJTriggerWeaponHelper.applyCooldownBeats(player, stack, session,
                Math.max(0.0, DJItemCooldownManager.getBeatCooldown(stack) - 0.4));

    }

    private static void handleAreaMelee(InputEvent.InteractionKeyMappingTriggered event,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack stack,
            DJActionSource actionSource,
            DJSessionClient session,
            DJAreaMeleeBehavior areaBehavior,
            boolean predictTridentRedirect,
            otto.djgun.djcraft.client.animation.DJAnimationEvent.Kind animationKind) {
        event.setCanceled(true);
        event.setSwingHand(false);
        if (player.getCooldowns().isOnCooldown(stack.getItem())
                || session.getEnergy() < DJItemCooldownManager.getAttackEnergyCost(stack)) {
            return;
        }

        HitResult result = BeatJudgeFacade.judgeMeleeAttackAndNotify(session, stack);
        var proof = DJClientJudgmentProofs.create(session, result);
        DJAnimationRuntime.getInstance().emit(
                animationKind,
                InteractionHand.MAIN_HAND, stack, session, DJItemCooldownManager.getBeatCooldown(stack),
                DJActionOutcome.judged(result, true), proof.actionSequence(), result.judgedAtMs(), result.beatIndex());
        if (predictTridentRedirect && result.isHit()) {
            predictTridentRedirects(player, areaBehavior);
        }
        PacketDistributor.sendToServer(new DJAttackClientPayload(proof, InteractionHand.MAIN_HAND,
                actionSource));
        DJTriggerWeaponHelper.applyCooldownBeats(player, stack, session,
                Math.max(0.0, DJItemCooldownManager.getBeatCooldown(stack) - 0.4));
    }

    private static void predictTridentRedirects(net.minecraft.world.entity.player.Player player,
            DJAreaMeleeBehavior behavior) {
        var volume = DJSweptMeleeVolume.create(
                player.getEyePosition(), player.getEyePosition(), player.getLookAngle(), behavior);
        player.level().getEntities(player, volume.bounds(), target -> target instanceof ThrownTrident trident
                        && trident instanceof DJThrownTridentExtension extension
                        && extension.djcraft$canBeRedirected()
                        && volume.intersects(trident.getBoundingBox())
                        && player.hasLineOfSight(trident))
                .forEach(target -> ((DJThrownTridentExtension) target).djcraft$tryRedirect(player));
    }
}
