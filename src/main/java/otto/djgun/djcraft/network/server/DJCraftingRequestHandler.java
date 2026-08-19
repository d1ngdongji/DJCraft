package otto.djgun.djcraft.network.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.init.ModBlocks;
import otto.djgun.djcraft.init.ModDataComponents;
import otto.djgun.djcraft.init.ModItems;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.DJCraftingSelectTrackPayload;

public final class DJCraftingRequestHandler {
    private static final double MAX_DISTANCE_SQUARED = 64.0;

    private DJCraftingRequestHandler() {
    }

    public static void handleSelectTrack(DJCraftingSelectTrackPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        boolean validTable = player.level().getBlockState(payload.tablePos()).is(ModBlocks.DJ_CRAFTING_TABLE.get());
        boolean nearby = player.position().distanceToSqr(Vec3.atCenterOf(payload.tablePos())) <= MAX_DISTANCE_SQUARED;
        var stack = player.getItemInHand(payload.hand());
        boolean validTrack = TrackPackManager.getInstance().isPackLoaded(payload.trackId());
        String expectedHash = TrackPackManager.getInstance().getContentHash(payload.trackId()).orElse(null);
        boolean clientVerified = ClientTrackStatusService.isVerified(
                player, payload.trackId(), expectedHash);
        boolean blankDisc = stack.is(ModItems.EMPTY_DISC.get())
                && stack.get(ModDataComponents.TRACK_PACK_ID.get()) == null;
        if (!validTable || !nearby || !blankDisc || !validTrack || !clientVerified) {
            player.displayClientMessage(Component.translatable("message.djcraft.crafting_invalid"), true);
            DJCraft.LOGGER.warn("Rejected invalid DJ crafting request from {}", player.getName().getString());
            return;
        }

        stack.set(ModDataComponents.TRACK_PACK_ID.get(), payload.trackId());
        stack.set(ModDataComponents.DISC_ID.get(), java.util.UUID.randomUUID());
        stack.set(ModDataComponents.DISC_STATISTICS.get(), otto.djgun.djcraft.data.DiscStatistics.EMPTY);
        player.displayClientMessage(Component.translatable("message.djcraft.crafting_success", payload.trackId()), true);
    }
}
