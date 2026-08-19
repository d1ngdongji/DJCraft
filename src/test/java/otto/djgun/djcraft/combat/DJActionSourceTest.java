package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

class DJActionSourceTest {
    @Test
    void roundTripsSlotAndRegistryId() {
        DJActionSource source = new DJActionSource(7, 1234);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        DJActionSource.CODEC.encode(buffer, source);

        assertEquals(source, DJActionSource.CODEC.decode(buffer));
    }

    @Test
    void restrictsSourcesToTheClaimedHand() {
        assertTrue(new DJActionSource(0, 1).isValidFor(InteractionHand.MAIN_HAND));
        assertTrue(new DJActionSource(8, 1).isValidFor(InteractionHand.MAIN_HAND));
        assertFalse(new DJActionSource(9, 1).isValidFor(InteractionHand.MAIN_HAND));
        assertTrue(new DJActionSource(DJActionSource.OFFHAND_SLOT, 1)
                .isValidFor(InteractionHand.OFF_HAND));
        assertFalse(new DJActionSource(0, 1).isValidFor(InteractionHand.OFF_HAND));
    }

    @Test
    void acceptsOnlyThePreviousSelectionDuringTheSwitchGraceWindow() {
        long[] expiries = new long[9];

        DJActionSourceHistory.recordSelection(expiries, 2, 5, 100L);

        assertTrue(DJActionSourceHistory.isRecent(expiries, 2, 100L));
        assertTrue(DJActionSourceHistory.isRecent(expiries, 2, 104L));
        assertFalse(DJActionSourceHistory.isRecent(expiries, 2, 105L));
        assertFalse(DJActionSourceHistory.isRecent(expiries, 5, 100L));
        assertFalse(DJActionSourceHistory.isRecent(expiries, 8, 0L));
    }
}
