package otto.djgun.djcraft.hud;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.sound.TrackPackResources;

/** Loads combo digit overrides and their effect colors outside the HUD render path. */
@OnlyIn(Dist.CLIENT)
public final class DJComboTextureLibrary extends SimplePreparableReloadListener<DJComboTextureLibrary.Snapshot> {
    private static final int DIGIT_COUNT = 10;
    private static final DJComboTextureLibrary INSTANCE = new DJComboTextureLibrary();
    private volatile Snapshot snapshot = Snapshot.empty();

    private DJComboTextureLibrary() {
    }

    public static DJComboTextureLibrary getInstance() {
        return INSTANCE;
    }

    public Style resolve(String packId, int combo, int digit) {
        if (digit < 0 || digit >= DIGIT_COUNT) {
            throw new IllegalArgumentException("Combo digit must be between 0 and 9");
        }
        Snapshot current = snapshot;
        DJComboTextureStages<Style> packStyles = packId == null ? null : current.packStyles().get(packId);
        return (packStyles == null ? current.defaults() : packStyles).resolve(combo, digit);
    }

    /** Resolves the legacy first-combo style. */
    public Style resolve(String packId, int digit) {
        return resolve(packId, 1, digit);
    }

    @Override
    protected Snapshot prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<Style> baseStyles = new ArrayList<>(DIGIT_COUNT);
        for (int digit = 0; digit < DIGIT_COUNT; digit++) {
            ResourceLocation location = defaultTextureLocation(digit);
            baseStyles.add(loadBuiltInStyle(resourceManager, location, null));
        }

        List<Style> fiftyStyles = new ArrayList<>(DIGIT_COUNT);
        for (int digit = 0; digit < DIGIT_COUNT; digit++) {
            ResourceLocation location = builtInFiftyTextureLocation(digit);
            fiftyStyles.add(loadBuiltInStyle(resourceManager, location, baseStyles.get(digit)));
        }

        List<Style> immutableBase = List.copyOf(baseStyles);
        List<Style> immutableFifty = List.copyOf(fiftyStyles);
        DJComboTextureStages<Style> defaults = DJComboTextureStages.create(
                immutableBase, immutableFifty, Map.of());

        Map<String, DJComboTextureStages<Style>> packStyles = new HashMap<>();
        for (TrackPack pack : TrackPackManager.getInstance().getLoadedPacks()) {
            Map<Integer, Map<Integer, Style>> overrides = new TreeMap<>();
            for (TrackPackResources.ComboTextureFile texture : TrackPackResources.comboTextureFiles(pack.id())) {
                Optional<Style> loaded = loadCustomStyle(resourceManager, texture.location());
                loaded.ifPresent(style -> overrides
                        .computeIfAbsent(texture.threshold(), ignored -> new HashMap<>())
                        .put(texture.digit(), style));
            }
            packStyles.put(pack.id(), DJComboTextureStages.create(
                    immutableBase, immutableFifty, overrides));
        }
        return new Snapshot(defaults, Map.copyOf(packStyles));
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        snapshot = prepared;
        DJCraft.LOGGER.info("Loaded combo digit styles for {} TrackPack(s)", prepared.packStyles().size());
    }

    private static Style loadBuiltInStyle(ResourceManager resourceManager, ResourceLocation location, Style fallback) {
        Optional<Style> loaded = readStyle(resourceManager, location);
        if (loaded.isPresent()) {
            return loaded.get();
        }
        if (fallback != null) {
            DJCraft.LOGGER.error("Failed to load built-in combo digit texture {}; using the base digit", location);
            return fallback;
        }
        DJCraft.LOGGER.error("Failed to load built-in combo digit texture {}; using white effects", location);
        return new Style(location, 1.0f, 1.0f, 1.0f);
    }

    private static Optional<Style> loadCustomStyle(ResourceManager resourceManager, ResourceLocation location) {
        Optional<Style> loaded = readStyle(resourceManager, location);
        if (loaded.isEmpty()) {
            DJCraft.LOGGER.error("Rejected combo digit texture {}; inheriting the previous-stage digit", location);
        }
        return loaded;
    }

    private static Optional<Style> readStyle(ResourceManager resourceManager, ResourceLocation location) {
        try (NativeImage image = NativeImage.read(resourceManager.open(location))) {
            int rgb = DJComboTextureColor.dominantVisibleRgb(image.getPixelsRGBA());
            if (rgb < 0) {
                throw new IOException("texture contains no visible pixels");
            }
            return Optional.of(new Style(location,
                    ((rgb >>> 16) & 0xFF) / 255.0f,
                    ((rgb >>> 8) & 0xFF) / 255.0f,
                    (rgb & 0xFF) / 255.0f));
        } catch (IOException | RuntimeException exception) {
            DJCraft.LOGGER.debug("Could not read combo digit texture {}", location, exception);
            return Optional.empty();
        }
    }

    private static ResourceLocation defaultTextureLocation(int digit) {
        return ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "textures/gui/combo/" + digit + ".png");
    }

    private static ResourceLocation builtInFiftyTextureLocation(int digit) {
        return ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "textures/gui/combo/50/" + digit + ".png");
    }

    public record Style(ResourceLocation texture, float red, float green, float blue) {
    }

    protected record Snapshot(DJComboTextureStages<Style> defaults,
            Map<String, DJComboTextureStages<Style>> packStyles) {
        private static Snapshot empty() {
            List<Style> styles = new ArrayList<>(DIGIT_COUNT);
            for (int digit = 0; digit < DIGIT_COUNT; digit++) {
                styles.add(new Style(defaultTextureLocation(digit), 1.0f, 1.0f, 1.0f));
            }
            List<Style> immutable = List.copyOf(styles);
            return new Snapshot(DJComboTextureStages.create(immutable, immutable, Map.of()), Map.of());
        }
    }
}
