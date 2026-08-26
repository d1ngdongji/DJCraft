package otto.djgun.djcraft.client.render;

import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.world.phys.Vec3;

/** Resource-driven visual tuning for reusable ray effects. */
public record DJRayEffectProfile(int coreColor, int haloColor, float coreWidth, float haloWidth,
        long beamLifetimeMs, boolean beamFadeFromNear,
        float beamWidthStartScale, float beamWidthPeakScale, long burstLifetimeMs,
        float burstStartRadius, float burstEndRadius,
        float pulseSpeed, float muzzleBurstScale, float contactBurstScale, float endBurstScale,
        long shockwaveLifetimeMs, float shockwaveStartRadius, int shockwaveCoreColor, int shockwaveHaloColor,
        Vec3 firstPersonMainMuzzle, Vec3 firstPersonOffhandMuzzle,
        Vec3 thirdPersonMuzzle) {
    private static final Set<String> FIELDS = Set.of(
            "core_color", "halo_color", "core_width", "halo_width", "beam_lifetime_ms",
            "beam_fade_from_near", "beam_width_start_scale", "beam_width_peak_scale",
            "burst_lifetime_ms", "burst_start_radius", "burst_end_radius", "pulse_speed",
            "muzzle_burst_scale", "contact_burst_scale", "end_burst_scale",
            "shockwave_lifetime_ms", "shockwave_start_radius",
            "shockwave_core_color", "shockwave_halo_color",
            "first_person_muzzle", "first_person_main_muzzle", "first_person_offhand_muzzle",
            "third_person_muzzle");

    public DJRayEffectProfile {
        if (!Float.isFinite(coreWidth) || !Float.isFinite(haloWidth)
                || coreWidth <= 0.0F || haloWidth < coreWidth
                || beamLifetimeMs <= 0L || burstLifetimeMs <= 0L
                || !Float.isFinite(beamWidthStartScale) || beamWidthStartScale < 0.0F
                || !Float.isFinite(beamWidthPeakScale) || beamWidthPeakScale < 0.0F
                || (beamWidthPeakScale > 0.0F && beamWidthPeakScale < beamWidthStartScale)
                || !Float.isFinite(burstStartRadius) || !Float.isFinite(burstEndRadius)
                || burstStartRadius < 0.0F || burstEndRadius < burstStartRadius
                || !Float.isFinite(pulseSpeed) || pulseSpeed <= 0.0F
                || !Float.isFinite(muzzleBurstScale) || muzzleBurstScale < 0.0F
                || !Float.isFinite(contactBurstScale) || contactBurstScale < 0.0F
                || !Float.isFinite(endBurstScale) || endBurstScale < 0.0F
                || shockwaveLifetimeMs < 0L || !Float.isFinite(shockwaveStartRadius)
                || shockwaveStartRadius < 0.0F
                || firstPersonMainMuzzle == null || firstPersonOffhandMuzzle == null
                || thirdPersonMuzzle == null) {
            throw new IllegalArgumentException("Invalid ray effect dimensions or lifetime");
        }
    }

    public static DJRayEffectProfile parse(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Ray effect must be a JSON object");
        }
        JsonObject object = element.getAsJsonObject();
        for (String field : object.keySet()) {
            if (!FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unknown field: " + field);
            }
        }
        Vec3 legacyFirstPerson = optionalVector(object, "first_person_muzzle");
        Vec3 firstPersonMain = optionalVector(object, "first_person_main_muzzle");
        Vec3 firstPersonOffhand = optionalVector(object, "first_person_offhand_muzzle");
        if (firstPersonMain == null) {
            firstPersonMain = legacyFirstPerson;
        }
        if (firstPersonOffhand == null && legacyFirstPerson != null) {
            firstPersonOffhand = new Vec3(-legacyFirstPerson.x, legacyFirstPerson.y, legacyFirstPerson.z);
        }
        if (firstPersonMain == null || firstPersonOffhand == null) {
            throw new IllegalArgumentException("Provide first_person_muzzle or both hand-specific first-person muzzle fields");
        }
        return new DJRayEffectProfile(
                color(object, "core_color"), color(object, "halo_color"),
                positiveFloat(object, "core_width"), positiveFloat(object, "halo_width"),
                positiveLong(object, "beam_lifetime_ms"),
                optionalBoolean(object, "beam_fade_from_near", false),
                optionalNonNegativeFloat(object, "beam_width_start_scale", 1.0F),
                optionalNonNegativeFloat(object, "beam_width_peak_scale", 0.0F),
                positiveLong(object, "burst_lifetime_ms"),
                nonNegativeFloat(object, "burst_start_radius"), nonNegativeFloat(object, "burst_end_radius"),
                number(object, "pulse_speed").floatValue(), optionalNonNegativeFloat(object, "muzzle_burst_scale", 1.0F),
                optionalNonNegativeFloat(object, "contact_burst_scale", 1.0F),
                optionalNonNegativeFloat(object, "end_burst_scale", 1.0F),
                optionalNonNegativeLong(object, "shockwave_lifetime_ms", 0L),
                optionalNonNegativeFloat(object, "shockwave_start_radius", 0.0F),
                optionalColor(object, "shockwave_core_color", color(object, "core_color")),
                optionalColor(object, "shockwave_halo_color", color(object, "halo_color")),
                firstPersonMain, firstPersonOffhand,
                vector(object, "third_person_muzzle"));
    }

    private static int color(JsonObject object, String field) {
        JsonElement element = required(object, field);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new IllegalArgumentException(field + " must be #RRGGBB or #RRGGBBAA");
        }
        String value = primitive.getAsString();
        if (!value.matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) {
            throw new IllegalArgumentException(field + " must be #RRGGBB or #RRGGBBAA");
        }
        long rgba = Long.parseUnsignedLong(value.substring(1), 16);
        if (value.length() == 7) {
            rgba = rgba << 8 | 0xFFL;
        }
        int red = (int) (rgba >>> 24) & 0xFF;
        int green = (int) (rgba >>> 16) & 0xFF;
        int blue = (int) (rgba >>> 8) & 0xFF;
        int alpha = (int) rgba & 0xFF;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int optionalColor(JsonObject object, String field, int fallback) {
        return object.has(field) ? color(object, field) : fallback;
    }

    private static Number number(JsonObject object, String field) {
        JsonElement element = required(object, field);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        double value = primitive.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return value;
    }

    private static float positiveFloat(JsonObject object, String field) {
        float value = number(object, field).floatValue();
        if (!Float.isFinite(value) || !(value > 0.0F)) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static float nonNegativeFloat(JsonObject object, String field) {
        float value = number(object, field).floatValue();
        if (!Float.isFinite(value) || value < 0.0F) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    private static float optionalNonNegativeFloat(JsonObject object, String field, float fallback) {
        return object.has(field) ? nonNegativeFloat(object, field) : fallback;
    }

    private static long positiveLong(JsonObject object, String field) {
        double value = number(object, field).doubleValue();
        if (value <= 0.0 || value != Math.rint(value) || value > Long.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return (long) value;
    }

    private static long optionalNonNegativeLong(JsonObject object, String field, long fallback) {
        if (!object.has(field)) {
            return fallback;
        }
        double value = number(object, field).doubleValue();
        if (value < 0.0 || value != Math.rint(value) || value > Long.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must be a non-negative integer");
        }
        return (long) value;
    }

    private static boolean optionalBoolean(JsonObject object, String field, boolean fallback) {
        if (!object.has(field)) {
            return fallback;
        }
        JsonElement element = object.get(field);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return primitive.getAsBoolean();
    }

    private static Vec3 vector(JsonObject object, String field) {
        JsonElement element = required(object, field);
        if (!(element instanceof JsonArray array) || array.size() != 3) {
            throw new IllegalArgumentException(field + " must contain three numbers");
        }
        double[] values = new double[3];
        for (int index = 0; index < 3; index++) {
            JsonElement component = array.get(index);
            if (!(component instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
                throw new IllegalArgumentException(field + " must contain three numbers");
            }
            values[index] = primitive.getAsDouble();
            if (!Double.isFinite(values[index])) {
                throw new IllegalArgumentException(field + " components must be finite");
            }
        }
        return new Vec3(values[0], values[1], values[2]);
    }

    private static Vec3 optionalVector(JsonObject object, String field) {
        return object.has(field) ? vector(object, field) : null;
    }

    private static JsonElement required(JsonObject object, String field) {
        if (!object.has(field)) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return object.get(field);
    }
}
