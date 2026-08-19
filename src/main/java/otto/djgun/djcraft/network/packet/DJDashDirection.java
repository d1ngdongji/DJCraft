package otto.djgun.djcraft.network.packet;

public enum DJDashDirection {
    NONE(0, 0),
    FORWARD(0, 1),
    FORWARD_LEFT(1, 1),
    LEFT(1, 0),
    BACK_LEFT(1, -1),
    BACKWARD(0, -1),
    BACK_RIGHT(-1, -1),
    RIGHT(-1, 0),
    FORWARD_RIGHT(-1, 1);

    private final int strafe;
    private final int forward;

    DJDashDirection(int strafe, int forward) {
        this.strafe = strafe;
        this.forward = forward;
    }

    public int strafe() {
        return strafe;
    }

    public int forward() {
        return forward;
    }

    public static DJDashDirection fromInput(boolean up, boolean down, boolean left, boolean right) {
        int forward = (up ? 1 : 0) - (down ? 1 : 0);
        int strafe = (left ? 1 : 0) - (right ? 1 : 0);
        for (DJDashDirection direction : values()) {
            if (direction.forward == forward && direction.strafe == strafe) {
                return direction;
            }
        }
        return NONE;
    }
}
