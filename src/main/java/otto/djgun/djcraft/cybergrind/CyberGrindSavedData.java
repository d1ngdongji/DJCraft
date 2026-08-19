package otto.djgun.djcraft.cybergrind;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;

final class CyberGrindSavedData extends SavedData {
    static final String NAME = "djcraft_cyber_grind";
    static final Factory<CyberGrindSavedData> FACTORY = new Factory<>(
            CyberGrindSavedData::new, CyberGrindSavedData::load);

    private final Map<UUID, ReturnPoint> returns = new HashMap<>();
    private final Map<String, Map<UUID, Integer>> personalBests = new HashMap<>();
    private final Map<String, Map<String, Integer>> groupBests = new HashMap<>();

    Optional<ReturnPoint> returnPoint(UUID playerId) {
        return Optional.ofNullable(returns.get(playerId));
    }

    void putReturn(UUID playerId, ReturnPoint point) {
        returns.put(playerId, point);
        setDirty();
    }

    void removeReturn(UUID playerId) {
        if (returns.remove(playerId) != null) {
            setDirty();
        }
    }

    int updatePersonal(String profileId, UUID playerId, int completedWaves) {
        Map<UUID, Integer> profile = personalBests.computeIfAbsent(profileId, ignored -> new HashMap<>());
        int best = Math.max(profile.getOrDefault(playerId, 0), completedWaves);
        if (best != profile.getOrDefault(playerId, 0)) {
            profile.put(playerId, best);
            setDirty();
        }
        return best;
    }

    int updateGroup(String profileId, List<UUID> roster, int completedWaves) {
        String key = roster.stream().sorted().map(UUID::toString).reduce((left, right) -> left + "," + right)
                .orElse("");
        Map<String, Integer> profile = groupBests.computeIfAbsent(profileId, ignored -> new HashMap<>());
        int best = Math.max(profile.getOrDefault(key, 0), completedWaves);
        if (best != profile.getOrDefault(key, 0)) {
            profile.put(key, best);
            setDirty();
        }
        return best;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag returnTag = new CompoundTag();
        returns.forEach((id, point) -> returnTag.put(id.toString(), point.save()));
        tag.put("returns", returnTag);

        CompoundTag personalTag = new CompoundTag();
        personalBests.forEach((profileId, values) -> {
            CompoundTag profile = new CompoundTag();
            values.forEach((id, best) -> profile.putInt(id.toString(), best));
            personalTag.put(profileId, profile);
        });
        tag.put("personal_bests", personalTag);

        CompoundTag groupTag = new CompoundTag();
        groupBests.forEach((profileId, values) -> {
            CompoundTag profile = new CompoundTag();
            values.forEach(profile::putInt);
            groupTag.put(profileId, profile);
        });
        tag.put("group_bests", groupTag);
        return tag;
    }

    static CyberGrindSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CyberGrindSavedData data = new CyberGrindSavedData();
        CompoundTag returnTag = tag.getCompound("returns");
        for (String key : returnTag.getAllKeys()) {
            try {
                ReturnPoint.load(returnTag.getCompound(key)).ifPresent(point ->
                        data.returns.put(UUID.fromString(key), point));
            } catch (IllegalArgumentException ignored) {
            }
        }
        loadPersonal(tag.getCompound("personal_bests"), data.personalBests);
        loadGroups(tag.getCompound("group_bests"), data.groupBests);
        return data;
    }

    private static void loadPersonal(CompoundTag root, Map<String, Map<UUID, Integer>> destination) {
        for (String profileId : root.getAllKeys()) {
            CompoundTag profile = root.getCompound(profileId);
            Map<UUID, Integer> values = new HashMap<>();
            for (String key : profile.getAllKeys()) {
                try {
                    values.put(UUID.fromString(key), Math.max(0, profile.getInt(key)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            destination.put(profileId, values);
        }
    }

    private static void loadGroups(CompoundTag root, Map<String, Map<String, Integer>> destination) {
        for (String profileId : root.getAllKeys()) {
            CompoundTag profile = root.getCompound(profileId);
            Map<String, Integer> values = new HashMap<>();
            for (String key : profile.getAllKeys()) {
                values.put(key, Math.max(0, profile.getInt(key)));
            }
            destination.put(profileId, values);
        }
    }

    record ReturnPoint(ResourceLocation dimension, double x, double y, double z, float yaw, float pitch) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimension.toString());
            tag.putDouble("x", x);
            tag.putDouble("y", y);
            tag.putDouble("z", z);
            tag.putFloat("yaw", yaw);
            tag.putFloat("pitch", pitch);
            return tag;
        }

        static Optional<ReturnPoint> load(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            if (dimension == null) {
                return Optional.empty();
            }
            return Optional.of(new ReturnPoint(dimension, tag.getDouble("x"), tag.getDouble("y"),
                    tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch")));
        }
    }
}
