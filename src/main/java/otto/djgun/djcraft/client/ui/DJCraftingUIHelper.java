package otto.djgun.djcraft.client.ui;

import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DJCraftingUIHelper {
    public static void openCraftingUI(InteractionHand hand, BlockPos tablePos) {
        ClientScreenBridge.openScreen(new DJCraftingFragment(hand, tablePos));
    }
}
