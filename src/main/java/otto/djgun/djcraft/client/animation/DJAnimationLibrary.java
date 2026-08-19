package otto.djgun.djcraft.client.animation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;
import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;

/**
 * Loads Blockbench/GeckoLib clips and DJCraft semantic profiles as one immutable
 * client resource snapshot.
 */
public final class DJAnimationLibrary extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final DJAnimationLibrary INSTANCE = new DJAnimationLibrary();
    private static final String PROFILE_DIRECTORY = "djcraft/animation_profiles";
    private static final ResourceLocation GENERIC_PROFILE =
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "generic");
    private static final Comparator<LoadedProfile> PROFILE_ORDER =
            Comparator.comparingInt(LoadedProfile::priority).reversed()
                    .thenComparing(profile -> profile.id().toString());

    private volatile Snapshot snapshot = Snapshot.empty();
    private final Set<String> warnedConflicts = new HashSet<>();

    private DJAnimationLibrary() {
        super(GSON, "animations");
    }

    public static DJAnimationLibrary getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> documents, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Map<String, DJAnimationCurve> curves = new HashMap<>();
        Map<String, ResourceLocation> curveSources = new HashMap<>();
        documents.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    try {
                        parseClipDocument(entry.getKey(), entry.getValue(), curves, curveSources);
                    } catch (RuntimeException exception) {
                        DJCraft.LOGGER.error("Rejected first-person animation resource {}", entry.getKey(), exception);
                    }
                });

        List<LoadedProfile> profiles = loadProfiles(resourceManager, curves);
        Snapshot loaded = Snapshot.compile(curves, profiles);
        DJAnimationClips.install(curves);
        snapshot = loaded;
        synchronized (warnedConflicts) {
            warnedConflicts.clear();
        }
        DJAnimationRuntime.getInstance().onAnimationResourcesReloaded();
        DJCraft.LOGGER.info("Loaded {} DJCraft first-person animation clips and {} profiles",
                curves.size(), profiles.size());
    }

    DJAnimationSelection resolve(DJAnimationEvent event) {
        ResourceLocation behaviorId = event.renderIdentity() instanceof Item item
                ? DJItemBehaviorManager.resolveDefinition(item).id() : DJItemBehavior.NONE.id();
        return resolve(event, behaviorId);
    }

    DJAnimationSelection resolve(DJAnimationEvent event, DJItemBehavior behavior) {
        return resolve(event, behavior.id());
    }

    DJAnimationSelection resolve(DJAnimationEvent event, ResourceLocation behaviorId) {
        DJAnimationProfile fallback = DJAnimationProfile.fallback(event.semantic());
        Binding binding = resolveBinding(
                event.itemIdentity(), event.renderIdentity(), behaviorId, event.semantic());
        if (binding == null) {
            return new DJAnimationSelection(fallback, Double.NaN);
        }
        return new DJAnimationSelection(fallback.withCurve(binding.curve()), binding.durationBeats());
    }

    IdleSelection resolveIdle(String itemIdentity, Object renderIdentity) {
        ResourceLocation behaviorId = renderIdentity instanceof Item item
                ? DJItemBehaviorManager.resolveDefinition(item).id() : DJItemBehavior.NONE.id();
        Binding binding = resolveBinding(
                itemIdentity, renderIdentity, behaviorId, DJAnimationSemantic.IDLE);
        return binding == null ? null : new IdleSelection(binding.curve(), binding.durationBeats());
    }

    void installForTests(Map<String, DJAnimationCurve> curves, List<LoadedProfile> profiles) {
        DJAnimationClips.install(curves);
        snapshot = Snapshot.compile(curves, profiles);
        synchronized (warnedConflicts) {
            warnedConflicts.clear();
        }
    }

    private Binding resolveBinding(String itemIdentity, Object renderIdentity,
            ResourceLocation behaviorId, DJAnimationSemantic semantic) {
        Snapshot current = snapshot;
        ResourceLocation itemId;
        try {
            itemId = ResourceLocation.parse(itemIdentity);
        } catch (RuntimeException ignored) {
            itemId = null;
        }

        if (itemId != null) {
            Binding exact = select(current.exactProfiles().getOrDefault(itemId, List.of()),
                    semantic, itemId + "/exact");
            if (exact != null) {
                return exact;
            }
        }
        if (renderIdentity instanceof Item item) {
            List<LoadedProfile> matchingTags = new ArrayList<>();
            for (LoadedProfile profile : current.tagProfiles()) {
                if (profile.matchesTag(item)) {
                    matchingTags.add(profile);
                }
            }
            Binding tagged = select(matchingTags, semantic,
                    (itemId == null ? "unknown" : itemId) + "/tag");
            if (tagged != null) {
                return tagged;
            }
        }
        Binding behaviorBinding = select(
                current.behaviorProfiles().getOrDefault(behaviorId, List.of()),
                semantic, (itemId == null ? "unknown" : itemId) + "/behavior");
        if (behaviorBinding != null) {
            return behaviorBinding;
        }
        return current.genericProfile() == null
                ? null : current.genericProfile().animations().get(semantic);
    }

    private Binding select(List<LoadedProfile> candidates, DJAnimationSemantic semantic, String warningKey) {
        LoadedProfile selected = null;
        Binding binding = null;
        for (LoadedProfile candidate : candidates) {
            Binding candidateBinding = candidate.animations().get(semantic);
            if (candidateBinding == null) {
                continue;
            }
            if (selected == null) {
                selected = candidate;
                binding = candidateBinding;
                continue;
            }
            if (candidate.priority() == selected.priority()) {
                warnConflictOnce(warningKey + "/" + semantic.serializedName(),
                        selected.id(), candidate.id());
            }
            break;
        }
        return binding;
    }

    private void warnConflictOnce(String key, ResourceLocation selected, ResourceLocation ignored) {
        synchronized (warnedConflicts) {
            if (warnedConflicts.add(key)) {
                DJCraft.LOGGER.warn("Animation profile conflict for {}: selected {}, ignored {}",
                        key, selected, ignored);
            }
        }
    }

    private static List<LoadedProfile> loadProfiles(ResourceManager resourceManager,
            Map<String, DJAnimationCurve> curves) {
        List<LoadedProfile> profiles = new ArrayList<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                PROFILE_DIRECTORY, location -> location.getPath().endsWith(".json"));
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ResourceLocation profileId = profileId(entry.getKey());
                    try (Reader reader = entry.getValue().openAsReader()) {
                        profiles.add(parseProfile(profileId, GSON.fromJson(reader, JsonElement.class), curves));
                    } catch (IOException | RuntimeException exception) {
                        DJCraft.LOGGER.error("Rejected first-person animation profile {}", profileId, exception);
                    }
                });
        return List.copyOf(profiles);
    }

    private static ResourceLocation profileId(ResourceLocation resource) {
        String path = resource.getPath();
        String prefix = PROFILE_DIRECTORY + "/";
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            throw new IllegalArgumentException("Invalid animation profile path " + resource);
        }
        return ResourceLocation.fromNamespaceAndPath(
                resource.getNamespace(), path.substring(prefix.length(), path.length() - ".json".length()));
    }

    static LoadedProfile parseProfile(ResourceLocation id, JsonElement element,
            Map<String, DJAnimationCurve> curves) {
        JsonObject root = GsonHelper.convertToJsonObject(element, "animation profile");
        requireOnlyKeys(root, "animation profile", Set.of("priority", "selectors", "animations"));
        int priority = GsonHelper.getAsInt(root, "priority", 0);
        if (priority < 0 || priority > 1_000) {
            throw new IllegalArgumentException("priority must be between 0 and 1000");
        }

        Set<ResourceLocation> items = new HashSet<>();
        Set<TagKey<Item>> tags = new HashSet<>();
        Set<ResourceLocation> behaviors = new HashSet<>();
        if (root.has("selectors")) {
            JsonObject selectors = GsonHelper.getAsJsonObject(root, "selectors");
            requireOnlyKeys(selectors, "animation selectors", Set.of("items", "tags", "behaviors"));
            readIds(selectors, "items").forEach(items::add);
            readIds(selectors, "tags").forEach(tagId ->
                    tags.add(TagKey.create(Registries.ITEM, tagId)));
            readIds(selectors, "behaviors").forEach(behaviorId -> {
                DJItemBehaviorRegistry.require(behaviorId);
                behaviors.add(behaviorId);
            });
        }
        if (items.isEmpty() && tags.isEmpty() && behaviors.isEmpty() && !GENERIC_PROFILE.equals(id)) {
            throw new IllegalArgumentException("Only djcraft:generic may omit selectors");
        }

        JsonObject animationsObject = GsonHelper.getAsJsonObject(root, "animations");
        Map<DJAnimationSemantic, Binding> animations = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : animationsObject.entrySet()) {
            DJAnimationSemantic semantic = DJAnimationSemantic.parse(entry.getKey());
            JsonObject animation = GsonHelper.convertToJsonObject(entry.getValue(), "animation binding");
            requireOnlyKeys(animation, "animation binding", Set.of("clip", "duration_beats"));
            String clipId = GsonHelper.getAsString(animation, "clip");
            DJAnimationCurve curve = curves.get(clipId);
            if (curve == null) {
                DJCraft.LOGGER.error("Ignoring {} binding in profile {} because clip {} is missing",
                        semantic.serializedName(), id, clipId);
                continue;
            }
            double duration = animation.has("duration_beats")
                    ? GsonHelper.getAsDouble(animation, "duration_beats") : Double.NaN;
            if (!Double.isNaN(duration) && (!Double.isFinite(duration) || duration <= 0.0)) {
                throw new IllegalArgumentException("duration_beats must be a positive finite number");
            }
            if (semantic == DJAnimationSemantic.IDLE && Double.isNaN(duration)) {
                throw new IllegalArgumentException("idle requires duration_beats");
            }
            animations.put(semantic, new Binding(curve, duration));
        }
        return new LoadedProfile(id, priority, Set.copyOf(items), Set.copyOf(tags),
                Set.copyOf(behaviors), Map.copyOf(animations));
    }

    private static void requireOnlyKeys(JsonObject object, String description, Set<String> allowed) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown " + description + " field " + key);
            }
        }
    }

    private static List<ResourceLocation> readIds(JsonObject object, String name) {
        if (!object.has(name)) {
            return List.of();
        }
        JsonArray array = GsonHelper.getAsJsonArray(object, name);
        List<ResourceLocation> result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            result.add(ResourceLocation.parse(GsonHelper.convertToString(element, name + " selector")));
        }
        return result;
    }

    private static void parseClipDocument(ResourceLocation source, JsonElement document,
            Map<String, DJAnimationCurve> output, Map<String, ResourceLocation> sources) {
        JsonObject root = GsonHelper.convertToJsonObject(document, "animation document");
        JsonObject animations = GsonHelper.getAsJsonObject(root, "animations");
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            ResourceLocation previousSource = sources.putIfAbsent(entry.getKey(), source);
            if (previousSource != null) {
                DJCraft.LOGGER.error("Ignoring duplicate animation clip {} from {}; already supplied by {}",
                        entry.getKey(), source, previousSource);
                continue;
            }
            output.put(entry.getKey(), parseClip(entry.getValue()));
        }
    }

    static DJAnimationCurve parseClip(JsonElement element) {
        JsonObject clip = GsonHelper.convertToJsonObject(element, "animation clip");
        float length = GsonHelper.getAsFloat(clip, "animation_length");
        if (!Float.isFinite(length) || length <= 0.0f) {
            throw new IllegalArgumentException("Animation length must be positive");
        }
        JsonObject bones = GsonHelper.getAsJsonObject(clip, "bones");
        ParsedBone handBone = readBone(bones, "first_person_hand", length);
        ParsedBone itemBone = readBone(bones, "first_person_item", length);
        if (handBone == null && itemBone == null) {
            throw new IllegalArgumentException(
                    "Animation clip requires first_person_hand or first_person_item");
        }
        ParsedBone timeline = handBone == null ? itemBone : handBone;
        if (handBone != null && itemBone != null
                && !handBone.positions().keySet().equals(itemBone.positions().keySet())) {
            throw new IllegalArgumentException(
                    "Hand-space and item-center tracks must share the same keyframe times");
        }

        DJAnimationCurve.Keyframe[] keyframes =
                new DJAnimationCurve.Keyframe[timeline.positions().size()];
        int index = 0;
        for (Float time : timeline.positions().keySet()) {
            float phase = index == keyframes.length - 1 ? 1.0f : time / length;
            DJAnimationTransform handTransform =
                    handBone == null ? DJAnimationTransform.IDENTITY : transformAt(handBone, time);
            DJAnimationTransform itemTransform =
                    itemBone == null ? DJAnimationTransform.IDENTITY : transformAt(itemBone, time);
            keyframes[index++] = new DJAnimationCurve.Keyframe(
                    phase, new DJAnimationPose(handTransform, itemTransform));
        }
        return new DJAnimationCurve(keyframes);
    }

    private static ParsedBone readBone(JsonObject bones, String name, float length) {
        if (!bones.has(name)) {
            return null;
        }
        JsonObject bone = GsonHelper.getAsJsonObject(bones, name);
        TreeMap<Float, float[]> positions = readChannel(bone, "position");
        TreeMap<Float, float[]> rotations = readChannel(bone, "rotation");
        if (!positions.keySet().equals(rotations.keySet()) || positions.size() < 2
                || positions.firstKey() != 0.0f || positions.lastKey() != length) {
            throw new IllegalArgumentException(name
                    + " position and rotation channels must share keyframes covering time 0 through animation_length");
        }
        return new ParsedBone(positions, rotations);
    }

    private static DJAnimationTransform transformAt(ParsedBone bone, float time) {
        float[] position = bone.positions().get(time);
        float[] rotation = bone.rotations().get(time);
        return new DJAnimationTransform(
                -position[0] / 16.0f,
                position[1] / 16.0f,
                position[2] / 16.0f,
                -rotation[0],
                -rotation[1],
                rotation[2],
                1.0f);
    }

    private static TreeMap<Float, float[]> readChannel(JsonObject bone, String name) {
        JsonObject channel = GsonHelper.getAsJsonObject(bone, name);
        TreeMap<Float, float[]> result = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : channel.entrySet()) {
            float time = Float.parseFloat(entry.getKey());
            JsonObject keyframe = GsonHelper.convertToJsonObject(entry.getValue(), name + " keyframe");
            JsonArray vector = GsonHelper.getAsJsonArray(keyframe, "vector");
            if (!Float.isFinite(time) || time < 0.0f || vector.size() != 3) {
                throw new IllegalArgumentException("Invalid " + name + " keyframe");
            }
            float[] values = {
                    vector.get(0).getAsFloat(), vector.get(1).getAsFloat(), vector.get(2).getAsFloat()
            };
            if (!Float.isFinite(values[0]) || !Float.isFinite(values[1]) || !Float.isFinite(values[2])) {
                throw new IllegalArgumentException("Invalid " + name + " vector");
            }
            result.put(time, values);
        }
        return result;
    }

    record IdleSelection(DJAnimationCurve curve, double durationBeats) {
    }

    private record ParsedBone(
            TreeMap<Float, float[]> positions,
            TreeMap<Float, float[]> rotations) {
    }

    record Binding(DJAnimationCurve curve, double durationBeats) {
    }

    record LoadedProfile(ResourceLocation id, int priority, Set<ResourceLocation> items,
            Set<TagKey<Item>> tags, Set<ResourceLocation> behaviors,
            Map<DJAnimationSemantic, Binding> animations) {
        LoadedProfile(ResourceLocation id, int priority, Set<ResourceLocation> items,
                Set<TagKey<Item>> tags, Map<DJAnimationSemantic, Binding> animations) {
            this(id, priority, items, tags, Set.of(), animations);
        }

        boolean matchesTag(Item item) {
            for (TagKey<Item> tag : tags) {
                if (item.builtInRegistryHolder().is(tag)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record Snapshot(Map<ResourceLocation, List<LoadedProfile>> exactProfiles,
            List<LoadedProfile> tagProfiles,
            Map<ResourceLocation, List<LoadedProfile>> behaviorProfiles,
            LoadedProfile genericProfile) {
        static Snapshot empty() {
            return new Snapshot(Map.of(), List.of(), Map.of(), null);
        }

        static Snapshot compile(Map<String, DJAnimationCurve> curves, List<LoadedProfile> profiles) {
            Map<ResourceLocation, List<LoadedProfile>> exact = new HashMap<>();
            List<LoadedProfile> tagged = new ArrayList<>();
            Map<ResourceLocation, List<LoadedProfile>> behaviorProfiles = new HashMap<>();
            LoadedProfile generic = null;
            for (LoadedProfile profile : profiles) {
                if (GENERIC_PROFILE.equals(profile.id()) && profile.items().isEmpty()
                        && profile.tags().isEmpty() && profile.behaviors().isEmpty()) {
                    generic = profile;
                }
                for (ResourceLocation item : profile.items()) {
                    exact.computeIfAbsent(item, ignored -> new ArrayList<>()).add(profile);
                }
                if (!profile.tags().isEmpty()) {
                    tagged.add(profile);
                }
                for (ResourceLocation behaviorId : profile.behaviors()) {
                    behaviorProfiles.computeIfAbsent(behaviorId, ignored -> new ArrayList<>()).add(profile);
                }
            }
            exact.replaceAll((ignored, candidates) ->
                    candidates.stream().sorted(PROFILE_ORDER).toList());
            tagged.sort(PROFILE_ORDER);
            behaviorProfiles.replaceAll((ignored, candidates) ->
                    candidates.stream().sorted(PROFILE_ORDER).toList());
            return new Snapshot(Map.copyOf(exact), List.copyOf(tagged),
                    Map.copyOf(behaviorProfiles), generic);
        }
    }
}
