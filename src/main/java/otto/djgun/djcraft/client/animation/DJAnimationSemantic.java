package otto.djgun.djcraft.client.animation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

/**
 * Registered first-person animation semantic.
 *
 * <p>Addons register namespaced semantics through
 * {@link RegisterDJAnimationSemanticsEvent}. Registration is client-only and
 * finishes during client setup, before animation profiles are loaded.</p>
 */
public final class DJAnimationSemantic {
    private static final Map<ResourceLocation, DJAnimationSemantic> REGISTRY = new LinkedHashMap<>();
    private static boolean frozen;

    public static final DJAnimationSemantic IDLE = builtin("idle",
            DJAnimationProfile.Channel.ACTION, 0, 1.0, 0, 0, 0, 0);
    public static final DJAnimationSemantic MELEE_STRIKE = builtin("melee_strike",
            DJAnimationProfile.Channel.IMPULSE, 100, 0.55, -0.1125f, 0.125f, 0, 62);
    public static final DJAnimationSemantic MELEE_THRUST = builtin("melee_thrust",
            DJAnimationProfile.Channel.IMPULSE, 100, 0.5, -0.15f, 0.375f, 0, 90);
    public static final DJAnimationSemantic MELEE_SWEEP = builtin("melee_sweep",
            DJAnimationProfile.Channel.IMPULSE, 100, 0.65, -0.1f, 0.09375f, 6, 78);
    public static final DJAnimationSemantic MELEE_CRITICAL = builtin("melee_critical",
            DJAnimationProfile.Channel.IMPULSE, 100, 0.7, -0.275f, 0.14375f, 4, 168);
    public static final DJAnimationSemantic TRIGGER_IMPACT = builtin("trigger_impact",
            DJAnimationProfile.Channel.IMPULSE, 100, 0.55, 0.025f, -0.12f, -8, 2.5f);
    public static final DJAnimationSemantic CHARGE_START = builtin("charge_start",
            DJAnimationProfile.Channel.ACTION, 60, 1.0, 0.025f, -0.055f, -7, 1.5f);
    public static final DJAnimationSemantic CHARGE_RELEASE = builtin("charge_release",
            DJAnimationProfile.Channel.IMPULSE, 100, 0.45, 0.02f, -0.15f, -10, 3);
    public static final DJAnimationSemantic UNEQUIP_START = builtin("unequip_start",
            DJAnimationProfile.Channel.TRANSITION, 80, 1.0, -0.969f, 0, 0, 0);
    public static final DJAnimationSemantic EQUIP_START = builtin("equip_start",
            DJAnimationProfile.Channel.TRANSITION, 80, 1.0, -0.969f, 0, 0, 0);
    public static final DJAnimationSemantic RELOAD_START = builtin("reload_start",
            DJAnimationProfile.Channel.ACTION, 90, 2.0, -0.18f, 0.08f, 14, 5);
    public static final DJAnimationSemantic INSPECT_START = builtin("inspect_start",
            DJAnimationProfile.Channel.ACTION, 10, 2.0, 0.025f, 0.04f, 8, 8);
    public static final DJAnimationSemantic USE = builtin("use",
            DJAnimationProfile.Channel.ACTION, 60, 1.0, 0, 0, 0, 0);
    public static final DJAnimationSemantic USE_START = builtin("use_start",
            DJAnimationProfile.Channel.ACTION, 60, 1.0, 0.035f, -0.035f, -9, 2);
    public static final DJAnimationSemantic USE_RELEASE = builtin("use_release",
            DJAnimationProfile.Channel.IMPULSE, 100, 0.35, 0.015f, -0.08f, -5, 2);
    public static final DJAnimationSemantic PARRY = builtin("parry",
            DJAnimationProfile.Channel.IMPULSE, 100, 0.35, 0, 0, 0, 0);
    public static final DJAnimationSemantic READY = builtin("ready",
            DJAnimationProfile.Channel.ACTION, 100, 0, 0, 0, 0, 0);
    public static final DJAnimationSemantic CANCEL = builtin("cancel",
            DJAnimationProfile.Channel.ACTION, 100, 0, 0, 0, 0, 0);

    private final ResourceLocation id;
    private final DJAnimationProfile.Channel channel;
    private final int priority;
    private final double defaultDurationBeats;
    private final float translationYBlocks;
    private final float translationZBlocks;
    private final float rotationXDegrees;
    private final float rotationZDegrees;

    private DJAnimationSemantic(ResourceLocation id, DJAnimationProfile.Channel channel, int priority,
            double defaultDurationBeats, float translationYBlocks, float translationZBlocks,
            float rotationXDegrees, float rotationZDegrees) {
        if (id == null || channel == null || priority < 0 || priority > 100
                || !Double.isFinite(defaultDurationBeats) || defaultDurationBeats < 0
                || !Float.isFinite(translationYBlocks) || !Float.isFinite(translationZBlocks)
                || !Float.isFinite(rotationXDegrees) || !Float.isFinite(rotationZDegrees)) {
            throw new IllegalArgumentException("Invalid animation semantic");
        }
        this.id = id;
        this.channel = channel;
        this.priority = priority;
        this.defaultDurationBeats = defaultDurationBeats;
        this.translationYBlocks = translationYBlocks;
        this.translationZBlocks = translationZBlocks;
        this.rotationXDegrees = rotationXDegrees;
        this.rotationZDegrees = rotationZDegrees;
    }

    static synchronized DJAnimationSemantic register(ResourceLocation id, DJAnimationProfile.Channel channel,
            int priority, double defaultDurationBeats, float translationYBlocks, float translationZBlocks,
            float rotationXDegrees, float rotationZDegrees) {
        if (frozen) {
            throw new IllegalStateException("Animation semantics are already frozen");
        }
        DJAnimationSemantic semantic = new DJAnimationSemantic(id, channel, priority, defaultDurationBeats,
                translationYBlocks, translationZBlocks, rotationXDegrees, rotationZDegrees);
        if (REGISTRY.putIfAbsent(id, semantic) != null) {
            throw new IllegalArgumentException("Duplicate animation semantic " + id);
        }
        return semantic;
    }

    /** Finalizes registration. Called by DJCraft during client setup. */
    public static synchronized void freeze() {
        frozen = true;
    }

    public static synchronized DJAnimationSemantic get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static synchronized List<DJAnimationSemantic> values() {
        return List.copyOf(REGISTRY.values());
    }

    static DJAnimationSemantic parse(String name) {
        ResourceLocation id = name.indexOf(':') >= 0
                ? ResourceLocation.parse(name)
                : ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, name);
        DJAnimationSemantic semantic = get(id);
        if (semantic == null) {
            throw new IllegalArgumentException("Unknown animation semantic " + id);
        }
        return semantic;
    }

    private static DJAnimationSemantic builtin(String path, DJAnimationProfile.Channel channel, int priority,
            double defaultDurationBeats, float translationYBlocks, float translationZBlocks,
            float rotationXDegrees, float rotationZDegrees) {
        return register(ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, path), channel, priority,
                defaultDurationBeats, translationYBlocks, translationZBlocks,
                rotationXDegrees, rotationZDegrees);
    }

    public ResourceLocation id() {
        return id;
    }

    public DJAnimationProfile.Channel channel() {
        return channel;
    }

    public int priority() {
        return priority;
    }

    public double defaultDurationBeats() {
        return defaultDurationBeats;
    }

    public String serializedName() {
        return DJCraft.MODID.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    float translationYBlocks() {
        return translationYBlocks;
    }

    float translationZBlocks() {
        return translationZBlocks;
    }

    float rotationXDegrees() {
        return rotationXDegrees;
    }

    float rotationZDegrees() {
        return rotationZDegrees;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
