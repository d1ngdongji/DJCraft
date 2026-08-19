package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

import java.util.LinkedHashMap;
import java.util.Map;

public record SyncItemBehaviorPayload(Map<ResourceLocation, ResourceLocation> overrides)
        implements CustomPacketPayload {
    private static final int MAX_OVERRIDES = 65_536;
    public static final Type<SyncItemBehaviorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "sync_item_behaviors"));
    public static final StreamCodec<FriendlyByteBuf, SyncItemBehaviorPayload> CODEC = StreamCodec.of(
            SyncItemBehaviorPayload::encode, SyncItemBehaviorPayload::decode);

    public SyncItemBehaviorPayload {
        overrides = Map.copyOf(overrides);
    }

    private static void encode(FriendlyByteBuf buffer, SyncItemBehaviorPayload payload) {
        if (payload.overrides().size() > MAX_OVERRIDES) {
            throw new IllegalArgumentException("Too many item behavior overrides");
        }
        buffer.writeVarInt(payload.overrides().size());
        payload.overrides().forEach((itemId, behaviorId) -> {
            buffer.writeResourceLocation(itemId);
            buffer.writeResourceLocation(behaviorId);
        });
    }

    private static SyncItemBehaviorPayload decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_OVERRIDES) {
            throw new IllegalArgumentException("Invalid item behavior override count: " + size);
        }
        Map<ResourceLocation, ResourceLocation> overrides = new LinkedHashMap<>(size);
        for (int index = 0; index < size; index++) {
            ResourceLocation itemId = buffer.readResourceLocation();
            ResourceLocation behaviorId = buffer.readResourceLocation();
            if (overrides.put(itemId, behaviorId) != null) {
                throw new IllegalArgumentException("Duplicate item behavior override: " + itemId);
            }
        }
        return new SyncItemBehaviorPayload(overrides);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
