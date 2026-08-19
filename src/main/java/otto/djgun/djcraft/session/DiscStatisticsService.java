package otto.djgun.djcraft.session;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.DiscPlaybackReference;
import otto.djgun.djcraft.data.DiscStatistics;
import otto.djgun.djcraft.init.ModDataComponents;
import otto.djgun.djcraft.init.ModItems;

/** Locates physical discs and safely merges authoritative statistics into them. */
public final class DiscStatisticsService {
    private DiscStatisticsService() {
    }

    public static ResolvedDisc resolveForPlayback(ServerPlayer player, DiscPlaybackReference requested) {
        DiscLocation fallback = locationAt(player, requested.jukeboxInventorySlot(), requested.discSlot());
        UUID requestedId = requested.discId();
        DiscLocation location;
        if (requestedId == null) {
            location = fallback;
        } else {
            List<DiscLocation> matches = findAll(player, requestedId);
            if (matches.isEmpty()) {
                return null;
            }
            location = matches.size() == 1 ? matches.get(0)
                    : matches.stream().filter(candidate -> candidate.sameSlot(fallback)).findFirst().orElse(null);
            if (location == null) {
                return null;
            }
            if (matches.size() > 1) {
                requestedId = UUID.randomUUID();
                location.disc().set(ModDataComponents.DISC_ID.get(), requestedId);
                location.save();
                DJCraft.LOGGER.warn("Reassigned duplicate DJ disc UUID for {}", player.getName().getString());
            }
        }
        if (location == null || !isTrackDisc(location.disc(), requested.trackId())) {
            return null;
        }
        UUID id = location.disc().get(ModDataComponents.DISC_ID.get());
        if (id == null) {
            id = UUID.randomUUID();
            location.disc().set(ModDataComponents.DISC_ID.get(), id);
        } else if (requestedId == null && findAll(player, id).size() > 1) {
            id = UUID.randomUUID();
            location.disc().set(ModDataComponents.DISC_ID.get(), id);
            DJCraft.LOGGER.warn("Reassigned duplicate legacy DJ disc UUID for {}",
                    player.getName().getString());
        }
        DiscStatisticsPendingData pending = pending(player.getServer());
        DiscStatistics statistics = location.disc().getOrDefault(
                ModDataComponents.DISC_STATISTICS.get(), DiscStatistics.EMPTY).merge(pending.get(id));
        location.disc().set(ModDataComponents.DISC_STATISTICS.get(), statistics);
        location.save();
        pending.remove(id);
        return new ResolvedDisc(new DiscPlaybackReference(requested.trackId(), id,
                location.jukeboxSlot(), location.discSlot()), statistics);
    }

    public static void write(ServerPlayer player, UUID discId, DiscStatistics statistics) {
        DiscStatisticsPendingData pending = pending(player.getServer());
        DiscLocation location = findAll(player, discId).stream().findFirst().orElse(null);
        if (location == null) {
            pending.merge(discId, statistics);
            return;
        }
        DiscStatistics merged = location.disc().getOrDefault(
                ModDataComponents.DISC_STATISTICS.get(), DiscStatistics.EMPTY)
                .merge(pending.get(discId)).merge(statistics);
        location.disc().set(ModDataComponents.DISC_STATISTICS.get(), merged);
        location.save();
        pending.remove(discId);
    }

    public static void reconcile(MinecraftServer server) {
        DiscStatisticsPendingData pending = pending(server);
        if (pending.snapshot().isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (var entry : pending.snapshot().entrySet()) {
                DiscLocation location = findAll(player, entry.getKey()).stream().findFirst().orElse(null);
                if (location != null) {
                    DiscStatistics merged = location.disc().getOrDefault(
                            ModDataComponents.DISC_STATISTICS.get(), DiscStatistics.EMPTY).merge(entry.getValue());
                    location.disc().set(ModDataComponents.DISC_STATISTICS.get(), merged);
                    location.save();
                    pending.remove(entry.getKey());
                }
            }
        }
    }

    private static DiscStatisticsPendingData pending(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                DiscStatisticsPendingData.FACTORY, DiscStatisticsPendingData.NAME);
    }

    private static List<DiscLocation> findAll(ServerPlayer player, UUID discId) {
        List<DiscLocation> found = new ArrayList<>();
        for (int jukeboxSlot = 0; jukeboxSlot < player.getInventory().getContainerSize(); jukeboxSlot++) {
            ItemStack jukebox = player.getInventory().getItem(jukeboxSlot);
            if (discId.equals(jukebox.get(ModDataComponents.DISC_ID.get()))) {
                found.add(new DiscLocation(jukeboxSlot, -1, null, null, jukebox));
            }
            if (!jukebox.is(ModItems.PORTABLE_JUKEBOX.get())) {
                continue;
            }
            NonNullList<ItemStack> items = contents(jukebox);
            for (int discSlot = 0; discSlot < items.size(); discSlot++) {
                ItemStack disc = items.get(discSlot);
                if (discId.equals(disc.get(ModDataComponents.DISC_ID.get()))) {
                    found.add(new DiscLocation(jukeboxSlot, discSlot, jukebox, items, disc));
                }
            }
        }
        return found;
    }

    private static DiscLocation locationAt(ServerPlayer player, int jukeboxSlot, int discSlot) {
        if (jukeboxSlot < 0 || jukeboxSlot >= player.getInventory().getContainerSize()
                || discSlot < 0 || discSlot >= 54) {
            return null;
        }
        ItemStack jukebox = player.getInventory().getItem(jukeboxSlot);
        if (!jukebox.is(ModItems.PORTABLE_JUKEBOX.get())) {
            return null;
        }
        NonNullList<ItemStack> items = contents(jukebox);
        return new DiscLocation(jukeboxSlot, discSlot, jukebox, items, items.get(discSlot));
    }

    private static NonNullList<ItemStack> contents(ItemStack jukebox) {
        NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);
        jukebox.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        return items;
    }

    private static boolean isTrackDisc(ItemStack disc, String trackId) {
        return disc.is(ModItems.EMPTY_DISC.get())
                && trackId != null && trackId.equals(disc.get(ModDataComponents.TRACK_PACK_ID.get()));
    }

    public record ResolvedDisc(DiscPlaybackReference reference, DiscStatistics statistics) {
    }

    private record DiscLocation(int jukeboxSlot, int discSlot, ItemStack jukebox,
            NonNullList<ItemStack> items, ItemStack disc) {
        void save() {
            if (jukebox != null && items != null) {
                jukebox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
            }
        }

        boolean sameSlot(DiscLocation other) {
            return other != null && jukeboxSlot == other.jukeboxSlot && discSlot == other.discSlot;
        }
    }
}
