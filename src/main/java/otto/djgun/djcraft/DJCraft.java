package otto.djgun.djcraft;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import otto.djgun.djcraft.command.DJCommands;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.SyncTrackHashesPayload;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.init.ModAttributes;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;
import otto.djgun.djcraft.combat.DJItemBehaviorReloadListener;
import otto.djgun.djcraft.combat.DJItemTimingReloadListener;
import otto.djgun.djcraft.combat.DJRayWeaponManager;
import otto.djgun.djcraft.combat.DJRayWeaponReloadListener;
import otto.djgun.djcraft.network.packet.SyncItemBehaviorPayload;
import otto.djgun.djcraft.network.packet.SyncItemTimingPayload;
import otto.djgun.djcraft.network.packet.SyncRayWeaponProfilesPayload;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(DJCraft.MODID)
public class DJCraft {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "djcraft";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under
    // the "djcraft" namespace

    // The constructor for the mod class is the first code that is run when your mod
    // is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and
    // pass them in automatically.
    public DJCraft(IEventBus modEventBus, ModContainer modContainer) {
        otto.djgun.djcraft.init.ModGameRules.bootstrap();
        // Register the commonSetup method for modloading

        // Register the Deferred Register to the mod event bus so blocks get registered

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (DJCraft) to
        // respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in
        // this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // 注册服务端 DJ 战斗事件处理器（伤害管道 hook）
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.combat.DJCombatHandler.class);
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.effect.RendEffectEvents.class);
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.cybergrind.CyberGrindEvents.class);

        // 初始化 TrackPackManager 并加载曲目包 (尽早初始化以便资源包能读取到曲目)
        TrackPackManager.getInstance().initialize(
                net.neoforged.fml.loading.FMLPaths.GAMEDIR.get(),
                modContainer.getModInfo().getOwningFile().getFile()
                        .findResource("djcraft", "trackpacks"));

        // Register our mod's ModConfigSpec so that FML can create and load the config
        // file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 注册资源包查找器
        modEventBus.addListener(this::addPackFinders);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModAttributes::modifyEntityAttributes);

        // 注册配置重载事件
        modEventBus.addListener(DJCraft::onModConfigEvent);

        // 注册物品和数据组件
        otto.djgun.djcraft.init.ModBlocks.BLOCKS.register(modEventBus);
        otto.djgun.djcraft.init.ModItems.ITEMS.register(modEventBus);
        otto.djgun.djcraft.init.ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        otto.djgun.djcraft.init.ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        otto.djgun.djcraft.init.ModEntities.ENTITY_TYPES.register(modEventBus);
        ModAttributes.ATTRIBUTES.register(modEventBus);
        otto.djgun.djcraft.init.ModSounds.SOUNDS.register(modEventBus);
        otto.djgun.djcraft.init.ModEffects.EFFECTS.register(modEventBus);
    }

    public static void onModConfigEvent(final net.neoforged.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            TrackPackManager.getInstance().loadAllPacks();
        }
    }

    @SubscribeEvent
    public void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(DJItemTimingReloadListener.getInstance());
        event.addListener(DJItemBehaviorReloadListener.getInstance());
        event.addListener(DJRayWeaponReloadListener.getInstance());
        event.addListener(otto.djgun.djcraft.cybergrind.CyberGrindProfileManager.getInstance());
    }

    @SubscribeEvent
    public void syncDatapackProfiles(OnDatapackSyncEvent event) {
        SyncItemTimingPayload payload = new SyncItemTimingPayload(DJItemCooldownManager.getProfilesSnapshot());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
        SyncItemBehaviorPayload behaviorPayload =
                new SyncItemBehaviorPayload(DJItemBehaviorManager.getOverridesSnapshot());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, behaviorPayload));
        SyncRayWeaponProfilesPayload rayPayload =
                new SyncRayWeaponProfilesPayload(DJRayWeaponManager.getProfilesSnapshot());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, rayPayload));
        event.getRelevantPlayers().forEach(player ->
                otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance().syncPresets(player));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NeoForge.EVENT_BUS.post(new otto.djgun.djcraft.api.combat.RegisterDJItemBehaviorsEvent());
            otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry.freeze();
            NeoForge.EVENT_BUS.post(new otto.djgun.djcraft.sound.RegisterDJWeaponSoundResolversEvent());
            otto.djgun.djcraft.sound.DJWeaponSoundIdentityRegistry.freeze();
        });
    }

    private void addPackFinders(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        if (event.getPackType() == net.minecraft.server.packs.PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(new otto.djgun.djcraft.sound.TrackPackRepositorySource());
        }
    }

    // Add the example block item to the building blocks tab

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // 关服或退出单人世界时，必须清空服务端的静态单例缓存
    @SubscribeEvent
    public void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance().clear(event.getServer());
        DJModeManager.getInstance().stopAllSessions();
        otto.djgun.djcraft.session.DJNetworkGroupManager.getInstance().clear(event.getServer());
        otto.djgun.djcraft.combat.DJCombatHandler.clear();
        otto.djgun.djcraft.combat.DJDeferredDamageManager.clear();
        otto.djgun.djcraft.combat.DJMeleeAttackWindowManager.clear();
        otto.djgun.djcraft.combat.FloweryDashController.clear();
        otto.djgun.djcraft.combat.DJDashMomentumController.clear();
        otto.djgun.djcraft.combat.DJFallDamageImmunity.clear();
        otto.djgun.djcraft.combat.DJGroundSlamController.clear();
        otto.djgun.djcraft.combat.DJAutoChargeManager.clear();
        otto.djgun.djcraft.network.server.TrackPackTransferService.shutdown();
        otto.djgun.djcraft.network.server.DJWeaponSoundRequestHandler.clear();
        otto.djgun.djcraft.network.server.DJUtilityActionRequestHandler.clear();
        LOGGER.info("Server stopped, cleaned up all DJ sessions in memory");
    }

    // 注册指令
    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        DJCommands.register(event.getDispatcher());
    }

    // 每tick更新DJ会话
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        DJModeManager.getInstance().tick();
        otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance().tick(event.getServer());
        otto.djgun.djcraft.combat.DJDeferredDamageManager.tick(event.getServer());
        otto.djgun.djcraft.combat.DJMeleeAttackWindowManager.tick(event.getServer());
        otto.djgun.djcraft.session.DJNetworkGroupManager.getInstance().tick(event.getServer());
        if (event.getServer().overworld().getGameTime() % 20L == 0L) {
            otto.djgun.djcraft.session.DiscStatisticsService.reconcile(event.getServer());
        }
        otto.djgun.djcraft.combat.DJCombatHandler.cleanupExpired(event.getServer().overworld().getGameTime());
        otto.djgun.djcraft.combat.FloweryDashController.tick(event.getServer());
        otto.djgun.djcraft.combat.DJGroundSlamController.tick(event.getServer());
        otto.djgun.djcraft.combat.DJAutoChargeManager.tick(event.getServer());
        otto.djgun.djcraft.network.server.TrackPackTransferService.cleanupExpired();
        otto.djgun.djcraft.network.server.AdminPlayRequestService.cleanupExpired();
        otto.djgun.djcraft.network.server.DJUtilityActionRequestHandler.tick(event.getServer());
    }

    // 玩家加入服务器时，下发服务端的曲目包哈希表
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SyncTrackHashesPayload payload = new SyncTrackHashesPayload(
                    TrackPackManager.getInstance().getContentHashes());
            PacketDistributor.sendToPlayer(serverPlayer, payload);
            LOGGER.info("Sent track hash list to player {}: {} packs",
                    serverPlayer.getName().getString(),
                    TrackPackManager.getInstance().getContentHashes().size());
            otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance().onLogin(serverPlayer);
        }
    }

    // 玩家退出服务器时，清理服务端遗留的 DJ 会话
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance().onLogout(serverPlayer);
            otto.djgun.djcraft.session.DJNetworkGroupManager.getInstance().removePlayer(serverPlayer);
            DJModeManager.getInstance().stopSession(serverPlayer);
            otto.djgun.djcraft.combat.DJCombatHandler.cleanupPlayer(serverPlayer.getUUID());
            otto.djgun.djcraft.combat.DJDeferredDamageManager.cleanupPlayer(serverPlayer.getUUID());
            otto.djgun.djcraft.combat.DJMeleeAttackWindowManager.cleanupPlayer(serverPlayer.getUUID());
            otto.djgun.djcraft.combat.FloweryDashController.cleanupPlayer(serverPlayer.getUUID());
            otto.djgun.djcraft.combat.DJDashMomentumController.cancel(serverPlayer.getUUID());
            otto.djgun.djcraft.combat.DJFallDamageImmunity.clear(serverPlayer.getUUID());
            otto.djgun.djcraft.combat.DJGroundSlamController.cleanupPlayer(serverPlayer);
            otto.djgun.djcraft.combat.DJAutoChargeManager.cleanupPlayer(serverPlayer.getUUID());
            otto.djgun.djcraft.network.server.TrackPackTransferService.cleanupPlayer(serverPlayer.getUUID());
            otto.djgun.djcraft.network.server.AdminPlayRequestService.cleanupPlayer(serverPlayer.getUUID());
            otto.djgun.djcraft.network.server.ClientTrackStatusService.cleanup(serverPlayer.getUUID());
            otto.djgun.djcraft.network.server.DJWeaponSoundRequestHandler.cleanupPlayer(serverPlayer.getUUID());
            otto.djgun.djcraft.network.server.DJUtilityActionRequestHandler.cleanupPlayer(serverPlayer.getUUID());
            LOGGER.info("Cleaned up DJ session for disconnected player {}", serverPlayer.getName().getString());
        }
    }

    // 玩家死亡时立即终止服务端会话，并同步停止客户端播放
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            boolean cyberGrindDeath = otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance()
                    .handleLethalDamage(serverPlayer);
            otto.djgun.djcraft.combat.DJDeferredDamageManager.cleanupPlayer(serverPlayer.getUUID());
            otto.djgun.djcraft.combat.DJMeleeAttackWindowManager.cleanupPlayer(serverPlayer.getUUID());
            otto.djgun.djcraft.combat.FloweryDashController.cleanupPlayer(serverPlayer.getUUID());
            otto.djgun.djcraft.combat.DJDashMomentumController.cancel(serverPlayer.getUUID());
            otto.djgun.djcraft.combat.DJFallDamageImmunity.clear(serverPlayer.getUUID());
            otto.djgun.djcraft.combat.DJGroundSlamController.cleanupPlayer(serverPlayer);
            otto.djgun.djcraft.combat.DJAutoChargeManager.cleanupPlayer(serverPlayer.getUUID());
            if (cyberGrindDeath) {
                event.setCanceled(true);
                return;
            }
            DJModeManager.getInstance().stopSession(serverPlayer, StopReason.PLAYER_DIED);
        }
    }

    @SubscribeEvent
    public void onLivingFall(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer
                && otto.djgun.djcraft.combat.DJFallDamageImmunity.consume(serverPlayer.getUUID())) {
            event.setCanceled(true);
            serverPlayer.resetFallDistance();
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            otto.djgun.djcraft.session.DJNetworkGroupManager.getInstance().onRespawn(serverPlayer);
        }
    }
}
