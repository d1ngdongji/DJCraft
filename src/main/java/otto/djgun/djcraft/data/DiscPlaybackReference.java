package otto.djgun.djcraft.data;

import java.util.UUID;

/** Identifies one physical disc, with slots used only as a legacy/fallback locator. */
public record DiscPlaybackReference(String trackId, UUID discId, int jukeboxInventorySlot, int discSlot) {
    public DiscPlaybackReference withDiscId(UUID resolvedId) {
        return new DiscPlaybackReference(trackId, resolvedId, jukeboxInventorySlot, discSlot);
    }
}
