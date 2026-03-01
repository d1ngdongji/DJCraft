package otto.djgun.djcraft.loader;

import com.google.gson.*;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.*;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 曲目包加载器
 * 负责解析 JSON 文件并转换为 TrackPack 对象
 */
public class TrackPackLoader {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(Timeline.class, new TimelineDeserializer())
            .create();

    /**
     * 从文件路径加载曲目包
     * 
     * @param packId   曲目包ID（通常是文件夹名）
     * @param jsonPath JSON 文件路径
     * @return 加载的曲目包，失败时返回 null
     */
    public static TrackPack loadFromFile(String packId, Path jsonPath) {
        if (!Files.exists(jsonPath)) {
            DJCraft.LOGGER.error("TrackPack file not found: {}", jsonPath);
            return null;
        }

        try (Reader reader = Files.newBufferedReader(jsonPath)) {
            return loadFromReader(packId, reader);
        } catch (IOException e) {
            DJCraft.LOGGER.error("Failed to read TrackPack file: {}", jsonPath, e);
            return null;
        }
    }

    /**
     * 从 Reader 加载曲目包（可用于 ZIP 流）
     *
     * @param packId 曲目包ID
     * @param reader JSON 读取器（调用方负责关闭）
     * @return 加载的曲目包，失败时返回 null
     */
    public static TrackPack loadFromReader(String packId, Reader reader) {
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            // 解析 meta
            TrackMeta meta = GSON.fromJson(root.get("meta"), TrackMeta.class);

            // 解析 settings
            TrackSettings settings = null;
            if (root.has("settings")) {
                settings = GSON.fromJson(root.get("settings"), TrackSettings.class);
            }

            // 解析 definitions
            Map<String, BeatDefinition> definitions = parseDefinitions(root.get("definitions"));

            // 解析 timeline
            Timeline timeline = parseTimeline(root.get("timeline"));

            TrackPack pack = new TrackPack(packId, meta, settings, definitions, timeline);
            DJCraft.LOGGER.info("Successfully loaded TrackPack: {}", packId);
            return pack;

        } catch (JsonParseException e) {
            DJCraft.LOGGER.error("Failed to parse TrackPack JSON for pack: {}", packId, e);
            return null;
        }
    }

    /**
     * 解析 definitions 节点
     */
    private static Map<String, BeatDefinition> parseDefinitions(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Collections.emptyMap();
        }

        Map<String, BeatDefinition> definitions = new HashMap<>();
        JsonObject obj = element.getAsJsonObject();

        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String name = entry.getKey();
            JsonObject defObj = entry.getValue().getAsJsonObject();

            BeatDefinition def = new BeatDefinition(
                    getBoolean(defObj, "can_attack", true),
                    getString(defObj, "color", "#FFFFFF"),
                    getFloat(defObj, "scale", 1.0f),
                    getFloat(defObj, "damage_rate", 1.0f),
                    getFloat(defObj, "haptic_intensity", 1.0f),
                    getFloat(defObj, "tolerance", 0.1f),
                    getString(defObj, "particle", null),
                    getString(defObj, "trigger", null));

            definitions.put(name, def);
        }

        return definitions;
    }

    /**
     * 解析 timeline 节点
     */
    private static Timeline parseTimeline(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Timeline.empty();
        }

        JsonObject obj = element.getAsJsonObject();
        List<BeatEvent> combatLine = new ArrayList<>();
        Map<String, List<BeatEvent>> effectLines = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String lineName = entry.getKey();
            List<BeatEvent> events = parseBeatEvents(entry.getValue());

            if ("combat_line".equals(lineName)) {
                combatLine = events;
            } else {
                effectLines.put(lineName, events);
            }
        }

        return new Timeline(combatLine, effectLines);
    }

    /**
     * 解析节拍事件列表
     */
    private static List<BeatEvent> parseBeatEvents(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return Collections.emptyList();
        }

        List<BeatEvent> events = new ArrayList<>();
        JsonArray arr = element.getAsJsonArray();

        for (JsonElement eventElem : arr) {
            if (!eventElem.isJsonObject())
                continue;
            JsonObject eventObj = eventElem.getAsJsonObject();

            int t = getInt(eventObj, "t", 0);
            String type = getString(eventObj, "type", "normal_hit");

            // 解析 props
            Map<String, Object> props = null;
            if (eventObj.has("props") && eventObj.get("props").isJsonObject()) {
                props = parseProps(eventObj.get("props").getAsJsonObject());
            }

            events.add(new BeatEvent(t, type, props));
        }

        // 按时间排序
        events.sort(Comparator.comparingInt(BeatEvent::t));
        return events;
    }

    /**
     * 解析 props 对象
     */
    private static Map<String, Object> parseProps(JsonObject obj) {
        Map<String, Object> props = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                JsonPrimitive prim = value.getAsJsonPrimitive();
                if (prim.isNumber()) {
                    props.put(entry.getKey(), prim.getAsDouble());
                } else if (prim.isBoolean()) {
                    props.put(entry.getKey(), prim.getAsBoolean());
                } else {
                    props.put(entry.getKey(), prim.getAsString());
                }
            }
        }
        return props;
    }

    // 辅助方法
    private static boolean getBoolean(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }

    private static String getString(JsonObject obj, String key, String def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        return obj.has(key) ? obj.get(key).getAsFloat() : def;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }

    /**
     * Timeline 自定义反序列化器
     */
    private static class TimelineDeserializer implements JsonDeserializer<Timeline> {
        @Override
        public Timeline deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return parseTimeline(json);
        }
    }
}
