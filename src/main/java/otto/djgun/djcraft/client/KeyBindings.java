package otto.djgun.djcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String KEY_CATEGORY_DJCRAFT = "key.category.djcraft.general";
    public static final String KEY_OPEN_JUKEBOX = "key.djcraft.open_jukebox";
    public static final String KEY_DASH = "key.djcraft.dash";

    public static final KeyMapping OPEN_JUKEBOX = new KeyMapping(
            KEY_OPEN_JUKEBOX,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            KEY_CATEGORY_DJCRAFT);

    public static final KeyMapping DASH = new KeyMapping(
            KEY_DASH,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KEY_CATEGORY_DJCRAFT);
}
