package otto.djgun.djcraft.hud;

import java.util.HashMap;
import java.util.Map;

/** Pure color-frequency math used while loading combo textures. */
final class DJComboTextureColor {
    private DJComboTextureColor() {
    }

    /** Returns the most frequent RGB value among non-transparent ABGR pixels, or -1 when none are visible. */
    static int dominantVisibleRgb(int[] abgrPixels) {
        Map<Integer, Integer> frequencies = new HashMap<>();
        int dominantRgb = -1;
        int dominantCount = 0;
        for (int pixel : abgrPixels) {
            if ((pixel >>> 24) == 0) {
                continue;
            }
            int rgb = ((pixel & 0xFF) << 16) | (pixel & 0xFF00) | ((pixel >>> 16) & 0xFF);
            int count = frequencies.merge(rgb, 1, Integer::sum);
            if (count > dominantCount) {
                dominantRgb = rgb;
                dominantCount = count;
            }
        }
        return dominantRgb;
    }
}
