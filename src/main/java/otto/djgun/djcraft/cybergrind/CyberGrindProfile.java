package otto.djgun.djcraft.cybergrind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

/** Immutable, server-authored Cyber Grind preset loaded from a data pack. */
public record CyberGrindProfile(ResourceLocation id, String displayName, String description,
        int advanceThreshold, int warningTicks, double partyBudgetPerExtraPlayer,
        List<BudgetRange> budgetRanges, List<EnemyEntry> entries) {
    private static final int MAX_NBT_CHARS = 65_536;
    private static final Set<String> RESERVED_NBT = Set.of(
            "id", "UUID", "Pos", "Rotation", "Motion", "Dimension", "Passengers", "PortalCooldown");

    public CyberGrindProfile {
        if (id == null || displayName == null || description == null) {
            throw new IllegalArgumentException("id, display name and description are required");
        }
        if (advanceThreshold < 0) {
            throw new IllegalArgumentException("advance_threshold must be non-negative");
        }
        if (warningTicks < 1 || warningTicks > 20 * 60) {
            throw new IllegalArgumentException("warning_ticks must be in [1, 1200]");
        }
        if (!Double.isFinite(partyBudgetPerExtraPlayer) || partyBudgetPerExtraPlayer < 0.0
                || partyBudgetPerExtraPlayer > 16.0) {
            throw new IllegalArgumentException("party_budget_per_extra_player must be in [0, 16]");
        }
        budgetRanges = validateRanges(budgetRanges);
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("entries must contain at least one valid enemy");
        }
    }

    public int budgetFor(int wave, int alivePlayers) {
        if (wave < 1) {
            throw new IllegalArgumentException("wave must be positive");
        }
        BudgetRange range = budgetRanges.stream().filter(candidate -> candidate.contains(wave))
                .findFirst().orElseThrow(() -> new IllegalStateException("No budget range for wave " + wave));
        long base = Math.min(range.maxBudget(), (long) range.baseBudget()
                + (long) (wave - range.minWave()) * range.budgetPerWave());
        double scaled = base * (1.0 + partyBudgetPerExtraPlayer * Math.max(0, alivePlayers - 1));
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(scaled));
    }

    public static CyberGrindProfile parse(ResourceLocation id, JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Preset must be a JSON object");
        }
        JsonObject object = element.getAsJsonObject();
        String displayName = readText(object, "display_name", id.toString());
        String description = readText(object, "description", "");
        int threshold = readInt(object, "advance_threshold", 5);
        int warningTicks = readInt(object, "warning_ticks", 40);
        double partyScale = readDouble(object, "party_budget_per_extra_player", 0.75);

        JsonArray rangeArray = requiredArray(object, "budget_ranges");
        List<BudgetRange> ranges = new ArrayList<>();
        for (JsonElement rangeElement : rangeArray) {
            JsonObject range = requireObject(rangeElement, "budget range");
            int minWave = readInt(range, "min_wave", -1);
            Integer maxWave = range.has("max_wave") ? readInt(range, "max_wave", -1) : null;
            int baseBudget = readInt(range, "base_budget", -1);
            int perWave = readInt(range, "budget_per_wave", 0);
            int maxBudget = readInt(range, "max_budget", baseBudget);
            ranges.add(new BudgetRange(minWave, maxWave, baseBudget, perWave, maxBudget));
        }

        JsonArray entryArray = requiredArray(object, "entries");
        List<EnemyEntry> entries = new ArrayList<>();
        int entryIndex = 0;
        for (JsonElement entryElement : entryArray) {
            try {
                JsonObject entry = requireObject(entryElement, "enemy entry");
                ResourceLocation entity = ResourceLocation.tryParse(readString(entry, "entity"));
                if (entity == null) {
                    throw new IllegalArgumentException("entity must be a valid resource location");
                }
                int cost = readInt(entry, "cost", -1);
                int drawWeight = readInt(entry, "draw_weight", 1);
                int minWave = readInt(entry, "min_wave", 1);
                Integer maxWave = entry.has("max_wave") ? readInt(entry, "max_wave", -1) : null;
                double chance = readDouble(entry, "chance", 1.0);
                int minCount = readInt(entry, "min_count", 0);
                int maxCount = readInt(entry, "max_count", 1);
                CompoundTag nbt = new CompoundTag();
                if (entry.has("nbt")) {
                    String snbt = readString(entry, "nbt");
                    if (snbt.length() > MAX_NBT_CHARS) {
                        throw new IllegalArgumentException("nbt exceeds 65536 characters");
                    }
                    try {
                        nbt = TagParser.parseTag(snbt);
                    } catch (Exception exception) {
                        throw new IllegalArgumentException("invalid nbt: " + exception.getMessage(), exception);
                    }
                    RESERVED_NBT.forEach(nbt::remove);
                }
                entries.add(new EnemyEntry(entity, cost, drawWeight, minWave, maxWave,
                        chance, minCount, maxCount, nbt));
            } catch (RuntimeException exception) {
                otto.djgun.djcraft.DJCraft.LOGGER.error(
                        "Ignoring invalid Cyber Grind enemy entry {}[{}]: {}", id, entryIndex,
                        exception.getMessage());
            }
            entryIndex++;
        }
        return new CyberGrindProfile(id, displayName, description, threshold, warningTicks,
                partyScale, ranges, entries);
    }

    private static List<BudgetRange> validateRanges(List<BudgetRange> source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("budget_ranges must not be empty");
        }
        List<BudgetRange> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingInt(BudgetRange::minWave));
        int expected = 1;
        boolean unbounded = false;
        for (BudgetRange range : sorted) {
            if (unbounded || range.minWave() != expected) {
                throw new IllegalArgumentException("budget_ranges must continuously cover waves from 1");
            }
            if (range.maxWave() == null) {
                unbounded = true;
            } else {
                expected = range.maxWave() + 1;
            }
        }
        if (!unbounded) {
            throw new IllegalArgumentException("final budget range must omit max_wave");
        }
        return List.copyOf(sorted);
    }

    private static JsonArray requiredArray(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return object.getAsJsonArray(field);
    }

    private static JsonObject requireObject(JsonElement element, String label) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static String readText(JsonObject object, String field, String fallback) {
        if (!object.has(field)) {
            return fallback;
        }
        JsonElement value = object.get(field);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return value.getAsString();
        }
        if (value.isJsonObject()) {
            JsonObject component = value.getAsJsonObject();
            if (component.has("text")) {
                return component.get("text").getAsString();
            }
            if (component.has("translate")) {
                return component.get("translate").getAsString();
            }
        }
        throw new IllegalArgumentException(field + " must be a string or simple text component");
    }

    private static String readString(JsonObject object, String field) {
        if (!object.has(field) || !(object.get(field) instanceof JsonPrimitive primitive)
                || !primitive.isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return primitive.getAsString();
    }

    private static int readInt(JsonObject object, String field, int fallback) {
        if (!object.has(field)) {
            return fallback;
        }
        JsonElement value = object.get(field);
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        double number = primitive.getAsDouble();
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return (int) number;
    }

    private static double readDouble(JsonObject object, String field, double fallback) {
        if (!object.has(field)) {
            return fallback;
        }
        JsonElement value = object.get(field);
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return primitive.getAsDouble();
    }

    public record BudgetRange(int minWave, Integer maxWave, int baseBudget,
            int budgetPerWave, int maxBudget) {
        public BudgetRange {
            if (minWave < 1 || maxWave != null && maxWave < minWave) {
                throw new IllegalArgumentException("invalid budget wave interval");
            }
            if (baseBudget < 1 || budgetPerWave < 0 || maxBudget < baseBudget) {
                throw new IllegalArgumentException("budgets must be positive and max_budget >= base_budget");
            }
        }

        public boolean contains(int wave) {
            return wave >= minWave && (maxWave == null || wave <= maxWave);
        }
    }

    public record EnemyEntry(ResourceLocation entityId, int cost, int drawWeight,
            int minWave, Integer maxWave, double chance, int minCount, int maxCount,
            CompoundTag nbt) {
        public EnemyEntry {
            if (entityId == null || nbt == null) {
                throw new IllegalArgumentException("entity and nbt are required");
            }
            if (cost < 1 || drawWeight < 1 || minWave < 1 || maxWave != null && maxWave < minWave) {
                throw new IllegalArgumentException("invalid enemy cost, weight, or wave interval");
            }
            if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
                throw new IllegalArgumentException("chance must be in [0, 1]");
            }
            if (minCount < 0 || maxCount < minCount) {
                throw new IllegalArgumentException("enemy counts must satisfy 0 <= min_count <= max_count");
            }
            nbt = nbt.copy();
        }

        public boolean availableAt(int wave) {
            return wave >= minWave && (maxWave == null || wave <= maxWave);
        }
    }
}
