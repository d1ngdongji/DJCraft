package otto.djgun.djcraft.network.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

class CyberGrindPayloadTest {
    @Test
    void clientAndServerPayloadsRoundTrip() {
        UUID runId = UUID.randomUUID();
        assertEquals(new CyberGrindStartPayload("djcraft:default", 4, 2, 3, new BlockPos(1, 2, 3)),
                roundTrip(CyberGrindStartPayload.CODEC,
                        new CyberGrindStartPayload("djcraft:default", 4, 2, 3, new BlockPos(1, 2, 3))));
        assertEquals(new CyberGrindReadyPayload(runId, false, "declined"),
                roundTrip(CyberGrindReadyPayload.CODEC,
                        new CyberGrindReadyPayload(runId, false, "declined")));
        assertEquals(new CyberGrindExitPayload(runId),
                roundTrip(CyberGrindExitPayload.CODEC, new CyberGrindExitPayload(runId)));
    }

    @Test
    void preparationStateWarningAndResultRoundTrip() {
        UUID runId = UUID.randomUUID();
        CyberGrindPreparePayload prepare = new CyberGrindPreparePayload(runId, "Default", "Owner",
                List.of("Owner", "Member"), List.of(
                        new CyberGrindPreparePayload.TrackRequirement("track", "a".repeat(64), true)));
        assertEquals(prepare, roundTrip(CyberGrindPreparePayload.CODEC, prepare));

        CyberGrindStatePayload state = new CyberGrindStatePayload(true, runId, "Default", 12, 11, 4, 5, 0);
        assertEquals(state, roundTrip(CyberGrindStatePayload.CODEC, state));
        CyberGrindSpawnWarningPayload warning = new CyberGrindSpawnWarningPayload(
                UUID.randomUUID(), 1.5, 82.0, -4.5, 2.5F, 40);
        assertEquals(warning, roundTrip(CyberGrindSpawnWarningPayload.CODEC, warning));
        CyberGrindResultPayload result = new CyberGrindResultPayload("Default", 11, 20, 15);
        assertEquals(result, roundTrip(CyberGrindResultPayload.CODEC, result));
    }

    @Test
    void presetSummariesRoundTrip() {
        CyberGrindPresetListPayload payload = new CyberGrindPresetListPayload(List.of(
                new CyberGrindPresetListPayload.Preset("djcraft:default", "Default", "Description")));
        assertEquals(payload, roundTrip(CyberGrindPresetListPayload.CODEC, payload));
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
