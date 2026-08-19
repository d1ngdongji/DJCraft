package otto.djgun.djcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJSession;

import java.util.Collection;
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
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("trackpack", StringArgumentType.word())
                                        .suggests(TRACKPACK_SUGGESTIONS)
                                        .executes(DJCommands::executePlaySelf))
                                .then(Commands
                                        .argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                                        .then(Commands.argument("trackpack", StringArgumentType.word())
                                                .suggests(TRACKPACK_SUGGESTIONS)
                                                .executes(DJCommands::executePlayMultiple))))
                        .then(Commands.literal("stop")
                                .requires(source -> source.hasPermission(2))
                                .executes(DJCommands::executeStopSelf)
                                .then(Commands
                                        .argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                                        .executes(DJCommands::executeStopMultiple)))
                        .then(Commands.literal("list")
                                .executes(DJCommands::executeList))
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("combo")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(DJCommands::executeSetCombo))))
                                .then(Commands.literal("energy")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                                                        .executes(DJCommands::executeSetEnergy)))))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2)) // 需要OP权限
                                .executes(DJCommands::executeReload))
                        .then(DJNetworkCommands.literal()));

        DJCraft.LOGGER.info("Registered DJ commands");
    }

    /**
     * 执行 /dj play <trackpack> 指令
     * 给自己播放
     */
    private static int executePlaySelf(CommandContext<CommandSourceStack> context) {
        String packId = StringArgumentType.getString(context, "trackpack").toLowerCase(Locale.ROOT);
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cThis command can only be executed by a player"));
            return 0;
        }

        return playForPlayers(source, java.util.Collections.singletonList(player), packId);
    }

    /**
     * 执行 /dj play <targets> <trackpack> 指令
     * 给选择的玩家播放
     */
    private static int executePlayMultiple(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String packId = StringArgumentType.getString(context, "trackpack").toLowerCase(Locale.ROOT);
        CommandSourceStack source = context.getSource();
        java.util.Collection<ServerPlayer> targets = net.minecraft.commands.arguments.EntityArgument.getPlayers(context,
                "targets");

        return playForPlayers(source, targets, packId);
    }

    private static int playForPlayers(CommandSourceStack source, java.util.Collection<ServerPlayer> players,
            String packId) {
        Optional<TrackPack> packOptional = TrackPackManager.getInstance().getTrackPack(packId);

        if (packOptional.isEmpty()) {
            if (players.size() == 1 && players.iterator().next() == source.getEntity()) {
                source.sendFailure(Component.literal("§cTrackPack not found: " + packId));
                source.sendFailure(Component.literal("§7Use /dj list to see available packs"));
            } else {
                source.sendFailure(Component.literal("§cTrackPack not found: " + packId));
            }
            return 0;
        }

        final TrackPack pack = packOptional.get();

        if (TrackPackManager.getInstance().getContentHash(packId).isEmpty()) {
            source.sendFailure(Component.literal("§cInternal error: TrackPack hash not found for: " + packId));
            return 0;
        }

        int successCount = 0;
        for (ServerPlayer player : players) {
            if (otto.djgun.djcraft.network.server.AdminPlayRequestService
                    .request(source, player, packId)) {
                successCount++;
            }
        }

        if (successCount > 0) {
            final int count = successCount;
            if (count == 1 && players.iterator().next() == source.getEntity()) {
                source.sendSuccess(() -> Component.translatable(
                        "message.djcraft.admin_play_accepted", packId), true);
                source.sendSuccess(() -> Component.literal("§7  BPM: §f" + pack.getBpm() +
                        " §7| Beats: §f" + pack.getCombatBeatCount()), false);
            } else {
                source.sendSuccess(
                        () -> Component.translatable(
                                "message.djcraft.admin_play_accepted_many", packId, count),
                        true);
            }

            DJCraft.LOGGER.info("Started DJ session for {} players: {} (BPM: {}, Beats: {})",
                    successCount, packId, pack.getBpm(), pack.getCombatBeatCount());
        }

        return successCount;
    }

    /**
     * 执行 /dj stop 指令
     * 停止自己的播放
     */
    private static int executeStopSelf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cThis command can only be executed by a player"));
            return 0;
        }

        return stopForPlayers(source, java.util.Collections.singletonList(player));
    }

    /**
     * 执行 /dj stop <targets> 指令
     * 停止选择玩家的播放
     */
    private static int executeStopMultiple(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        java.util.Collection<ServerPlayer> targets = net.minecraft.commands.arguments.EntityArgument.getPlayers(context,
                "targets");

        return stopForPlayers(source, targets);
    }

    private static int stopForPlayers(CommandSourceStack source, java.util.Collection<ServerPlayer> players) {
        int successCount = 0;
        for (ServerPlayer player : players) {
            boolean removedFromGroup = otto.djgun.djcraft.session.DJNetworkGroupManager.getInstance()
                    .isParticipant(player.getUUID());
            if (removedFromGroup) {
                otto.djgun.djcraft.session.DJNetworkGroupManager.getInstance().leave(player, false);
            }
            if (DJModeManager.getInstance().isInDJMode(player)) {
                DJModeManager.getInstance().stopSession(player);
                successCount++;
            } else if (removedFromGroup) {
                successCount++;
            }
        }

        if (successCount > 0) {
            final int count = successCount;
            if (count == 1 && players.iterator().next() == source.getEntity()) {
                source.sendSuccess(() -> Component.literal("§c♪ Stopped DJ Mode"), true);
            } else {
                source.sendSuccess(() -> Component.literal("§c♪ Stopped DJ Mode for " + count + " player(s)"), true);
            }
        } else {
            source.sendFailure(Component.literal("§7No players were in DJ Mode"));
        }

        return successCount;
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

    private static int executeSetCombo(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int value = IntegerArgumentType.getInteger(context, "value");
        int changed = updateActiveSessions(source, targets, session -> session.setCombo(value));
        if (changed > 0) {
            final int count = changed;
            source.sendSuccess(() -> Component.literal(
                    "§aSet combo to §e" + value + "§a for " + count + " active DJ session(s)"), true);
        }
        return changed;
    }

    private static int executeSetEnergy(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        double value = DoubleArgumentType.getDouble(context, "value");
        if (!Double.isFinite(value)) {
            source.sendFailure(Component.literal("§cEnergy must be a finite number"));
            return 0;
        }
        int changed = updateActiveSessions(source, targets, session -> session.setEnergy(value));
        if (changed > 0) {
            final int count = changed;
            source.sendSuccess(() -> Component.literal(
                    "§aSet energy to §e" + value + "§a for " + count
                            + " active DJ session(s); values above each player's maximum were clamped"),
                    true);
        }
        return changed;
    }

    private static int updateActiveSessions(CommandSourceStack source, Collection<ServerPlayer> targets,
            java.util.function.Consumer<DJSession> update) {
        int changed = 0;
        DJModeManager manager = DJModeManager.getInstance();
        for (ServerPlayer target : targets) {
            Optional<DJSession> session = manager.getSession(target).filter(DJSession::isPlaying);
            if (session.isPresent()) {
                update.accept(session.get());
                changed++;
            }
        }
        if (changed == 0) {
            source.sendFailure(Component.literal("§7None of the selected players have an active DJ session"));
        }
        return changed;
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
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
                new otto.djgun.djcraft.network.packet.SyncTrackHashesPayload(
                        TrackPackManager.getInstance().getContentHashes()));

        source.sendSuccess(() -> Component.literal("§aReloaded " + count + " TrackPack(s)"), true);

        return 1;
    }
}
