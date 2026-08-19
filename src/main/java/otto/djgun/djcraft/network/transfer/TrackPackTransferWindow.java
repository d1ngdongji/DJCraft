package otto.djgun.djcraft.network.transfer;

import java.util.ArrayList;
import java.util.List;

public final class TrackPackTransferWindow {
    private TrackPackTransferWindow() {
    }

    public static List<Segment> plan(long offset, long totalSize, int chunkBytes, int windowChunks) {
        if (offset < 0 || totalSize < offset || chunkBytes <= 0 || windowChunks <= 0) {
            throw new IllegalArgumentException("Invalid transfer window parameters");
        }
        List<Segment> segments = new ArrayList<>(windowChunks);
        long cursor = offset;
        for (int index = 0; index < windowChunks && cursor < totalSize; index++) {
            int length = (int) Math.min(chunkBytes, totalSize - cursor);
            long next = cursor + length;
            segments.add(new Segment(cursor, length, index == windowChunks - 1 || next == totalSize));
            cursor = next;
        }
        return List.copyOf(segments);
    }

    public record Segment(long offset, int length, boolean windowEnd) {
    }
}
