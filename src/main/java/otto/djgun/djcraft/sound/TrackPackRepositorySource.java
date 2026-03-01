package otto.djgun.djcraft.sound;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import otto.djgun.djcraft.DJCraft;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 动态曲目包资源源
 * 将 TrackPackResources 注册为 Minecraft 资源包
 */
public class TrackPackRepositorySource implements RepositorySource {

    @Override
    public void loadPacks(Consumer<Pack> packConsumer) {
        PackLocationInfo info = new PackLocationInfo(
                "djcraft_tracks",
                Component.literal("DJCraft TrackPacks"),
                PackSource.BUILT_IN,
                Optional.empty());

        Pack.ResourcesSupplier resourcesSupplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo locationInfo) {
                return new TrackPackResources(locationInfo);
            }

            @Override
            public PackResources openFull(PackLocationInfo locationInfo, Pack.Metadata metadata) {
                return new TrackPackResources(locationInfo);
            }
        };

        Pack pack = Pack.readMetaAndCreate(
                info,
                resourcesSupplier,
                PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false));

        if (pack != null) {
            packConsumer.accept(pack);
            DJCraft.LOGGER.info("Registered DJCraft TrackPack ResourcePack");
        }
    }
}
