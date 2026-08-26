package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJMeleeWindowStateTest {
    @Test
    void lingeringSweepAddsThreeTicksPerLevelToMaceWindow() {
        assertEquals(DJMeleeAttackWindowManager.WINDOW_TICKS * 2L,
                DJMeleeAttackWindowManager.windowTicks(DJItemBehavior.MACE, 0));
        assertEquals(7L, DJMeleeAttackWindowManager.windowTicks(DJItemBehavior.MACE, 1));
        assertEquals(10L, DJMeleeAttackWindowManager.windowTicks(DJItemBehavior.MACE, 2));
        assertEquals(13L, DJMeleeAttackWindowManager.windowTicks(DJItemBehavior.MACE, 3));
        assertEquals(4L, DJMeleeAttackWindowManager.windowTicks(DJItemBehavior.MACE, -1));
        assertEquals(DJMeleeAttackWindowManager.WINDOW_TICKS,
                DJMeleeAttackWindowManager.windowTicks(DJItemBehavior.TRIDENT, 2));
    }

    @Test
    void ordinaryWindowClosesOnItsFirstContact() {
        DJMeleeWindowState state = new DJMeleeWindowState(false, 12L);

        assertTrue(state.acceptContact(10));
        assertTrue(state.shouldFinish(10L));
        assertFalse(state.acceptContact(11));
    }

    @Test
    void areaWindowDeduplicatesButRemainsOpenUntilSecondTick() {
        DJMeleeWindowState state = new DJMeleeWindowState(true, 12L);

        assertTrue(state.acceptContact(10));
        assertFalse(state.acceptContact(10));
        assertTrue(state.acceptContact(11));
        assertFalse(state.shouldFinish(11L));
        assertTrue(state.shouldFinish(12L));
    }
}
