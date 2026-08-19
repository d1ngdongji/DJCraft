package otto.djgun.djcraft.combat;

import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.resources.ResourceLocation;

/** Server-authored configuration for an instantaneous trigger-family ray weapon. */
public record DJRayWeaponProfile(double range, double baseDamage, boolean pierceEntities,
        ResourceLocation effect, double horizontalAimAssistPercent, double verticalAimAssistPercent,
        int autoChargeBeats, DJRayExplosionProfile explosion) {
    public static final double DEFAULT_AIM_ASSIST_PERCENT = 7.0;
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "range", "base_damage", "pierce_entities", "effect",
            "horizontal_aim_assist_percent", "vertical_aim_assist_percent",
            "auto_charge_beats", "explosion");

    public DJRayWeaponProfile(double range, double baseDamage, boolean pierceEntities,
            ResourceLocation effect, double horizontalAimAssistPercent, double verticalAimAssistPercent) {
        this(range, baseDamage, pierceEntities, effect, horizontalAimAssistPercent,
                verticalAimAssistPercent, 0, null);
    }

    public DJRayWeaponProfile {
        if (!Double.isFinite(range) || range <= 0.0 || range > 1_024.0) {
            throw new IllegalArgumentException("range must be a finite number in (0, 1024]");
        }
        if (!Double.isFinite(baseDamage) || baseDamage < 0.0 || baseDamage > Float.MAX_VALUE) {
            throw new IllegalArgumentException("base_damage must be a finite non-negative number");
        }
        if (effect == null) {
            throw new IllegalArgumentException("effect is required");
        }
        requirePercent(horizontalAimAssistPercent, "horizontal_aim_assist_percent");
        requirePercent(verticalAimAssistPercent, "vertical_aim_assist_percent");
        if (autoChargeBeats < 0 || autoChargeBeats > 32) {
            throw new IllegalArgumentException("auto_charge_beats must be an integer in [0, 32]");
        }
    }

    public static DJRayWeaponProfile parse(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Profile must be a JSON object");
        }
        JsonObject object = element.getAsJsonObject();
        for (String field : object.keySet()) {
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unknown field: " + field);
            }
        }
        double range = readNumber(object, "range");
        double baseDamage = readNumber(object, "base_damage");
        JsonElement pierceElement = required(object, "pierce_entities");
        if (!(pierceElement instanceof JsonPrimitive pierce) || !pierce.isBoolean()) {
            throw new IllegalArgumentException("pierce_entities must be a boolean");
        }
        JsonElement effectElement = required(object, "effect");
        if (!(effectElement instanceof JsonPrimitive effectPrimitive) || !effectPrimitive.isString()) {
            throw new IllegalArgumentException("effect must be a resource location string");
        }
        ResourceLocation effect = ResourceLocation.tryParse(effectPrimitive.getAsString());
        if (effect == null) {
            throw new IllegalArgumentException("effect must be a valid resource location");
        }
        double horizontalAimAssistPercent = readOptionalNumber(object,
                "horizontal_aim_assist_percent", DEFAULT_AIM_ASSIST_PERCENT);
        double verticalAimAssistPercent = readOptionalNumber(object,
                "vertical_aim_assist_percent", DEFAULT_AIM_ASSIST_PERCENT);
        int autoChargeBeats = readOptionalInteger(object, "auto_charge_beats", 0);
        DJRayExplosionProfile explosion = object.has("explosion")
                ? DJRayExplosionProfile.parse(object.get("explosion")) : null;
        return new DJRayWeaponProfile(range, baseDamage, pierce.getAsBoolean(), effect,
                horizontalAimAssistPercent, verticalAimAssistPercent, autoChargeBeats, explosion);
    }

    private static double readNumber(JsonObject object, String field) {
        JsonElement element = required(object, field);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return primitive.getAsDouble();
    }

    private static double readOptionalNumber(JsonObject object, String field, double fallback) {
        return object.has(field) ? readNumber(object, field) : fallback;
    }

    private static int readOptionalInteger(JsonObject object, String field, int fallback) {
        if (!object.has(field)) {
            return fallback;
        }
        JsonElement element = object.get(field);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        double value = primitive.getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value)) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return (int) value;
    }

    private static void requirePercent(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
            throw new IllegalArgumentException(field + " must be a finite number in [0, 100]");
        }
    }

    private static JsonElement required(JsonObject object, String field) {
        if (!object.has(field)) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return object.get(field);
    }
}
