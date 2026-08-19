package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import java.util.UUID;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.sound.BeatOutcome;
import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;
import otto.djgun.djcraft.sound.TargetOutcome;

public record DJWeaponSoundBroadcastPayload(long soundSequence, UUID shooterId, long actionSequence,
        InteractionHand hand,
        DJWeaponSoundSemantic semantic, ResourceLocation profileId, BeatOutcome beat, TargetOutcome target,
        double x, double y, double z, long seed) implements CustomPacketPayload {
    public static final Type<DJWeaponSoundBroadcastPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "weapon_sound_broadcast"));
    public static final StreamCodec<FriendlyByteBuf, DJWeaponSoundBroadcastPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarLong(value.soundSequence);
                buf.writeUUID(value.shooterId);
                buf.writeVarLong(value.actionSequence);
                buf.writeEnum(value.hand);
                buf.writeEnum(value.semantic);
                buf.writeResourceLocation(value.profileId);
                buf.writeEnum(value.beat);
                buf.writeEnum(value.target);
                buf.writeDouble(value.x);
                buf.writeDouble(value.y);
                buf.writeDouble(value.z);
                buf.writeLong(value.seed);
            },
            buf -> new DJWeaponSoundBroadcastPayload(buf.readVarLong(), buf.readUUID(), buf.readVarLong(),
                    buf.readEnum(InteractionHand.class),
                    buf.readEnum(DJWeaponSoundSemantic.class), buf.readResourceLocation(),
                    buf.readEnum(BeatOutcome.class), buf.readEnum(TargetOutcome.class),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
