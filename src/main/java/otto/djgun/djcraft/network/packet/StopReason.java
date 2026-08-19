package otto.djgun.djcraft.network.packet;

public enum StopReason {
    REQUESTED,
    AUDIO_ENDED,
    CLOCK_DESYNC,
    SERVER_STOP,
    PLAYER_DIED,
    AUDIO_UNAVAILABLE
}
