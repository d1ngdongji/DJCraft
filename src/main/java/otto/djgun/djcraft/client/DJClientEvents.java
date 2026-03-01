package otto.djgun.djcraft.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(modid = DJCraft.MODID, value = Dist.CLIENT)
public class DJClientEvents {

    /**
     * 追踪上一帧主手持有的物品类型
     */
    private static Item lastMainHandItem = null;

    /**
     * 记录每个物品冷却的**结束 Session 时间**（毫秒）。
     * 使用 session 时间而非系统时间，以确保在游戏暂停或切歌时冷却能够正确同步。
     */
    private static final Map<Item, Long> cooldownEndSessionMs = new HashMap<>();

    // ─────────────────────────────────────────────────────────
    // 攻击拦截：在 DJ 模式下，冷却中的攻击会被取消；攻击成功后应用节拍冷却
    // ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;
        Player player = mc.player;

        Optional<DJSessionClient> sessionOpt = DJModeManagerClient.getInstance().getSession();
        if (sessionOpt.isEmpty() || !sessionOpt.get().isPlaying())
            return;
        DJSessionClient session = sessionOpt.get();

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty())
            return;

        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        applyBeatCooldown(player, stack, session);
    }

    // ─────────────────────────────────────────────────────────
    // 客户端 Tick：检测物品切换（包括捡起），应用切换前摇
    // ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;
        Player player = mc.player;

        Optional<DJSessionClient> sessionOpt = DJModeManagerClient.getInstance().getSession();
        if (sessionOpt.isEmpty() || !sessionOpt.get().isPlaying()) {
            lastMainHandItem = null;
            cooldownEndSessionMs.clear(); // 退出时清理状态
            return;
        }
        DJSessionClient session = sessionOpt.get();

        ItemStack currentStack = player.getMainHandItem();
        Item currentItem = currentStack.isEmpty() ? null : currentStack.getItem();

        // 捡起补丁：只要当前物品不是 null 且与上一帧不同，就触发。
        if (currentItem != null && currentItem != lastMainHandItem) {
            applySwitchWarmup(player, currentStack, session);
        }

        lastMainHandItem = currentItem;
    }

    // ─────────────────────────────────────────────────────────
    // 第一人称渲染：手持物品随节拍上下摆动 (待机动画)
    // ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Optional<DJSessionClient> sessionOpt = DJModeManagerClient.getInstance().getSession();
        if (sessionOpt.isEmpty() || !sessionOpt.get().isPlaying())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused())
            return;

        float fraction = getBeatFraction(sessionOpt.get());
        // 利用 cos 曲线生成上下摆动：fraction 为 0 (正踩在节拍上) 时 cos 值为 1，乘以 -0.035f 达到最低点
        // fraction 为 0.5 时达到最高点 (0.035f)
        float offsetY = -0.035f * (float) Math.cos(fraction * Math.PI * 2);

        event.getPoseStack().translate(0.0, offsetY, 0.0);
    }

    private static float getBeatFraction(DJSessionClient session) {
        long current = session.getCurrentTimeMs();
        java.util.List<otto.djgun.djcraft.data.BeatEvent> beats = session.getTrackPack().timeline().combatLine();
        if (beats == null || beats.isEmpty())
            return 0f;

        // 使用 BeatGridUtil 计算当前的 virtual beat
        double virtualBeat = otto.djgun.djcraft.util.BeatGridUtil.getVirtualBeat(current, beats);
        // 取小数部分作为节拍间的进度 [0, 1)
        return (float) (virtualBeat - Math.floor(virtualBeat));
    }

    // ─────────────────────────────────────────────────────────
    // 内部方法
    // ─────────────────────────────────────────────────────────

    private static void applyBeatCooldown(Player player, ItemStack stack, DJSessionClient session) {
        int n = DJItemCooldownManager.getBeatCooldown(stack);
        double waitBeats = Math.max(0, n - 0.4);
        applyCooldownBeats(player, stack, session, waitBeats);
    }

    private static void applySwitchWarmup(Player player, ItemStack stack, DJSessionClient session) {
        int warmupBeats = DJItemCooldownManager.getSwitchWarmup(stack);
        if (warmupBeats <= 0)
            return;

        double actualWarmupBeats = Math.max(0, warmupBeats - 0.4);

        long now = session.getCurrentTimeMs();
        long targetTimeMs = session.getCompletionTimeMs(actualWarmupBeats);
        long warmupMs = targetTimeMs - now;
        if (warmupMs <= 0)
            return;

        long existingRemainingMs = getTrackedRemainingMs(stack.getItem(), now);

        // 如果前摇时长 > 当前已有冷却剩余，则覆盖。
        if (warmupMs > existingRemainingMs) {
            int warmupTicks = (int) (warmupMs / 50.0);
            if (warmupTicks > 0) {
                setCooldown(player, stack.getItem(), warmupTicks, targetTimeMs);
            }
        }
    }

    private static void applyCooldownBeats(Player player, ItemStack stack, DJSessionClient session, double beats) {
        long now = session.getCurrentTimeMs();
        long targetTimeMs = session.getCompletionTimeMs(beats);
        long durationMs = targetTimeMs - now;

        if (durationMs > 0) {
            int ticks = (int) (durationMs / 50.0);
            if (ticks > 0) {
                setCooldown(player, stack.getItem(), ticks, targetTimeMs);
            }
        }
    }

    private static void setCooldown(Player player, Item item, int ticks, long sessionEndTimeMs) {
        player.getCooldowns().addCooldown(item, ticks);
        cooldownEndSessionMs.put(item, sessionEndTimeMs);
    }

    private static long getTrackedRemainingMs(Item item, long sessionNowMs) {
        Long endTime = cooldownEndSessionMs.get(item);
        if (endTime == null)
            return 0L;
        return Math.max(0L, endTime - sessionNowMs);
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty())
            return;

        int beatCooldown = DJItemCooldownManager.getBeatCooldown(stack);
        int switchWarmup = DJItemCooldownManager.getSwitchWarmup(stack);

        event.getToolTip().add(Component.translatable("tooltip.djcraft.beat_cooldown", beatCooldown)
                .withStyle(ChatFormatting.YELLOW));

        if (switchWarmup != beatCooldown) {
            event.getToolTip().add(Component.translatable("tooltip.djcraft.switch_warmup", switchWarmup)
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
