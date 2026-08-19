package otto.djgun.djcraft.network.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.DJCraft;

public record DJRayEffectPayload(long sequence, UUID shooterId, InteractionHand hand,
        ResourceLocation effect, Vec3 origin, Vec3 end, List<Vec3> contacts,
        double shockwaveRadius) implements CustomPacketPayload {
    public static final int MAX_CONTACTS = 4_096;
    public static final Type<DJRayEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "ray_effect"));
    public static final StreamCodec<FriendlyByteBuf, DJRayEffectPayload> CODEC = StreamCodec.of(
            DJRayEffectPayload::encode, DJRayEffectPayload::decode);

    public DJRayEffectPayload {
        contacts = List.copyOf(contacts);
        if (contacts.size() > MAX_CONTACTS) {
            throw new IllegalArgumentException("Too many ray contacts");
        }
        if (!Double.isFinite(shockwaveRadius) || shockwaveRadius < 0.0 || shockwaveRadius > 64.0) {
            throw new IllegalArgumentException("Invalid shockwave radius");
        }
    }

    public DJRayEffectPayload(long sequence, UUID shooterId, InteractionHand hand,
            ResourceLocation effect, Vec3 origin, Vec3 end, List<Vec3> contacts) {
        this(sequence, shooterId, hand, effect, origin, end, contacts, 0.0);
    }

    private static void encode(FriendlyByteBuf buffer, DJRayEffectPayload payload) {
        buffer.writeVarLong(payload.sequence());
        buffer.writeUUID(payload.shooterId());
        buffer.writeEnum(payload.hand());
        buffer.writeResourceLocation(payload.effect());
        buffer.writeVec3(payload.origin());
        buffer.writeVec3(payload.end());
        buffer.writeVarInt(payload.contacts().size());
        payload.contacts().forEach(buffer::writeVec3);
        buffer.writeDouble(payload.shockwaveRadius());
    }

    private static DJRayEffectPayload decode(FriendlyByteBuf buffer) {
        long sequence = buffer.readVarLong();
        UUID shooterId = buffer.readUUID();
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        ResourceLocation effect = buffer.readResourceLocation();
        Vec3 origin = buffer.readVec3();
        Vec3 end = buffer.readVec3();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_CONTACTS) {
            throw new IllegalArgumentException("Invalid ray contact count: " + size);
        }
        List<Vec3> contacts = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            contacts.add(buffer.readVec3());
        }
        return new DJRayEffectPayload(sequence, shooterId, hand, effect, origin, end, contacts,
                buffer.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
