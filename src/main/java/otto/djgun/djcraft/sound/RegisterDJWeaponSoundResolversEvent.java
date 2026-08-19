package otto.djgun.djcraft.sound;

import net.neoforged.bus.api.Event;

/** Fired once on the NeoForge game bus during common setup. Resolvers must be side-safe. */
public final class RegisterDJWeaponSoundResolversEvent extends Event {
    public void register(int priority, DJWeaponSoundIdentityResolver resolver) {
        DJWeaponSoundIdentityRegistry.register(priority, resolver);
    }
}
