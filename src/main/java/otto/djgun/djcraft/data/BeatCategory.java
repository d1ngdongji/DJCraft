package otto.djgun.djcraft.data;

import java.util.Locale;

/** Combat category attached to a beat definition. */
public enum BeatCategory {
    NORMAL("normal"),
    WEAKBEAT("weakbeat"),
    DOWNBEAT("downbeat");

    private final String id;

    BeatCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static BeatCategory fromId(String id) {
        if (id == null) {
            return NORMAL;
        }
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "weakbeat" -> WEAKBEAT;
            case "downbeat" -> DOWNBEAT;
            default -> NORMAL;
        };
    }
}
