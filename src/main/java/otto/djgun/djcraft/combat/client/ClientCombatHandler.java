package otto.djgun.djcraft.combat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.combat.BeatJudgeUtil;
import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.hud.DJCrosshairRenderer;
import otto.djgun.djcraft.network.packet.DJAttackClientPayload;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;

/**
 * 客户端攻击输入监听器
 *
 * 用途：
 * 1. 在玩家点击攻击时，利用客户端 OpenAL 精确时间做节拍判定
 * 2. 立即将结果通知 DJCrosshairRenderer 显示颜色闪烁效果（零延迟视觉反馈）
 * 3. 发送 DJAttackClientPayload 到服务端，让服务端直接信任此判定结果
 *
 * 重要：本类不取消原版攻击，不负责实际伤害——那由服务端的
 * DJCombatHandler (LivingIncomingDamageEvent) 完成。
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

        // 只在 DJ 模式中触发
        if (!DJModeManagerClient.getInstance().isInDJMode())
            return;
        var optSession = DJModeManagerClient.getInstance().getSession();
        if (optSession.isEmpty())
            return;
        DJSessionClient session = optSession.get();

        if (Minecraft.getInstance().player == null)
            return;

        net.minecraft.world.entity.player.Player player = Minecraft.getInstance().player;
        net.minecraft.world.item.ItemStack stack = player.getMainHandItem();

        // 如果物品处于冷却中（或切换前摇中），不触发攻击判定和准星动画
        if (!stack.isEmpty() && player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }

        // 使用 OpenAL 精确时间做本地判定
        HitResult result = BeatJudgeUtil.judge(session);

        // 1. 立即通知渲染器做视觉反馈（零延迟）
        DJCrosshairRenderer.notifyJudgment(result.isHit(), result.beatEvent(), session.getCurrentTimeMs());

        // 2. 发送判定结果到服务端，让服务端信任此结果
        PacketDistributor.sendToServer(new DJAttackClientPayload(
                result.isHit(),
                result.damageModifier(),
                session.getCurrentTimeMs(),
                0, // beatIndex 暂时不使用
                InteractionHand.MAIN_HAND));

        // 注意：不 cancel 事件，让原版攻击流程和冷却系统正常运行
    }
}
