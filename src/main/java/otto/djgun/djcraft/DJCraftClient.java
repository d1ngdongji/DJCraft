package otto.djgun.djcraft;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import otto.djgun.djcraft.client.DJDebugHud;
import otto.djgun.djcraft.client.config.DJClientConfig;
import otto.djgun.djcraft.client.texture.DJBeatTextureLibrary;
import otto.djgun.djcraft.client.animation.DJAnimationLibrary;
import otto.djgun.djcraft.client.animation.DJAnimationSemantic;
import otto.djgun.djcraft.client.animation.RegisterDJAnimationSemanticsEvent;
import otto.djgun.djcraft.client.render.DJRayEffectLibrary;
import otto.djgun.djcraft.client.render.DJRayEffectRenderer;
import otto.djgun.djcraft.client.sound.DJWeaponSoundLibrary;
import otto.djgun.djcraft.client.ui.DJModernUiHandler;
import otto.djgun.djcraft.hud.DJCrosshairRenderer;
import otto.djgun.djcraft.hud.DJBeatHudRenderer;
import otto.djgun.djcraft.hud.DJComboHudRenderer;
import otto.djgun.djcraft.hud.DJComboTextureLibrary;
import otto.djgun.djcraft.hud.DJEnergyHudRenderer;
import otto.djgun.djcraft.hud.DJFallingBeatRenderer;
import otto.djgun.djcraft.hud.DJToleranceHudRenderer;
import otto.djgun.djcraft.util.DJClientUiBridge;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = DJCraft.MODID, dist = Dist.CLIENT)
public class DJCraftClient {

    public DJCraftClient(IEventBus modEventBus, ModContainer container) {
        DJClientUiBridge.install(DJModernUiHandler.INSTANCE);
        container.registerConfig(ModConfig.Type.CLIENT, DJClientConfig.SPEC);
        modEventBus.addListener(otto.djgun.djcraft.client.render.DJEntityRenderers::register);

        // 注册配置界面
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // 注册客户端事件（ClientTickEvent、RenderGuiLayerEvent 等，挂到游戏事件总线）
        NeoForge.EVENT_BUS.register(ClientEvents.class);

        // 注册客户端攻击监听器与原始输入快照（实体攻击由单一 DJ payload 接管）
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.combat.client.ClientCombatHandler.class);
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.combat.client.DJClientActionCapture.class);
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.combat.client.DJGroupCombatSuppressionClient.class);

        // 注册客户端弩 DJ 集成处理器（节拍判定控制射击 + 装弹冷却）
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.combat.client.ClientTriggerWeaponHandler.class);

        // 注册客户端弓 DJ 集成处理器（按下右键开始拉弓的节拍判定）
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.combat.client.ClientBowHandler.class);

        // 注册客户端三叉戟 DJ 集成处理器（按下右键立即判定并投掷）
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.combat.client.ClientTridentHandler.class);

        // 注册客户端重锤 DJ 集成处理器（按下右键立即判定并投掷副本）
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.combat.client.ClientMaceHandler.class);

        // 注册客户端盾牌 DJ 集成处理器
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.combat.client.ClientShieldHandler.class);
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.combat.client.DJUtilityActionClientHandler.class);
    }

    /**
     * 客户端事件处理
     */
    public static class ClientEvents {

        @SubscribeEvent
        public static void onRenderGuiPre(RenderGuiLayerEvent.Pre event) {
            if (event.getName().equals(VanillaGuiLayers.BOSS_OVERLAY)) {
                DJToleranceHudRenderer.beginBossOverlay();
            }
            if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
                if (otto.djgun.djcraft.session.DJModeManagerClient.getInstance().isInDJMode()) {
                    // 隐藏原版准星
                    event.setCanceled(true);

                    // 渲染 DJ 模式定制准星
                    DJCrosshairRenderer.renderCenterCrosshair(event.getGuiGraphics());

                    // 渲染判定线 HUD
                    DJBeatHudRenderer.render(event.getGuiGraphics());

                    // Beat 检测
                    if (Minecraft.getInstance().player != null) {
                        otto.djgun.djcraft.session.DJModeManagerClient.getInstance().renderTick();
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            otto.djgun.djcraft.hud.FloweryDashHudRenderer.render(event.getGuiGraphics());
            otto.djgun.djcraft.hud.CyberGrindHudRenderer.render(event.getGuiGraphics());
        }

        @SubscribeEvent
        public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
            otto.djgun.djcraft.client.render.DJNormalDashVisualState.capture(event);
            otto.djgun.djcraft.client.render.FloweryDashVisualState.capture(event);
        }

        @SubscribeEvent
        public static void onRenderLevelStage(RenderLevelStageEvent event) {
            otto.djgun.djcraft.client.render.DJPlayerAfterimageRenderer.render(event);
            DJRayEffectRenderer.render(event);
            otto.djgun.djcraft.client.render.CyberGrindSpawnRingRenderer.render(event);
        }

        @SubscribeEvent
        public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
            // 如果不在 DJ 模式中，CROSSHAIR 本不会被 cancel，我们需要继续检查并执行 renderTick
            if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
                if (!otto.djgun.djcraft.session.DJModeManagerClient.getInstance().isInDJMode()) {
                    if (Minecraft.getInstance().player != null) {
                        otto.djgun.djcraft.session.DJModeManagerClient.getInstance().renderTick();
                    }
                }
            }

            // Debug HUD 在血量层渲染
            if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) {
                DJComboHudRenderer.render(event.getGuiGraphics());
                DJDebugHud.render(event.getGuiGraphics());
            }

            if (event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) {
                DJEnergyHudRenderer.render(event.getGuiGraphics());
            }

            if (event.getName().equals(VanillaGuiLayers.BOSS_OVERLAY)) {
                DJToleranceHudRenderer.render(event.getGuiGraphics());
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void onCustomizeBossEvent(CustomizeGuiOverlayEvent.BossEventProgress event) {
            DJToleranceHudRenderer.observeBossEvent(event);
        }

        @SubscribeEvent
        public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // gameTick 仅负责低频的 OpenAL source 存活性检测和会话结束拦截
                // beat 检测已移到 renderTick()（每帧执行）
                otto.djgun.djcraft.session.DJModeManagerClient.getInstance().gameTick();
                otto.djgun.djcraft.combat.client.DJMovementAbilityClientHandler.tick(mc);
                otto.djgun.djcraft.combat.client.DJAutoChargeClientState.tick(mc);

                // 检查快捷键
                while (otto.djgun.djcraft.client.KeyBindings.OPEN_JUKEBOX.consumeClick()) {
                    net.minecraft.world.entity.player.Inventory inv = mc.player.getInventory();
                    boolean opened = false;
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        net.minecraft.world.item.ItemStack stack = inv.getItem(i);
                        if (stack.is(otto.djgun.djcraft.init.ModItems.PORTABLE_JUKEBOX.get())) {
                            otto.djgun.djcraft.client.ui.DJPlayerUIHelper.openPlayerUI(stack, i);
                            opened = true;
                            break;
                        }
                    }
                    if (!opened && otto.djgun.djcraft.client.CyberGrindClientState.getInstance().isActive()) {
                        otto.djgun.djcraft.client.ui.DJPlayerUIHelper.openPlayerUI(null, -1);
                    }
                }
            }
        }

        // 当玩家断开连接或退出世界时，强行终止任何残留的 DJ 会话和 HUD
        @SubscribeEvent
        public static void onClientLogout(
                net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            otto.djgun.djcraft.session.DJModeManagerClient.getInstance().reset();
            otto.djgun.djcraft.client.ClientTrackRegistry.getInstance().clear();
            otto.djgun.djcraft.client.CyberGrindClientState.getInstance().reset();
            otto.djgun.djcraft.client.ClientTrackPackTransferService.cleanup();
            otto.djgun.djcraft.client.playback.DJPlaybackController.getInstance().resetPlayback();
            otto.djgun.djcraft.client.DJNetworkGroupClient.getInstance().reset();
            otto.djgun.djcraft.combat.client.DJMovementAbilityClientHandler.reset();
            otto.djgun.djcraft.client.render.FloweryDashVisualState.reset();
            otto.djgun.djcraft.client.render.DJNormalDashVisualState.reset();
            DJRayEffectRenderer.clear();
            otto.djgun.djcraft.combat.client.DJAutoChargeClientState.reset();
            DJBeatHudRenderer.reset();
        }

        // 玩家重新载入/登入新的世界时，确保重置过去的任何幽灵缓存
        @SubscribeEvent
        public static void onClientLogin(
                net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
            otto.djgun.djcraft.session.DJModeManagerClient.getInstance().reset();
            otto.djgun.djcraft.client.ClientTrackPackTransferService.cleanup();
            otto.djgun.djcraft.client.playback.DJPlaybackController.getInstance().resetPlayback();
            otto.djgun.djcraft.client.DJNetworkGroupClient.getInstance().reset();
            otto.djgun.djcraft.combat.client.DJMovementAbilityClientHandler.reset();
            otto.djgun.djcraft.client.render.FloweryDashVisualState.reset();
            otto.djgun.djcraft.client.render.DJNormalDashVisualState.reset();
            DJRayEffectRenderer.clear();
            otto.djgun.djcraft.combat.client.DJAutoChargeClientState.reset();
            DJBeatHudRenderer.reset();
        }

        // 拦截并禁用音乐与唱片机/音符盒声音
        @SubscribeEvent
        public static void onPlaySound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
            if (otto.djgun.djcraft.session.DJModeManagerClient.getInstance().isInDJMode()) {
                net.minecraft.client.resources.sounds.SoundInstance sound = event.getSound();
                if (sound != null && !(sound instanceof otto.djgun.djcraft.sound.DJSoundInstance)) {
                    if (sound.getSource() == net.minecraft.sounds.SoundSource.MUSIC ||
                            sound.getSource() == net.minecraft.sounds.SoundSource.RECORDS) {
                        event.setSound(null);
                    }
                }
            }
        }
    }

    /**
     * Mod总线事件
     */
    @EventBusSubscriber(modid = DJCraft.MODID, value = Dist.CLIENT)
    public static class ModEvents {

        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {
            DJCraft.LOGGER.info("DJCraft Client Setup");
            event.enqueueWork(() -> {
                NeoForge.EVENT_BUS.post(new RegisterDJAnimationSemanticsEvent());
                DJAnimationSemantic.freeze();
                net.minecraft.client.renderer.item.ItemProperties.register(
                        otto.djgun.djcraft.init.ModItems.EMPTY_DISC.get(),
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "pack_index"),
                        (stack, level, entity, seed) -> {
                            String packId = stack.get(otto.djgun.djcraft.init.ModDataComponents.TRACK_PACK_ID.get());
                            var manager = otto.djgun.djcraft.loader.TrackPackManager.getInstance();
                            return manager.getDiscModelIndex(packId);
                        });
                net.minecraft.client.renderer.item.ItemProperties.register(
                        otto.djgun.djcraft.init.ModItems.EMPTY_DISC.get(),
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "gilded"),
                        (stack, level, entity, seed) -> {
                            String packId = stack.get(otto.djgun.djcraft.init.ModDataComponents.TRACK_PACK_ID.get());
                            var statistics = stack.getOrDefault(
                                    otto.djgun.djcraft.init.ModDataComponents.DISC_STATISTICS.get(),
                                    otto.djgun.djcraft.data.DiscStatistics.EMPTY);
                            int beats = otto.djgun.djcraft.loader.TrackPackManager.getInstance()
                                    .getCombatBeatCount(packId);
                            return statistics.isGilded(beats) ? 1.0f : 0.0f;
                        });
                net.minecraft.client.renderer.item.ItemProperties.register(
                        otto.djgun.djcraft.init.ModItems.LASER_CROSSBOW.get(),
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("charged"),
                        (stack, level, entity, seed) ->
                                net.minecraft.world.item.CrossbowItem.isCharged(stack) ? 1.0f : 0.0f);
                net.minecraft.client.renderer.item.ItemProperties.register(
                        otto.djgun.djcraft.init.ModItems.MAGIC_CROSSBOW.get(),
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("charged"),
                        (stack, level, entity, seed) ->
                                net.minecraft.world.item.CrossbowItem.isCharged(stack) ? 1.0f : 0.0f);
                net.minecraft.client.renderer.item.ItemProperties.register(
                        otto.djgun.djcraft.init.ModItems.ASSAULT_CROSSBOW.get(),
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("charged"),
                        (stack, level, entity, seed) ->
                                net.minecraft.world.item.CrossbowItem.isCharged(stack) ? 1.0f : 0.0f);
                net.minecraft.client.renderer.item.ItemProperties.register(
                        otto.djgun.djcraft.init.ModItems.EXPLOSIVE_BOW.get(),
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"),
                        (stack, level, entity, seed) -> entity == null || entity.getUseItem() != stack ? 0.0F
                                : (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F);
                net.minecraft.client.renderer.item.ItemProperties.register(
                        otto.djgun.djcraft.init.ModItems.EXPLOSIVE_BOW.get(),
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"),
                        (stack, level, entity, seed) -> entity != null && entity.isUsingItem()
                                && entity.getUseItem() == stack ? 1.0F : 0.0F);
                net.minecraft.client.renderer.item.ItemProperties.register(
                        otto.djgun.djcraft.init.ModItems.EXPLOSIVE_BOW.get(),
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "auto_charge"),
                        (stack, level, entity, seed) ->
                                otto.djgun.djcraft.combat.client.DJAutoChargeClientState.progress(stack));
            });
        }

        @SubscribeEvent
        static void onRegisterKeyMappings(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
            event.register(otto.djgun.djcraft.client.KeyBindings.OPEN_JUKEBOX);
            event.register(otto.djgun.djcraft.client.KeyBindings.DASH);
        }

        @SubscribeEvent
        static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(DJAnimationLibrary.getInstance());
            event.registerReloadListener(DJWeaponSoundLibrary.getInstance());
            event.registerReloadListener(DJComboTextureLibrary.getInstance());
            event.registerReloadListener(DJBeatTextureLibrary.getInstance());
            event.registerReloadListener(DJRayEffectLibrary.getInstance());
        }

        @SubscribeEvent
        static void onRegisterShaders(RegisterShadersEvent event) {
            DJComboHudRenderer.registerShaders(event);
            DJFallingBeatRenderer.registerShaders(event);
            otto.djgun.djcraft.client.render.DJParryShieldRenderer.registerShaders(event);
            DJRayEffectRenderer.registerShaders(event);
        }
    }
}
