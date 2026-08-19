package otto.djgun.djcraft.combat;

import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatEvent;
/**
 * 节拍判定结果
 */
public record HitResult(boolean isHit, BeatDefinition beatData, BeatEvent beatEvent,
        int beatIndex, long judgedAtMs) {

    public static HitResult miss(long judgedAtMs) {
        return new HitResult(false, null, null, -1, judgedAtMs);
    }

    public static HitResult miss(BeatDefinition beatData, BeatEvent beatEvent, int beatIndex, long judgedAtMs) {
        return new HitResult(false, beatData, beatEvent, beatIndex, judgedAtMs);
    }
}
