package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackPackContentHashTest {
    @TempDir
    Path temporary;

    @Test
    void directoryAndArchiveUseTheSameCanonicalContentHash() throws Exception {
        Path directory = temporary.resolve("pack");
        Files.createDirectories(directory.resolve("art"));
        Files.writeString(directory.resolve("track.json"), "{}");
        Files.write(directory.resolve("art/disc.png"), new byte[] { 1, 2, 3 });

        Path archive = temporary.resolve("pack.djcraft");
        URI uri = URI.create("jar:" + archive.toUri());
        try (FileSystem zip = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            Files.createDirectories(zip.getPath("/art"));
            Files.writeString(zip.getPath("/track.json"), "{}");
            Files.write(zip.getPath("/art/disc.png"), new byte[] { 1, 2, 3 });
            assertEquals(TrackPackManager.computeCanonicalContentHash(directory),
                    TrackPackManager.computeCanonicalContentHash(zip.getPath("/")));
        }

        Files.write(directory.resolve("art/disc.png"), new byte[] { 3, 2, 1 });
        try (FileSystem zip = FileSystems.newFileSystem(uri, Map.of())) {
            assertNotEquals(TrackPackManager.computeCanonicalContentHash(directory),
                    TrackPackManager.computeCanonicalContentHash(zip.getPath("/")));
        }
    }
}
