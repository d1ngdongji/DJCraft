package otto.djgun.djcraft.client.sound;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.client.animation.DJAnimationEvent;
import otto.djgun.djcraft.client.animation.DJAnimationSoundSemantics;
import otto.djgun.djcraft.network.packet.DJWeaponSoundBroadcastPayload;
import otto.djgun.djcraft.network.packet.DJWeaponSoundIntentPayload;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.sound.DJActionOutcome;
import otto.djgun.djcraft.sound.DJWeaponSoundIdentityRegistry;
import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;

public final class DJWeaponSoundRuntime {
    private static final DJWeaponSoundRuntime INSTANCE = new DJWeaponSoundRuntime();
    private final Set<ResourceLocation> missingEvents = new HashSet<>();
    private final Map<ChargeKey, SoundInstance> activeChargeSounds = new HashMap<>();
    private long soundSequence;

    private DJWeaponSoundRuntime() {
    }

    public static DJWeaponSoundRuntime getInstance() {
        return INSTANCE;
    }

    public void onAnimationEvent(DJAnimationEvent event, InteractionHand hand, ItemStack stack,
            long actionSequence, long judgedAtMs, int beatIndex) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ResourceLocation profile = DJWeaponSoundIdentityRegistry.resolve(stack);
        DJWeaponSoundSemantic semantic = DJAnimationSoundSemantics.from(event.semantic());
        if (semantic == null) {
            return;
        }
        ChargeKey chargeKey = new ChargeKey(minecraft.player.getUUID(), actionSequence, hand, profile);
        stopForCompletion(semantic, chargeKey);
        long sequence = ++soundSequence;
        long seed = mixSeed(event.timelineGeneration(), sequence, profile.hashCode());
        Playback playback = play(profile, semantic, event.outcome(), seed,
                minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
        trackCharge(semantic, chargeKey, playback);
        DJWeaponSoundProfile.Selection selection = playback == null ? null : playback.selection();
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (selection == null || !selection.spatial() || session == null) {
            return;
        }
        PacketDistributor.sendToServer(new DJWeaponSoundIntentPayload(session.getSessionId(), sequence,
                actionSequence, hand, semantic, profile, event.outcome().beat(), event.outcome().target(),
                judgedAtMs, beatIndex, seed));
    }

    public void onBroadcast(DJWeaponSoundBroadcastPayload payload) {
        ChargeKey chargeKey = new ChargeKey(payload.shooterId(), payload.actionSequence(),
                payload.hand(), payload.profileId());
        stopForCompletion(payload.semantic(), chargeKey);
        Playback playback = play(payload.profileId(), payload.semantic(),
                new DJActionOutcome(payload.beat(), payload.target()), payload.seed(),
                payload.x(), payload.y(), payload.z());
        trackCharge(payload.semantic(), chargeKey, playback);
    }

    public void stopCharge(UUID shooterId, long actionSequence, InteractionHand hand, ItemStack stack) {
        stopCharge(new ChargeKey(shooterId, actionSequence, hand,
                DJWeaponSoundIdentityRegistry.resolve(stack)));
    }

    public void reset() {
        Minecraft minecraft = Minecraft.getInstance();
        activeChargeSounds.values().forEach(minecraft.getSoundManager()::stop);
        activeChargeSounds.clear();
        soundSequence = 0L;
        missingEvents.clear();
    }

    private Playback play(ResourceLocation profile, DJWeaponSoundSemantic semantic,
            DJActionOutcome outcome, long seed, double x, double y, double z) {
        DJWeaponSoundProfile.Selection selection = DJWeaponSoundLibrary.getInstance()
                .select(profile, semantic, outcome, seed);
        if (selection == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager().getSoundEvent(selection.event()) == null) {
            if (missingEvents.add(selection.event())) {
                DJCraft.LOGGER.warn("Weapon sound event {} is missing; skipping playback", selection.event());
            }
            return new Playback(selection, null);
        }
        SoundInstance instance = new SimpleSoundInstance(selection.event(), SoundSource.PLAYERS,
                selection.volume(), selection.pitch(), RandomSource.create(seed), false, 0,
                net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR,
                x, y, z, false);
        minecraft.getSoundManager().play(instance);
        return new Playback(selection, instance);
    }

    private void trackCharge(DJWeaponSoundSemantic semantic, ChargeKey key, Playback playback) {
        if (semantic != DJWeaponSoundSemantic.CHARGE_START || key.actionSequence() <= 0L
                || playback == null || playback.instance() == null) {
            return;
        }
        SoundInstance previous = activeChargeSounds.put(key, playback.instance());
        if (previous != null) {
            Minecraft.getInstance().getSoundManager().stop(previous);
        }
    }

    private void stopForCompletion(DJWeaponSoundSemantic semantic, ChargeKey key) {
        if (shouldStopCharge(semantic)) {
            stopCharge(key);
        }
    }

    static boolean shouldStopCharge(DJWeaponSoundSemantic semantic) {
        return semantic == DJWeaponSoundSemantic.TRIGGER_IMPACT
                || semantic == DJWeaponSoundSemantic.CHARGE_RELEASE
                || semantic == DJWeaponSoundSemantic.CANCEL;
    }

    private void stopCharge(ChargeKey key) {
        SoundInstance instance = activeChargeSounds.remove(key);
        if (instance != null) {
            Minecraft.getInstance().getSoundManager().stop(instance);
        }
    }

    private static long mixSeed(long generation, long sequence, int profileHash) {
        long value = generation * 0x9E3779B97F4A7C15L + sequence;
        value ^= (long) profileHash << 32;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        return value ^ (value >>> 31);
    }

    private record ChargeKey(UUID shooterId, long actionSequence, InteractionHand hand,
            ResourceLocation profileId) {
    }

    private record Playback(DJWeaponSoundProfile.Selection selection, SoundInstance instance) {
    }
}
