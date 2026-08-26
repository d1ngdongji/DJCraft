package otto.djgun.djcraft.network.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import otto.djgun.djcraft.combat.DJActionSource;
import otto.djgun.djcraft.sound.BeatOutcome;
import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;
import otto.djgun.djcraft.sound.TargetOutcome;

class DJActionPayloadCodecTest {
    private static final DJJudgmentProof PROOF = new DJJudgmentProof(9L, 12L, true, 345L, 6);
    private static final DJActionSource SOURCE = new DJActionSource(4, 77);

    @Test
    void roundTripsEveryActionSourcePayload() {
        roundTrip(DJAttackClientPayload.CODEC,
                new DJAttackClientPayload(PROOF, InteractionHand.MAIN_HAND, SOURCE));
        roundTrip(DJChargeReleasePayload.CODEC,
                new DJChargeReleasePayload(PROOF, InteractionHand.MAIN_HAND, SOURCE));
        roundTrip(DJTriggerFirePayload.CODEC,
                new DJTriggerFirePayload(PROOF, InteractionHand.MAIN_HAND, SOURCE));
        roundTrip(DJAutoChargeStartPayload.CODEC,
                new DJAutoChargeStartPayload(PROOF, InteractionHand.MAIN_HAND, SOURCE));
        roundTrip(DJTridentFirePayload.CODEC,
                new DJTridentFirePayload(PROOF, InteractionHand.MAIN_HAND, SOURCE));
        roundTrip(DJMaceThrowPayload.CODEC,
                new DJMaceThrowPayload(PROOF, InteractionHand.MAIN_HAND, SOURCE));
        roundTrip(DJShieldUsePayload.CODEC,
                new DJShieldUsePayload(PROOF, InteractionHand.OFF_HAND,
                        new DJActionSource(DJActionSource.OFFHAND_SLOT, 77)));
        roundTrip(DJMovementAbilityPayload.CODEC,
                new DJMovementAbilityPayload(PROOF, DJMovementAbility.GROUND_JUMP,
                        DJDashDirection.FORWARD_RIGHT));
        roundTrip(DJDoubleJumpImpulsePayload.CODEC,
                new DJDoubleJumpImpulsePayload(9L, new Vec3(1.25, 0.8, -0.5), 3));
        roundTrip(DJWeaponSoundBroadcastPayload.CODEC,
                new DJWeaponSoundBroadcastPayload(17L,
                        UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"), 12L,
                        InteractionHand.MAIN_HAND, DJWeaponSoundSemantic.TRIGGER_IMPACT,
                        ResourceLocation.fromNamespaceAndPath("djcraft", "explosive_bow"),
                        BeatOutcome.HIT, TargetOutcome.NOT_APPLICABLE,
                        1.0, 2.0, 3.0, 99L));
    }

    private static <T> void roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buffer, payload);
        assertEquals(payload, codec.decode(buffer));
    }
}
