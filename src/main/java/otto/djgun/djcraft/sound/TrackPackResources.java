package otto.djgun.djcraft.sound;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.loader.TrackPackManager;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 动态曲目包资源
 * 将 run/djcraft/trackpacks 目录下的 OGG 文件暴露给 Minecraft 资源系统
 * 并动态生成 sounds.json
 */
public class TrackPackResources implements PackResources {

    private static final String COMBO_TEXTURE_RESOURCE_PREFIX = "textures/gui/combo/trackpacks/";
    private static final String COMBO_TEXTURE_FILE_PREFIX = "combo/";
    private static final String BEAT_TEXTURE_RESOURCE_PREFIX = "textures/gui/beats/trackpacks/";
    private static final String BEAT_TEXTURE_FILE_PREFIX = "beats/";
    private final PackLocationInfo locationInfo;
    private static final Gson GSON = new Gson();

    public TrackPackResources(PackLocationInfo info) {
        this.locationInfo = info;
        DJCraft.LOGGER.info("TrackPackResources initialized for pack: {}", info.id());
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        if (elements.length == 1 && elements[0].equals("pack.mcmeta")) {
            return () -> {
                JsonObject root = new JsonObject();
                JsonObject pack = new JsonObject();
                pack.addProperty("pack_format", 34); // Minecraft 1.21.1 resource format
                pack.addProperty("description", "DJCraft Dynamic TrackPacks");
                root.add("pack", pack);
                return new ByteArrayInputStream(GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            };
        }
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES)
            return null;
        if (!location.getNamespace().equals(DJCraft.MODID))
            return null;

        String path = location.getPath();

        // 1. 动态生成 sounds.json
        if (path.equals("sounds.json")) {
            DJCraft.LOGGER.debug("Resource requested from TrackPackResources: {}", path);
            DJCraft.LOGGER.info("Generating dynamic sounds.json for DJCraft");
            return generateSoundsJson();
        }

        // 2. 映射音频文件
        // 路径格式: sounds/trackpacks/<packId>.ogg
        if (path.startsWith("sounds/trackpacks/") && path.endsWith(".ogg")) {
            DJCraft.LOGGER.debug("Resource requested from TrackPackResources: {}", path);
            String packId = path.substring("sounds/trackpacks/".length(), path.length() - 4);
            return getTrackSoundStream(packId);
        }

        // 3. 模型
        if (path.equals("models/item/empty_disc.json")) {
            return generateEmptyDiscModelJson();
        }
        if (path.startsWith("models/item/disc_") && path.endsWith(".json")) {
            String packId = path.substring("models/item/disc_".length(), path.length() - 5);
            return generateDiscModelJson(packId);
        }
        if (path.startsWith("models/item/perfect_disc_") && path.endsWith(".json")) {
            String packId = path.substring("models/item/perfect_disc_".length(), path.length() - 5);
            return generatePerfectDiscModelJson(packId);
        }

        // 4. 纹理
        if (path.startsWith("textures/item/disc_") && path.endsWith(".png")) {
            String packId = path.substring("textures/item/disc_".length(), path.length() - 4);
            return getTrackFileStream(packId, "disc.png");
        }
        if (path.startsWith("textures/item/perfect_disc_") && path.endsWith(".png")) {
            String packId = path.substring("textures/item/perfect_disc_".length(), path.length() - 4);
            return getTrackFileStream(packId, "perfect_disc.png");
        }

        // 5. Active-track combo digit overrides, including threshold directories.
        if (path.startsWith(COMBO_TEXTURE_RESOURCE_PREFIX) && path.endsWith(".png")) {
            for (TrackPack pack : TrackPackManager.getInstance().getLoadedPacks()) {
                for (ComboTextureFile texture : comboTextureFiles(pack.id())) {
                    if (path.equals(texture.location().getPath())) {
                        return getTrackFileStream(pack.id(), texture.fileName());
                    }
                }
            }
        }

        // 6. Full-color PNG/GIF beat visuals supplied by TrackPacks.
        if (path.startsWith(BEAT_TEXTURE_RESOURCE_PREFIX)
                && (path.endsWith(".png") || path.endsWith(".gif"))) {
            for (TrackPack pack : TrackPackManager.getInstance().getLoadedPacks()) {
                for (BeatTextureFile texture : beatTextureFiles(pack.id())) {
                    if (path.equals(texture.location().getPath())) {
                        return getTrackFileStream(pack.id(), texture.fileName());
                    }
                }
            }
        }

        // 纹理等其他资源：不处理，返回 null 让 fallback 到 mod 内置资源
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput resourceOutput) {
        if (type != PackType.CLIENT_RESOURCES || !namespace.equals(DJCraft.MODID)) {
            return;
        }

        // 1. 提供 sounds.json 和 empty_disc 模型
        if (path.isEmpty() || "sounds".startsWith(path) || path.startsWith("sounds")) {
            resourceOutput.accept(ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "sounds.json"),
                    generateSoundsJson());
        }
        if (path.isEmpty() || "models/item".startsWith(path) || path.startsWith("models/item")) {
            resourceOutput.accept(ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "models/item/empty_disc.json"),
                    generateEmptyDiscModelJson());
        }

        // 2. 提供所有曲目包的音频文件以便资源系统发现
        boolean matchSounds = path.isEmpty() || "sounds/trackpacks".startsWith(path)
                || path.startsWith("sounds/trackpacks");
        boolean matchTextures = path.isEmpty() || "textures/item".startsWith(path) || path.startsWith("textures/item");
        boolean matchComboTextures = path.isEmpty() || COMBO_TEXTURE_RESOURCE_PREFIX.startsWith(path)
                || path.startsWith(COMBO_TEXTURE_RESOURCE_PREFIX);
        boolean matchBeatTextures = path.isEmpty() || BEAT_TEXTURE_RESOURCE_PREFIX.startsWith(path)
                || path.startsWith(BEAT_TEXTURE_RESOURCE_PREFIX);
        boolean matchModels = path.isEmpty() || "models/item".startsWith(path) || path.startsWith("models/item");

        if (matchSounds || matchTextures || matchComboTextures || matchBeatTextures || matchModels) {
            for (TrackPack pack : TrackPackManager.getInstance().getLoadedPacks()) {
                String packId = pack.id().toLowerCase(java.util.Locale.ROOT);

                // Audio
                if (matchSounds) {
                    String oggPath = "sounds/trackpacks/" + packId + ".ogg";
                    if (oggPath.startsWith(path)) {
                        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, oggPath);
                        resourceOutput.accept(location, getTrackSoundStream(packId));
                    }
                }

                if (TrackPackManager.getInstance().hasFile(packId, "disc.png")) {
                    if (matchTextures) {
                        String texPath = "textures/item/disc_" + packId + ".png";
                        if (texPath.startsWith(path)) {
                            resourceOutput.accept(ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, texPath),
                                    getTrackFileStream(packId, "disc.png"));
                        }
                    }
                    if (matchModels) {
                        String modelPath = "models/item/disc_" + packId + ".json";
                        if (modelPath.startsWith(path)) {
                            resourceOutput.accept(ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, modelPath),
                                    generateDiscModelJson(packId));
                        }
                    }
                }

                if (TrackPackManager.getInstance().hasFile(packId, "perfect_disc.png")) {
                    if (matchTextures) {
                        String texPath = "textures/item/perfect_disc_" + packId + ".png";
                        if (texPath.startsWith(path)) {
                            resourceOutput.accept(ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, texPath),
                                    getTrackFileStream(packId, "perfect_disc.png"));
                        }
                    }
                    if (matchModels) {
                        String modelPath = "models/item/perfect_disc_" + packId + ".json";
                        if (modelPath.startsWith(path)) {
                            resourceOutput.accept(ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, modelPath),
                                    generatePerfectDiscModelJson(packId));
                        }
                    }
                }

                if (matchComboTextures) {
                    for (ComboTextureFile texture : comboTextureFiles(packId)) {
                        if (texture.location().getPath().startsWith(path)) {
                            resourceOutput.accept(texture.location(),
                                    getTrackFileStream(packId, texture.fileName()));
                        }
                    }
                }

                if (matchBeatTextures) {
                    for (BeatTextureFile texture : beatTextureFiles(packId)) {
                        if (texture.location().getPath().startsWith(path)) {
                            resourceOutput.accept(texture.location(),
                                    getTrackFileStream(packId, texture.fileName()));
                        }
                    }
                }
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(DJCraft.MODID) : Collections.emptySet();
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(net.minecraft.server.packs.metadata.MetadataSectionSerializer<T> deserializer) {
        if (deserializer.getMetadataSectionName().equals("pack")) {
            JsonObject root = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", 34);
            pack.addProperty("description", "DJCraft Dynamic TrackPacks");
            root.add("pack", pack);
            return deserializer.fromJson(pack);
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return locationInfo;
    }

    @Override
    public void close() {
        // 无需关闭资源
    }

    // --- 内部实现 ---

    private IoSupplier<InputStream> generateSoundsJson() {
        return () -> {
            DJCraft.LOGGER.info("Resource system is reading dynamic sounds.json");
            JsonObject root = new JsonObject();

            for (TrackPack pack : TrackPackManager.getInstance().getLoadedPacks()) {
                String eventName = ("trackpacks." + pack.id()).toLowerCase(java.util.Locale.ROOT);

                JsonObject eventObj = new JsonObject();
                JsonArray soundsArr = new JsonArray();

                JsonObject soundEntry = new JsonObject();
                // 引用 trackpacks/<packId>，这将对应 getResource 中的 sounds/trackpacks/<packId>.ogg
                soundEntry.addProperty("name",
                        DJCraft.MODID + ":trackpacks/" + pack.id().toLowerCase(java.util.Locale.ROOT));

                // 【核心修复】：静态音频 + 预载机制！
                // 将 stream 设为 false，确保使用最高精度的全缓冲物理硬件同步。
                // 增加 preload: true，让 Minecraft 在游戏加载资源时就在后台将其解码成静态缓冲，
                // 从而彻底消除第一次播放时的解码耗时和超时引起的引擎崩溃！
                soundEntry.addProperty("stream", false);
                soundEntry.addProperty("preload", true);

                soundsArr.add(soundEntry);
                eventObj.add("sounds", soundsArr);
                eventObj.addProperty("category", "record");

                root.add(eventName, eventObj);
            }

            String json = GSON.toJson(root);
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        };
    }

    private IoSupplier<InputStream> getTrackSoundStream(String packId) {
        return () -> {
            try {
                InputStream stream = TrackPackManager.getInstance().openAudioStream(packId);
                if (stream == null) {
                    DJCraft.LOGGER.warn("Audio stream not available for pack: {}", packId);
                }
                return stream;
            } catch (IOException e) {
                DJCraft.LOGGER.error("Failed to open audio stream for pack: {}", packId, e);
                return null;
            }
        };
    }

    private IoSupplier<InputStream> generateEmptyDiscModelJson() {
        return () -> {
            JsonObject root = new JsonObject();
            root.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", DJCraft.MODID + ":item/empty_disc");
            root.add("textures", textures);

            JsonArray overrides = new JsonArray();
            java.util.List<TrackPack> packs = new java.util.ArrayList<>(
                    TrackPackManager.getInstance().getLoadedPacks());
            packs.sort(java.util.Comparator.comparing(TrackPack::id));

            int index = 1;
            for (TrackPack pack : packs) {
                if (TrackPackManager.getInstance().hasFile(pack.id(), "disc.png")) {
                    JsonObject override = new JsonObject();
                    JsonObject predicate = new JsonObject();
                    predicate.addProperty("djcraft:pack_index", index);
                    override.add("predicate", predicate);
                    override.addProperty("model", DJCraft.MODID + ":item/disc_" + pack.id());
                    overrides.add(override);
                }
                index++;
            }
            JsonObject gildedDefault = new JsonObject();
            JsonObject gildedPredicate = new JsonObject();
            gildedPredicate.addProperty("djcraft:gilded", 1.0f);
            gildedDefault.add("predicate", gildedPredicate);
            gildedDefault.addProperty("model", DJCraft.MODID + ":item/perfect_disc");
            overrides.add(gildedDefault);

            index = 1;
            for (TrackPack pack : packs) {
                if (TrackPackManager.getInstance().hasFile(pack.id(), "perfect_disc.png")) {
                    JsonObject override = new JsonObject();
                    JsonObject predicate = new JsonObject();
                    predicate.addProperty("djcraft:pack_index", index);
                    predicate.addProperty("djcraft:gilded", 1.0f);
                    override.add("predicate", predicate);
                    override.addProperty("model", DJCraft.MODID + ":item/perfect_disc_" + pack.id());
                    overrides.add(override);
                }
                index++;
            }
            if (overrides.size() > 0) {
                root.add("overrides", overrides);
            }

            return new ByteArrayInputStream(GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        };
    }

    private IoSupplier<InputStream> generateDiscModelJson(String packId) {
        return () -> {
            JsonObject root = new JsonObject();
            root.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", DJCraft.MODID + ":item/disc_" + packId);
            root.add("textures", textures);
            return new ByteArrayInputStream(GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        };
    }

    private IoSupplier<InputStream> generatePerfectDiscModelJson(String packId) {
        return () -> {
            JsonObject root = new JsonObject();
            root.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", DJCraft.MODID + ":item/perfect_disc_" + packId);
            root.add("textures", textures);
            return new ByteArrayInputStream(GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        };
    }

    private IoSupplier<InputStream> getTrackFileStream(String packId, String fileName) {
        return () -> {
            try {
                InputStream st = TrackPackManager.getInstance().openFileStream(packId, fileName);
                return st;
            } catch (IOException e) {
                return null;
            }
        };
    }

    /** Stable resource ID for a track pack's legacy combo digit override. */
    public static ResourceLocation comboTextureLocation(String packId, int digit) {
        return comboTextureLocation(packId, 1, digit);
    }

    /** Stable resource ID for a track pack's optional threshold combo digit override. */
    public static ResourceLocation comboTextureLocation(String packId, int threshold, int digit) {
        if (digit < 0 || digit > 9) {
            throw new IllegalArgumentException("Combo digit must be between 0 and 9");
        }
        if (threshold < 1) {
            throw new IllegalArgumentException("Combo threshold must be positive");
        }
        String packKey = UUID.nameUUIDFromBytes(packId.getBytes(StandardCharsets.UTF_8)).toString();
        String thresholdPath = threshold == 1 ? "" : threshold + "/";
        return ResourceLocation.fromNamespaceAndPath(
                DJCraft.MODID, COMBO_TEXTURE_RESOURCE_PREFIX + packKey + "/" + thresholdPath + digit + ".png");
    }

    public static String comboTextureFileName(int digit) {
        return comboTextureFileName(1, digit);
    }

    public static String comboTextureFileName(int threshold, int digit) {
        if (digit < 0 || digit > 9) {
            throw new IllegalArgumentException("Combo digit must be between 0 and 9");
        }
        if (threshold < 1) {
            throw new IllegalArgumentException("Combo threshold must be positive");
        }
        String thresholdPath = threshold == 1 ? "" : threshold + "/";
        return COMBO_TEXTURE_FILE_PREFIX + thresholdPath + digit + ".png";
    }

    public static List<ComboTextureFile> comboTextureFiles(String packId) {
        List<ComboTextureFile> textures = new ArrayList<>();
        for (String fileName : TrackPackManager.getInstance().listFiles(packId, COMBO_TEXTURE_FILE_PREFIX)) {
            Optional<ComboTextureFile> parsed = parseComboTextureFile(packId, fileName);
            if (parsed.isPresent()) {
                textures.add(parsed.get());
            } else if (fileName.matches("^combo/[^/]+/[0-9]\\.png$")) {
                DJCraft.LOGGER.warn("Ignoring invalid combo texture threshold path in TrackPack {}: {}",
                        packId, fileName);
            }
        }
        textures.sort(Comparator.comparingInt(ComboTextureFile::threshold)
                .thenComparingInt(ComboTextureFile::digit));
        return List.copyOf(textures);
    }

    static Optional<ComboTextureFile> parseComboTextureFile(String packId, String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        String[] elements = fileName.split("/");
        int threshold;
        String digitFile;
        if (elements.length == 2 && elements[0].equals("combo")) {
            threshold = 1;
            digitFile = elements[1];
        } else if (elements.length == 3 && elements[0].equals("combo")) {
            String thresholdText = elements[1];
            if (!thresholdText.matches("[1-9][0-9]*")) {
                return Optional.empty();
            }
            try {
                threshold = Integer.parseInt(thresholdText);
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
            if (threshold < 2) {
                return Optional.empty();
            }
            digitFile = elements[2];
        } else {
            return Optional.empty();
        }
        if (!digitFile.matches("[0-9]\\.png")) {
            return Optional.empty();
        }
        int digit = digitFile.charAt(0) - '0';
        return Optional.of(new ComboTextureFile(threshold, digit, fileName,
                comboTextureLocation(packId, threshold, digit)));
    }

    public record ComboTextureFile(int threshold, int digit, String fileName, ResourceLocation location) {
    }

    public static List<BeatTextureFile> beatTextureFiles(String packId) {
        List<BeatTextureFile> textures = new ArrayList<>();
        for (String fileName : TrackPackManager.getInstance().listFiles(packId, BEAT_TEXTURE_FILE_PREFIX)) {
            Optional<BeatTextureFile> parsed = parseBeatTextureFile(packId, fileName);
            if (parsed.isPresent()) {
                textures.add(parsed.get());
            } else {
                DJCraft.LOGGER.warn("Ignoring invalid beat texture path in TrackPack {}: {}", packId, fileName);
            }
        }
        textures.sort(Comparator.comparing(BeatTextureFile::fileName));
        return List.copyOf(textures);
    }

    public static Optional<ResourceLocation> beatTextureLocation(String packId, String fileName) {
        return parseBeatTextureFile(packId, fileName).map(BeatTextureFile::location);
    }

    static Optional<BeatTextureFile> parseBeatTextureFile(String packId, String fileName) {
        if (fileName == null || !fileName.startsWith(BEAT_TEXTURE_FILE_PREFIX)
                || fileName.length() <= BEAT_TEXTURE_FILE_PREFIX.length()) {
            return Optional.empty();
        }
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (!fileName.equals(lower) || (!lower.endsWith(".png") && !lower.endsWith(".gif"))) {
            return Optional.empty();
        }
        String relative = fileName.substring(BEAT_TEXTURE_FILE_PREFIX.length());
        if (relative.startsWith("/") || relative.contains("//") || relative.contains("..")) {
            return Optional.empty();
        }
        String packKey = UUID.nameUUIDFromBytes(packId.getBytes(StandardCharsets.UTF_8)).toString();
        ResourceLocation location = ResourceLocation.tryParse(
                DJCraft.MODID + ":" + BEAT_TEXTURE_RESOURCE_PREFIX + packKey + "/" + relative);
        return location == null
                ? Optional.empty()
                : Optional.of(new BeatTextureFile(fileName, location));
    }

    public record BeatTextureFile(String fileName, ResourceLocation location) {
    }
}
