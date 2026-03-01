package otto.djgun.djcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.session.DJModeManager;

import java.util.Optional;
import java.util.Locale;

/**
 * DJ 模组指令
 * 提供 /dj 系列指令
 */
public class DJCommands {

    /**
     * 曲目包ID建议提供器
     */
    private static final SuggestionProvider<CommandSourceStack> TRACKPACK_SUGGESTIONS = (context,
            builder) -> SharedSuggestionProvider.suggest(
                    TrackPackManager.getInstance().getLoadedPackIds(),
                    builder);

    /**
     * 注册所有 DJ 指令
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("dj")
                        .then(Commands.literal("play")
                                .then(Commands.argument("trackpack", StringArgumentType.word())
                                        .suggests(TRACKPACK_SUGGESTIONS)
                                        .executes(DJCommands::executePlay)))
                        .then(Commands.literal("stop")
                                .executes(DJCommands::executeStop))
                        .then(Commands.literal("list")
                                .executes(DJCommands::executeList))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2)) // 需要OP权限
                                .executes(DJCommands::executeReload)));

        DJCraft.LOGGER.info("Registered DJ commands");
    }

    /**
     * 执行 /dj play <trackpack> 指令
     * 开始播放曲目并进入DJ模式
     */
    private static int executePlay(CommandContext<CommandSourceStack> context) {
        String packId = StringArgumentType.getString(context, "trackpack").toLowerCase(Locale.ROOT);
        CommandSourceStack source = context.getSource();

        // 检查是否是玩家执行
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cThis command can only be executed by a player"));
            return 0;
        }

        Optional<TrackPack> packOptional = TrackPackManager.getInstance().getTrackPack(packId);

        if (packOptional.isEmpty()) {
            source.sendFailure(Component.literal("§cTrackPack not found: " + packId));
            source.sendFailure(Component.literal("§7Use /dj list to see available packs"));
            return 0;
        }

        TrackPack pack = packOptional.get();

        // 确认服务端有该曲目包的哈希（正常情况下一定有，因为是从 TrackPackManager 取的）
        // 客户端在收到 SyncTrackHashesPayload 时已经做了本地哈希比对；
        // 如果客户端没有该曲目包或者 JSON 不一致，DJModeManagerClient.startSession 会因找不到 pack 而中止。
        if (TrackPackManager.getInstance().getPackHash(packId).isEmpty()) {
            source.sendFailure(Component.literal("§cInternal error: TrackPack hash not found for: " + packId));
            return 0;
        }

        // 开始DJ会话 (服务器端)
        DJModeManager.getInstance().startSession(player, pack);

        // 发送数据包通知客户端开始播放
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new otto.djgun.djcraft.network.packet.PlayTrackPayload(packId));

        // 发送玩家消息
        source.sendSuccess(() -> Component.literal("§a♪ Playing TrackPack: §e" + packId), true);
        source.sendSuccess(() -> Component.literal("§7  BPM: §f" + pack.getBpm() +
                " §7| Beats: §f" + pack.getCombatBeatCount()), false);

        // 在控制台打印详细节拍信息
        DJCraft.LOGGER.info("Started DJ session: {} (BPM: {}, Beats: {})",
                packId, pack.getBpm(), pack.getCombatBeatCount());

        return 1;
    }

    /**
     * 执行 /dj stop 指令
     * 停止当前播放
     */
    private static int executeStop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cThis command can only be executed by a player"));
            return 0;
        }

        if (DJModeManager.getInstance().isInDJMode(player)) {
            DJModeManager.getInstance().stopSession(player);

            source.sendSuccess(() -> Component.literal("§c♪ Stopped DJ Mode"), true);
        } else {
            source.sendFailure(Component.literal("§7Not in DJ Mode"));
        }

        return 1;
    }

    /**
     * 执行 /dj list 指令
     * 列出所有已加载的曲目包
     */
    private static int executeList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TrackPackManager manager = TrackPackManager.getInstance();

        int count = manager.getLoadedPackCount();
        source.sendSuccess(() -> Component.literal("§6♪ Loaded TrackPacks: §f" + count), false);

        if (count == 0) {
            source.sendSuccess(() -> Component.literal("§7  No trackpacks found."), false);
            source.sendSuccess(() -> Component.literal("§7  Add packs to: djcraft/trackpacks/"), false);
        } else {
            for (TrackPack pack : manager.getLoadedPacks()) {
                source.sendSuccess(() -> Component.literal(
                        "§7  - §e" + pack.id() + " §7(BPM: " + pack.getBpm() +
                                ", Beats: " + pack.getCombatBeatCount() + ")"),
                        false);
            }
        }

        return 1;
    }

    /**
     * 执行 /dj reload 指令
     * 重新加载所有曲目包
     */
    private static int executeReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal("§7Reloading TrackPacks..."), true);

        TrackPackManager.getInstance().reloadAllPacks();
        int count = TrackPackManager.getInstance().getLoadedPackCount();

        // 发送数据包通知所有客户端重载资源 (以便新曲目立即生效)
        net.neoforged.neoforge.network.PacketDistributor
                .sendToAllPlayers(new otto.djgun.djcraft.network.packet.ReloadTracksPayload());

        source.sendSuccess(() -> Component.literal("§aReloaded " + count + " TrackPack(s)"), true);

        return 1;
    }
}
