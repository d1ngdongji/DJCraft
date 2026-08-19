package otto.djgun.djcraft.client.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import otto.djgun.djcraft.data.DiscPlaybackReference;
import otto.djgun.djcraft.playback.DJPlaybackMode;

import org.junit.jupiter.api.Test;

class DJPlaylistStateTest {

    @Test
    void defaultRandomImplementationCanPlanPlayback() {
        DJPlaylistState state = new DJPlaylistState();

        assertEquals(0, state.configureAndPlan(refs("a"), 0, 0L).index());
    }

    @Test
    void sequentialPlaybackWrapsToFirstSlot() {
        DJPlaylistState state = new DJPlaylistState(new Random(1L));
        var start = state.configureAndPlan(refs("a", "b", "c"), 2, 0L);
        assertEquals(2, start.index());
        assertTrue(state.confirmStarted(10L, "c", null));

        var next = state.naturalEnd(10L, 100L).orElseThrow();

        assertEquals(0, next.index());
        assertEquals("a", next.trackId());
    }

    @Test
    void repeatOneKeepsCurrentSlot() {
        DJPlaylistState state = new DJPlaylistState(new Random(2L));
        state.setMode(DJPlaybackMode.REPEAT_ONE);
        state.configureAndPlan(refs("a", "b"), 1, 0L);
        state.confirmStarted(20L, "b", null);

        assertEquals(1, state.naturalEnd(20L, 100L).orElseThrow().index());
    }

    @Test
    void shuffleBagCoversEveryRemainingSlotAndAvoidsSameTrackWhenPossible() {
        DJPlaylistState state = new DJPlaylistState(new Random(3L));
        state.setMode(DJPlaybackMode.SHUFFLE);
        List<DiscPlaybackReference> playlist = refs("a", "a", "b", "c");
        state.configureAndPlan(playlist, 0, 0L);
        state.confirmStarted(30L, "a", null);

        Set<Integer> visited = new HashSet<>();
        String previous = "a";
        for (int step = 0; step < 3; step++) {
            var next = state.naturalEnd(30L + step, 100L + step).orElseThrow();
            assertFalse(previous.equals(next.trackId()));
            visited.add(next.index());
            state.confirmStarted(31L + step, next.trackId(), null);
            previous = next.trackId();
        }

        assertEquals(Set.of(1, 2, 3), visited);
    }

    @Test
    void modeChangeAppliesAtNextNaturalEnd() {
        DJPlaylistState state = new DJPlaylistState(new Random(4L));
        state.configureAndPlan(refs("a", "b"), 0, 0L);
        state.confirmStarted(40L, "a", null);
        state.setMode(DJPlaybackMode.REPEAT_ONE);

        assertEquals(0, state.naturalEnd(40L, 100L).orElseThrow().index());
    }

    @Test
    void duplicateNaturalEndAndOldStopCannotCreateOrClearAnotherRequest() {
        DJPlaylistState state = new DJPlaylistState(new Random(5L));
        state.configureAndPlan(refs("a", "b"), 0, 0L);
        state.confirmStarted(50L, "a", null);

        assertTrue(state.naturalEnd(50L, 100L).isPresent());
        assertTrue(state.naturalEnd(50L, 101L).isEmpty());
        state.stop(50L);
        assertTrue(state.hasPending());
        assertTrue(state.confirmStarted(51L, "b", null));
    }

    @Test
    void pendingRequestExpiresAndExternalStartClearsPlaylist() {
        DJPlaylistState state = new DJPlaylistState(new Random(6L));
        state.configureAndPlan(refs("a", "b"), 0, 1_000L);
        assertFalse(state.expirePending(5_999L, 5_000L));
        assertTrue(state.expirePending(6_000L, 5_000L));
        assertFalse(state.hasPending());

        state.configureAndPlan(refs("a", "b"), 0, 7_000L);
        assertFalse(state.confirmStarted(60L, "external", null));
        assertTrue(state.entries().isEmpty());
        assertTrue(state.naturalEnd(60L, 8_000L).isEmpty());
    }

    @Test
    void duplicateTracksKeepIndependentDiscReferences() {
        DJPlaylistState state = new DJPlaylistState(new Random(7L));
        var first = new DiscPlaybackReference("same", java.util.UUID.randomUUID(), 0, 1);
        var second = new DiscPlaybackReference("same", java.util.UUID.randomUUID(), 0, 2);

        var selection = state.configureAndPlan(List.of(first, second), 1, 0L);

        assertEquals(second, selection.disc());
        assertTrue(state.confirmStarted(70L, "same", second.discId()));
        assertEquals(second.discId(), state.entries().get(1).discId());
    }

    private static List<DiscPlaybackReference> refs(String... trackIds) {
        java.util.ArrayList<DiscPlaybackReference> result = new java.util.ArrayList<>();
        for (int index = 0; index < trackIds.length; index++) {
            result.add(new DiscPlaybackReference(trackIds[index], null, 0, index));
        }
        return result;
    }
}
