package otto.djgun.djcraft.cybergrind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.conditions.ICondition;
import otto.djgun.djcraft.DJCraft;

public final class CyberGrindProfileManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final CyberGrindProfileManager INSTANCE = new CyberGrindProfileManager();
    private volatile Map<ResourceLocation, CyberGrindProfile> profiles = Map.of();

    private CyberGrindProfileManager() {
        super(GSON, "djcraft/cyber_grind");
    }

    public static CyberGrindProfileManager getInstance() {
        return INSTANCE;
    }

    public Optional<CyberGrindProfile> get(ResourceLocation id) {
        return Optional.ofNullable(profiles.get(id));
    }

    public List<Summary> summaries() {
        return profiles.values().stream()
                .map(profile -> new Summary(profile.id(), profile.displayName(), profile.description()))
                .sorted(java.util.Comparator.comparing(summary -> summary.id().toString()))
                .toList();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> documents, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Map<ResourceLocation, CyberGrindProfile> next = new LinkedHashMap<>();
        documents.forEach((id, document) -> {
            try {
                if (!ICondition.conditionsMatched(JsonOps.INSTANCE, document)) {
                    DJCraft.LOGGER.debug("Skipping Cyber Grind preset {} because its conditions did not match", id);
                    return;
                }
                next.put(id, CyberGrindProfile.parse(id, document));
            } catch (RuntimeException exception) {
                DJCraft.LOGGER.error("Ignoring invalid Cyber Grind preset {}: {}", id, exception.getMessage());
            }
        });
        profiles = Map.copyOf(next);
        DJCraft.LOGGER.info("Loaded {} Cyber Grind presets", profiles.size());
    }

    public record Summary(ResourceLocation id, String displayName, String description) {
    }
}
