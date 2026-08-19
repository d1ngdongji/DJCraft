package otto.djgun.djcraft.combat.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import otto.djgun.djcraft.session.DJSessionClient;
import otto.djgun.djcraft.util.BeatGridUtil;

/** Keeps every client-predicted DJ item cooldown on the same session-time timeline. */
@OnlyIn(Dist.CLIENT)
public final class DJClientItemCooldowns {
    private static final Map<Item, Long> END_SESSION_MS = new HashMap<>();

    private DJClientItemCooldowns() {
    }

    public static void apply(Player player, ItemStack stack, DJSessionClient session, double beats) {
        apply(player, stack, session, beats, false);
    }

    public static void applyIfLonger(Player player, ItemStack stack, DJSessionClient session, double beats) {
        apply(player, stack, session, beats, true);
    }

    public static void clear() {
        END_SESSION_MS.clear();
    }

    static boolean shouldReplace(long durationMs, long currentEndMs, long nowMs) {
        return durationMs > Math.max(0L, currentEndMs - nowMs);
    }

    private static void apply(Player player, ItemStack stack, DJSessionClient session, double beats,
            boolean onlyIfLonger) {
        if (stack.isEmpty()) {
            return;
        }
        double resolvedBeats = Math.max(0.0, beats);
        long now = session.getCurrentTimeMs();
        long durationMs = BeatGridUtil.getDurationMs(now,
                session.getTrackPack().timeline().combatLine(), resolvedBeats);
        if (durationMs <= 0L) {
            return;
        }

        Item item = stack.getItem();
        long currentEndMs = END_SESSION_MS.getOrDefault(item, now);
        if (onlyIfLonger && !shouldReplace(durationMs, currentEndMs, now)) {
            return;
        }

        int ticks = BeatGridUtil.getDurationTicks(now,
                session.getTrackPack().timeline().combatLine(), resolvedBeats);
        if (ticks > 0) {
            player.getCooldowns().addCooldown(item, ticks);
            END_SESSION_MS.put(item, now + durationMs);
        }
    }
}
