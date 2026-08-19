package otto.djgun.djcraft.combat.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DJDeferredDamageIndicatorStateTest {
    @AfterEach
    void resetState() {
        DJDeferredDamageIndicatorState.reset();
    }

    @Test
    void remainsActiveUntilServerClearsIt() {
        DJDeferredDamageIndicatorState.update(7L, true);

        assertTrue(DJDeferredDamageIndicatorState.hasPending(7L));
        DJDeferredDamageIndicatorState.update(7L, false);
        assertFalse(DJDeferredDamageIndicatorState.hasPending(7L));
    }

    @Test
    void sessionReplacementDiscardsOldIndicator() {
        DJDeferredDamageIndicatorState.update(7L, true);
        DJDeferredDamageIndicatorState.update(8L, true);

        assertFalse(DJDeferredDamageIndicatorState.hasPending(7L));
        assertTrue(DJDeferredDamageIndicatorState.hasPending(8L));
    }
}
