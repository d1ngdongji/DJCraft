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
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
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
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
import otto.djgun.djcraft.session.DJModeManager;

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

        // 初始化 TrackPackManager 并加载曲目包 (尽早初始化以便资源包能读取到曲目)
        TrackPackManager.getInstance().initialize(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get());

        // Register our mod's ModConfigSpec so that FML can create and load the config
        // file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 注册资源包查找器
        modEventBus.addListener(this::addPackFinders);

        // 注册配置重载事件
        modEventBus.addListener(DJCraft::onModConfigEvent);
    }

    public static void onModConfigEvent(final net.neoforged.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            otto.djgun.djcraft.combat.DJItemCooldownManager.reloadConfig();
        }
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
        DJModeManager.getInstance().stopAllSessions();
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
    }

    // 玩家加入服务器时，下发服务端的曲目包哈希表
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            SyncTrackHashesPayload payload = new SyncTrackHashesPayload(TrackPackManager.getInstance().getPackHashes());
            PacketDistributor.sendToPlayer(serverPlayer, payload);
            LOGGER.info("Sent track hash list to player {}: {} packs",
                    serverPlayer.getName().getString(),
                    TrackPackManager.getInstance().getPackHashes().size());
        }
    }

    // 玩家退出服务器时，清理服务端遗留的 DJ 会话
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            DJModeManager.getInstance().stopSession(serverPlayer);
            LOGGER.info("Cleaned up DJ session for disconnected player {}", serverPlayer.getName().getString());
        }
    }
}
