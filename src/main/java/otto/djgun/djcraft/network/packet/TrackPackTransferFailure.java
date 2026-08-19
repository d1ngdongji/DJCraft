package otto.djgun.djcraft.network.packet;

import java.util.Locale;

public enum TrackPackTransferFailure {
    NOT_FOUND,
    TOO_LARGE,
    BUSY,
    INVALID,
    IO_ERROR,
    TIMEOUT,
    HASH_MISMATCH,
    CANCELED,
    RELOAD_FAILED;

    public String translationKey() {
        return "message.djcraft.download_failure." + name().toLowerCase(Locale.ROOT);
    }
}
