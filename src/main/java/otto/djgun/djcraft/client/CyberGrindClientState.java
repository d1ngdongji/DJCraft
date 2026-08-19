package otto.djgun.djcraft.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.client.ui.ClientScreenBridge;
import otto.djgun.djcraft.client.ui.CyberGrindReadyFragment;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.CyberGrindPreparePayload;
import otto.djgun.djcraft.network.packet.CyberGrindPresetListPayload;
import otto.djgun.djcraft.network.packet.CyberGrindReadyPayload;
import otto.djgun.djcraft.network.packet.CyberGrindResultPayload;
import otto.djgun.djcraft.network.packet.CyberGrindSpawnWarningPayload;
import otto.djgun.djcraft.network.packet.CyberGrindStatePayload;

public final class CyberGrindClientState {
    private static final CyberGrindClientState INSTANCE = new CyberGrindClientState();
    private volatile List<CyberGrindPresetListPayload.Preset> presets = List.of();
    private volatile CyberGrindStatePayload state = CyberGrindStatePayload.empty();
    private volatile CyberGrindPreparePayload preparation;
    private final List<Warning> warnings = new ArrayList<>();

    private CyberGrindClientState() {
    }

    public static CyberGrindClientState getInstance() {
        return INSTANCE;
    }

    public List<CyberGrindPresetListPayload.Preset> presets() {
        return presets;
    }

    public CyberGrindStatePayload state() {
        return state;
    }

    public boolean isActive() {
        return state.active();
    }

    public boolean ownsPlayback(UUID id) {
        return id != null && (state.runId().equals(id)
                || preparation != null && preparation.runId().equals(id));
    }

    public synchronized List<Warning> warnings() {
        long now = System.nanoTime();
        warnings.removeIf(warning -> warning.expiresAtNanos <= now);
        return List.copyOf(warnings);
    }

    public void handlePresets(CyberGrindPresetListPayload payload) {
        presets = payload.presets();
    }

    public void handlePrepare(CyberGrindPreparePayload payload) {
        preparation = payload;
        ClientScreenBridge.openScreen(new CyberGrindReadyFragment(payload));
    }

    public void respond(boolean accepted) {
        CyberGrindPreparePayload pending = preparation;
        if (pending == null) {
            return;
        }
        if (!accepted) {
            PacketDistributor.sendToServer(new CyberGrindReadyPayload(pending.runId(), false, "declined"));
            preparation = null;
            return;
        }
        TrackPackManager manager = TrackPackManager.getInstance();
        List<String> downloads = new ArrayList<>();
        for (var requirement : pending.tracks()) {
            if (requirement.contentHash().equals(manager.getContentHash(requirement.trackId()).orElse(null))) {
                continue;
            }
            if (!requirement.downloadable()) {
                PacketDistributor.sendToServer(new CyberGrindReadyPayload(
                        pending.runId(), false, requirement.trackId() + ": unavailable"));
                preparation = null;
                return;
            }
            downloads.add(requirement.trackId());
        }
        if (downloads.isEmpty()) {
            readyAfterVerification(pending);
            return;
        }
        ClientTrackPackTransferService.requestBatch(downloads,
                () -> readyAfterVerification(pending),
                failure -> {
                    PacketDistributor.sendToServer(new CyberGrindReadyPayload(
                            pending.runId(), false, failure.name().toLowerCase(java.util.Locale.ROOT)));
                    preparation = null;
                });
    }

    private void readyAfterVerification(CyberGrindPreparePayload pending) {
        TrackPackManager manager = TrackPackManager.getInstance();
        for (var requirement : pending.tracks()) {
            if (!requirement.contentHash().equals(manager.getContentHash(requirement.trackId()).orElse(null))) {
                PacketDistributor.sendToServer(new CyberGrindReadyPayload(
                        pending.runId(), false, requirement.trackId() + ": hash mismatch"));
                preparation = null;
                return;
            }
        }
        PacketDistributor.sendToServer(new CyberGrindReadyPayload(pending.runId(), true, ""));
        preparation = null;
    }

    public void handleState(CyberGrindStatePayload payload) {
        state = payload;
        if (!payload.active()) {
            synchronized (this) {
                warnings.clear();
            }
        }
    }

    public synchronized void handleWarning(CyberGrindSpawnWarningPayload payload) {
        warnings.add(new Warning(payload.warningId(), payload.x(), payload.y(), payload.z(), payload.radius(),
                System.nanoTime() + payload.durationTicks() * 50_000_000L,
                payload.durationTicks() * 50_000_000L));
    }

    public void handleResult(CyberGrindResultPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable("message.djcraft.cyber_grind.result",
                    payload.completedWaves(), payload.personalBest(), payload.groupBest()));
        }
    }

    public synchronized void reset() {
        presets = List.of();
        state = CyberGrindStatePayload.empty();
        preparation = null;
        warnings.clear();
    }

    public record Warning(UUID id, double x, double y, double z, float radius,
            long expiresAtNanos, long durationNanos) {
        public float progress(long now) {
            return 1.0F - Math.max(0.0F, Math.min(1.0F,
                    (float) (expiresAtNanos - now) / durationNanos));
        }
    }
}
