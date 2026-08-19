package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackPackSafetyTest {
    @TempDir
    Path tempDir;

    @Test
    void validatesResourcePathIdsButRejectsUnicodeSpacesAndPaths() {
        assertTrue(TrackPackIdValidator.isValid("safe_track-pack.01"));
        assertFalse(TrackPackIdValidator.isValid("test pack"));
        assertFalse(TrackPackIdValidator.isValid("测试曲包-01"));
        assertFalse(TrackPackIdValidator.isValid("Uppercase"));
        assertFalse(TrackPackIdValidator.isValid("../escape"));
        assertFalse(TrackPackIdValidator.isValid("folder\\escape"));
        assertFalse(TrackPackIdValidator.isValid("C:escape"));
    }

    @Test
    void acceptsSafeArchiveAndRejectsMissingOrTraversalTrackJson() throws IOException {
        Path safe = archive("safe.djcraft", "track.json", "{}");
        Path missing = archive("missing.djcraft", "cover.png", "x");
        Path traversal = archive("traversal.djcraft", "../track.json", "{}");

        assertTrue(TrackPackArchiveValidator.validate(safe, 1024));
        assertFalse(TrackPackArchiveValidator.validate(missing, 1024));
        assertFalse(TrackPackArchiveValidator.validate(traversal, 1024));
        assertFalse(TrackPackArchiveValidator.validate(safe, 1));
    }

    @Test
    void rejectsArchiveWithMalformedEntryNameEncoding() throws IOException {
        Path malformed = archive("malformed.djcraft", "track.json", "{}");
        byte[] bytes = Files.readAllBytes(malformed);
        byte[] entryName = "track.json".getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index <= bytes.length - entryName.length; index++) {
            boolean matches = true;
            for (int offset = 0; offset < entryName.length; offset++) {
                if (bytes[index + offset] != entryName[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                bytes[index] = (byte) 0xC3;
                bytes[index + 1] = (byte) 0x28;
                Files.write(malformed, bytes);
                assertFalse(TrackPackArchiveValidator.validate(malformed, 1024));
                return;
            }
        }
        throw new AssertionError("Failed to locate ZIP entry name");
    }

    private Path archive(String name, String entryName, String content) throws IOException {
        Path archive = tempDir.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return archive;
    }
}
