package otto.djgun.djcraft.combat.access;

import net.minecraft.world.entity.Entity;

public interface AbstractArrowAccess {
    void djcraft$setPierceLevel(byte level);

    boolean djcraft$canHitEntity(Entity entity);

    void djcraft$resetFlightState();
}
