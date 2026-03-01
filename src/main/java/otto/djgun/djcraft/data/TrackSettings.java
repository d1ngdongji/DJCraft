package otto.djgun.djcraft.data;

/**
 * 曲目包设置
 * 控制与该曲目包相关的客户端表现，如准星模式
 */
public record TrackSettings(
        String crosshairMode, // "time" 或 "beat"
        Integer crosshairTimeMs, // 若为 "time" 模式：节拍提前多少毫秒出现在屏幕底端
        Integer crosshairBeatCount, // 若为 "beat" 模式：准星视野内固定显示多少个未来的节拍
        Float volumeMultiplier // 播放音量倍数
) {
    public TrackSettings {
        if (crosshairMode == null)
            crosshairMode = "time";
        if (crosshairTimeMs == null)
            crosshairTimeMs = 1400;
        if (crosshairBeatCount == null)
            crosshairBeatCount = 4;
        if (volumeMultiplier == null)
            volumeMultiplier = 1.0f;
    }

    public static TrackSettings createDefault() {
        return new TrackSettings("time", 1400, 4, 1.0f);
    }
}
