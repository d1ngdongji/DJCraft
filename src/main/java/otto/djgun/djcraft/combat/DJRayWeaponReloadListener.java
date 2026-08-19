package otto.djgun.djcraft.combat;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import otto.djgun.djcraft.DJCraft;

public final class DJRayWeaponReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final DJRayWeaponReloadListener INSTANCE = new DJRayWeaponReloadListener();

    private DJRayWeaponReloadListener() {
        super(GSON, "djcraft/ray_weapons");
    }

    public static DJRayWeaponReloadListener getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> documents, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Map<ResourceLocation, DJRayWeaponProfile> profiles = new LinkedHashMap<>();
        documents.forEach((itemId, document) -> {
            try {
                profiles.put(itemId, DJRayWeaponProfile.parse(document));
            } catch (RuntimeException exception) {
                DJCraft.LOGGER.error("Ignoring invalid ray weapon profile {}: {}", itemId, exception.getMessage());
            }
        });
        DJRayWeaponManager.replaceProfiles(profiles);
    }
}
