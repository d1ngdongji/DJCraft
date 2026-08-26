package otto.djgun.djcraft.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.Set;

public record DJItemTimingProfile(
        Integer beatCooldown,
        Integer useBeatCooldown,
        Integer switchWarmup,
        Double attackEnergyCost,
        Double useEnergyCost) {
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "beat_cooldown", "use_beat_cooldown", "switch_warmup",
            "attack_energy_cost", "use_energy_cost");

    public DJItemTimingProfile {
        if (beatCooldown == null && useBeatCooldown == null && switchWarmup == null
                && attackEnergyCost == null && useEnergyCost == null) {
            throw new IllegalArgumentException("At least one item profile field is required");
        }
        if (beatCooldown != null && beatCooldown < 0) {
            throw new IllegalArgumentException("beat_cooldown must be non-negative");
        }
        if (useBeatCooldown != null && useBeatCooldown < 0) {
            throw new IllegalArgumentException("use_beat_cooldown must be non-negative");
        }
        if (switchWarmup != null && switchWarmup < 0) {
            throw new IllegalArgumentException("switch_warmup must be non-negative");
        }
        validateEnergyCost(attackEnergyCost, "attack_energy_cost");
        validateEnergyCost(useEnergyCost, "use_energy_cost");
    }

    public DJItemTimingProfile(Integer beatCooldown, Integer switchWarmup) {
        this(beatCooldown, null, switchWarmup, null, null);
    }

    public DJItemTimingProfile(Integer beatCooldown, Integer switchWarmup,
            Double attackEnergyCost, Double useEnergyCost) {
        this(beatCooldown, null, switchWarmup, attackEnergyCost, useEnergyCost);
    }

    public static DJItemTimingProfile parse(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Profile must be a JSON object");
        }

        JsonObject object = element.getAsJsonObject();
        for (String field : object.keySet()) {
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unknown field: " + field);
            }
        }

        Integer beatCooldown = readNonNegativeInteger(object, "beat_cooldown");
        Integer useBeatCooldown = readNonNegativeInteger(object, "use_beat_cooldown");
        Integer switchWarmup = readNonNegativeInteger(object, "switch_warmup");
        Double attackEnergyCost = readNonNegativeDouble(object, "attack_energy_cost");
        Double useEnergyCost = readNonNegativeDouble(object, "use_energy_cost");
        return new DJItemTimingProfile(beatCooldown, useBeatCooldown, switchWarmup,
                attackEnergyCost, useEnergyCost);
    }

    public int resolveBeatCooldown(int calculatedFallback) {
        return beatCooldown != null ? beatCooldown : calculatedFallback;
    }

    public int resolveUseBeatCooldown(int resolvedBeatCooldown) {
        return useBeatCooldown != null ? useBeatCooldown : resolvedBeatCooldown;
    }

    public int resolveSwitchWarmup(int resolvedBeatCooldown) {
        return switchWarmup != null ? switchWarmup : resolvedBeatCooldown;
    }

    public double resolveAttackEnergyCost() {
        return attackEnergyCost != null ? attackEnergyCost : 0.0;
    }

    public double resolveUseEnergyCost() {
        return useEnergyCost != null ? useEnergyCost : 0.0;
    }

    private static Integer readNonNegativeInteger(JsonObject object, String field) {
        if (!object.has(field)) {
            return null;
        }

        JsonElement element = object.get(field);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new IllegalArgumentException(field + " must be a non-negative integer");
        }

        try {
            BigDecimal value = primitive.getAsBigDecimal();
            int result = value.intValueExact();
            if (result < 0) {
                throw new IllegalArgumentException(field + " must be non-negative");
            }
            return result;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + " must be a 32-bit integer", exception);
        }
    }

    private static Double readNonNegativeDouble(JsonObject object, String field) {
        if (!object.has(field)) {
            return null;
        }

        JsonElement element = object.get(field);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new IllegalArgumentException(field + " must be a finite non-negative number");
        }

        double value = primitive.getAsDouble();
        validateEnergyCost(value, field);
        return value;
    }

    private static void validateEnergyCost(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value < 0.0)) {
            throw new IllegalArgumentException(field + " must be a finite non-negative number");
        }
    }
}
