package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJDeferredDamageStatePayload(long sessionId, boolean pending) implements CustomPacketPayload {
    public static final Type<DJDeferredDamageStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "deferred_damage_state"));
    public static final StreamCodec<FriendlyByteBuf, DJDeferredDamageStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DJDeferredDamageStatePayload::sessionId,
            ByteBufCodecs.BOOL, DJDeferredDamageStatePayload::pending,
            DJDeferredDamageStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
