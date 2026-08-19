package otto.djgun.djcraft.network.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import otto.djgun.djcraft.playback.DJPlaybackMode;
import otto.djgun.djcraft.network.packet.DJGroupControlPayload;
import otto.djgun.djcraft.network.packet.DJGroupCreatePayload;
import otto.djgun.djcraft.network.packet.DJGroupInvitePayload;
import otto.djgun.djcraft.network.packet.DJGroupInviteResponsePayload;
import otto.djgun.djcraft.network.packet.DJGroupReadyPayload;
import otto.djgun.djcraft.session.DJNetworkGroupManager;

public final class DJGroupRequestHandler {
    private DJGroupRequestHandler() {
    }

    public static void handleCreate(DJGroupCreatePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DJNetworkGroupManager.getInstance().create(player, payload.jukeboxSlot());
        }
    }

    public static void handleInvite(DJGroupInvitePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerPlayer target = player.getServer().getPlayerList().getPlayer(payload.targetPlayerId());
        if (target != null && target != player) {
            DJNetworkGroupManager.getInstance().invite(player, target);
        }
    }

    public static void handleInviteResponse(DJGroupInviteResponsePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DJNetworkGroupManager.getInstance().respond(player, payload.groupId(), payload.accepted());
        }
    }

    public static void handleReady(DJGroupReadyPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DJNetworkGroupManager.getInstance().setReady(player, payload.groupId(),
                    payload.ready(), payload.detail());
        }
    }

    public static void handleControl(DJGroupControlPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance()
                .isParticipant(player.getUUID())) {
            return;
        }
        DJNetworkGroupManager manager = DJNetworkGroupManager.getInstance();
        switch (payload.action()) {
            case PLAY_INDEX -> manager.play(player, payload.value());
            case STOP -> manager.stopPlayback(player);
            case SET_MODE -> {
                if (payload.value() >= 0 && payload.value() < DJPlaybackMode.values().length) {
                    manager.setMode(player, DJPlaybackMode.values()[payload.value()]);
                }
            }
            case RETRY -> manager.retry(player);
            case LEAVE -> manager.leave(player, false);
            case DISBAND -> manager.leave(player, true);
        }
    }
}
