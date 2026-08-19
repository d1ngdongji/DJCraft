package otto.djgun.djcraft.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class TrackPackArchiveValidator {
    private static final int MAX_ENTRIES = 4096;
    private static final long MAX_TRACK_JSON_BYTES = 1024L * 1024L;

    private TrackPackArchiveValidator() {
    }

    public static boolean validate(Path archive, long maxBytes) throws IOException {
        if (!Files.isRegularFile(archive) || Files.size(archive) > maxBytes) {
            return false;
        }
        int entries = 0;
        long totalBytes = 0;
        boolean hasTrackJson = false;
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES || !isSafeEntryName(entry.getName())) {
                    return false;
                }
                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    totalBytes += read;
                    if (totalBytes > maxBytes || (entry.getName().equals("track.json")
                            && entryBytes > MAX_TRACK_JSON_BYTES)) {
                        return false;
                    }
                }
                if (entry.getName().equals("track.json")) {
                    hasTrackJson = true;
                }
                zip.closeEntry();
            }
        } catch (IllegalArgumentException e) {
            // ZipInputStream may reject malformed entry-name encoding with an unchecked exception.
            return false;
        }
        return hasTrackJson;
    }

    private static boolean isSafeEntryName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0) {
            return false;
        }
        Path path = Path.of(name).normalize();
        return !path.isAbsolute() && path.getNameCount() > 0 && !path.startsWith("..");
    }
}
