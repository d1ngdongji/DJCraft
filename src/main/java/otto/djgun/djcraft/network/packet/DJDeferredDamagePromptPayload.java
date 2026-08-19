package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJDeferredDamagePromptPayload(long sessionId) implements CustomPacketPayload {
    public static final Type<DJDeferredDamagePromptPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "deferred_damage_prompt"));
    public static final StreamCodec<FriendlyByteBuf, DJDeferredDamagePromptPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DJDeferredDamagePromptPayload::sessionId,
            DJDeferredDamagePromptPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
