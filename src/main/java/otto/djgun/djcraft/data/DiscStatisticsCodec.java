package otto.djgun.djcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public final class DiscStatisticsCodec {
    public static final Codec<DiscStatistics> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("max_combo", 0).forGetter(DiscStatistics::maxCombo),
            Codec.LONG.optionalFieldOf("total_play_time_ms", 0L).forGetter(DiscStatistics::totalPlayTimeMs))
            .apply(instance, DiscStatistics::new));

    private DiscStatisticsCodec() {
    }
}
