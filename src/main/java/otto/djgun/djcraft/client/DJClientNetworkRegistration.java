package otto.djgun.djcraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.network.packet.PlayTrackPayload;
import otto.djgun.djcraft.network.packet.DJSessionStatePayload;
import otto.djgun.djcraft.network.packet.ReloadTracksPayload;
import otto.djgun.djcraft.network.packet.StopTrackPayload;
import otto.djgun.djcraft.network.packet.SyncTrackHashesPayload;
import otto.djgun.djcraft.network.packet.AdminPlayPreparePayload;
import otto.djgun.djcraft.network.packet.SyncItemTimingPayload;
import otto.djgun.djcraft.network.packet.SyncItemBehaviorPayload;
import otto.djgun.djcraft.network.packet.SyncRayWeaponProfilesPayload;
import otto.djgun.djcraft.network.packet.DJRayEffectPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferChunkPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferFailedPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferStartPayload;
import otto.djgun.djcraft.network.packet.DJWeaponSoundBroadcastPayload;
import otto.djgun.djcraft.network.packet.DJMovementStatePayload;
import otto.djgun.djcraft.network.packet.DJDashAfterimagePayload;
import otto.djgun.djcraft.network.packet.DJDashMomentumPayload;
import otto.djgun.djcraft.network.packet.FloweryDashEffectPayload;
import otto.djgun.djcraft.network.packet.DJDoubleJumpImpulsePayload;
import otto.djgun.djcraft.network.packet.DJParrySuccessPayload;
import otto.djgun.djcraft.network.packet.DJDeferredDamagePromptPayload;
import otto.djgun.djcraft.network.packet.DJDeferredDamageStatePayload;
import otto.djgun.djcraft.network.packet.DJShieldParryWindowPayload;
import otto.djgun.djcraft.network.packet.DJGroupInvitationPayload;
import otto.djgun.djcraft.network.packet.DJGroupPreparePayload;
import otto.djgun.djcraft.network.packet.DJGroupStatePayload;
import otto.djgun.djcraft.network.packet.DJGroupAudioRecoveryPayload;
import otto.djgun.djcraft.network.packet.CyberGrindPresetListPayload;
import otto.djgun.djcraft.network.packet.CyberGrindPreparePayload;
import otto.djgun.djcraft.network.packet.CyberGrindStatePayload;
import otto.djgun.djcraft.network.packet.CyberGrindSpawnWarningPayload;
import otto.djgun.djcraft.network.packet.CyberGrindResultPayload;

@EventBusSubscriber(modid = DJCraft.MODID, value = Dist.CLIENT)
public final class DJClientNetworkRegistration {
    private DJClientNetworkRegistration() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(DJCraft.MODID).versioned("2.28.0");
        registrar.playToClient(PlayTrackPayload.TYPE, PlayTrackPayload.CODEC,
                DJClientNetworkHandler::handlePlayTrack);
        registrar.playToClient(DJSessionStatePayload.TYPE, DJSessionStatePayload.CODEC,
                DJClientNetworkHandler::handleSessionState);
        registrar.playToClient(DJMovementStatePayload.TYPE, DJMovementStatePayload.CODEC,
                DJClientNetworkHandler::handleMovementState);
        registrar.playToClient(DJDashAfterimagePayload.TYPE, DJDashAfterimagePayload.CODEC,
                DJClientNetworkHandler::handleDashAfterimage);
        registrar.playToClient(DJDashMomentumPayload.TYPE, DJDashMomentumPayload.CODEC,
                DJClientNetworkHandler::handleDashMomentum);
        registrar.playToClient(FloweryDashEffectPayload.TYPE, FloweryDashEffectPayload.CODEC,
                DJClientNetworkHandler::handleFloweryDashEffect);
        registrar.playToClient(DJDoubleJumpImpulsePayload.TYPE, DJDoubleJumpImpulsePayload.CODEC,
                DJClientNetworkHandler::handleDoubleJumpImpulse);
        registrar.playToClient(DJParrySuccessPayload.TYPE, DJParrySuccessPayload.CODEC,
                DJClientNetworkHandler::handleParrySuccess);
        registrar.playToClient(DJDeferredDamagePromptPayload.TYPE, DJDeferredDamagePromptPayload.CODEC,
                DJClientNetworkHandler::handleDeferredDamagePrompt);
        registrar.playToClient(DJDeferredDamageStatePayload.TYPE, DJDeferredDamageStatePayload.CODEC,
                DJClientNetworkHandler::handleDeferredDamageState);
        registrar.playToClient(DJShieldParryWindowPayload.TYPE, DJShieldParryWindowPayload.CODEC,
                DJClientNetworkHandler::handleShieldParryWindow);
        registrar.playToClient(DJWeaponSoundBroadcastPayload.TYPE, DJWeaponSoundBroadcastPayload.CODEC,
                DJClientNetworkHandler::handleWeaponSound);
        registrar.playToClient(StopTrackPayload.TYPE, StopTrackPayload.CODEC,
                DJClientNetworkHandler::handleStopTrack);
        registrar.playToClient(ReloadTracksPayload.TYPE, ReloadTracksPayload.CODEC,
                DJClientNetworkHandler::handleReloadTracks);
        registrar.playToClient(SyncTrackHashesPayload.TYPE, SyncTrackHashesPayload.CODEC,
                DJClientNetworkHandler::handleSyncTrackHashes);
        registrar.playToClient(AdminPlayPreparePayload.TYPE, AdminPlayPreparePayload.CODEC,
                DJClientNetworkHandler::handleAdminPlayPrepare);
        registrar.playToClient(SyncItemTimingPayload.TYPE, SyncItemTimingPayload.CODEC,
                DJClientNetworkHandler::handleSyncItemTiming);
        registrar.playToClient(SyncItemBehaviorPayload.TYPE, SyncItemBehaviorPayload.CODEC,
                DJClientNetworkHandler::handleSyncItemBehavior);
        registrar.playToClient(SyncRayWeaponProfilesPayload.TYPE, SyncRayWeaponProfilesPayload.CODEC,
                DJClientNetworkHandler::handleSyncRayWeaponProfiles);
        registrar.playToClient(DJRayEffectPayload.TYPE, DJRayEffectPayload.CODEC,
                DJClientNetworkHandler::handleRayEffect);
        registrar.playToClient(TrackPackTransferStartPayload.TYPE, TrackPackTransferStartPayload.CODEC,
                DJClientNetworkHandler::handleTransferStart);
        registrar.playToClient(TrackPackTransferChunkPayload.TYPE, TrackPackTransferChunkPayload.CODEC,
                DJClientNetworkHandler::handleTransferChunk);
        registrar.playToClient(TrackPackTransferFailedPayload.TYPE, TrackPackTransferFailedPayload.CODEC,
                DJClientNetworkHandler::handleTransferFailed);
        registrar.playToClient(DJGroupInvitationPayload.TYPE, DJGroupInvitationPayload.CODEC,
                DJClientNetworkHandler::handleGroupInvitation);
        registrar.playToClient(DJGroupPreparePayload.TYPE, DJGroupPreparePayload.CODEC,
                DJClientNetworkHandler::handleGroupPrepare);
        registrar.playToClient(DJGroupStatePayload.TYPE, DJGroupStatePayload.CODEC,
                DJClientNetworkHandler::handleGroupState);
        registrar.playToClient(DJGroupAudioRecoveryPayload.TYPE, DJGroupAudioRecoveryPayload.CODEC,
                DJClientNetworkHandler::handleGroupAudioRecovery);
        registrar.playToClient(CyberGrindPresetListPayload.TYPE, CyberGrindPresetListPayload.CODEC,
                DJClientNetworkHandler::handleCyberGrindPresets);
        registrar.playToClient(CyberGrindPreparePayload.TYPE, CyberGrindPreparePayload.CODEC,
                DJClientNetworkHandler::handleCyberGrindPrepare);
        registrar.playToClient(CyberGrindStatePayload.TYPE, CyberGrindStatePayload.CODEC,
                DJClientNetworkHandler::handleCyberGrindState);
        registrar.playToClient(CyberGrindSpawnWarningPayload.TYPE, CyberGrindSpawnWarningPayload.CODEC,
                DJClientNetworkHandler::handleCyberGrindWarning);
        registrar.playToClient(CyberGrindResultPayload.TYPE, CyberGrindResultPayload.CODEC,
                DJClientNetworkHandler::handleCyberGrindResult);
    }
}
