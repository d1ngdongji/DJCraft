package otto.djgun.djcraft.cybergrind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import otto.djgun.djcraft.data.DiscPlaybackReference;

class CyberGrindPlaylistRulesTest {
    @Test
    void resolvesPhysicalDiscSlotAfterInvalidEntriesWereFiltered() {
        var playlist = List.of(
                new DiscPlaybackReference("first", null, 4, 2),
                new DiscPlaybackReference("selected", null, 4, 19));

        assertEquals(1, CyberGrindPlaylistRules.indexForDiscSlot(playlist, 19));
        assertEquals(-1, CyberGrindPlaylistRules.indexForDiscSlot(playlist, 7));
    }
}
