package otto.djgun.djcraft.client.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.client.ClientTrackRegistry;
import otto.djgun.djcraft.loader.TrackPackManager;

final class DJTrackPackUiActions {
    private DJTrackPackUiActions() {
    }

    static void openTrackPackDirectory() {
        Minecraft minecraft = Minecraft.getInstance();
        Path directory = minecraft.gameDirectory.toPath().resolve("djcraft").resolve("trackpacks")
                .toAbsolutePath().normalize();
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(directory);
                Util.getPlatform().openFile(directory.toFile());
            } catch (IOException | SecurityException exception) {
                DJCraft.LOGGER.error("Failed to open TrackPacks directory {}", directory, exception);
                showMessage(minecraft, Component.translatable("message.djcraft.trackpacks.open_failed"));
            }
        });
    }

    static void reloadTrackPacks(Runnable onReloaded) {
        Minecraft minecraft = Minecraft.getInstance();
        TrackPackManager manager = TrackPackManager.getInstance();
        manager.reloadAllPacks();
        int count = manager.getLoadedPackCount();

        minecraft.reloadResourcePacks().whenComplete((unused, error) -> minecraft.execute(() -> {
            if (error != null) {
                DJCraft.LOGGER.error("Failed to refresh resources after reloading TrackPacks", error);
                showMessage(minecraft, Component.translatable("message.djcraft.trackpacks.reload_failed"));
                return;
            }
            ClientTrackRegistry registry = ClientTrackRegistry.getInstance();
            if (minecraft.hasSingleplayerServer()) {
                registry.onReceiveServerHashes(manager.getContentHashes());
            } else {
                registry.revalidate();
            }
            showMessage(minecraft, Component.translatable("message.djcraft.trackpacks.reloaded", count));
            onReloaded.run();
        }));
    }

    private static void showMessage(Minecraft minecraft, Component message) {
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(message, false);
            }
        });
    }
}
