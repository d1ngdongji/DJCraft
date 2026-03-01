package otto.djgun.djcraft.data;

import com.google.gson.annotations.SerializedName;

/**
 * 曲目包元信息
 * 包含曲目的基本配置信息
 */
public record TrackMeta(
        String version,
        String author,
        int bpm,
        String difficulty,
        @SerializedName("sound_file") String soundFile,
        @SerializedName("offset_ms") int offsetMs,
        @SerializedName("total_duration_ms") int totalDurationMs) {
    /**
     * 创建默认元信息
     */
    public static TrackMeta createDefault() {
        return new TrackMeta("1.0", "Unknown", 120, "normal", "track.ogg", 0, 180000);
    }
}
