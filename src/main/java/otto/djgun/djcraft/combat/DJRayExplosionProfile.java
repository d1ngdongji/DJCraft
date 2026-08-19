package otto.djgun.djcraft.combat;

import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Optional server-authored radial damage attached to a ray endpoint. */
public record DJRayExplosionProfile(double radius, double damage,
        double airborneRadius, double airborneDamage, boolean explodeAtMaxRange) {
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "radius", "damage", "airborne_radius", "airborne_damage", "explode_at_max_range");

    public DJRayExplosionProfile {
        requireRadius(radius, "radius");
        requireDamage(damage, "damage");
        requireRadius(airborneRadius, "airborne_radius");
        requireDamage(airborneDamage, "airborne_damage");
    }

    public static DJRayExplosionProfile parse(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("explosion must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        for (String field : object.keySet()) {
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unknown explosion field: " + field);
            }
        }
        return new DJRayExplosionProfile(
                number(object, "radius"), number(object, "damage"),
                number(object, "airborne_radius"), number(object, "airborne_damage"),
                booleanValue(object, "explode_at_max_range"));
    }

    private static double number(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return value.getAsDouble();
    }

    private static boolean booleanValue(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static JsonElement required(JsonObject object, String field) {
        if (!object.has(field)) {
            throw new IllegalArgumentException("Missing explosion field: " + field);
        }
        return object.get(field);
    }

    private static void requireRadius(double value, String field) {
        if (!Double.isFinite(value) || value <= 0.0 || value > 64.0) {
            throw new IllegalArgumentException(field + " must be a finite number in (0, 64]");
        }
    }

    private static void requireDamage(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > Float.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must be a finite non-negative number");
        }
    }
}
