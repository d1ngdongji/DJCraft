package otto.djgun.djcraft.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Schedules one blue player-model afterimage at the start of an ordinary DJ dash. */
public final class DJNormalDashVisualState {
    private static final int CAPTURE_WINDOW_TICKS = 2;
    private static final long AFTERIMAGE_LIFETIME_MS = 750L;
    private static final int BLUE_AFTERIMAGE_ARGB = 0xF22A8CFF;
    private static final Map<UUID, Long> PENDING_CAPTURES = new HashMap<>();

    private DJNormalDashVisualState() {
    }

    public static void activate(UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && playerId != null) {
            PENDING_CAPTURES.put(playerId,
                    minecraft.level.getGameTime() + CAPTURE_WINDOW_TICKS);
        }
    }

    public static void capture(RenderPlayerEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        Long expiresAtTick = PENDING_CAPTURES.remove(player.getUUID());
        if (expiresAtTick == null || minecraft.level.getGameTime() > expiresAtTick) {
            return;
        }
        DJPlayerAfterimageRenderer.emit(event.getRenderer(), player, event.getPartialTick(),
                BLUE_AFTERIMAGE_ARGB, AFTERIMAGE_LIFETIME_MS);
    }

    public static void reset() {
        PENDING_CAPTURES.clear();
    }
}
