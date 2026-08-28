package otto.djgun.djcraft.client.animation;

import java.util.function.BiPredicate;

/** Coordinates the two renderer caches that make up one offhand-swap operation. */
final class DJHandSwapTransaction<T> {
    enum Side {
        MAIN,
        OFF
    }

    private final long generation;
    private final T sourceMain;
    private final T sourceOff;
    private final T targetMain;
    private final T targetOff;
    private final double handoffBeat;

    DJHandSwapTransaction(long generation, T sourceMain, T sourceOff, T targetMain, T targetOff,
            double handoffBeat) {
        this.generation = generation;
        this.sourceMain = sourceMain;
        this.sourceOff = sourceOff;
        this.targetMain = targetMain;
        this.targetOff = targetOff;
        this.handoffBeat = handoffBeat;
    }

    static <T> boolean isSwap(T sourceMain, T sourceOff, T targetMain, T targetOff,
            BiPredicate<T, T> matches) {
        boolean changed = !matches.test(sourceMain, targetMain) || !matches.test(sourceOff, targetOff);
        return changed && matches.test(sourceMain, targetOff) && matches.test(sourceOff, targetMain);
    }

    boolean isSourcePair(T main, T off, BiPredicate<T, T> matches) {
        return matches.test(sourceMain, main) && matches.test(sourceOff, off);
    }

    boolean isTargetPair(T main, T off, BiPredicate<T, T> matches) {
        return matches.test(targetMain, main) && matches.test(targetOff, off);
    }

    boolean shouldHold(Side side, T cached, T next, BiPredicate<T, T> matches) {
        T source = side == Side.MAIN ? sourceMain : sourceOff;
        T target = side == Side.MAIN ? targetMain : targetOff;
        return matches.test(source, cached) && matches.test(target, next);
    }

    boolean isReady(long currentGeneration, double virtualBeat) {
        return generation == currentGeneration && virtualBeat >= handoffBeat;
    }

    long generation() {
        return generation;
    }
}
