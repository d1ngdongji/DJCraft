package otto.djgun.djcraft.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import otto.djgun.djcraft.DJCraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DJItemBehaviorReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final DJItemBehaviorReloadListener INSTANCE = new DJItemBehaviorReloadListener();

    private DJItemBehaviorReloadListener() {
        super(GSON, "djcraft/item_behaviors");
    }

    public static DJItemBehaviorReloadListener getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> documents, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        List<DJItemBehaviorProfile> profiles = new ArrayList<>();
        documents.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                profiles.add(DJItemBehaviorProfile.parse(entry.getKey(), entry.getValue()));
            } catch (RuntimeException exception) {
                DJCraft.LOGGER.error("Ignoring invalid item behavior profile {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        });
        DJItemBehaviorManager.replaceProfiles(profiles);
    }
}
