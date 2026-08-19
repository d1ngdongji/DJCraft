package otto.djgun.djcraft.network.server;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.combat.DJJudgmentVerifier;
import otto.djgun.djcraft.combat.DJMiningRules;
import otto.djgun.djcraft.network.packet.DJEatingPayload;
import otto.djgun.djcraft.network.packet.DJMiningPayload;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.session.DJModeManager;

/** Server-authoritative execution for successful utility-action judgments. */
public final class DJUtilityActionRequestHandler {
    private static final long EATING_START_GRACE_TICKS = 5L;
    private static final Map<UUID, PendingEating> PENDING_EATING = new HashMap<>();
    private static final Map<UUID, MiningBeat> LAST_MINING_BEAT = new HashMap<>();

    private DJUtilityActionRequestHandler() {
    }

    public static void handleMining(DJMiningPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        var verification = DJJudgmentVerifier.verify(
                player, payload.proof(), DJItemBehaviorRegistry.MINING.disabledByCanAttack());
        if (verification.session() == null || !verification.accepted()) {
            return;
        }

        var state = player.level().getBlockState(payload.pos());
        ItemStack tool = player.getMainHandItem();
        if (verification.result().isHit() && !state.isAir()
                && player.canInteractWithBlock(payload.pos(), 1.0)
                && player.level().mayInteract(player, payload.pos())
                && DJMiningRules.isMiningIntent(tool, state)) {
            MiningBeat miningBeat = new MiningBeat(
                    verification.session().getSessionId(), verification.result().beatIndex());
            if (!miningBeat.equals(LAST_MINING_BEAT.get(player.getUUID()))
                    && player.gameMode.destroyBlock(payload.pos())) {
                LAST_MINING_BEAT.put(player.getUUID(), miningBeat);
            }
        }
        stopAfterAction(player, verification);
    }

    public static void handleEating(DJEatingPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        var verification = DJJudgmentVerifier.verify(
                player, payload.proof(), DJItemBehaviorRegistry.EATING.disabledByCanAttack());
        if (verification.session() == null || !verification.accepted()) {
            return;
        }

        if (verification.result().isHit()) {
            ItemStack expectedStack = player.getItemInHand(payload.hand());
            if (expectedStack.getFoodProperties(player) != null) {
                PENDING_EATING.put(player.getUUID(), new PendingEating(
                        payload.hand(), expectedStack.getItem(),
                        player.level().getGameTime() + EATING_START_GRACE_TICKS));
            }
        }
        stopAfterAction(player, verification);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, PendingEating>> iterator = PENDING_EATING.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            PendingEating pending = entry.getValue();
            if (player.level().getGameTime() > pending.expiresAtGameTime()) {
                iterator.remove();
                continue;
            }
            if (!player.isUsingItem()) {
                continue;
            }
            if (player.getUsedItemHand() != pending.hand()
                    || player.getUseItem().getItem() != pending.item()) {
                iterator.remove();
                continue;
            }

            finishEating(player, pending.hand());
            iterator.remove();
        }
    }

    public static void cleanupPlayer(UUID playerId) {
        PENDING_EATING.remove(playerId);
        LAST_MINING_BEAT.remove(playerId);
    }

    public static void clear() {
        PENDING_EATING.clear();
        LAST_MINING_BEAT.clear();
    }

    private static void finishEating(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getUseItem();
        var food = stack.getFoodProperties(player);
        if (food == null || !player.canEat(food.canAlwaysEat())) {
            return;
        }
        ItemStack original = stack.copy();
        ItemStack finished = EventHooks.onItemUseFinish(
                player, original, 0, stack.finishUsingItem(player.level(), player));
        if (finished != stack) {
            player.setItemInHand(hand, finished);
        }
        player.stopUsingItem();
    }

    private static void stopAfterAction(ServerPlayer player, DJJudgmentVerifier.Verification verification) {
        if (!verification.stopAfterAction()) {
            return;
        }
        player.sendSystemMessage(Component.translatable("message.djcraft.clock_desync"));
        DJModeManager.getInstance().stopSession(player, StopReason.CLOCK_DESYNC);
        DJCraft.LOGGER.warn("Stopped DJ session for {} after five clock anomalies",
                player.getName().getString());
    }

    private record PendingEating(InteractionHand hand, Item item, long expiresAtGameTime) {
    }

    private record MiningBeat(long sessionId, int beatIndex) {
    }
}
