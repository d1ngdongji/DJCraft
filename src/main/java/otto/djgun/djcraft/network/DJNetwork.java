package otto.djgun.djcraft.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.network.packet.ClientPlaybackReadyPayload;
import otto.djgun.djcraft.network.packet.ClientRequestDownloadPayload;
import otto.djgun.djcraft.network.packet.ClientRequestPlayPayload;
import otto.djgun.djcraft.network.packet.ClientTrackStatusPayload;
import otto.djgun.djcraft.network.packet.AdminPlayPreparePayload;
import otto.djgun.djcraft.network.packet.AdminPlayReadyPayload;
import otto.djgun.djcraft.network.packet.ClientStopSessionPayload;
import otto.djgun.djcraft.network.packet.DJAttackClientPayload;
import otto.djgun.djcraft.network.packet.DJAutoChargeStartPayload;
import otto.djgun.djcraft.network.packet.DJChargeReleasePayload;
import otto.djgun.djcraft.network.packet.DJChargeStartPayload;
import otto.djgun.djcraft.network.packet.DJCraftingSelectTrackPayload;
import otto.djgun.djcraft.network.packet.DJTriggerFirePayload;
import otto.djgun.djcraft.network.packet.DJSessionStatePayload;
import otto.djgun.djcraft.network.packet.DJTridentFirePayload;
import otto.djgun.djcraft.network.packet.DJWeaponSoundIntentPayload;
import otto.djgun.djcraft.network.packet.DJWeaponSoundBroadcastPayload;
import otto.djgun.djcraft.network.packet.DJMovementAbilityPayload;
import otto.djgun.djcraft.network.packet.DJMovementStatePayload;
import otto.djgun.djcraft.network.packet.DJDashAfterimagePayload;
import otto.djgun.djcraft.network.packet.DJDashMomentumPayload;
import otto.djgun.djcraft.network.packet.FloweryDashEffectPayload;
import otto.djgun.djcraft.network.packet.DJDoubleJumpImpulsePayload;
import otto.djgun.djcraft.network.packet.DJShieldUsePayload;
import otto.djgun.djcraft.network.packet.DJEatingPayload;
import otto.djgun.djcraft.network.packet.DJMiningPayload;
import otto.djgun.djcraft.network.packet.DJParrySuccessPayload;
import otto.djgun.djcraft.network.packet.DJDeferredDamagePromptPayload;
import otto.djgun.djcraft.network.packet.DJDeferredDamageStatePayload;
import otto.djgun.djcraft.network.packet.DJShieldParryWindowPayload;
import otto.djgun.djcraft.network.packet.PlayTrackPayload;
import otto.djgun.djcraft.network.packet.ReloadTracksPayload;
import otto.djgun.djcraft.network.packet.StopTrackPayload;
import otto.djgun.djcraft.network.packet.SyncTrackHashesPayload;
import otto.djgun.djcraft.network.packet.SyncItemTimingPayload;
import otto.djgun.djcraft.network.packet.SyncItemBehaviorPayload;
import otto.djgun.djcraft.network.packet.SyncRayWeaponProfilesPayload;
import otto.djgun.djcraft.network.packet.DJRayEffectPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferAckPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferCancelPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferChunkPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferFailedPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferStartPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferControlPayload;
import otto.djgun.djcraft.network.server.DJCombatRequestHandler;
import otto.djgun.djcraft.network.server.DJCraftingRequestHandler;
import otto.djgun.djcraft.network.server.DJSessionRequestHandler;
import otto.djgun.djcraft.network.server.TrackPackTransferService;
import otto.djgun.djcraft.network.server.ClientTrackStatusService;
import otto.djgun.djcraft.network.server.AdminPlayRequestService;
import otto.djgun.djcraft.network.server.DJWeaponSoundRequestHandler;
import otto.djgun.djcraft.network.server.DJMovementAbilityRequestHandler;
import otto.djgun.djcraft.network.server.DJShieldRequestHandler;
import otto.djgun.djcraft.network.server.DJUtilityActionRequestHandler;
import otto.djgun.djcraft.network.server.DJGroupRequestHandler;
import otto.djgun.djcraft.network.packet.DJGroupControlPayload;
import otto.djgun.djcraft.network.packet.DJGroupCreatePayload;
import otto.djgun.djcraft.network.packet.DJGroupInvitePayload;
import otto.djgun.djcraft.network.packet.DJGroupInviteResponsePayload;
import otto.djgun.djcraft.network.packet.DJGroupReadyPayload;
import otto.djgun.djcraft.network.packet.DJGroupInvitationPayload;
import otto.djgun.djcraft.network.packet.DJGroupPreparePayload;
import otto.djgun.djcraft.network.packet.DJGroupStatePayload;
import otto.djgun.djcraft.network.packet.DJGroupAudioRecoveryPayload;
import otto.djgun.djcraft.network.packet.CyberGrindStartPayload;
import otto.djgun.djcraft.network.packet.CyberGrindReadyPayload;
import otto.djgun.djcraft.network.packet.CyberGrindExitPayload;
import otto.djgun.djcraft.network.packet.CyberGrindPresetListPayload;
import otto.djgun.djcraft.network.packet.CyberGrindPreparePayload;
import otto.djgun.djcraft.network.packet.CyberGrindStatePayload;
import otto.djgun.djcraft.network.packet.CyberGrindSpawnWarningPayload;
import otto.djgun.djcraft.network.packet.CyberGrindResultPayload;
import otto.djgun.djcraft.network.server.CyberGrindRequestHandler;

@EventBusSubscriber(modid = DJCraft.MODID)
public final class DJNetwork {
    private DJNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(DJCraft.MODID).versioned("2.28.0");

        registrar.playToServer(DJAttackClientPayload.TYPE, DJAttackClientPayload.CODEC,
                DJCombatRequestHandler::handleAttack);
        registrar.playToServer(DJChargeReleasePayload.TYPE, DJChargeReleasePayload.CODEC,
                DJCombatRequestHandler::handleChargeRelease);
        registrar.playToServer(DJChargeStartPayload.TYPE, DJChargeStartPayload.CODEC,
                DJCombatRequestHandler::handleChargeStart);
        registrar.playToServer(DJTriggerFirePayload.TYPE, DJTriggerFirePayload.CODEC,
                DJCombatRequestHandler::handleTriggerFire);
        registrar.playToServer(DJAutoChargeStartPayload.TYPE, DJAutoChargeStartPayload.CODEC,
                DJCombatRequestHandler::handleAutoChargeStart);
        registrar.playToServer(DJTridentFirePayload.TYPE, DJTridentFirePayload.CODEC,
                DJCombatRequestHandler::handleTridentFire);
        registrar.playToServer(DJMovementAbilityPayload.TYPE, DJMovementAbilityPayload.CODEC,
                DJMovementAbilityRequestHandler::handle);
        registrar.playToServer(DJShieldUsePayload.TYPE, DJShieldUsePayload.CODEC,
                DJShieldRequestHandler::handle);
        registrar.playToServer(DJMiningPayload.TYPE, DJMiningPayload.CODEC,
                DJUtilityActionRequestHandler::handleMining);
        registrar.playToServer(DJEatingPayload.TYPE, DJEatingPayload.CODEC,
                DJUtilityActionRequestHandler::handleEating);
        registrar.playToServer(DJWeaponSoundIntentPayload.TYPE, DJWeaponSoundIntentPayload.CODEC,
                DJWeaponSoundRequestHandler::handle);
        registrar.playToServer(ClientRequestPlayPayload.TYPE, ClientRequestPlayPayload.CODEC,
                DJSessionRequestHandler::handlePlay);
        registrar.playToServer(ClientStopSessionPayload.TYPE, ClientStopSessionPayload.CODEC,
                DJSessionRequestHandler::handleStop);
        registrar.playToServer(ClientPlaybackReadyPayload.TYPE, ClientPlaybackReadyPayload.CODEC,
                DJSessionRequestHandler::handlePlaybackReady);
        registrar.playToServer(ClientTrackStatusPayload.TYPE, ClientTrackStatusPayload.CODEC,
                ClientTrackStatusService::handle);
        registrar.playToServer(AdminPlayReadyPayload.TYPE, AdminPlayReadyPayload.CODEC,
                AdminPlayRequestService::handleReady);
        registrar.playToServer(DJCraftingSelectTrackPayload.TYPE, DJCraftingSelectTrackPayload.CODEC,
                DJCraftingRequestHandler::handleSelectTrack);
        registrar.playToServer(ClientRequestDownloadPayload.TYPE, ClientRequestDownloadPayload.CODEC,
                TrackPackTransferService::handleRequest);
        registrar.playToServer(TrackPackTransferAckPayload.TYPE, TrackPackTransferAckPayload.CODEC,
                TrackPackTransferService::handleAck);
        registrar.playToServer(TrackPackTransferCancelPayload.TYPE, TrackPackTransferCancelPayload.CODEC,
                TrackPackTransferService::handleCancel);
        registrar.playToServer(TrackPackTransferControlPayload.TYPE, TrackPackTransferControlPayload.CODEC,
                TrackPackTransferService::handleControl);
        registrar.playToServer(DJGroupCreatePayload.TYPE, DJGroupCreatePayload.CODEC,
                DJGroupRequestHandler::handleCreate);
        registrar.playToServer(DJGroupInvitePayload.TYPE, DJGroupInvitePayload.CODEC,
                DJGroupRequestHandler::handleInvite);
        registrar.playToServer(DJGroupInviteResponsePayload.TYPE, DJGroupInviteResponsePayload.CODEC,
                DJGroupRequestHandler::handleInviteResponse);
        registrar.playToServer(DJGroupReadyPayload.TYPE, DJGroupReadyPayload.CODEC,
                DJGroupRequestHandler::handleReady);
        registrar.playToServer(DJGroupControlPayload.TYPE, DJGroupControlPayload.CODEC,
                DJGroupRequestHandler::handleControl);
        registrar.playToServer(CyberGrindStartPayload.TYPE, CyberGrindStartPayload.CODEC,
                CyberGrindRequestHandler::handleStart);
        registrar.playToServer(CyberGrindReadyPayload.TYPE, CyberGrindReadyPayload.CODEC,
                CyberGrindRequestHandler::handleReady);
        registrar.playToServer(CyberGrindExitPayload.TYPE, CyberGrindExitPayload.CODEC,
                CyberGrindRequestHandler::handleExit);

        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            registerClientboundCodecs(registrar);
        }
    }

    private static void registerClientboundCodecs(
            net.neoforged.neoforge.network.registration.PayloadRegistrar registrar) {
        registrar.playToClient(PlayTrackPayload.TYPE, PlayTrackPayload.CODEC, (payload, context) -> {
        });
        registrar.playToClient(DJSessionStatePayload.TYPE, DJSessionStatePayload.CODEC, (payload, context) -> {
        });
        registrar.playToClient(DJMovementStatePayload.TYPE, DJMovementStatePayload.CODEC, (payload, context) -> {
        });
        registrar.playToClient(DJDashAfterimagePayload.TYPE, DJDashAfterimagePayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(DJDashMomentumPayload.TYPE, DJDashMomentumPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(FloweryDashEffectPayload.TYPE, FloweryDashEffectPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(DJDoubleJumpImpulsePayload.TYPE, DJDoubleJumpImpulsePayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(DJParrySuccessPayload.TYPE, DJParrySuccessPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(DJDeferredDamagePromptPayload.TYPE, DJDeferredDamagePromptPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(DJDeferredDamageStatePayload.TYPE, DJDeferredDamageStatePayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(DJShieldParryWindowPayload.TYPE, DJShieldParryWindowPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(DJWeaponSoundBroadcastPayload.TYPE, DJWeaponSoundBroadcastPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(StopTrackPayload.TYPE, StopTrackPayload.CODEC, (payload, context) -> {
        });
        registrar.playToClient(ReloadTracksPayload.TYPE, ReloadTracksPayload.CODEC, (payload, context) -> {
        });
        registrar.playToClient(SyncTrackHashesPayload.TYPE, SyncTrackHashesPayload.CODEC, (payload, context) -> {
        });
        registrar.playToClient(AdminPlayPreparePayload.TYPE, AdminPlayPreparePayload.CODEC, (payload, context) -> {
        });
        registrar.playToClient(SyncItemTimingPayload.TYPE, SyncItemTimingPayload.CODEC, (payload, context) -> {
        });
        registrar.playToClient(SyncItemBehaviorPayload.TYPE, SyncItemBehaviorPayload.CODEC, (payload, context) -> {
        });
        registrar.playToClient(SyncRayWeaponProfilesPayload.TYPE, SyncRayWeaponProfilesPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(DJRayEffectPayload.TYPE, DJRayEffectPayload.CODEC, (payload, context) -> {
        });
        registrar.playToClient(TrackPackTransferStartPayload.TYPE, TrackPackTransferStartPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(TrackPackTransferChunkPayload.TYPE, TrackPackTransferChunkPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(TrackPackTransferFailedPayload.TYPE, TrackPackTransferFailedPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(DJGroupInvitationPayload.TYPE, DJGroupInvitationPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(DJGroupPreparePayload.TYPE, DJGroupPreparePayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(DJGroupStatePayload.TYPE, DJGroupStatePayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(DJGroupAudioRecoveryPayload.TYPE, DJGroupAudioRecoveryPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(CyberGrindPresetListPayload.TYPE, CyberGrindPresetListPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(CyberGrindPreparePayload.TYPE, CyberGrindPreparePayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(CyberGrindStatePayload.TYPE, CyberGrindStatePayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(CyberGrindSpawnWarningPayload.TYPE, CyberGrindSpawnWarningPayload.CODEC,
                (payload, context) -> {
                });
        registrar.playToClient(CyberGrindResultPayload.TYPE, CyberGrindResultPayload.CODEC,
                (payload, context) -> {
                });
    }
}
