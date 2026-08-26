package otto.djgun.djcraft.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;
import otto.djgun.djcraft.combat.client.DJClientItemCooldowns;
import otto.djgun.djcraft.init.ModItemTags;
import otto.djgun.djcraft.init.ModItems;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;
import otto.djgun.djcraft.client.animation.DJAnimationRuntime;
import otto.djgun.djcraft.client.animation.DJAnimationEvent;
import otto.djgun.djcraft.client.animation.DJAnimationSwitchRules;
import otto.djgun.djcraft.sound.DJActionOutcome;

import java.math.BigDecimal;
import java.util.Optional;

@EventBusSubscriber(modid = DJCraft.MODID, value = Dist.CLIENT)
public class DJClientEvents {

    /**
     * 追踪上一帧主手持有的物品类型
     */
    private static Item lastMainHandItem = null;
    private static ItemStack lastMainHandStack = ItemStack.EMPTY;

    /**
     * 追踪上一帧副手持有的物品类型
     */
    private static Item lastOffHandItem = null;
    private static ItemStack lastOffHandStack = ItemStack.EMPTY;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUseItem(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        InteractionHand hand = event.getHand();
        DJSessionClient session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (player == null || hand == null || session == null || player.isUsingItem()) {
            return;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || hasDedicatedUseAnimation(stack.getItem())) {
            return;
        }
        DJAnimationRuntime.getInstance().emit(
                DJAnimationEvent.Kind.USE, hand, stack, session, 1.0, DJActionOutcome.NOT_JUDGED);
    }

    private static boolean hasDedicatedUseAnimation(Item item) {
        return DJItemBehaviorManager.resolve(item) != DJItemBehavior.NONE;
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
            lastMainHandStack = ItemStack.EMPTY;
            lastOffHandItem = null;
            lastOffHandStack = ItemStack.EMPTY;
            DJClientItemCooldowns.clear();
            DJAnimationRuntime.getInstance().reset();
            otto.djgun.djcraft.client.sound.DJWeaponSoundRuntime.getInstance().reset();
            return;
        }
        DJSessionClient session = sessionOpt.get();

        // ── 主手：检测物品变化，施加切换前摇 ──
        ItemStack currentStack = player.getMainHandItem();
        Item currentItem = currentStack.isEmpty() ? null : currentStack.getItem();
        ItemStack outgoingStack = lastMainHandStack;

        if (currentItem != null && currentItem != lastMainHandItem) {
            // 如果新出现在主手的物品就是上一帧的副手物品（F键换手场景），
            // 说明是从副手换过来的，按冷却节拍处理而非前摇节拍。
            if (currentItem == lastOffHandItem) {
                applyHandSwapCooldown(player, outgoingStack, currentStack, InteractionHand.MAIN_HAND, session);
            } else {
                applySwitchWarmup(player, outgoingStack, currentStack, InteractionHand.MAIN_HAND, session);
            }
        } else if (currentItem == null && lastMainHandItem != null
                && !DJAnimationSwitchRules.isInstantRemoval(player, outgoingStack, currentStack)) {
            applySwitchOut(outgoingStack, InteractionHand.MAIN_HAND, session);
        }

        lastMainHandItem = currentItem;
        lastMainHandStack = currentStack.copy();

        // ── 副手：检测物品变化，施加冷却节拍（不是前摇节拍） ──
        ItemStack offhandStack = player.getOffhandItem();
        Item offhandItem = offhandStack.isEmpty() ? null : offhandStack.getItem();

        if (offhandItem != lastOffHandItem) {
            if (offhandItem == null) {
                if (!DJAnimationSwitchRules.isInstantRemoval(player, lastOffHandStack, offhandStack)) {
                    applySwitchOut(lastOffHandStack, InteractionHand.OFF_HAND, session);
                }
            } else {
                applySwitchWarmup(player, lastOffHandStack, offhandStack, InteractionHand.OFF_HAND, session);
            }
        }
        if (offhandItem != null && offhandItem != lastOffHandItem) {
            applyBeatCooldownWithoutAnimation(player, offhandStack, session);
        }

        lastOffHandItem = offhandItem;
        lastOffHandStack = offhandStack.copy();
    }

    // ─────────────────────────────────────────────────────────
    // 内部方法
    // ─────────────────────────────────────────────────────────

    private static void applyHandSwapCooldown(Player player, ItemStack outgoingStack, ItemStack incomingStack,
            InteractionHand hand, DJSessionClient session) {
        int beats = DJItemCooldownManager.getBeatCooldown(incomingStack);
        int warmupBeats = DJItemCooldownManager.getSwitchWarmup(incomingStack);
        if (warmupBeats > 0) {
            DJAnimationRuntime.getInstance().scheduleSwitch(
                    hand, outgoingStack, incomingStack, session, warmupBeats);
        }
        applyCooldownBeats(player, incomingStack, session, Math.max(0, beats - 0.4));
    }

    private static void applyBeatCooldownWithoutAnimation(Player player, ItemStack stack, DJSessionClient session) {
        int beats = DJItemCooldownManager.getBeatCooldown(stack);
        applyCooldownBeats(player, stack, session, Math.max(0, beats - 0.4));
    }

    private static void applySwitchWarmup(Player player, ItemStack outgoingStack, ItemStack incomingStack,
            InteractionHand hand, DJSessionClient session) {
        int warmupBeats = DJItemCooldownManager.getSwitchWarmup(incomingStack);
        if (warmupBeats <= 0)
            return;

        DJAnimationRuntime.getInstance().scheduleSwitch(
                hand, outgoingStack, incomingStack, session, warmupBeats);

        double actualWarmupBeats = Math.max(0, warmupBeats - 0.4);

        DJClientItemCooldowns.applyIfLonger(player, incomingStack, session, actualWarmupBeats);
    }

    private static void applySwitchOut(ItemStack outgoingStack, InteractionHand hand, DJSessionClient session) {
        int warmupBeats = DJItemCooldownManager.getSwitchWarmup(outgoingStack);
        if (warmupBeats > 0) {
            DJAnimationRuntime.getInstance().scheduleSwitch(
                    hand, outgoingStack, ItemStack.EMPTY, session, warmupBeats);
        }
    }

    private static void applyCooldownBeats(Player player, ItemStack stack, DJSessionClient session, double beats) {
        DJClientItemCooldowns.apply(player, stack, session, beats);
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty())
            return;

        if (stack.is(ModItems.FLOWERY.get())) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.djcraft.flowery_dash",
                    Component.literal("Jarona").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
        }
        if (stack.is(ModItems.DJ_CRAFTING_TABLE.get())) {
            event.getToolTip().add(Component.translatable("tooltip.djcraft.dj_crafting_table_usage")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.PORTABLE_JUKEBOX.get())) {
            event.getToolTip().add(Component.translatable("tooltip.djcraft.portable_jukebox_usage")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItemTags.SWIFT)) {
            event.getToolTip().add(Component.translatable("tooltip.djcraft.item_tag.swift")
                    .withStyle(ChatFormatting.GREEN));
        }
        if (stack.is(ModItemTags.SMASH)) {
            event.getToolTip().add(Component.translatable("tooltip.djcraft.item_tag.smash")
                    .withStyle(ChatFormatting.RED));
        }

        int beatCooldown = DJItemCooldownManager.getBeatCooldown(stack);
        int useBeatCooldown = DJItemCooldownManager.getUseBeatCooldown(stack);
        int switchWarmup = DJItemCooldownManager.getSwitchWarmup(stack);
        double attackEnergyCost = DJItemCooldownManager.getAttackEnergyCost(stack);
        double useEnergyCost = DJItemCooldownManager.getUseEnergyCost(stack);

        event.getToolTip().add(Component.translatable("tooltip.djcraft.beat_cooldown", beatCooldown)
                .withStyle(ChatFormatting.YELLOW));

        if (DJItemCooldownManager.hasExplicitUseBeatCooldown(stack)) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.djcraft.use_beat_cooldown", useBeatCooldown)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }

        if (switchWarmup != beatCooldown) {
            event.getToolTip().add(Component.translatable("tooltip.djcraft.switch_warmup", switchWarmup)
                    .withStyle(ChatFormatting.GOLD));
        }
        if (attackEnergyCost > 0.0) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.djcraft.attack_energy_cost", formatEnergyCost(attackEnergyCost))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (useEnergyCost > 0.0) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.djcraft.use_energy_cost", formatEnergyCost(useEnergyCost))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private static String formatEnergyCost(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
