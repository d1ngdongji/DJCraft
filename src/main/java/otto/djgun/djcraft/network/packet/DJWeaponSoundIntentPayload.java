package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.sound.BeatOutcome;
import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;
import otto.djgun.djcraft.sound.TargetOutcome;

public record DJWeaponSoundIntentPayload(long sessionId, long soundSequence, long actionSequence,
        InteractionHand hand, DJWeaponSoundSemantic semantic, ResourceLocation profileId,
        BeatOutcome beat, TargetOutcome target, long judgedAtMs, int beatIndex, long seed)
        implements CustomPacketPayload {
    public static final Type<DJWeaponSoundIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "weapon_sound_intent"));
    public static final StreamCodec<FriendlyByteBuf, DJWeaponSoundIntentPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarLong(value.sessionId);
                buf.writeVarLong(value.soundSequence);
                buf.writeVarLong(value.actionSequence);
                buf.writeEnum(value.hand);
                buf.writeEnum(value.semantic);
                buf.writeResourceLocation(value.profileId);
                buf.writeEnum(value.beat);
                buf.writeEnum(value.target);
                buf.writeVarLong(value.judgedAtMs);
                buf.writeVarInt(value.beatIndex);
                buf.writeLong(value.seed);
            },
            buf -> new DJWeaponSoundIntentPayload(buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                    buf.readEnum(InteractionHand.class), buf.readEnum(DJWeaponSoundSemantic.class),
                    buf.readResourceLocation(), buf.readEnum(BeatOutcome.class), buf.readEnum(TargetOutcome.class),
                    buf.readVarLong(), buf.readVarInt(), buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
