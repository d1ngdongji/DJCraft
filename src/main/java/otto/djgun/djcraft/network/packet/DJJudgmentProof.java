package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record DJJudgmentProof(long sessionId, long actionSequence, boolean hit, long clientTimeMs, int beatIndex) {

    public static final StreamCodec<FriendlyByteBuf, DJJudgmentProof> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DJJudgmentProof::sessionId,
            ByteBufCodecs.VAR_LONG, DJJudgmentProof::actionSequence,
            ByteBufCodecs.BOOL, DJJudgmentProof::hit,
            ByteBufCodecs.VAR_LONG, DJJudgmentProof::clientTimeMs,
            ByteBufCodecs.VAR_INT, DJJudgmentProof::beatIndex,
            DJJudgmentProof::new);

}
