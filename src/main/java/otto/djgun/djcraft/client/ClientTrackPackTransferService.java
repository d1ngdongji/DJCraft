package otto.djgun.djcraft.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.Config;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.loader.TrackPackIdValidator;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.TrackPackTransferAckPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferCancelPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferChunkPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferFailedPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferFailure;
import otto.djgun.djcraft.network.packet.TrackPackTransferStartPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferControlPayload;
import otto.djgun.djcraft.network.packet.ClientRequestDownloadPayload;

public final class ClientTrackPackTransferService {
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "djcraft-trackpack-client-io");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile ClientTransfer activeTransfer;
    private static final Queue<String> batchQueue = new ArrayDeque<>();
    private static Runnable batchSuccess;
    private static Consumer<TrackPackTransferFailure> batchFailure;

    private ClientTrackPackTransferService() {
    }

    public static void handleStart(TrackPackTransferStartPayload payload) {
        IO_EXECUTOR.execute(() -> start(payload));
    }

    public static void handleChunk(TrackPackTransferChunkPayload payload) {
        IO_EXECUTOR.execute(() -> writeChunk(payload));
    }

    public static void handleFailed(TrackPackTransferFailedPayload payload) {
        IO_EXECUTOR.execute(() -> {
            if (payload.transferId() != 0L && activeTransfer != null
                    && activeTransfer.id == payload.transferId()) {
                cleanupActive();
            }
            showFailure(payload.reason());
            failBatch(payload.reason());
        });
    }

    public static void cleanup() {
        IO_EXECUTOR.execute(() -> {
            cleanupActive();
            clearBatch();
        });
    }

    public static synchronized void request(String packId, Runnable onSuccess,
            Consumer<TrackPackTransferFailure> onFailure) {
        requestBatch(java.util.List.of(packId), onSuccess, onFailure);
    }

    public static void pause() {
        IO_EXECUTOR.execute(() -> {
            ClientTransfer transfer = activeTransfer;
            if (transfer != null && !transfer.paused) {
                transfer.paused = true;
                transfer.pauseStartedMs = System.currentTimeMillis();
                PacketDistributor.sendToServer(
                        new TrackPackTransferControlPayload(transfer.id, true));
            }
        });
    }

    public static void resume() {
        IO_EXECUTOR.execute(() -> {
            ClientTransfer transfer = activeTransfer;
            if (transfer == null || !transfer.paused) {
                return;
            }
            transfer.paused = false;
            transfer.pausedDurationMs += Math.max(0L,
                    System.currentTimeMillis() - transfer.pauseStartedMs);
            transfer.pauseStartedMs = 0L;
            PacketDistributor.sendToServer(
                    new TrackPackTransferControlPayload(transfer.id, false));
            if (transfer.awaitingResume && Minecraft.getInstance().getConnection() != null) {
                transfer.awaitingResume = false;
                PacketDistributor.sendToServer(
                        new TrackPackTransferAckPayload(transfer.id, transfer.expectedOffset));
            }
        });
    }

    public static TransferSnapshot snapshot() {
        ClientTransfer transfer = activeTransfer;
        if (transfer == null) {
            return TransferSnapshot.idle();
        }
        long now = System.currentTimeMillis();
        long paused = transfer.pausedDurationMs
                + (transfer.paused ? Math.max(0L, now - transfer.pauseStartedMs) : 0L);
        long activeMs = Math.max(1L, now - transfer.startedAtMs - paused);
        long bytesPerSecond = transfer.expectedOffset * 1_000L / activeMs;
        return new TransferSnapshot(true, transfer.packId, transfer.expectedOffset,
                transfer.totalSize, bytesPerSecond, transfer.paused);
    }

    public static synchronized void requestBatch(Collection<String> packIds, Runnable onSuccess,
            Consumer<TrackPackTransferFailure> onFailure) {
        if (batchSuccess != null || packIds == null || packIds.isEmpty()) {
            if (packIds == null || packIds.isEmpty()) {
                onSuccess.run();
            } else {
                onFailure.accept(TrackPackTransferFailure.BUSY);
            }
            return;
        }
        batchQueue.addAll(packIds);
        batchSuccess = onSuccess;
        batchFailure = onFailure;
        requestNextBatchPack();
    }

    private static void start(TrackPackTransferStartPayload payload) {
        cleanupActive();
        long maxBytes = Config.maxTrackPackBytes();
        if (!TrackPackIdValidator.isValid(payload.packId()) || payload.totalSize() <= 0
                || payload.totalSize() > maxBytes || payload.sha256().length() != 64) {
            PacketDistributor.sendToServer(new TrackPackTransferCancelPayload(payload.transferId()));
            showFailure(TrackPackTransferFailure.INVALID);
            failBatch(TrackPackTransferFailure.INVALID);
            return;
        }

        try {
            Path root = Minecraft.getInstance().gameDirectory.toPath().resolve("djcraft").resolve("trackpacks")
                    .toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path output = root.resolve(payload.packId() + ".djcraft").normalize();
            if (!output.startsWith(root)) {
                throw new IOException("TrackPack output escaped destination directory");
            }
            Path temporary = root.resolve(".djcraft-download-" + payload.transferId() + ".part");
            FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            activeTransfer = new ClientTransfer(payload.transferId(), payload.packId(), payload.totalSize(),
                    payload.sha256(), temporary, output, channel, MessageDigest.getInstance("SHA-256"));
        } catch (IOException | NoSuchAlgorithmException e) {
            DJCraft.LOGGER.error("Failed to start TrackPack download", e);
            PacketDistributor.sendToServer(new TrackPackTransferCancelPayload(payload.transferId()));
            cleanupActive();
            showFailure(TrackPackTransferFailure.IO_ERROR);
            failBatch(TrackPackTransferFailure.IO_ERROR);
        }
    }

    private static void writeChunk(TrackPackTransferChunkPayload payload) {
        ClientTransfer transfer = activeTransfer;
        if (transfer == null || transfer.id != payload.transferId()) {
            PacketDistributor.sendToServer(new TrackPackTransferCancelPayload(payload.transferId()));
            return;
        }
        if (payload.offset() != transfer.expectedOffset
                || transfer.expectedOffset + payload.data().length > transfer.totalSize) {
            PacketDistributor.sendToServer(new TrackPackTransferCancelPayload(payload.transferId()));
            cleanupActive();
            showFailure(TrackPackTransferFailure.INVALID);
            failBatch(TrackPackTransferFailure.INVALID);
            return;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload.data());
            while (buffer.hasRemaining()) {
                transfer.channel.write(buffer);
            }
            transfer.digest.update(payload.data());
            transfer.expectedOffset += payload.data().length;

            if (transfer.expectedOffset == transfer.totalSize) {
                finish(transfer);
            } else if (payload.windowEnd()) {
                if (transfer.paused) {
                    transfer.awaitingResume = true;
                } else {
                    PacketDistributor.sendToServer(
                            new TrackPackTransferAckPayload(transfer.id, transfer.expectedOffset));
                }
            }
        } catch (IOException e) {
            DJCraft.LOGGER.error("Failed to write TrackPack chunk", e);
            PacketDistributor.sendToServer(new TrackPackTransferCancelPayload(payload.transferId()));
            cleanupActive();
            showFailure(TrackPackTransferFailure.IO_ERROR);
            failBatch(TrackPackTransferFailure.IO_ERROR);
        }
    }

    private static void finish(ClientTransfer transfer) throws IOException {
        transfer.channel.force(true);
        transfer.channel.close();
        String actualHash = HexFormat.of().formatHex(transfer.digest.digest());
        if (!actualHash.equalsIgnoreCase(transfer.sha256)) {
            PacketDistributor.sendToServer(new TrackPackTransferCancelPayload(transfer.id));
            cleanupActive();
            showFailure(TrackPackTransferFailure.HASH_MISMATCH);
            failBatch(TrackPackTransferFailure.HASH_MISMATCH);
            return;
        }

        try {
            Files.move(transfer.temporary, transfer.output, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(transfer.temporary, transfer.output, StandardCopyOption.REPLACE_EXISTING);
        }
        activeTransfer = null;
        var prepared = TrackPackManager.getInstance().prepareReloadPack(transfer.packId);
        Minecraft.getInstance().execute(() -> installDownloadedPack(transfer, prepared));
    }

    private static void installDownloadedPack(ClientTransfer transfer,
            TrackPackManager.PreparedPackReload prepared) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean loaded = TrackPackManager.getInstance().installPreparedPack(prepared);
        if (!loaded) {
            if (minecraft.getConnection() != null) {
                PacketDistributor.sendToServer(new TrackPackTransferCancelPayload(transfer.id));
            }
            showMessage(failureMessage(TrackPackTransferFailure.RELOAD_FAILED));
            failBatch(TrackPackTransferFailure.RELOAD_FAILED);
            return;
        }

        if (isBatchPack(transfer.packId)) {
            if (minecraft.getConnection() != null) {
                PacketDistributor.sendToServer(new TrackPackTransferAckPayload(transfer.id, transfer.totalSize));
            }
            completeBatchPack(minecraft);
            return;
        }

        reloadResources(minecraft, transfer, null);
    }

    private static void reloadResources(Minecraft minecraft, ClientTransfer transfer, Runnable success) {
        minecraft.reloadResourcePacks().whenComplete((unused, error) -> minecraft.execute(() -> {
            if (error != null) {
                DJCraft.LOGGER.error("Failed to refresh resources after downloading TrackPack {}",
                        transfer.packId, error);
                if (minecraft.getConnection() != null) {
                    PacketDistributor.sendToServer(new TrackPackTransferCancelPayload(transfer.id));
                }
                showMessage(failureMessage(TrackPackTransferFailure.RELOAD_FAILED));
                failBatch(TrackPackTransferFailure.RELOAD_FAILED);
                return;
            }
            ClientTrackRegistry.getInstance().revalidate();
            if (minecraft.getConnection() != null) {
                PacketDistributor.sendToServer(new TrackPackTransferAckPayload(transfer.id, transfer.totalSize));
            }
            showMessage(Component.translatable("message.djcraft.download_success", transfer.packId));
            if (success != null) {
                success.run();
            }
        }));
    }

    private static synchronized boolean isBatchPack(String packId) {
        return batchSuccess != null && packId.equals(batchQueue.peek());
    }

    private static synchronized void completeBatchPack(Minecraft minecraft) {
        batchQueue.poll();
        if (!batchQueue.isEmpty()) {
            requestNextBatchPack();
            return;
        }
        Runnable success = batchSuccess;
        minecraft.reloadResourcePacks().whenComplete((unused, error) -> minecraft.execute(() -> {
            if (error != null) {
                failBatch(TrackPackTransferFailure.RELOAD_FAILED);
                return;
            }
            ClientTrackRegistry.getInstance().revalidate();
            clearBatch();
            success.run();
        }));
    }

    private static synchronized void requestNextBatchPack() {
        String next = batchQueue.peek();
        if (next != null) {
            PacketDistributor.sendToServer(new ClientRequestDownloadPayload(next));
        }
    }

    private static synchronized void failBatch(TrackPackTransferFailure reason) {
        Consumer<TrackPackTransferFailure> failure = batchFailure;
        clearBatch();
        if (failure != null) {
            Minecraft.getInstance().execute(() -> failure.accept(reason));
        }
    }

    private static synchronized void clearBatch() {
        batchQueue.clear();
        batchSuccess = null;
        batchFailure = null;
    }

    private static void cleanupActive() {
        ClientTransfer transfer = activeTransfer;
        activeTransfer = null;
        if (transfer == null) {
            return;
        }
        try {
            transfer.channel.close();
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(transfer.temporary);
        } catch (IOException e) {
            DJCraft.LOGGER.warn("Failed to delete partial TrackPack {}", transfer.temporary, e);
        }
    }

    private static void showMessage(Component message) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(message, false);
            }
        });
    }

    private static void showFailure(TrackPackTransferFailure reason) {
        showMessage(failureMessage(reason));
    }

    private static Component failureMessage(TrackPackTransferFailure reason) {
        return Component.translatable("message.djcraft.download_failed",
                Component.translatable(reason.translationKey()));
    }

    private static final class ClientTransfer {
        private final long id;
        private final String packId;
        private final long totalSize;
        private final String sha256;
        private final Path temporary;
        private final Path output;
        private final FileChannel channel;
        private final MessageDigest digest;
        private long expectedOffset;
        private final long startedAtMs = System.currentTimeMillis();
        private volatile boolean paused;
        private boolean awaitingResume;
        private long pauseStartedMs;
        private long pausedDurationMs;

        private ClientTransfer(long id, String packId, long totalSize, String sha256, Path temporary, Path output,
                FileChannel channel, MessageDigest digest) {
            this.id = id;
            this.packId = packId;
            this.totalSize = totalSize;
            this.sha256 = sha256;
            this.temporary = temporary;
            this.output = output;
            this.channel = channel;
            this.digest = digest;
        }
    }

    public record TransferSnapshot(boolean active, String packId, long receivedBytes,
            long totalBytes, long bytesPerSecond, boolean paused) {
        private static TransferSnapshot idle() {
            return new TransferSnapshot(false, "", 0L, 0L, 0L, false);
        }

        public double fraction() {
            return totalBytes <= 0L ? 0.0 : Math.min(1.0, (double) receivedBytes / totalBytes);
        }
    }
}
