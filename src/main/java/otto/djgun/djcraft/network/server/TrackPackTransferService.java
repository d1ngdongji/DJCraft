package otto.djgun.djcraft.network.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import otto.djgun.djcraft.Config;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.loader.TrackPackIdValidator;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.ClientRequestDownloadPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferAckPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferCancelPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferChunkPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferFailedPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferFailure;
import otto.djgun.djcraft.network.packet.TrackPackTransferStartPayload;
import otto.djgun.djcraft.network.packet.TrackPackTransferControlPayload;
import otto.djgun.djcraft.network.transfer.TrackPackTransferWindow;

public final class TrackPackTransferService {
    private static final int WINDOW_CHUNKS = 8;
    private static final long TIMEOUT_MS = 60_000L;
    private static final Map<UUID, ServerTransfer> TRANSFERS = new ConcurrentHashMap<>();
    private static final ThreadPoolExecutor IO_EXECUTOR = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), runnable -> {
                Thread thread = new Thread(runnable, "djcraft-trackpack-server-io");
                thread.setDaemon(true);
                return thread;
            });

    private TrackPackTransferService() {
    }

    public static void handleRequest(ClientRequestDownloadPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!TrackPackIdValidator.isValid(payload.packId())) {
            fail(player, 0L, TrackPackTransferFailure.INVALID);
            return;
        }
        if (TRANSFERS.containsKey(player.getUUID())) {
            fail(player, 0L, TrackPackTransferFailure.BUSY);
            return;
        }

        var descriptor = TrackPackManager.getInstance().getArchiveDescriptor(payload.packId()).orElse(null);
        if (descriptor == null) {
            fail(player, 0L, TrackPackTransferFailure.NOT_FOUND);
            return;
        }
        long maxBytes = Config.maxTrackPackBytes();
        if (descriptor.size() <= 0 || descriptor.size() > maxBytes) {
            fail(player, 0L, TrackPackTransferFailure.TOO_LARGE);
            return;
        }

        long transferId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        ServerTransfer transfer = new ServerTransfer(player, transferId, payload.packId(), descriptor.path(),
                descriptor.size(), descriptor.sha256());
        TRANSFERS.put(player.getUUID(), transfer);
        PacketDistributor.sendToPlayer(player,
                new TrackPackTransferStartPayload(transferId, payload.packId(), descriptor.size(), descriptor.sha256()));
        sendWindow(player, transfer);
    }

    public static void handleAck(TrackPackTransferAckPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerTransfer transfer = TRANSFERS.get(player.getUUID());
        if (transfer == null || transfer.id != payload.transferId()) {
            return;
        }
        synchronized (transfer) {
            if (!transfer.awaitingAck || payload.nextOffset() != transfer.sentThrough) {
                cancel(player, transfer, TrackPackTransferFailure.INVALID);
                return;
            }
            transfer.lastActivityMs = System.currentTimeMillis();
            transfer.awaitingAck = false;
            transfer.nextOffset = payload.nextOffset();
            if (transfer.nextOffset >= transfer.size) {
                TRANSFERS.remove(player.getUUID(), transfer);
                return;
            }
        }
        sendWindow(player, transfer);
    }

    public static void handleCancel(TrackPackTransferCancelPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ServerTransfer transfer = TRANSFERS.get(player.getUUID());
            if (transfer != null && transfer.id == payload.transferId()) {
                TRANSFERS.remove(player.getUUID(), transfer);
            }
        }
    }

    public static void handleControl(TrackPackTransferControlPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerTransfer transfer = TRANSFERS.get(player.getUUID());
        if (transfer == null || transfer.id != payload.transferId()) {
            return;
        }
        transfer.paused = payload.paused();
        transfer.lastActivityMs = System.currentTimeMillis();
    }

    public static void cleanupExpired() {
        long now = System.currentTimeMillis();
        TRANSFERS.forEach((playerId, transfer) -> {
            if (!transfer.paused && now - transfer.lastActivityMs > TIMEOUT_MS
                    && TRANSFERS.remove(playerId, transfer)) {
                DJCraft.LOGGER.warn("TrackPack transfer {} timed out for {}", transfer.id, playerId);
                fail(transfer.player, transfer.id, TrackPackTransferFailure.TIMEOUT);
            }
        });
    }

    public static void cleanupPlayer(UUID playerId) {
        TRANSFERS.remove(playerId);
    }

    public static void shutdown() {
        TRANSFERS.clear();
        IO_EXECUTOR.getQueue().clear();
    }

    private static void sendWindow(ServerPlayer player, ServerTransfer transfer) {
        try {
            IO_EXECUTOR.execute(() -> readAndSendWindow(player, transfer));
        } catch (RuntimeException e) {
            cancel(player, transfer, TrackPackTransferFailure.BUSY);
        }
    }

    private static void readAndSendWindow(ServerPlayer player, ServerTransfer transfer) {
        try (FileChannel channel = FileChannel.open(transfer.path, StandardOpenOption.READ)) {
            long offset;
            synchronized (transfer) {
                if (transfer.awaitingAck || TRANSFERS.get(player.getUUID()) != transfer) {
                    return;
                }
                offset = transfer.nextOffset;
            }

            List<TrackPackTransferChunkPayload> chunks = new ArrayList<>(WINDOW_CHUNKS);
            long sentThrough = offset;
            for (var segment : TrackPackTransferWindow.plan(offset, transfer.size,
                    TrackPackTransferChunkPayload.MAX_CHUNK_BYTES, WINDOW_CHUNKS)) {
                if (TRANSFERS.get(player.getUUID()) != transfer) {
                    return;
                }
                byte[] data = new byte[segment.length()];
                ByteBuffer buffer = ByteBuffer.wrap(data);
                channel.position(segment.offset());
                while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                    // Continue until the requested chunk is full.
                }
                if (buffer.hasRemaining()) {
                    throw new IOException("Unexpected end of TrackPack archive");
                }
                long chunkOffset = segment.offset();
                sentThrough = chunkOffset + segment.length();
                chunks.add(new TrackPackTransferChunkPayload(transfer.id, chunkOffset, data, segment.windowEnd()));
            }

            synchronized (transfer) {
                if (TRANSFERS.get(player.getUUID()) != transfer) {
                    return;
                }
                transfer.sentThrough = sentThrough;
                transfer.awaitingAck = true;
                transfer.lastActivityMs = System.currentTimeMillis();
            }
            for (TrackPackTransferChunkPayload chunk : chunks) {
                if (TRANSFERS.get(player.getUUID()) != transfer) {
                    return;
                }
                PacketDistributor.sendToPlayer(player, chunk);
            }
        } catch (IOException e) {
            DJCraft.LOGGER.error("Failed to transfer TrackPack {}", transfer.packId, e);
            cancel(player, transfer, TrackPackTransferFailure.IO_ERROR);
        }
    }

    private static void cancel(ServerPlayer player, ServerTransfer transfer, TrackPackTransferFailure reason) {
        if (TRANSFERS.remove(player.getUUID(), transfer)) {
            fail(player, transfer.id, reason);
        }
    }

    private static void fail(ServerPlayer player, long transferId, TrackPackTransferFailure reason) {
        PacketDistributor.sendToPlayer(player, new TrackPackTransferFailedPayload(transferId, reason));
    }

    private static final class ServerTransfer {
        private final ServerPlayer player;
        private final long id;
        private final String packId;
        private final java.nio.file.Path path;
        private final long size;
        @SuppressWarnings("unused")
        private final String sha256;
        private long nextOffset;
        private long sentThrough;
        private boolean awaitingAck;
        private volatile long lastActivityMs = System.currentTimeMillis();
        private volatile boolean paused;

        private ServerTransfer(ServerPlayer player, long id, String packId, java.nio.file.Path path, long size,
                String sha256) {
            this.player = player;
            this.id = id;
            this.packId = packId;
            this.path = path;
            this.size = size;
            this.sha256 = sha256;
        }
    }
}
