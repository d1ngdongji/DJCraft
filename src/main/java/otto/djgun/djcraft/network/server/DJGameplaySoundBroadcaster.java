package otto.djgun.djcraft.network.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.network.packet.DJWeaponSoundBroadcastPayload;
import otto.djgun.djcraft.sound.BeatOutcome;
import otto.djgun.djcraft.sound.DJWeaponSoundIdentityRegistry;
import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;
import otto.djgun.djcraft.sound.TargetOutcome;

public final class DJGameplaySoundBroadcaster {
    private DJGameplaySoundBroadcaster() {
    }

    public static void broadcast(ServerPlayer player, long sequence, InteractionHand hand,
            DJWeaponSoundSemantic semantic, BeatOutcome beat, TargetOutcome target) {
        broadcast(player, sequence, hand, semantic, DJWeaponSoundIdentityRegistry.GENERIC, beat, target);
    }

    public static void broadcast(ServerPlayer player, long sequence, InteractionHand hand,
            DJWeaponSoundSemantic semantic, ResourceLocation profileId, BeatOutcome beat, TargetOutcome target) {
        long safeSequence = Math.max(1L, sequence);
        long seed = safeSequence * 0x9E3779B97F4A7C15L ^ player.getUUID().hashCode();
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null,
                player.getX(), player.getY(), player.getZ(), 64.0,
                new DJWeaponSoundBroadcastPayload(safeSequence, player.getUUID(), safeSequence,
                        hand, semantic,
                        profileId, beat, target,
                        player.getX(), player.getY(), player.getZ(), seed));
    }
}
