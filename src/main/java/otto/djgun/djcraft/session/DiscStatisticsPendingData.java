package otto.djgun.djcraft.session;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import otto.djgun.djcraft.data.DiscStatistics;

final class DiscStatisticsPendingData extends SavedData {
    static final String NAME = "djcraft_disc_statistics";
    static final Factory<DiscStatisticsPendingData> FACTORY = new Factory<>(
            DiscStatisticsPendingData::new, DiscStatisticsPendingData::load);

    private final Map<UUID, DiscStatistics> pending = new HashMap<>();

    DiscStatistics get(UUID id) {
        return pending.getOrDefault(id, DiscStatistics.EMPTY);
    }

    void merge(UUID id, DiscStatistics statistics) {
        DiscStatistics merged = get(id).merge(statistics);
        if (!merged.equals(pending.get(id))) {
            pending.put(id, merged);
            setDirty();
        }
    }

    void remove(UUID id) {
        if (pending.remove(id) != null) {
            setDirty();
        }
    }

    Map<UUID, DiscStatistics> snapshot() {
        return Map.copyOf(pending);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        pending.forEach((id, statistics) -> {
            CompoundTag value = new CompoundTag();
            value.putInt("max_combo", statistics.maxCombo());
            value.putLong("total_play_time_ms", statistics.totalPlayTimeMs());
            tag.put(id.toString(), value);
        });
        return tag;
    }

    private static DiscStatisticsPendingData load(CompoundTag tag, HolderLookup.Provider registries) {
        DiscStatisticsPendingData data = new DiscStatisticsPendingData();
        for (String key : tag.getAllKeys()) {
            try {
                UUID id = UUID.fromString(key);
                CompoundTag value = tag.getCompound(key);
                data.pending.put(id, new DiscStatistics(
                        value.getInt("max_combo"), value.getLong("total_play_time_ms")));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy entries; the item data remains authoritative.
            }
        }
        return data;
    }
}
