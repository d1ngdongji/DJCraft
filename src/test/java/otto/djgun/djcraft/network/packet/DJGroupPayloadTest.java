package otto.djgun.djcraft.network.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

class DJGroupPayloadTest {
    @Test
    void playTrackRoundTripsGroupPlaybackIdentityAndInitialPosition() {
        PlayTrackPayload expected = new PlayTrackPayload(41L, "example_track", UUID.randomUUID(),
                UUID.randomUUID(), 73L, 1_250L, 375L, true);
        assertEquals(expected, roundTrip(PlayTrackPayload.CODEC, expected));
    }

    @Test
    void recoveryAndStopReportsRoundTripPlaybackIdentity() {
        DJGroupAudioRecoveryPayload recovery = new DJGroupAudioRecoveryPayload(UUID.randomUUID(),
                73L, DJGroupAudioRecoveryPayload.Status.RETRYING, 1, 2);
        assertEquals(recovery, roundTrip(DJGroupAudioRecoveryPayload.CODEC, recovery));
        ClientStopSessionPayload stop = new ClientStopSessionPayload(
                41L, 73L, StopReason.AUDIO_UNAVAILABLE);
        assertEquals(stop, roundTrip(ClientStopSessionPayload.CODEC, stop));
    }

    @Test
    void preparationRoundTripsDistinctTrackRequirements() {
        DJGroupPreparePayload expected = new DJGroupPreparePayload(UUID.randomUUID(), List.of(
                new DJGroupPreparePayload.TrackRequirement("one", "a".repeat(64), true),
                new DJGroupPreparePayload.TrackRequirement("two", "b".repeat(64), false)));
        assertEquals(expected, roundTrip(DJGroupPreparePayload.CODEC, expected));
    }

    @Test
    void preparationRejectsMoreThanPortableJukeboxCapacity() {
        var tracks = java.util.stream.IntStream.range(0, 55)
                .mapToObj(index -> new DJGroupPreparePayload.TrackRequirement(
                        "track_" + index, "a".repeat(64), true))
                .toList();
        DJGroupPreparePayload payload = new DJGroupPreparePayload(UUID.randomUUID(), tracks);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> DJGroupPreparePayload.CODEC.encode(buffer, payload));
        } finally {
            buffer.release();
        }
    }

    @Test
    void stateRoundTripsBoundedMemberLists() {
        DJGroupStatePayload expected = new DJGroupStatePayload(true, UUID.randomUUID(), UUID.randomUUID(),
                "Owner", List.of("Owner", "Member"), List.of("Preparing (hash mismatch)"),
                2, 3, 91L, "current_track");
        assertEquals(expected, roundTrip(DJGroupStatePayload.CODEC, expected));
    }

    @Test
    void resourceVerificationAndAdminPlayPayloadsRoundTrip() {
        assertEquals(new ClientTrackStatusPayload(Map.of("track", "a".repeat(64))),
                roundTrip(ClientTrackStatusPayload.CODEC,
                        new ClientTrackStatusPayload(Map.of("track", "a".repeat(64)))));
        AdminPlayPreparePayload prepare = new AdminPlayPreparePayload(
                17L, "track", "b".repeat(64), true);
        assertEquals(prepare, roundTrip(AdminPlayPreparePayload.CODEC, prepare));
        AdminPlayReadyPayload ready = new AdminPlayReadyPayload(
                17L, false, "", "hash mismatch");
        assertEquals(ready, roundTrip(AdminPlayReadyPayload.CODEC, ready));
        TrackPackTransferControlPayload control =
                new TrackPackTransferControlPayload(9L, true);
        assertEquals(control, roundTrip(TrackPackTransferControlPayload.CODEC, control));
    }

    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
