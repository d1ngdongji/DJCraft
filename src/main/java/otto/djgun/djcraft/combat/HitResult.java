package otto.djgun.djcraft.combat;

import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.session.DJSessionClient;

/**
 * 节拍判定结果
 */
public record HitResult(boolean isHit, float damageModifier, BeatDefinition beatData, BeatEvent beatEvent) {

    public static HitResult miss() {
        return new HitResult(false, 0.0f, null, null);
    }

    public static HitResult miss(BeatDefinition beatData, BeatEvent beatEvent) {
        return new HitResult(false, 0.0f, beatData, beatEvent);
    }
}
