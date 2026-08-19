package otto.djgun.djcraft.network.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import otto.djgun.djcraft.cybergrind.CyberGrindManager;
import otto.djgun.djcraft.network.packet.CyberGrindExitPayload;
import otto.djgun.djcraft.network.packet.CyberGrindReadyPayload;
import otto.djgun.djcraft.network.packet.CyberGrindStartPayload;

public final class CyberGrindRequestHandler {
    private CyberGrindRequestHandler() {
    }

    public static void handleStart(CyberGrindStartPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CyberGrindManager.getInstance().prepare(player, payload.profileId(), payload.jukeboxSlot(),
                    payload.mode(), payload.startDiscSlot(), payload.tablePos());
        }
    }

    public static void handleReady(CyberGrindReadyPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CyberGrindManager.getInstance().setReady(player, payload.runId(), payload.ready(), payload.detail());
        }
    }

    public static void handleExit(CyberGrindExitPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CyberGrindManager.getInstance().exit(player, payload.runId());
        }
    }
}
