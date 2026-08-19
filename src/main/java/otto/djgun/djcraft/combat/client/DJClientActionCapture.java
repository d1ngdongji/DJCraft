package otto.djgun.djcraft.combat.client;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import otto.djgun.djcraft.combat.DJActionSource;
import otto.djgun.djcraft.session.DJModeManagerClient;

/** Captures the main-hand source at physical input time, before vanilla applies hotbar number keys. */
@OnlyIn(Dist.CLIENT)
public final class DJClientActionCapture {
    private static final long MAX_AGE_NANOS = TimeUnit.MILLISECONDS.toNanos(500L);
    private static final long MAX_AGE_TICKS = 2L;
    private static Pending attack;
    private static Pending use;

    private DJClientActionCapture() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_PRESS
                && minecraft.options.keyAttack.matchesMouse(event.getButton())) {
            captureAttack(minecraft);
        }
        if (event.getAction() == GLFW.GLFW_PRESS
                && minecraft.options.keyUse.matchesMouse(event.getButton())) {
            captureUse(minecraft);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_PRESS
                && minecraft.options.keyAttack.matches(event.getKey(), event.getScanCode())) {
            captureAttack(minecraft);
        }
        if (event.getAction() == GLFW.GLFW_PRESS
                && minecraft.options.keyUse.matches(event.getKey(), event.getScanCode())) {
            captureUse(minecraft);
        }
    }

    public static CapturedAction consumeAttack(Player player) {
        Pending pending = attack;
        attack = null;
        boolean physicalPress = isUsable(pending, player);
        if (physicalPress) {
            ItemStack liveStack = pending.source.resolve(player, InteractionHand.MAIN_HAND);
            if (!liveStack.isEmpty()) {
                return new CapturedAction(pending.source, liveStack, true);
            }
        }
        ItemStack current = player.getMainHandItem();
        return new CapturedAction(
                DJActionSource.capture(player, InteractionHand.MAIN_HAND, current), current, physicalPress);
    }

    public static void clear() {
        attack = null;
        use = null;
    }

    private static void captureAttack(Minecraft minecraft) {
        attack = captureMainHand(minecraft);
    }

    private static void captureUse(Minecraft minecraft) {
        use = captureMainHand(minecraft);
    }

    public static CapturedAction consumeUseIf(Player player, InteractionHand hand,
            Predicate<ItemStack> matcher) {
        if (hand == InteractionHand.MAIN_HAND) {
            Pending pending = use;
            if (isUsable(pending, player)) {
                ItemStack liveStack = pending.source.resolve(player, hand);
                if (!liveStack.isEmpty() && matcher.test(liveStack)) {
                    use = null;
                    return new CapturedAction(pending.source, liveStack, true);
                }
                return null;
            }
            use = null;
        }
        ItemStack current = player.getItemInHand(hand);
        return matcher.test(current)
                ? new CapturedAction(DJActionSource.capture(player, hand, current), current, false)
                : null;
    }

    private static Pending captureMainHand(Minecraft minecraft) {
        Player player = minecraft.player;
        if (player == null || minecraft.screen != null
                || DJModeManagerClient.getInstance().getActiveSession().isEmpty()) {
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        return new Pending(player.getUUID(),
                DJActionSource.capture(player, InteractionHand.MAIN_HAND, stack),
                player.level().getGameTime(), System.nanoTime());
    }

    private static boolean isUsable(Pending pending, Player player) {
        if (pending == null || !pending.playerId.equals(player.getUUID())) {
            return false;
        }
        long tickAge = player.level().getGameTime() - pending.gameTime;
        long nanoAge = System.nanoTime() - pending.capturedAtNanos;
        return tickAge >= 0L && tickAge <= MAX_AGE_TICKS
                && nanoAge >= 0L && nanoAge <= MAX_AGE_NANOS;
    }

    public record CapturedAction(DJActionSource source, ItemStack stack, boolean physicalPress) {
    }

    private record Pending(UUID playerId, DJActionSource source, long gameTime, long capturedAtNanos) {
    }
}
