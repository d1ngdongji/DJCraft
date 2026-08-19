package otto.djgun.djcraft.client.ui;

import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.MuiModApi;
import net.minecraft.client.Minecraft;

public final class ClientScreenBridge {
    private ClientScreenBridge() {
    }

    public static void closeScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(null));
    }

    /**
     * ModernUI click callbacks run on its UI thread, while opening a Minecraft
     * screen is restricted to the Minecraft main thread.
     */
    public static void openScreen(Fragment fragment) {
        Minecraft.getInstance().execute(() -> MuiModApi.get().openScreen(fragment));
    }
}
