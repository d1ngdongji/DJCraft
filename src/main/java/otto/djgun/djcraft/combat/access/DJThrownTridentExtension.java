package otto.djgun.djcraft.combat.access;

import net.minecraft.world.entity.Entity;

public interface DJThrownTridentExtension {
    void djcraft$configureFlight(long sessionId, long returnAtTimelineMs);

    boolean djcraft$isDJTrident();

    boolean djcraft$canBeRedirected();

    boolean djcraft$tryRedirect(Entity attacker);
}
