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
    private static final java.util.regex.Pattern COLOR =
            java.util.regex.Pattern.compile("^#[0-9a-fA-F]{6}$");

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
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new JsonParseException("root must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            validateRoot(root);

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

        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            DJCraft.LOGGER.error("Failed to parse TrackPack JSON for pack: {}", packId, e);
            return null;
        }
    }

    private static void validateRoot(JsonObject root) {
        JsonObject meta = requireObject(root, "meta");
        requirePositiveNumber(meta, "bpm");
        requirePositiveInt(meta, "total_duration_ms");
        requireNonNegativeInt(meta, "playback_start_ms", false);
        requireInteger(meta, "offset_ms", false);
        Integer playbackStartMs = integerValue(meta, "playback_start_ms");
        Integer totalDurationMs = integerValue(meta, "total_duration_ms");
        if (playbackStartMs != null && totalDurationMs != null
                && playbackStartMs >= totalDurationMs) {
            throw new JsonParseException(
                    "meta.playback_start_ms must be less than meta.total_duration_ms");
        }
        String soundFile = getString(meta, "sound_file", "track.ogg");
        if (soundFile == null || soundFile.isBlank()) {
            throw new JsonParseException("meta.sound_file must not be blank");
        }

        if (root.has("settings") && !root.get("settings").isJsonNull()) {
            JsonObject settings = requireObject(root, "settings");
            String mode = getString(settings, "crosshair_mode", "time");
            if (!"time".equals(mode) && !"beat".equals(mode)) {
                throw new JsonParseException("settings.crosshair_mode must be 'time' or 'beat'");
            }
            requireNonNegativeInt(settings, "crosshair_time_ms", false);
            if (settings.has("crosshair_beat_count")
                    && (integerValue(settings, "crosshair_beat_count") == null
                    || integerValue(settings, "crosshair_beat_count") <= 0)) {
                throw new JsonParseException("settings.crosshair_beat_count must be a positive integer");
            }
            requireFinite(settings, "volume_multiplier");
        }

        JsonObject definitions = root.has("definitions")
                ? requireObject(root, "definitions") : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : definitions.entrySet()) {
            if (entry.getKey().isBlank() || !entry.getValue().isJsonObject()) {
                throw new JsonParseException("definitions entries must be named objects");
            }
            validateDefinition(entry.getValue().getAsJsonObject(),
                    "definitions." + entry.getKey());
        }

        if (root.has("timeline") && !root.get("timeline").isJsonNull()) {
            JsonObject timeline = requireObject(root, "timeline");
            for (Map.Entry<String, JsonElement> line : timeline.entrySet()) {
                if (!line.getValue().isJsonArray()) {
                    throw new JsonParseException("timeline." + line.getKey() + " must be an array");
                }
                for (JsonElement eventElement : line.getValue().getAsJsonArray()) {
                    if (!eventElement.isJsonObject()) {
                        throw new JsonParseException("timeline events must be objects");
                    }
                    JsonObject event = eventElement.getAsJsonObject();
                    requireNonNegativeInt(event, "t", true);
                    String type = getString(event, "type", null);
                    if (type == null || type.isBlank() || !definitions.has(type)) {
                        throw new JsonParseException("timeline event references missing definition: " + type);
                    }
                    if (event.has("props") && !event.get("props").isJsonNull()) {
                        JsonObject props = requireObject(event, "props");
                        validateFiniteNumbers(props, "timeline.props");
                        if (props.has("color")) {
                            requireColor(props.get("color").getAsString(), "timeline.props.color");
                        }
                    }
                }
            }
        }
    }

    private static void validateDefinition(JsonObject definition, String path) {
        if (definition.has("color")) {
            requireColor(definition.get("color").getAsString(), path + ".color");
        }
        requireFinite(definition, "scale");
        requireFinite(definition, "haptic_intensity");
        requireFinite(definition, "tolerance");
    }

    private static JsonObject requireObject(JsonObject parent, String key) {
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException(key + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static void requirePositiveInt(JsonObject object, String key) {
        Integer value = integerValue(object, key);
        if (value == null || value <= 0) {
            throw new JsonParseException(key + " must be a positive integer");
        }
    }

    private static void requirePositiveNumber(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException(key + " must be a positive number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new JsonParseException(key + " must be a positive number");
        }
    }

    private static void requireNonNegativeInt(JsonObject object, String key, boolean required) {
        if (!object.has(key)) {
            if (required) {
                throw new JsonParseException(key + " is required");
            }
            return;
        }
        Integer value = integerValue(object, key);
        if (value == null || value < 0) {
            throw new JsonParseException(key + " must be a non-negative integer");
        }
    }

    private static void requireFinite(JsonObject object, String key) {
        if (object.has(key)) {
            JsonElement element = object.get(key);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
                    || !Double.isFinite(element.getAsDouble())) {
                throw new JsonParseException(key + " must be a finite number");
            }
        }
    }

    private static void requireInteger(JsonObject object, String key, boolean required) {
        if (!object.has(key)) {
            if (required) {
                throw new JsonParseException(key + " is required");
            }
            return;
        }
        if (integerValue(object, key) == null) {
            throw new JsonParseException(key + " must be an integer");
        }
    }

    private static Integer integerValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            return null;
        }
        return (int) value;
    }

    private static void validateFiniteNumbers(JsonObject object, String path) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                    && !Double.isFinite(value.getAsDouble())) {
                throw new JsonParseException(path + "." + entry.getKey() + " must be finite");
            }
        }
    }

    private static void requireColor(String color, String path) {
        if (color == null || !COLOR.matcher(color).matches()) {
            throw new JsonParseException(path + " must use #RRGGBB");
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
                    BeatCategory.fromId(getString(defObj, "category", null)),
                    getFloat(defObj, "haptic_intensity", 1.0f),
                    getFloat(defObj, "tolerance", 0.1f),
                    getString(defObj, "particle", null),
                    getString(defObj, "trigger", null),
                    getString(defObj, "texture", BeatDefinition.DEFAULT_TEXTURE),
                    getFloat(defObj, "landing_x_percent", BeatDefinition.DEFAULT_LANDING_X_PERCENT),
                    getInt(defObj, "spawn_advance_ms", BeatDefinition.DEFAULT_SPAWN_ADVANCE_MS),
                    BeatPostJudgmentBehavior.fromId(
                            getString(defObj, "hit_behavior", null),
                            BeatDefinition.DEFAULT_HIT_BEHAVIOR),
                    BeatPostJudgmentBehavior.fromId(
                            getString(defObj, "miss_behavior", null),
                            BeatDefinition.DEFAULT_MISS_BEHAVIOR),
                    getFloat(defObj, "rotation_rpm", 0.0f),
                    BeatPostJudgmentBehavior.fromId(
                            getString(defObj, "matched_hit_behavior", null),
                            null));

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
