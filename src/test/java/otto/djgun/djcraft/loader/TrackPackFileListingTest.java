package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackPackFileListingTest {
    @TempDir
    Path tempDir;

    @Test
    void listsOnlyFilesBelowDirectoryPrefix() throws IOException {
        Path root = tempDir.resolve("pack");
        Files.createDirectories(root.resolve("combo/50"));
        Files.writeString(root.resolve("combo/0.png"), "zero");
        Files.writeString(root.resolve("combo/50/1.png"), "one");
        Files.writeString(root.resolve("track.json"), "{}");

        assertEquals(Set.of("combo/0.png", "combo/50/1.png"),
                TrackPackManager.listDirectoryFiles(root, "combo/"));
    }

    @Test
    void listsOnlyFilesBelowArchivePrefix() throws IOException {
        Path archive = tempDir.resolve("pack.djcraft");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            add(zip, "track.json", "{}");
            add(zip, "combo/0.png", "zero");
            add(zip, "combo/100/9.png", "nine");
        }

        assertEquals(Set.of("combo/0.png", "combo/100/9.png"),
                TrackPackManager.listArchiveFiles(archive, "combo/"));
    }

    private static void add(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
