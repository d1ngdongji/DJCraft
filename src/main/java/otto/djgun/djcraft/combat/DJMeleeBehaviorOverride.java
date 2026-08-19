package otto.djgun.djcraft.combat;

import java.util.Set;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import otto.djgun.djcraft.api.combat.DJAreaMeleeBehavior;
import otto.djgun.djcraft.api.combat.DJMeleeBehavior;
import otto.djgun.djcraft.api.combat.DJSoftTargetMeleeBehavior;

final class DJMeleeBehaviorOverride {
    private static final Set<String> SOFT_FIELDS = Set.of(
            "reach", "horizontal_angle_degrees", "vertical_angle_degrees");
    private static final Set<String> AREA_FIELDS = Set.of("cylinder_length", "radius");

    private DJMeleeBehaviorOverride() {
    }

    static DJMeleeBehavior parse(JsonObject object, DJMeleeBehavior defaults) {
        Set<String> allowed = defaults instanceof DJAreaMeleeBehavior ? AREA_FIELDS : SOFT_FIELDS;
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("Unknown melee field for "
                        + (defaults.area() ? "area" : "soft-target") + " behavior: " + field);
            }
        }
        if (defaults instanceof DJAreaMeleeBehavior area) {
            double length = GsonHelper.getAsDouble(object, "cylinder_length", area.cylinderLength());
            double radius = GsonHelper.getAsDouble(object, "radius", area.radius());
            return area.withDimensions(length, radius);
        }
        DJSoftTargetMeleeBehavior soft = (DJSoftTargetMeleeBehavior) defaults;
        return new DJSoftTargetMeleeBehavior(
                GsonHelper.getAsDouble(object, "reach", soft.reach()),
                GsonHelper.getAsDouble(object, "horizontal_angle_degrees", soft.horizontalAngleDegrees()),
                GsonHelper.getAsDouble(object, "vertical_angle_degrees", soft.verticalAngleDegrees()));
    }
}
