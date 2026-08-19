package otto.djgun.djcraft.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Client-only scheduling for Flowery's border and reusable player afterimages. */
public final class FloweryDashVisualState {
    private static final long EMISSION_INTERVAL_MS = 75L;
    private static final long AFTERIMAGE_LIFETIME_MS = 750L;
    private static final double RAINBOW_SPEED_MULTIPLIER = 2.5;
    private static final Map<UUID, TrailState> ACTIVE_TRAILS = new HashMap<>();

    private FloweryDashVisualState() {
    }

    public static void activate(UUID playerId, int durationTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || playerId == null) {
            return;
        }
        ACTIVE_TRAILS.put(playerId, new TrailState(
                minecraft.level.getGameTime() + Math.max(0, durationTicks), 0L));
    }

    public static boolean isLocalActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && isActive(minecraft.player.getUUID());
    }

    public static void capture(RenderPlayerEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        UUID playerId = player.getUUID();
        TrailState trail = ACTIVE_TRAILS.get(playerId);
        if (trail == null || minecraft.level.getGameTime() >= trail.expiresAtTick()) {
            ACTIVE_TRAILS.remove(playerId);
            return;
        }
        long nowMs = System.nanoTime() / 1_000_000L;
        if (nowMs < trail.nextEmissionAtMs()) {
            return;
        }
        ACTIVE_TRAILS.put(playerId,
                new TrailState(trail.expiresAtTick(), nowMs + EMISSION_INTERVAL_MS));
        int color = DJRainbowColor.argb(sampleRainbow(nowMs), 0.95F);
        DJPlayerAfterimageRenderer.emit(event.getRenderer(), player, event.getPartialTick(),
                color, AFTERIMAGE_LIFETIME_MS);
    }

    public static DJRainbowColor.Rgb sampleRainbow(long nowMs) {
        return DJRainbowColor.sample(Math.round(nowMs * RAINBOW_SPEED_MULTIPLIER), 0L);
    }

    public static void reset() {
        ACTIVE_TRAILS.clear();
        DJPlayerAfterimageRenderer.clear();
    }

    private static boolean isActive(UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        TrailState trail = ACTIVE_TRAILS.get(playerId);
        if (minecraft.level == null || trail == null
                || minecraft.level.getGameTime() >= trail.expiresAtTick()) {
            ACTIVE_TRAILS.remove(playerId);
            return false;
        }
        return true;
    }

    private record TrailState(long expiresAtTick, long nextEmissionAtMs) {
    }
}
