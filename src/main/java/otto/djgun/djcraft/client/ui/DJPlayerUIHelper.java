package otto.djgun.djcraft.client.ui;

public class DJPlayerUIHelper {
    public static void openPlayerUI(net.minecraft.world.item.ItemStack jukeboxStack, int jukeboxSlot) {
        java.util.List<DiscPlayerEntry> entries = new java.util.ArrayList<>();
        if (jukeboxStack != null) {
            net.minecraft.world.item.component.ItemContainerContents contents = jukeboxStack.getOrDefault(
                    net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.EMPTY);
            net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items = net.minecraft.core.NonNullList
                    .withSize(54, net.minecraft.world.item.ItemStack.EMPTY);
            contents.copyInto(items);
            for (int discSlot = 0; discSlot < items.size(); discSlot++) {
                net.minecraft.world.item.ItemStack discBase = items.get(discSlot);
                if (!discBase.isEmpty() && discBase.is(otto.djgun.djcraft.init.ModItems.EMPTY_DISC.get())) {
                    String packId = discBase.get(otto.djgun.djcraft.init.ModDataComponents.TRACK_PACK_ID.get());
                    if (packId != null && !packId.trim().isEmpty()) {
                        java.util.UUID discId = discBase.get(otto.djgun.djcraft.init.ModDataComponents.DISC_ID.get());
                        var statistics = discBase.getOrDefault(
                                otto.djgun.djcraft.init.ModDataComponents.DISC_STATISTICS.get(),
                                otto.djgun.djcraft.data.DiscStatistics.EMPTY);
                        long snapshotPlaybackMs = 0L;
                        var manager = otto.djgun.djcraft.session.DJModeManagerClient.getInstance();
                        if (discId != null && discId.equals(manager.getCurrentDiscId())) {
                            snapshotPlaybackMs = manager.getActiveSession()
                                    .map(otto.djgun.djcraft.session.DJSessionClient::getPlaybackTimeMs).orElse(0L);
                        }
                        entries.add(new DiscPlayerEntry(
                                new otto.djgun.djcraft.data.DiscPlaybackReference(
                                        packId, discId, jukeboxSlot, discSlot),
                                statistics, snapshotPlaybackMs));
                    }
                }
            }
        }
        ClientScreenBridge.openScreen(new DJPlayerFragment(entries));
    }
}
