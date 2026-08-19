package otto.djgun.djcraft.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.client.ClientTrackRegistry;
import otto.djgun.djcraft.network.packet.ClientRequestDownloadPayload;

import java.util.Locale;

@EventBusSubscriber(modid = DJCraft.MODID, value = Dist.CLIENT)
public class DJClientCommands {

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("djclient")
                        .then(Commands.literal("download")
                                .then(Commands.argument("trackpack", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                ClientTrackRegistry.getInstance().getServerPackIds(), builder))
                                        .executes(context -> {
                                            String packId = StringArgumentType.getString(context, "trackpack")
                                                    .toLowerCase(Locale.ROOT);
                                            CommandSourceStack source = context.getSource();

                                            // 1. 检查是否存在于服务端的列表中
                                            if (!ClientTrackRegistry.getInstance().getServerPackIds()
                                                    .contains(packId)) {
                                                source.sendFailure(Component
                                                        .literal("§cTrackPack not found on server: " + packId));
                                                return 0;
                                            }

                                            // 2. 检查是否已经下载且通过验证
                                            if (ClientTrackRegistry.getInstance().isVerified(packId)) {
                                                source.sendFailure(Component
                                                        .literal("§eYou already have this TrackPack: " + packId));
                                                return 0;
                                            }

                                            // 3. 发送下载请求
                                            source.sendSuccess(
                                                    () -> Component.literal(
                                                            "§7Requesting download for TrackPack: " + packId + "..."),
                                                    false);
                                            PacketDistributor.sendToServer(new ClientRequestDownloadPayload(packId));

                                            return 1;
                                        }))));

        DJCraft.LOGGER.info("Registered DJ client commands");
    }
}
