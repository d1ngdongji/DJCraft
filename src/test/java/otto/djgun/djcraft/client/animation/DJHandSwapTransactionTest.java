package otto.djgun.djcraft.client.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DJHandSwapTransactionTest {
    @Test
    void recognizesOnlyACompletePairExchange() {
        assertTrue(DJHandSwapTransaction.isSwap("main", "off", "off", "main", String::equals));
        assertFalse(DJHandSwapTransaction.isSwap("main", "off", "main", "off", String::equals));
        assertFalse(DJHandSwapTransaction.isSwap("main", "off", "third", "main", String::equals));
    }

    @Test
    void pairDetectionIncludesPerStackState() {
        StackKey damaged = new StackKey("weapon", 3);
        StackKey pristine = new StackKey("weapon", 0);
        StackKey shield = new StackKey("shield", 0);

        assertTrue(DJHandSwapTransaction.isSwap(
                damaged, shield, shield, damaged, StackKey::equals));
        assertFalse(DJHandSwapTransaction.isSwap(
                damaged, shield, shield, pristine, StackKey::equals));
    }

    @Test
    void holdsBothCacheWritesUntilTheSharedBarrier() {
        DJHandSwapTransaction<String> transaction = new DJHandSwapTransaction<>(7L,
                "main", "off", "off", "main", 12.5);

        assertTrue(transaction.shouldHold(
                DJHandSwapTransaction.Side.MAIN, "main", "off", String::equals));
        assertTrue(transaction.shouldHold(
                DJHandSwapTransaction.Side.OFF, "off", "main", String::equals));
        assertFalse(transaction.isReady(7L, 12.49));
        assertTrue(transaction.isReady(7L, 12.5));
        assertFalse(transaction.isReady(8L, 12.5));
    }

    @Test
    void rapidReverseMatchesTheSourcePairInsteadOfTheStaleTarget() {
        DJHandSwapTransaction<String> transaction = new DJHandSwapTransaction<>(3L,
                "main", "off", "off", "main", 4.0);

        assertTrue(transaction.isSourcePair("main", "off", String::equals));
        assertFalse(transaction.isTargetPair("main", "off", String::equals));
        assertTrue(transaction.isTargetPair("off", "main", String::equals));
    }

    private record StackKey(String item, int damage) {
    }
}
