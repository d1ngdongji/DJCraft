package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

class TrackPackAudioValidatorTest {
    @Test
    void acceptsStructurallyCompleteVorbisAndRejectsTruncation() throws Exception {
        byte[] ogg = completeVorbisStream();
        assertTrue(TrackPackAudioValidator.isPlayableOggVorbis(
                new ByteArrayInputStream(ogg)));
        assertFalse(TrackPackAudioValidator.isPlayableOggVorbis(
                new ByteArrayInputStream(java.util.Arrays.copyOf(ogg, ogg.length - 1))));
        assertFalse(TrackPackAudioValidator.isPlayableOggVorbis(
                new ByteArrayInputStream("not ogg".getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
    }

    private static byte[] completeVorbisStream() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] header = new byte[27];
        header[0] = 'O';
        header[1] = 'g';
        header[2] = 'g';
        header[3] = 'S';
        header[5] = 0x06; // Beginning and end of this tiny structural fixture.
        header[14] = 1; // Stream serial.
        header[26] = 4;
        output.writeBytes(header);
        output.writeBytes(new byte[] { 30, 7, 7, 1 });

        byte[] identification = new byte[30];
        putVorbisSignature(identification, 1);
        identification[11] = 2;
        int sampleRate = 48_000;
        identification[12] = (byte) sampleRate;
        identification[13] = (byte) (sampleRate >>> 8);
        identification[14] = (byte) (sampleRate >>> 16);
        identification[15] = (byte) (sampleRate >>> 24);
        output.writeBytes(identification);
        byte[] comment = new byte[7];
        putVorbisSignature(comment, 3);
        output.writeBytes(comment);
        byte[] setup = new byte[7];
        putVorbisSignature(setup, 5);
        output.writeBytes(setup);
        output.write(0);
        return output.toByteArray();
    }

    private static void putVorbisSignature(byte[] packet, int type) {
        packet[0] = (byte) type;
        byte[] signature = "vorbis".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(signature, 0, packet, 1, signature.length);
    }
}
