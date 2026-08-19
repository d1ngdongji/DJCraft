package otto.djgun.djcraft.client.animation;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/** Client-only registration event fired once during client setup. */
public final class RegisterDJAnimationSemanticsEvent extends Event {
    /**
     * Registers a semantic whose fallback is a no-op pose.
     */
    public DJAnimationSemantic register(ResourceLocation id, DJAnimationProfile.Channel channel,
            int priority, double defaultDurationBeats) {
        return register(id, channel, priority, defaultDurationBeats, 0, 0, 0, 0);
    }

    /**
     * Registers a semantic and its safe Java fallback transform.
     */
    public DJAnimationSemantic register(ResourceLocation id, DJAnimationProfile.Channel channel,
            int priority, double defaultDurationBeats, float translationYBlocks, float translationZBlocks,
            float rotationXDegrees, float rotationZDegrees) {
        return DJAnimationSemantic.register(id, channel, priority, defaultDurationBeats,
                translationYBlocks, translationZBlocks, rotationXDegrees, rotationZDegrees);
    }
}
