package otto.djgun.djcraft.client.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import otto.djgun.djcraft.DJCraft;

public final class DJRayEffectLibrary extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final ResourceLocation GENERIC = ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "generic");
    private static final DJRayEffectLibrary INSTANCE = new DJRayEffectLibrary();
    private final Set<ResourceLocation> reportedFallbacks = ConcurrentHashMap.newKeySet();
    private volatile Map<ResourceLocation, DJRayEffectProfile> profiles = Map.of();

    private DJRayEffectLibrary() {
        super(GSON, "djcraft/ray_effects");
    }

    public static DJRayEffectLibrary getInstance() {
        return INSTANCE;
    }

    public Optional<DJRayEffectProfile> resolve(ResourceLocation id) {
        Map<ResourceLocation, DJRayEffectProfile> current = profiles;
        DJRayEffectProfile profile = current.get(id);
        if (profile != null) {
            return Optional.of(profile);
        }
        if (reportedFallbacks.add(id)) {
            DJCraft.LOGGER.error("Missing or invalid ray effect {}; using {}", id, GENERIC);
        }
        return Optional.ofNullable(current.get(GENERIC));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> documents, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Map<ResourceLocation, DJRayEffectProfile> loaded = new LinkedHashMap<>();
        documents.forEach((id, document) -> {
            try {
                loaded.put(id, DJRayEffectProfile.parse(document));
            } catch (RuntimeException exception) {
                DJCraft.LOGGER.error("Ignoring invalid ray effect {}: {}", id, exception.getMessage());
            }
        });
        profiles = Map.copyOf(loaded);
        reportedFallbacks.clear();
        DJCraft.LOGGER.info("Loaded {} ray effect profiles", loaded.size());
    }
}
