package otto.djgun.djcraft.command;

import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import otto.djgun.djcraft.playback.DJPlaybackMode;
import otto.djgun.djcraft.session.DJNetworkGroupManager;

final class DJNetworkCommands {
    private DJNetworkCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> literal() {
        return Commands.literal("network")
                .then(Commands.literal("create")
                        .executes(context -> create(context.getSource(), -1))
                        .then(Commands.argument("jukeboxSlot", IntegerArgumentType.integer(0))
                                .executes(context -> create(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "jukeboxSlot")))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> DJNetworkGroupManager.getInstance().invite(
                                        context.getSource().getPlayerOrException(),
                                        EntityArgument.getPlayer(context, "player")) ? 1 : 0)))
                .then(Commands.literal("accept")
                        .then(Commands.argument("owner", EntityArgument.player())
                                .executes(context -> respond(context.getSource(),
                                        EntityArgument.getPlayer(context, "owner"), true))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("owner", EntityArgument.player())
                                .executes(context -> respond(context.getSource(),
                                        EntityArgument.getPlayer(context, "owner"), false))))
                .then(Commands.literal("retry").executes(context -> {
                    DJNetworkGroupManager.getInstance().retry(context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("mode")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        new String[] { "sequential", "repeat_one", "shuffle" }, builder))
                                .executes(context -> mode(context.getSource(),
                                        StringArgumentType.getString(context, "mode")))))
                .then(Commands.literal("play")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(context -> DJNetworkGroupManager.getInstance().play(
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "index") - 1) ? 1 : 0)))
                .then(Commands.literal("stop").executes(context -> {
                    DJNetworkGroupManager.getInstance().stopPlayback(context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("leave").executes(context -> leave(context.getSource(), false)))
                .then(Commands.literal("disband").executes(context -> leave(context.getSource(), true)))
                .then(Commands.literal("status").executes(context -> status(context.getSource())));
    }

    private static int create(CommandSourceStack source, int slot)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return DJNetworkGroupManager.getInstance().create(source.getPlayerOrException(), slot) ? 1 : 0;
    }

    private static int respond(CommandSourceStack source, ServerPlayer owner, boolean accept)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var view = DJNetworkGroupManager.getInstance().getView(owner.getUUID()).orElse(null);
        return view != null && DJNetworkGroupManager.getInstance().respond(
                source.getPlayerOrException(), view.id(), accept) ? 1 : 0;
    }

    private static int mode(CommandSourceStack source, String value)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        DJPlaybackMode mode;
        try {
            mode = DJPlaybackMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable("message.djcraft.group.invalid_mode"));
            return 0;
        }
        return DJNetworkGroupManager.getInstance().setMode(source.getPlayerOrException(), mode) ? 1 : 0;
    }

    private static int leave(CommandSourceStack source, boolean disband)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        DJNetworkGroupManager.getInstance().leave(source.getPlayerOrException(), disband);
        return 1;
    }

    private static int status(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var view = DJNetworkGroupManager.getInstance().getView(player.getUUID()).orElse(null);
        if (view == null) {
            source.sendFailure(Component.translatable("message.djcraft.group.not_member"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.djcraft.group.status",
                view.ownerName(), view.members().size(), view.pending().size(),
                view.mode().name().toLowerCase(Locale.ROOT),
                view.currentTrack().isBlank() ? "-" : view.currentTrack()), false);
        return 1;
    }
}
