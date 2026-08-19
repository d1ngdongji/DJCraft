package otto.djgun.djcraft.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import otto.djgun.djcraft.DJCraft;

public record DJCraftingSelectTrackPayload(String trackId, InteractionHand hand, BlockPos tablePos)
        implements CustomPacketPayload {
    public static final Type<DJCraftingSelectTrackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dj_crafting_select_track"));
    public static final StreamCodec<FriendlyByteBuf, DJCraftingSelectTrackPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.trackId(), 128);
                buf.writeEnum(payload.hand());
                buf.writeBlockPos(payload.tablePos());
            },
            buf -> new DJCraftingSelectTrackPayload(buf.readUtf(128), buf.readEnum(InteractionHand.class),
                    buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
