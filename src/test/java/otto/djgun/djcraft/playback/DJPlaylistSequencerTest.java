package otto.djgun.djcraft.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import otto.djgun.djcraft.data.DiscPlaybackReference;

class DJPlaylistSequencerTest {
    @Test
    void defaultRandomImplementationCanSequencePlayback() {
        DJPlaylistSequencer sequencer = new DJPlaylistSequencer(refs("a"));

        assertEquals("a", sequencer.next().trackId());
    }

    @Test
    void sequentialAndRepeatModesMatchPlayerBehavior() {
        DJPlaylistSequencer sequencer = new DJPlaylistSequencer(refs("a", "b", "c"), new Random(1L));
        assertEquals("c", sequencer.select(2).trackId());
        assertEquals("a", sequencer.next().trackId());
        sequencer.setMode(DJPlaybackMode.REPEAT_ONE);
        assertEquals("a", sequencer.next().trackId());
    }

    @Test
    void shufflePreservesDuplicateSlotsButAvoidsSameTrackWhenPossible() {
        DJPlaylistSequencer sequencer = new DJPlaylistSequencer(refs("a", "a", "b"), new Random(2L));
        sequencer.setMode(DJPlaybackMode.SHUFFLE);
        sequencer.select(0);
        assertNotEquals("a", sequencer.next().trackId());
    }

    @Test
    void shuffleBagVisitsEverySlotBeforeRefilling() {
        DJPlaylistSequencer sequencer = new DJPlaylistSequencer(refs("a", "b", "c"), new Random(4L));
        sequencer.setMode(DJPlaybackMode.SHUFFLE);
        sequencer.select(0);
        var firstCycle = new java.util.HashSet<Integer>();
        firstCycle.add(sequencer.currentIndex());
        firstCycle.add(indexOf(sequencer.entries(), sequencer.next()));
        firstCycle.add(indexOf(sequencer.entries(), sequencer.next()));
        assertEquals(3, firstCycle.size());
        assertTrue(firstCycle.containsAll(List.of(0, 1, 2)));
    }

    private static int indexOf(List<DiscPlaybackReference> entries, DiscPlaybackReference selected) {
        return entries.indexOf(selected);
    }

    private static List<DiscPlaybackReference> refs(String... ids) {
        List<DiscPlaybackReference> refs = new ArrayList<>();
        for (int index = 0; index < ids.length; index++) {
            refs.add(new DiscPlaybackReference(ids[index], null, 0, index));
        }
        return refs;
    }
}
