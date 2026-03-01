package otto.djgun.djcraft;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import otto.djgun.djcraft.client.DJDebugHud;
import otto.djgun.djcraft.hud.DJCrosshairRenderer;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = DJCraft.MODID, dist = Dist.CLIENT)
public class DJCraftClient {

    public DJCraftClient(ModContainer container) {
        // 注册配置界面
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // 注册客户端事件（ClientTickEvent、RenderGuiLayerEvent 等，挂到游戏事件总线）
        NeoForge.EVENT_BUS.register(ClientEvents.class);

        // 注册客户端攻击监听器（仅用于准星即时反馈，不取消原版攻击）
        NeoForge.EVENT_BUS.register(otto.djgun.djcraft.combat.client.ClientCombatHandler.class);
    }

    /**
     * 客户端事件处理
     */
    public static class ClientEvents {

        @SubscribeEvent
        public static void onRenderGuiPre(RenderGuiLayerEvent.Pre event) {
            if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
                if (otto.djgun.djcraft.session.DJModeManagerClient.getInstance().isInDJMode()) {
                    // 隐藏原版准星
                    event.setCanceled(true);

                    // 渲染 DJ 模式定制准星
                    DJCrosshairRenderer.renderCenterCrosshair(event.getGuiGraphics());

                    // 渲染判定线 HUD
                    DJCrosshairRenderer.render(event.getGuiGraphics());

                    // Beat 检测
                    if (Minecraft.getInstance().player != null) {
                        otto.djgun.djcraft.session.DJModeManagerClient.getInstance().renderTick();
                    }
                }
            }
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
                DJDebugHud.render(event.getGuiGraphics());
            }
        }

        @SubscribeEvent
        public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // gameTick 仅负责低频的 OpenAL source 存活性检测和会话结束拦截
                // beat 检测已移到 renderTick()（每帧执行）
                otto.djgun.djcraft.session.DJModeManagerClient.getInstance().gameTick();
            }
        }

        // 当玩家断开连接或退出世界时，强行终止任何残留的 DJ 会话和 HUD
        @SubscribeEvent
        public static void onClientLogout(
                net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            otto.djgun.djcraft.session.DJModeManagerClient.getInstance().stopSession();
        }

        // 玩家重新载入/登入新的世界时，确保重置过去的任何幽灵缓存
        @SubscribeEvent
        public static void onClientLogin(
                net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
            otto.djgun.djcraft.session.DJModeManagerClient.getInstance().stopSession();
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
    @EventBusSubscriber(modid = DJCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {

        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {
            DJCraft.LOGGER.info("DJCraft Client Setup");
        }
    }
}
