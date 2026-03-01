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
import java.util.Collections;
import java.util.Set;

/**
 * 动态曲目包资源
 * 将 run/djcraft/trackpacks 目录下的 OGG 文件暴露给 Minecraft 资源系统
 * 并动态生成 sounds.json
 */
public class TrackPackResources implements PackResources {

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

        // 纹理等其他资源：不处理，返回 null 让 fallback 到 mod 内置资源
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput resourceOutput) {
        if (type != PackType.CLIENT_RESOURCES || !namespace.equals(DJCraft.MODID)) {
            return;
        }

        // 1. 提供 sounds.json
        if (path.isEmpty() || path.equals("sounds")) {
            resourceOutput.accept(ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "sounds.json"),
                    generateSoundsJson());
        }

        // 2. 提供所有曲目包的音频文件以便资源系统发现
        // Minecraft 会请求 assets/djcraft/sounds/trackpacks
        if (path.isEmpty() || path.startsWith("sounds")) {
            for (TrackPack pack : TrackPackManager.getInstance().getLoadedPacks()) {
                String packId = pack.id().toLowerCase(java.util.Locale.ROOT);
                String oggPath = "sounds/trackpacks/" + packId + ".ogg";

                // 如果请求的 path 是 oggPath 的父目录，或者 path 为空
                if (oggPath.startsWith(path)) {
                    ResourceLocation location = ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, oggPath);
                    resourceOutput.accept(location, getTrackSoundStream(packId));
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
}
