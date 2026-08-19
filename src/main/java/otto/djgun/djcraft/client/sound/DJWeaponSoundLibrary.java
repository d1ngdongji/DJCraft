package otto.djgun.djcraft.client.sound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.sound.BeatOutcome;
import otto.djgun.djcraft.sound.DJActionOutcome;
import otto.djgun.djcraft.sound.DJWeaponSoundIdentityRegistry;
import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;
import otto.djgun.djcraft.sound.TargetOutcome;

public final class DJWeaponSoundLibrary extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final DJWeaponSoundLibrary INSTANCE = new DJWeaponSoundLibrary();
    private volatile Map<ResourceLocation, DJWeaponSoundProfile> profiles = Map.of();

    private DJWeaponSoundLibrary() {
        super(GSON, "djcraft/weapon_sounds");
    }

    public static DJWeaponSoundLibrary getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> documents, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Map<ResourceLocation, DJWeaponSoundProfile> loaded = new HashMap<>();
        boolean failed = false;
        for (Map.Entry<ResourceLocation, JsonElement> entry : documents.entrySet()) {
            try {
                loaded.put(entry.getKey(), parse(entry.getValue()));
            } catch (RuntimeException exception) {
                DJCraft.LOGGER.error("Rejected weapon sound profile {}", entry.getKey(), exception);
                failed = true;
            }
        }
        try {
            validateFallbackCycles(loaded);
        } catch (RuntimeException exception) {
            DJCraft.LOGGER.error("Rejected weapon sound reload because fallback links are cyclic", exception);
            failed = true;
        }
        if (failed) {
            DJCraft.LOGGER.warn("Keeping the previous weapon sound snapshot after a failed reload");
            return;
        }
        profiles = Map.copyOf(loaded);
        DJCraft.LOGGER.info("Loaded {} weapon sound profiles", profiles.size());
    }

    public DJWeaponSoundProfile.Selection select(ResourceLocation profileId, DJWeaponSoundSemantic semantic,
            DJActionOutcome outcome, long seed) {
        Map<ResourceLocation, DJWeaponSoundProfile> current = profiles;
        ResourceLocation cursor = current.containsKey(profileId) ? profileId : DJWeaponSoundIdentityRegistry.GENERIC;
        Set<ResourceLocation> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            DJWeaponSoundProfile profile = current.get(cursor);
            if (profile == null) {
                if (!visited.contains(DJWeaponSoundIdentityRegistry.GENERIC)) {
                    cursor = DJWeaponSoundIdentityRegistry.GENERIC;
                    continue;
                }
                break;
            }
            for (DJWeaponSoundProfile.Rule rule : profile.events().getOrDefault(semantic, List.of())) {
                if (rule.matches(outcome)) {
                    return choose(rule, seed);
                }
            }
            cursor = profile.fallback();
            if (cursor == null && !visited.contains(DJWeaponSoundIdentityRegistry.GENERIC)) {
                cursor = DJWeaponSoundIdentityRegistry.GENERIC;
            }
        }
        return null;
    }

    static DJWeaponSoundProfile parse(JsonElement element) {
        JsonObject root = GsonHelper.convertToJsonObject(element, "weapon sound profile");
        ResourceLocation fallback = root.has("fallback")
                ? ResourceLocation.parse(GsonHelper.getAsString(root, "fallback")) : null;
        JsonObject eventsObject = GsonHelper.getAsJsonObject(root, "events");
        Map<DJWeaponSoundSemantic, List<DJWeaponSoundProfile.Rule>> events =
                new EnumMap<>(DJWeaponSoundSemantic.class);
        for (Map.Entry<String, JsonElement> eventEntry : eventsObject.entrySet()) {
            DJWeaponSoundSemantic semantic = DJWeaponSoundSemantic.parse(eventEntry.getKey());
            JsonArray rules = GsonHelper.convertToJsonArray(eventEntry.getValue(), semantic.serializedName());
            List<DJWeaponSoundProfile.Rule> parsedRules = new ArrayList<>();
            for (JsonElement ruleElement : rules) {
                parsedRules.add(parseRule(GsonHelper.convertToJsonObject(ruleElement, "sound rule")));
            }
            events.put(semantic, List.copyOf(parsedRules));
        }
        return new DJWeaponSoundProfile(fallback, events);
    }

    private static DJWeaponSoundProfile.Rule parseRule(JsonObject rule) {
        JsonObject when = rule.has("when") ? GsonHelper.getAsJsonObject(rule, "when") : new JsonObject();
        String beatName = GsonHelper.getAsString(when, "beat", "any");
        String targetName = GsonHelper.getAsString(when, "target", "any");
        boolean anyBeat = beatName.equals("any");
        boolean anyTarget = targetName.equals("any");
        BeatOutcome beat = anyBeat ? BeatOutcome.NOT_APPLICABLE
                : BeatOutcome.valueOf(beatName.toUpperCase(java.util.Locale.ROOT));
        TargetOutcome target = anyTarget ? TargetOutcome.NOT_APPLICABLE
                : TargetOutcome.valueOf(targetName.toUpperCase(java.util.Locale.ROOT));
        JsonArray sounds = GsonHelper.getAsJsonArray(rule, "sounds");
        List<DJWeaponSoundProfile.Choice> choices = new ArrayList<>();
        for (JsonElement soundElement : sounds) {
            JsonObject sound = GsonHelper.convertToJsonObject(soundElement, "sound choice");
            choices.add(new DJWeaponSoundProfile.Choice(
                    ResourceLocation.parse(GsonHelper.getAsString(sound, "event")),
                    GsonHelper.getAsInt(sound, "weight", 1)));
        }
        float volume = GsonHelper.getAsFloat(rule, "volume", 1.0f);
        float minPitch;
        float maxPitch;
        if (rule.get("pitch") instanceof JsonArray pitch) {
            if (pitch.size() != 2) {
                throw new IllegalArgumentException("pitch range must contain two values");
            }
            minPitch = pitch.get(0).getAsFloat();
            maxPitch = pitch.get(1).getAsFloat();
        } else {
            minPitch = maxPitch = GsonHelper.getAsFloat(rule, "pitch", 1.0f);
        }
        return new DJWeaponSoundProfile.Rule(beat, anyBeat, target, anyTarget, choices,
                volume, minPitch, maxPitch, GsonHelper.getAsBoolean(rule, "spatial", true));
    }

    private static DJWeaponSoundProfile.Selection choose(DJWeaponSoundProfile.Rule rule, long seed) {
        Random random = new Random(seed);
        int totalWeight = rule.sounds().stream().mapToInt(DJWeaponSoundProfile.Choice::weight).sum();
        int selected = random.nextInt(totalWeight);
        DJWeaponSoundProfile.Choice choice = rule.sounds().getFirst();
        for (DJWeaponSoundProfile.Choice candidate : rule.sounds()) {
            selected -= candidate.weight();
            if (selected < 0) {
                choice = candidate;
                break;
            }
        }
        float pitch = rule.minPitch() == rule.maxPitch() ? rule.minPitch()
                : rule.minPitch() + random.nextFloat() * (rule.maxPitch() - rule.minPitch());
        return new DJWeaponSoundProfile.Selection(choice.event(), rule.volume(), pitch, rule.spatial());
    }

    private static void validateFallbackCycles(Map<ResourceLocation, DJWeaponSoundProfile> loaded) {
        for (ResourceLocation start : loaded.keySet()) {
            Set<ResourceLocation> path = new HashSet<>();
            ResourceLocation cursor = start;
            while (cursor != null && loaded.containsKey(cursor)) {
                if (!path.add(cursor)) {
                    throw new IllegalArgumentException("Cyclic fallback at " + cursor);
                }
                cursor = loaded.get(cursor).fallback();
            }
        }
    }
}
