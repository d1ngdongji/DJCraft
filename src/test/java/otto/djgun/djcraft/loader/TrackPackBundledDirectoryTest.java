package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackPackBundledDirectoryTest {
    @TempDir
    Path temporary;

    @Test
    void loadsBundledPackAndAllowsExternalOverrideAndFallback() throws Exception {
        Path gameDir = temporary.resolve("game");
        Path bundledRoot = temporary.resolve("bundled");
        writePack(bundledRoot.resolve("example"), "Bundled", "bundled art");

        TrackPackManager manager = new TrackPackManager();
        manager.initialize(gameDir, bundledRoot);

        assertEquals("Bundled", manager.getTrackPack("example").orElseThrow().meta().displayName());
        assertEquals("bundled art", read(manager.openFileStream("example", "art/marker.txt")));

        Path externalPack = gameDir.resolve("djcraft/trackpacks/example");
        writePack(externalPack, "External", "external art");
        manager.reloadAllPacks();

        assertEquals("External", manager.getTrackPack("example").orElseThrow().meta().displayName());
        assertEquals("external art", read(manager.openFileStream("example", "art/marker.txt")));

        Files.delete(externalPack.resolve("track.json"));
        manager.reloadPack("example");

        assertEquals("Bundled", manager.getTrackPack("example").orElseThrow().meta().displayName());
        assertEquals("bundled art", read(manager.openFileStream("example", "art/marker.txt")));
    }

    @Test
    void loadsBundledDjcraftArchiveAndKeepsItReadOnly() throws Exception {
        Path gameDir = temporary.resolve("game");
        Path bundledRoot = temporary.resolve("bundled");
        Path source = temporary.resolve("source");
        writePack(source, "Bundled archive", "archive art");
        Files.createDirectories(bundledRoot);
        Path archive = bundledRoot.resolve("example.djcraft");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive));
                var paths = Files.walk(source)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                zip.putNextEntry(new ZipEntry(source.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }

        TrackPackManager manager = new TrackPackManager();
        manager.initialize(gameDir, bundledRoot);

        assertEquals("Bundled archive", manager.getTrackPack("example").orElseThrow().meta().displayName());
        assertEquals("archive art", read(manager.openFileStream("example", "art/marker.txt")));
        try (InputStream audio = manager.openAudioStream("example")) {
            assertNotNull(audio);
        }
        assertTrue(manager.getArchiveFilePath("example").isEmpty());

        Path externalPack = gameDir.resolve("djcraft/trackpacks/example");
        writePack(externalPack, "External", "external art");
        manager.reloadPack("example");
        assertEquals("External", manager.getTrackPack("example").orElseThrow().meta().displayName());

        Files.delete(externalPack.resolve("track.json"));
        manager.reloadPack("example");
        assertEquals("Bundled archive", manager.getTrackPack("example").orElseThrow().meta().displayName());
    }

    @Test
    void loadsCheckedInBundledArchives() throws Exception {
        Path bundledRoot = Path.of(TrackPackBundledDirectoryTest.class
                .getResource("/djcraft/trackpacks").toURI());
        TrackPackManager manager = new TrackPackManager();

        manager.initialize(temporary.resolve("game"), bundledRoot);

        assertTrue(manager.isPackLoaded("bpm120"));
        assertTrue(manager.isPackLoaded("i_got_smoke"));
    }

    private static void writePack(Path packDir, String displayName, String marker) throws Exception {
        Files.createDirectories(packDir.resolve("art"));
        Files.writeString(packDir.resolve("track.json"), """
                {
                  "meta": {
                    "version": "1",
                    "author": "test",
                    "bpm": 120,
                    "difficulty": "normal",
                    "sound_file": "track.ogg",
                    "offset_ms": 0,
                    "total_duration_ms": 1000,
                    "display_name": "%s"
                  },
                  "definitions": {},
                  "timeline": { "combat_line": [] }
                }
                """.formatted(displayName), StandardCharsets.UTF_8);
        Files.write(packDir.resolve("track.ogg"), completeVorbisStream());
        Files.writeString(packDir.resolve("art/marker.txt"), marker, StandardCharsets.UTF_8);
    }

    private static String read(InputStream stream) throws Exception {
        assertNotNull(stream);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] completeVorbisStream() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] header = new byte[27];
        header[0] = 'O';
        header[1] = 'g';
        header[2] = 'g';
        header[3] = 'S';
        header[5] = 0x06;
        header[14] = 1;
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
        byte[] signature = "vorbis".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(signature, 0, packet, 1, signature.length);
    }
}
