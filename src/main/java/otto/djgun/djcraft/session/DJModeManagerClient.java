package otto.djgun.djcraft.session;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.ClientStopSessionPayload;
import otto.djgun.djcraft.sound.DJSoundManager;
import otto.djgun.djcraft.sound.OpenALHelper;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 客户端 DJ 模式管理器
 * 管理客户端的 DJ 会话，使用 OpenAL 高精度时间
 */
@OnlyIn(Dist.CLIENT)
public class DJModeManagerClient {

    private static DJModeManagerClient INSTANCE;

    @Nullable
    private DJSessionClient currentSession;

    @Nullable
    private String currentTrackPackId;

    private DJModeManagerClient() {
    }

    /**
     * 获取单例实例
     */
    public static DJModeManagerClient getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DJModeManagerClient();
        }
        return INSTANCE;
    }

    /**
     * 开始播放曲目（由网络包触发）
     */
    public void startSession(String trackPackId) {
        // 停止当前会话
        stopSession();

        // 获取曲目包
        Optional<TrackPack> packOpt = TrackPackManager.getInstance().getTrackPack(trackPackId);
        if (packOpt.isEmpty()) {
            DJCraft.LOGGER.error("TrackPack not found for client session: {}", trackPackId);
            return;
        }

        TrackPack pack = packOpt.get();

        // 播放音乐（这会触发 Mixin 捕获 OpenAL source ID）
        DJSoundManager.getInstance().playTrack(pack);

        // 创建客户端会话
        DJSessionClient session = new DJSessionClient(pack);
        currentSession = session;
        currentTrackPackId = trackPackId;

        // 立即启动会话（OpenAL source 应该已经被 Mixin 捕获）
        session.start();

        DJCraft.LOGGER.info("Client DJ session started for: {} (OpenAL source: {})",
                trackPackId, OpenALHelper.getCurrentDJSourceId());
    }

    /**
     * 停止当前会话
     */
    public void stopSession() {
        if (currentSession != null) {
            currentSession.stop();
            currentSession = null;
        }
        currentTrackPackId = null;

        // 停止音乐
        DJSoundManager.getInstance().stopTrack();

        // 清理 OpenAL 状态
        OpenALHelper.cleanup();
    }

    /**
     * 游戏 Tick 更新（20 TPS）
     * 仅负责 OpenAL source 存活性检测和会话结束拦截。
     * beat 检测已移到 renderTick()（每渲染帧执行）以提高精度。
     */
    public void gameTick() {
        DJSessionClient session = currentSession;
        if (session == null)
            return;

        if (!session.isPlaying()) {
            return;
        }

        // 同步暂停状态
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        session.setPaused(mc.isPaused());

        // OpenAL Stopped 检测：
        // 静态缓冲由于预载已经可以秒级提供 source。如果不再处于等待状态且丢失了有效 ID，或者 ID 有效但被明确报告为 STOPPED，
        // 则视为外部强制停止或自然终止（因为 Minecraft 的 SoundEngine 播放完毕会自动释放 Channel）。
        boolean lostSource = !OpenALHelper.hasValidDJSource() && !OpenALHelper.isWaitingForDJSource();
        boolean sourceStopped = OpenALHelper.hasValidDJSource() && OpenALHelper.isDJSourceStopped();

        if (lostSource || sourceStopped) {
            DJCraft.LOGGER.info("OpenAL source stopped externally, auto-closing DJ session.");
            PacketDistributor.sendToServer(new ClientStopSessionPayload());
            stopSession();
            return;
        }
    }

    /**
     * 渲染帧 Tick 更新（每渲染帧，~60fps）
     * 负责 beat 检测和触发，利用高频率检查降低最大 beat 延迟。
     */
    public void renderTick() {
        DJSessionClient session = currentSession;
        if (session == null)
            return;

        if (!session.isPlaying()) {
            return;
        }

        // 暂停时不进行 beat 检测
        if (session.isPaused()) {
            return;
        }

        // 更新 beat 检测
        session.tick();
    }

    /**
     * @deprecated 请使用 gameTick() + renderTick()
     */
    @Deprecated
    public void tick() {
        gameTick();
        renderTick();
    }

    /**
     * 获取当前会话
     */
    public Optional<DJSessionClient> getSession() {
        return Optional.ofNullable(currentSession);
    }

    /**
     * 是否在 DJ 模式中
     */
    public boolean isInDJMode() {
        return currentSession != null && currentSession.isPlaying();
    }

    /**
     * 获取当前曲目包ID
     */
    @Nullable
    public String getCurrentTrackPackId() {
        return currentTrackPackId;
    }
}
