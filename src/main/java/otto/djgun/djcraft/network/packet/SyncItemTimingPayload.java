package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.DJItemTimingProfile;

import java.util.LinkedHashMap;
import java.util.Map;

public record SyncItemTimingPayload(Map<ResourceLocation, DJItemTimingProfile> profiles)
        implements CustomPacketPayload {
    private static final int MAX_PROFILES = 65_536;

    public static final Type<SyncItemTimingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "sync_item_timing"));

    public static final StreamCodec<FriendlyByteBuf, SyncItemTimingPayload> CODEC = StreamCodec.of(
            SyncItemTimingPayload::encode,
            SyncItemTimingPayload::decode);

    public SyncItemTimingPayload {
        profiles = Map.copyOf(profiles);
    }

    private static void encode(FriendlyByteBuf buffer, SyncItemTimingPayload payload) {
        if (payload.profiles().size() > MAX_PROFILES) {
            throw new IllegalArgumentException("Too many item timing profiles");
        }

        buffer.writeVarInt(payload.profiles().size());
        payload.profiles().forEach((itemId, profile) -> {
            buffer.writeResourceLocation(itemId);
            int flags = (profile.beatCooldown() != null ? 1 : 0)
                    | (profile.switchWarmup() != null ? 2 : 0)
                    | (profile.attackEnergyCost() != null ? 4 : 0)
                    | (profile.useEnergyCost() != null ? 8 : 0)
                    | (profile.useBeatCooldown() != null ? 16 : 0);
            buffer.writeByte(flags);
            if (profile.beatCooldown() != null) {
                buffer.writeVarInt(profile.beatCooldown());
            }
            if (profile.switchWarmup() != null) {
                buffer.writeVarInt(profile.switchWarmup());
            }
            if (profile.attackEnergyCost() != null) {
                buffer.writeDouble(profile.attackEnergyCost());
            }
            if (profile.useEnergyCost() != null) {
                buffer.writeDouble(profile.useEnergyCost());
            }
            if (profile.useBeatCooldown() != null) {
                buffer.writeVarInt(profile.useBeatCooldown());
            }
        });
    }

    private static SyncItemTimingPayload decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_PROFILES) {
            throw new IllegalArgumentException("Invalid item timing profile count: " + size);
        }

        Map<ResourceLocation, DJItemTimingProfile> profiles = new LinkedHashMap<>(size);
        for (int index = 0; index < size; index++) {
            ResourceLocation itemId = buffer.readResourceLocation();
            int flags = buffer.readUnsignedByte();
            if (flags < 1 || (flags & ~31) != 0) {
                throw new IllegalArgumentException("Invalid item timing flags: " + flags);
            }
            Integer beatCooldown = (flags & 1) != 0 ? buffer.readVarInt() : null;
            Integer switchWarmup = (flags & 2) != 0 ? buffer.readVarInt() : null;
            Double attackEnergyCost = (flags & 4) != 0 ? buffer.readDouble() : null;
            Double useEnergyCost = (flags & 8) != 0 ? buffer.readDouble() : null;
            Integer useBeatCooldown = (flags & 16) != 0 ? buffer.readVarInt() : null;
            DJItemTimingProfile profile = new DJItemTimingProfile(
                    beatCooldown, useBeatCooldown, switchWarmup, attackEnergyCost, useEnergyCost);
            if (profiles.put(itemId, profile) != null) {
                throw new IllegalArgumentException("Duplicate item timing profile: " + itemId);
            }
        }
        return new SyncItemTimingPayload(profiles);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
