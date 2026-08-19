package otto.djgun.djcraft.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import otto.djgun.djcraft.DJCraft;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DJItemTimingReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final DJItemTimingReloadListener INSTANCE = new DJItemTimingReloadListener();

    private DJItemTimingReloadListener() {
        super(GSON, "djcraft/item_timing");
    }

    public static DJItemTimingReloadListener getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> documents, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Map<ResourceLocation, DJItemTimingProfile> profiles = new LinkedHashMap<>();
        documents.forEach((itemId, document) -> {
            try {
                profiles.put(itemId, DJItemTimingProfile.parse(document));
            } catch (RuntimeException exception) {
                DJCraft.LOGGER.error("Ignoring invalid item timing profile {}: {}",
                        itemId, exception.getMessage());
            }
        });

        DJItemCooldownManager.replaceProfiles(profiles);
    }
}
