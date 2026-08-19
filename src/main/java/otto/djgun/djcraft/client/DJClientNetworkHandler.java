package otto.djgun.djcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.PlayTrackPayload;
import otto.djgun.djcraft.network.packet.DJSessionStatePayload;
import otto.djgun.djcraft.network.packet.ReloadTracksPayload;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.network.packet.StopTrackPayload;
import otto.djgun.djcraft.network.packet.SyncTrackHashesPayload;
import otto.djgun.djcraft.network.packet.AdminPlayPreparePayload;
import otto.djgun.djcraft.network.packet.AdminPlayReadyPayload;
import otto.djgun.djcraft.network.packet.SyncItemTimingPayload;
import otto.djgun.djcraft.network.packet.SyncItemBehaviorPayload;
import otto.djgun.djcraft.network.packet.SyncRayWeaponProfilesPayload;
import otto.djgun.djcraft.network.packet.DJRayEffectPayload;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.combat.DJRayWeaponManager;
import otto.djgun.djcraft.client.render.DJRayEffectRenderer;
import otto.djgun.djcraft.combat.client.DJDeferredDamageIndicatorState;
import otto.djgun.djcraft.network.packet.TrackPackTransferChunkPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferFailedPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferStartPayload;
import otto.djgun.djcraft.network.packet.DJGroupInvitationPayload;
import otto.djgun.djcraft.network.packet.DJGroupPreparePayload;
import otto.djgun.djcraft.network.packet.DJGroupStatePayload;
import otto.djgun.djcraft.network.packet.DJGroupAudioRecoveryPayload;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.client.playback.DJPlaybackController;
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
import otto.djgun.djcraft.client.animation.DJAnimationEvent;
import otto.djgun.djcraft.client.animation.DJAnimationRuntime;
import otto.djgun.djcraft.client.sound.DJWeaponSoundRuntime;
import otto.djgun.djcraft.sound.BeatOutcome;
import otto.djgun.djcraft.sound.DJActionOutcome;
import otto.djgun.djcraft.sound.TargetOutcome;
import otto.djgun.djcraft.network.packet.CyberGrindPresetListPayload;
import otto.djgun.djcraft.network.packet.CyberGrindPreparePayload;
import otto.djgun.djcraft.network.packet.CyberGrindStatePayload;
import otto.djgun.djcraft.network.packet.CyberGrindSpawnWarningPayload;
import otto.djgun.djcraft.network.packet.CyberGrindResultPayload;

@OnlyIn(Dist.CLIENT)
public final class DJClientNetworkHandler {
    private static final SoundEvent DEFERRED_DAMAGE_PROMPT = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "combat.deferred_damage"));

    private DJClientNetworkHandler() {
    }

    public static void handlePlayTrack(PlayTrackPayload payload, IPayloadContext context) {
        DJShieldParryVisualState.reset();
        boolean cyberGrindPlayback = payload.groupId() != null
                && CyberGrindClientState.getInstance().ownsPlayback(payload.groupId());
        boolean started = DJModeManagerClient.getInstance().startSession(
                payload.sessionId(), payload.trackId(), payload.discId(), payload.initialPositionMs(),
                payload.estimatedTransitMs());
        if (started) {
            if (cyberGrindPlayback) {
                // The server-owned arena sequencer advances the playlist.
            } else if (payload.groupId() == null) {
                DJPlaybackController.getInstance().onTrackStarted(
                        payload.sessionId(), payload.trackId(), payload.discId());
            } else {
                DJNetworkGroupClient.getInstance().onGroupTrackStarted(payload);
                if (payload.groupOwner()) {
                    DJPlaybackController.getInstance().onTrackStarted(
                            payload.sessionId(), payload.trackId(), payload.discId());
                }
            }
            DJCraft.LOGGER.info("Client started DJ session {} for {}", payload.sessionId(), payload.trackId());
        } else if (cyberGrindPlayback) {
            PacketDistributor.sendToServer(new otto.djgun.djcraft.network.packet.ClientStopSessionPayload(
                    payload.sessionId(), payload.groupPlaybackId(), StopReason.AUDIO_UNAVAILABLE));
        } else if (payload.groupId() == null) {
            DJPlaybackController.getInstance().resetPlayback();
        } else {
            DJNetworkGroupClient groupClient = DJNetworkGroupClient.getInstance();
            groupClient.onGroupTrackStarted(payload);
            long playbackId = groupClient.onAudioUnavailable(payload.sessionId());
            PacketDistributor.sendToServer(new otto.djgun.djcraft.network.packet.ClientStopSessionPayload(
                    payload.sessionId(), playbackId, StopReason.AUDIO_UNAVAILABLE));
        }
    }

    public static void handleStopTrack(StopTrackPayload payload, IPayloadContext context) {
        var active = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (active == null || active.getSessionId() != payload.sessionId()) {
            return;
        }
        DJShieldParryVisualState.reset();
        DJModeManagerClient.getInstance().stopSession();
        if (CyberGrindClientState.getInstance().isActive()) {
            return;
        }
        if (DJNetworkGroupClient.getInstance().onStopped(payload.sessionId(), payload.reason())) {
            return;
        }
        if (payload.reason() == StopReason.AUDIO_ENDED) {
            DJPlaybackController.getInstance().onNaturalEnd(payload.sessionId());
        } else {
            DJPlaybackController.getInstance().onStopped(payload.sessionId());
        }
        if (payload.reason() == StopReason.CLOCK_DESYNC && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("message.djcraft.clock_desync"), false);
        }
    }

    public static void handleReloadTracks(ReloadTracksPayload payload, IPayloadContext context) {
        TrackPackManager.getInstance().reloadAllPacks();
        ClientTrackRegistry.getInstance().revalidate();
        Minecraft.getInstance().reloadResourcePacks();
    }

    public static void handleSyncTrackHashes(SyncTrackHashesPayload payload, IPayloadContext context) {
        ClientTrackRegistry.getInstance().onReceiveServerHashes(payload.packHashes());
    }

    public static void handleCyberGrindPresets(CyberGrindPresetListPayload payload, IPayloadContext context) {
        CyberGrindClientState.getInstance().handlePresets(payload);
    }

    public static void handleCyberGrindPrepare(CyberGrindPreparePayload payload, IPayloadContext context) {
        CyberGrindClientState.getInstance().handlePrepare(payload);
    }

    public static void handleCyberGrindState(CyberGrindStatePayload payload, IPayloadContext context) {
        CyberGrindClientState.getInstance().handleState(payload);
    }

    public static void handleCyberGrindWarning(CyberGrindSpawnWarningPayload payload, IPayloadContext context) {
        CyberGrindClientState.getInstance().handleWarning(payload);
    }

    public static void handleCyberGrindResult(CyberGrindResultPayload payload, IPayloadContext context) {
        CyberGrindClientState.getInstance().handleResult(payload);
    }

    public static void handleAdminPlayPrepare(AdminPlayPreparePayload payload, IPayloadContext context) {
        String localHash = TrackPackManager.getInstance().getContentHash(payload.trackId()).orElse("");
        if (payload.contentHash().equalsIgnoreCase(localHash)
                && ClientTrackRegistry.getInstance().isVerified(payload.trackId())) {
            PacketDistributor.sendToServer(new AdminPlayReadyPayload(
                    payload.requestId(), true, localHash, ""));
            return;
        }
        if (!payload.downloadable()) {
            PacketDistributor.sendToServer(new AdminPlayReadyPayload(
                    payload.requestId(), false, localHash, "server pack is not downloadable"));
            return;
        }
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(
                    "message.djcraft.admin_play_downloading", payload.trackId()), false);
        }
        ClientTrackPackTransferService.requestBatch(java.util.List.of(payload.trackId()),
                () -> {
                    String downloadedHash = TrackPackManager.getInstance()
                            .getContentHash(payload.trackId()).orElse("");
                    boolean ready = payload.contentHash().equalsIgnoreCase(downloadedHash)
                            && ClientTrackRegistry.getInstance().isVerified(payload.trackId());
                    PacketDistributor.sendToServer(new AdminPlayReadyPayload(
                            payload.requestId(), ready, downloadedHash,
                            ready ? "" : "downloaded hash mismatch"));
                },
                reason -> PacketDistributor.sendToServer(new AdminPlayReadyPayload(
                        payload.requestId(), false, "",
                        reason.name().toLowerCase(java.util.Locale.ROOT))));
    }

    public static void handleSyncItemTiming(SyncItemTimingPayload payload, IPayloadContext context) {
        DJItemCooldownManager.replaceProfiles(payload.profiles());
    }

    public static void handleSyncItemBehavior(SyncItemBehaviorPayload payload, IPayloadContext context) {
        DJItemBehaviorManager.replaceOverrides(payload.overrides());
    }

    public static void handleSyncRayWeaponProfiles(SyncRayWeaponProfilesPayload payload, IPayloadContext context) {
        DJRayWeaponManager.replaceProfiles(payload.profiles());
    }

    public static void handleRayEffect(DJRayEffectPayload payload, IPayloadContext context) {
        DJRayEffectRenderer.acceptAuthoritative(payload);
    }

    public static void handleTransferStart(TrackPackTransferStartPayload payload, IPayloadContext context) {
        ClientTrackPackTransferService.handleStart(payload);
    }

    public static void handleTransferChunk(TrackPackTransferChunkPayload payload, IPayloadContext context) {
        ClientTrackPackTransferService.handleChunk(payload);
    }

    public static void handleTransferFailed(TrackPackTransferFailedPayload payload, IPayloadContext context) {
        ClientTrackPackTransferService.handleFailed(payload);
    }

    public static void handleGroupInvitation(DJGroupInvitationPayload payload, IPayloadContext context) {
        DJNetworkGroupClient.getInstance().handleInvitation(payload);
    }

    public static void handleGroupPrepare(DJGroupPreparePayload payload, IPayloadContext context) {
        DJNetworkGroupClient.getInstance().handlePrepare(payload);
    }

    public static void handleGroupState(DJGroupStatePayload payload, IPayloadContext context) {
        DJNetworkGroupClient.getInstance().handleState(payload);
    }

    public static void handleGroupAudioRecovery(DJGroupAudioRecoveryPayload payload, IPayloadContext context) {
        DJNetworkGroupClient.getInstance().handleAudioRecovery(payload);
    }

    public static void handleSessionState(DJSessionStatePayload payload, IPayloadContext context) {
        DJModeManagerClient.getInstance().getActiveSession()
                .ifPresent(session -> session.applyResourceState(payload.sessionId(), payload.combo(),
                        payload.currentTrackCombo(),
                        payload.energy(), payload.maxEnergy(), payload.toleranceChances(),
                        payload.maxToleranceChances(), payload.offBeatAttackDamagePercent()));
    }

    public static void handleMovementState(DJMovementStatePayload payload, IPayloadContext context) {
        DJModeManagerClient.getInstance().getActiveSession()
                .ifPresent(session -> session.applyMovementState(payload.sessionId(),
                        payload.dashCooldownTicks(), payload.consecutiveDashes(),
                        payload.remainingAirJumps()));
    }

    public static void handleDoubleJumpImpulse(DJDoubleJumpImpulsePayload payload, IPayloadContext context) {
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        var player = Minecraft.getInstance().player;
        if (session == null || player == null || session.getSessionId() != payload.sessionId()) {
            return;
        }
        otto.djgun.djcraft.combat.client.DJClientDashMomentumState.reconcileAirMovement(
                player, payload.velocity(), payload.dashMomentumTicks());
    }

    public static void handleFloweryDashEffect(FloweryDashEffectPayload payload, IPayloadContext context) {
        otto.djgun.djcraft.client.render.FloweryDashVisualState.activate(
                payload.playerId(), payload.durationTicks());
    }

    public static void handleDashAfterimage(DJDashAfterimagePayload payload, IPayloadContext context) {
        otto.djgun.djcraft.client.render.DJNormalDashVisualState.activate(payload.playerId());
    }

    public static void handleDashMomentum(DJDashMomentumPayload payload, IPayloadContext context) {
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            otto.djgun.djcraft.combat.client.DJClientDashMomentumState.reconcile(
                    player, payload.momentum(), payload.durationTicks());
        }
    }

    public static void handleParrySuccess(DJParrySuccessPayload payload, IPayloadContext context) {
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        var player = Minecraft.getInstance().player;
        if (session == null || player == null || session.getSessionId() != payload.sessionId()) {
            return;
        }
        DJShieldParryVisualState.reset();
        var stack = player.getItemInHand(payload.hand());
        DJAnimationRuntime.getInstance().emitVisualOnly(DJAnimationEvent.Kind.PARRY,
                payload.hand(), stack, session, 0.35,
                new DJActionOutcome(BeatOutcome.HIT, TargetOutcome.HIT));
    }

    public static void handleDeferredDamagePrompt(DJDeferredDamagePromptPayload payload, IPayloadContext context) {
        DJCraft.LOGGER.debug("Received deferred-damage prompt for DJ session {}", payload.sessionId());
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(DEFERRED_DAMAGE_PROMPT, 1.0F, 1.0F));
    }

    public static void handleDeferredDamageState(DJDeferredDamageStatePayload payload, IPayloadContext context) {
        DJDeferredDamageIndicatorState.update(payload.sessionId(), payload.pending());
    }

    public static void handleShieldParryWindow(DJShieldParryWindowPayload payload, IPayloadContext context) {
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (session != null && session.getSessionId() == payload.sessionId()) {
            DJShieldParryVisualState.activate(payload.sessionId(), payload.hand(), payload.expiresAtMs());
        }
    }

    public static void handleWeaponSound(DJWeaponSoundBroadcastPayload payload, IPayloadContext context) {
        DJWeaponSoundRuntime.getInstance().onBroadcast(payload);
    }
}
