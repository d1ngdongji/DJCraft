package otto.djgun.djcraft.util;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Common-side bridge for UI requests. The client entrypoint installs the implementation,
 * keeping client and ModernUI classes out of common item and block class references.
 */
public final class DJClientUiBridge {
    private static final Handler NO_OP = new Handler() {
        @Override
        public void openPlayer(ItemStack jukeboxStack, InteractionHand hand) {
        }

        @Override
        public void openCrafting(InteractionHand hand, BlockPos tablePos) {
        }
    };

    private static volatile Handler handler = NO_OP;

    private DJClientUiBridge() {
    }

    public static void install(Handler clientHandler) {
        handler = Objects.requireNonNull(clientHandler, "clientHandler");
    }

    public static void openPlayer(ItemStack jukeboxStack, InteractionHand hand) {
        handler.openPlayer(jukeboxStack, hand);
    }

    public static void openCrafting(InteractionHand hand, BlockPos tablePos) {
        handler.openCrafting(hand, tablePos);
    }

    public interface Handler {
        void openPlayer(ItemStack jukeboxStack, InteractionHand hand);

        void openCrafting(InteractionHand hand, BlockPos tablePos);
    }
}
