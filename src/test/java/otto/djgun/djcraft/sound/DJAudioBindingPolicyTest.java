package otto.djgun.djcraft.sound;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJAudioBindingPolicyTest {
    @Test
    void clearsOnlyTheExactBoundChannelAndSource() {
        Object boundChannel = new Object();

        assertTrue(DJAudioBindingPolicy.shouldClearBinding(
                boundChannel, boundChannel, 7, 7));
        assertFalse(DJAudioBindingPolicy.shouldClearBinding(
                boundChannel, new Object(), 7, 7));
        assertFalse(DJAudioBindingPolicy.shouldClearBinding(
                boundChannel, boundChannel, 7, 8));
    }

    @Test
    void reusedSourceIdFromAnOldChannelCannotClearTheNewBinding() {
        Object oldChannel = new Object();
        Object newChannel = new Object();

        assertFalse(DJAudioBindingPolicy.shouldClearBinding(
                newChannel, oldChannel, 11, 11));
    }
}
