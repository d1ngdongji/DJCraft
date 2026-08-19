package otto.djgun.djcraft.network.packet;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.DJRayWeaponProfile;

public record SyncRayWeaponProfilesPayload(Map<ResourceLocation, DJRayWeaponProfile> profiles)
        implements CustomPacketPayload {
    private static final int MAX_PROFILES = 65_536;
    public static final Type<SyncRayWeaponProfilesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "sync_ray_weapon_profiles"));
    public static final StreamCodec<FriendlyByteBuf, SyncRayWeaponProfilesPayload> CODEC = StreamCodec.of(
            SyncRayWeaponProfilesPayload::encode, SyncRayWeaponProfilesPayload::decode);

    public SyncRayWeaponProfilesPayload {
        profiles = Map.copyOf(profiles);
    }

    private static void encode(FriendlyByteBuf buffer, SyncRayWeaponProfilesPayload payload) {
        if (payload.profiles().size() > MAX_PROFILES) {
            throw new IllegalArgumentException("Too many ray weapon profiles");
        }
        buffer.writeVarInt(payload.profiles().size());
        payload.profiles().forEach((itemId, profile) -> {
            buffer.writeResourceLocation(itemId);
            buffer.writeDouble(profile.range());
            buffer.writeDouble(profile.baseDamage());
            buffer.writeBoolean(profile.pierceEntities());
            buffer.writeResourceLocation(profile.effect());
            buffer.writeDouble(profile.horizontalAimAssistPercent());
            buffer.writeDouble(profile.verticalAimAssistPercent());
            buffer.writeVarInt(profile.autoChargeBeats());
            buffer.writeBoolean(profile.explosion() != null);
            if (profile.explosion() != null) {
                buffer.writeDouble(profile.explosion().radius());
                buffer.writeDouble(profile.explosion().damage());
                buffer.writeDouble(profile.explosion().airborneRadius());
                buffer.writeDouble(profile.explosion().airborneDamage());
                buffer.writeBoolean(profile.explosion().explodeAtMaxRange());
            }
        });
    }

    private static SyncRayWeaponProfilesPayload decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_PROFILES) {
            throw new IllegalArgumentException("Invalid ray weapon profile count: " + size);
        }
        Map<ResourceLocation, DJRayWeaponProfile> profiles = new LinkedHashMap<>(size);
        for (int index = 0; index < size; index++) {
            ResourceLocation itemId = buffer.readResourceLocation();
            double range = buffer.readDouble();
            double baseDamage = buffer.readDouble();
            boolean pierceEntities = buffer.readBoolean();
            ResourceLocation effect = buffer.readResourceLocation();
            double horizontal = buffer.readDouble();
            double vertical = buffer.readDouble();
            int autoChargeBeats = buffer.readVarInt();
            otto.djgun.djcraft.combat.DJRayExplosionProfile explosion = buffer.readBoolean()
                    ? new otto.djgun.djcraft.combat.DJRayExplosionProfile(
                            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                            buffer.readDouble(), buffer.readBoolean())
                    : null;
            DJRayWeaponProfile profile = new DJRayWeaponProfile(range, baseDamage, pierceEntities,
                    effect, horizontal, vertical, autoChargeBeats, explosion);
            if (profiles.put(itemId, profile) != null) {
                throw new IllegalArgumentException("Duplicate ray weapon profile: " + itemId);
            }
        }
        return new SyncRayWeaponProfilesPayload(profiles);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
