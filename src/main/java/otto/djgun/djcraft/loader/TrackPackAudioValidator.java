package otto.djgun.djcraft.loader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Streaming structural preflight for the Ogg Vorbis input expected by
 * Minecraft. It validates page continuity, all three Vorbis headers, at least
 * one audio packet and a clean end-of-stream page without buffering the file.
 */
final class TrackPackAudioValidator {
    private static final int OGG_HEADER_BYTES = 27;

    private TrackPackAudioValidator() {
    }

    static boolean isPlayableOggVorbis(InputStream input) throws IOException {
        int expectedSequence = 0;
        Integer serial = null;
        int packetIndex = 0;
        byte[] packetPrefix = new byte[16];
        int packetPrefixLength = 0;
        int packetLength = 0;
        boolean sawAudioPacket = false;
        boolean sawEnd = false;

        while (!sawEnd) {
            byte[] header = input.readNBytes(OGG_HEADER_BYTES);
            if (header.length == 0) {
                return false;
            }
            if (header.length != OGG_HEADER_BYTES
                    || header[0] != 'O' || header[1] != 'g'
                    || header[2] != 'g' || header[3] != 'S'
                    || header[4] != 0) {
                return false;
            }
            int headerType = Byte.toUnsignedInt(header[5]);
            if (expectedSequence == 0 && (headerType & 0x02) == 0) {
                return false;
            }
            if (((headerType & 0x01) != 0) != (packetLength > 0)) {
                return false;
            }
            int pageSerial = littleEndianInt(header, 14);
            int pageSequence = littleEndianInt(header, 18);
            if ((serial != null && serial != pageSerial) || pageSequence != expectedSequence++) {
                return false;
            }
            serial = pageSerial;

            int segmentCount = Byte.toUnsignedInt(header[26]);
            byte[] laces = input.readNBytes(segmentCount);
            if (laces.length != segmentCount) {
                return false;
            }
            for (byte laceByte : laces) {
                int lace = Byte.toUnsignedInt(laceByte);
                byte[] data = input.readNBytes(lace);
                if (data.length != lace) {
                    return false;
                }
                int copy = Math.min(data.length, packetPrefix.length - packetPrefixLength);
                if (copy > 0) {
                    System.arraycopy(data, 0, packetPrefix, packetPrefixLength, copy);
                    packetPrefixLength += copy;
                }
                packetLength += lace;
                if (lace < 255) {
                    if (!validatePacket(packetIndex, packetPrefix, packetPrefixLength, packetLength)) {
                        return false;
                    }
                    if (packetIndex >= 3) {
                        sawAudioPacket = true;
                    }
                    packetIndex++;
                    Arrays.fill(packetPrefix, (byte) 0);
                    packetPrefixLength = 0;
                    packetLength = 0;
                }
            }
            sawEnd = (headerType & 0x04) != 0;
        }
        return packetLength == 0 && packetIndex >= 4 && sawAudioPacket
                && input.read() == -1;
    }

    private static boolean validatePacket(int packetIndex, byte[] prefix,
            int prefixLength, int packetLength) {
        if (packetIndex >= 3) {
            return packetLength > 0;
        }
        int expectedType = switch (packetIndex) {
            case 0 -> 1;
            case 1 -> 3;
            case 2 -> 5;
            default -> -1;
        };
        if (prefixLength < 7 || Byte.toUnsignedInt(prefix[0]) != expectedType
                || prefix[1] != 'v' || prefix[2] != 'o' || prefix[3] != 'r'
                || prefix[4] != 'b' || prefix[5] != 'i' || prefix[6] != 's') {
            return false;
        }
        if (packetIndex != 0) {
            return true;
        }
        if (prefixLength < 16 || packetLength < 30) {
            return false;
        }
        int version = littleEndianInt(prefix, 7);
        int channels = Byte.toUnsignedInt(prefix[11]);
        int sampleRate = littleEndianInt(prefix, 12);
        return version == 0 && channels > 0 && channels <= 8 && sampleRate > 0;
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset])
                | Byte.toUnsignedInt(bytes[offset + 1]) << 8
                | Byte.toUnsignedInt(bytes[offset + 2]) << 16
                | Byte.toUnsignedInt(bytes[offset + 3]) << 24;
    }
}
