package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJGroupCreatePayload(int jukeboxSlot) implements CustomPacketPayload {
    public static final Type<DJGroupCreatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "group_create"));
    public static final StreamCodec<FriendlyByteBuf, DJGroupCreatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DJGroupCreatePayload::jukeboxSlot, DJGroupCreatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
