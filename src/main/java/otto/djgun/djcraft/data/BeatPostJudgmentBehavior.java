package otto.djgun.djcraft.data;

import java.util.Locale;

/** Client visual behavior applied to a beat after an attack judgment. */
public enum BeatPostJudgmentBehavior {
    NONE("none"),
    FREEZE_DISSIPATE("freeze_dissipate"),
    DISSIPATE("dissipate"),
    BOUNCE("bounce");

    private final String id;

    BeatPostJudgmentBehavior(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static BeatPostJudgmentBehavior fromId(String id, BeatPostJudgmentBehavior fallback) {
        if (id == null) {
            return fallback;
        }
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "none" -> NONE;
            case "freeze_dissipate" -> FREEZE_DISSIPATE;
            case "dissipate" -> DISSIPATE;
            case "bounce" -> BOUNCE;
            default -> fallback;
        };
    }
}
