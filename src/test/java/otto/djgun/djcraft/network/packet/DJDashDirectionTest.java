package otto.djgun.djcraft.network.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DJDashDirectionTest {
    @Test
    void resolvesEightWasdDirections() {
        assertEquals(DJDashDirection.FORWARD, DJDashDirection.fromInput(true, false, false, false));
        assertEquals(DJDashDirection.BACKWARD, DJDashDirection.fromInput(false, true, false, false));
        assertEquals(DJDashDirection.LEFT, DJDashDirection.fromInput(false, false, true, false));
        assertEquals(DJDashDirection.RIGHT, DJDashDirection.fromInput(false, false, false, true));
        assertEquals(DJDashDirection.FORWARD_LEFT, DJDashDirection.fromInput(true, false, true, false));
        assertEquals(DJDashDirection.FORWARD_RIGHT, DJDashDirection.fromInput(true, false, false, true));
        assertEquals(DJDashDirection.BACK_LEFT, DJDashDirection.fromInput(false, true, true, false));
        assertEquals(DJDashDirection.BACK_RIGHT, DJDashDirection.fromInput(false, true, false, true));
    }

    @Test
    void opposingOrMissingKeysResolveToNone() {
        assertEquals(DJDashDirection.NONE, DJDashDirection.fromInput(false, false, false, false));
        assertEquals(DJDashDirection.NONE, DJDashDirection.fromInput(true, true, false, false));
        assertEquals(DJDashDirection.NONE, DJDashDirection.fromInput(false, false, true, true));
        assertEquals(DJDashDirection.NONE, DJDashDirection.fromInput(true, true, true, true));
    }
}
