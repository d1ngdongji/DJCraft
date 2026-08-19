package otto.djgun.djcraft.cybergrind;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public final class CyberGrindEvents {
    private CyberGrindEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            CyberGrindManager.getInstance().associateSummon(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        Player attacker = event.getSource().getEntity() instanceof Player player ? player : null;
        if (attacker != null && CyberGrindManager.getInstance().sameActiveRun(
                attacker.getUUID(), victim.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (CyberGrindManager.getInstance().ownsEntity(event.getEntity().getUUID())) {
            event.getDrops().clear();
        }
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (CyberGrindManager.getInstance().ownsEntity(event.getEntity().getUUID())) {
            event.setDroppedExperience(0);
        }
    }

    @SubscribeEvent
    public static void onMobDespawn(MobDespawnEvent event) {
        if (CyberGrindManager.getInstance().ownsEntity(event.getEntity().getUUID())) {
            event.setResult(MobDespawnEvent.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && player.level().dimension().equals(CyberGrindManager.DIMENSION)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() != null
                && event.getEntity().level().dimension().equals(CyberGrindManager.DIMENSION)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level().dimension().equals(CyberGrindManager.DIMENSION)
                && (event.getItemStack().getItem() instanceof BlockItem
                        || event.getItemStack().getItem() instanceof BucketItem)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension().equals(CyberGrindManager.DIMENSION)) {
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (event.getEntity().level().dimension().equals(CyberGrindManager.DIMENSION)) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (event.getEntity().level().dimension().equals(CyberGrindManager.DIMENSION)) {
            event.setCanceled(true);
        }
    }
}
