package otto.djgun.djcraft.init;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.DiscStatistics;
import otto.djgun.djcraft.data.DiscStatisticsCodec;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister
            .create(BuiltInRegistries.DATA_COMPONENT_TYPE, DJCraft.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TRACK_PACK_ID = DATA_COMPONENTS
            .register("track_pack_id",
                    () -> DataComponentType.<String>builder().persistent(Codec.STRING).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> DISC_ID = DATA_COMPONENTS
            .register("disc_id", () -> DataComponentType.<java.util.UUID>builder()
                    .persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DiscStatistics>> DISC_STATISTICS = DATA_COMPONENTS
            .register("disc_statistics", () -> DataComponentType.<DiscStatistics>builder()
                    .persistent(DiscStatisticsCodec.CODEC).build());
}
