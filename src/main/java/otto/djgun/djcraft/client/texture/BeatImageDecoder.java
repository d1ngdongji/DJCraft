package otto.djgun.djcraft.client.texture;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.metadata.IIOMetadataNode;
import org.w3c.dom.Node;

/** Pure PNG/GIF decoder used by the falling-beat resource reload listener. */
public final class BeatImageDecoder {
    public static final int MAX_DIMENSION = 1024;
    public static final int MAX_GIF_FRAMES = 256;
    public static final long MAX_DECODED_PIXELS_PER_ASSET = 16_777_216L;
    public static final long MAX_ENCODED_BYTES = 32L * 1024L * 1024L;
    private static final int MIN_GIF_FRAME_DELAY_MS = 20;

    private BeatImageDecoder() {
    }

    public static DecodedAnimation decode(InputStream input, String sourceName) throws IOException {
        if (input == null) {
            throw new IOException("missing image stream");
        }
        String lowerName = sourceName == null ? "" : sourceName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".png") && !lowerName.endsWith(".gif")) {
            throw new IOException("only .png and .gif beat textures are supported");
        }

        try (InputStream limited = new LimitedInputStream(input, MAX_ENCODED_BYTES);
                ImageInputStream imageInput = ImageIO.createImageInputStream(limited)) {
            if (imageInput == null) {
                throw new IOException("could not create image input stream");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IOException("no ImageIO reader accepted the image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, false, false);
                return lowerName.endsWith(".gif") ? decodeGif(reader) : decodeStatic(reader);
            } finally {
                reader.dispose();
            }
        }
    }

    private static DecodedAnimation decodeStatic(ImageReader reader) throws IOException {
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        validateDimensions(width, height);
        validatePixels((long) width * height);
        BufferedImage image = reader.read(0);
        return new DecodedAnimation(width, height,
                List.of(new DecodedFrame(copyArgb(image, width, height), 0L)), 0);
    }

    private static DecodedAnimation decodeGif(ImageReader reader) throws IOException {
        int frameCount = reader.getNumImages(true);
        if (frameCount <= 0 || frameCount > MAX_GIF_FRAMES) {
            throw new IOException("GIF frame count must be between 1 and " + MAX_GIF_FRAMES);
        }

        int[] logicalSize = logicalScreenSize(reader.getStreamMetadata());
        int width = logicalSize[0] > 0 ? logicalSize[0] : reader.getWidth(0);
        int height = logicalSize[1] > 0 ? logicalSize[1] : reader.getHeight(0);
        validateDimensions(width, height);
        validatePixels((long) width * height * frameCount);

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        List<DecodedFrame> frames = new ArrayList<>(frameCount);
        String previousDisposal = "none";
        FrameMetadata previousMetadata = null;
        BufferedImage restoreBeforePrevious = null;

        for (int index = 0; index < frameCount; index++) {
            if (previousMetadata != null) {
                if ("restoreToBackgroundColor".equals(previousDisposal)) {
                    clearRegion(canvas, previousMetadata);
                } else if ("restoreToPrevious".equals(previousDisposal) && restoreBeforePrevious != null) {
                    canvas = deepCopy(restoreBeforePrevious);
                }
            }

            FrameMetadata metadata = frameMetadata(reader.getImageMetadata(index));
            BufferedImage raw = reader.read(index);
            BufferedImage restoreForCurrent = "restoreToPrevious".equals(metadata.disposalMethod())
                    ? deepCopy(canvas)
                    : null;

            Graphics2D graphics = canvas.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.SrcOver);
                int drawX = raw.getWidth() == width && raw.getHeight() == height ? 0 : metadata.left();
                int drawY = raw.getWidth() == width && raw.getHeight() == height ? 0 : metadata.top();
                graphics.drawImage(raw, drawX, drawY, null);
            } finally {
                graphics.dispose();
            }

            frames.add(new DecodedFrame(
                    canvas.getRGB(0, 0, width, height, null, 0, width),
                    Math.max(MIN_GIF_FRAME_DELAY_MS, metadata.delayHundredths() * 10L)));
            previousDisposal = metadata.disposalMethod();
            previousMetadata = metadata;
            restoreBeforePrevious = restoreForCurrent;
        }

        int declaredLoopCount = gifLoopCount(reader.getStreamMetadata());
        if (declaredLoopCount < 0) {
            declaredLoopCount = gifLoopCount(reader.getImageMetadata(0));
        }
        int totalCycles = declaredLoopCount < 0
                ? 1
                : declaredLoopCount == 0 ? 0 : declaredLoopCount + 1;
        return new DecodedAnimation(width, height, List.copyOf(frames), totalCycles);
    }

    private static void validateDimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IOException("image dimensions exceed " + MAX_DIMENSION + "x" + MAX_DIMENSION);
        }
    }

    private static void validatePixels(long pixels) throws IOException {
        if (pixels <= 0 || pixels > MAX_DECODED_PIXELS_PER_ASSET) {
            throw new IOException("decoded image exceeds " + MAX_DECODED_PIXELS_PER_ASSET + " pixels");
        }
    }

    private static int[] copyArgb(BufferedImage image, int width, int height) {
        if (image.getWidth() == width && image.getHeight() == height) {
            return image.getRGB(0, 0, width, height, null, 0, width);
        }
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return canvas.getRGB(0, 0, width, height, null, 0, width);
    }

    private static void clearRegion(BufferedImage canvas, FrameMetadata metadata) {
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(metadata.left(), metadata.top(), metadata.width(), metadata.height());
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage deepCopy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static int[] logicalScreenSize(IIOMetadata metadata) {
        IIOMetadataNode descriptor = findNode(metadata, "LogicalScreenDescriptor");
        if (descriptor == null) {
            return new int[] { 0, 0 };
        }
        return new int[] {
                intAttribute(descriptor, "logicalScreenWidth", 0),
                intAttribute(descriptor, "logicalScreenHeight", 0)
        };
    }

    private static FrameMetadata frameMetadata(IIOMetadata metadata) {
        IIOMetadataNode descriptor = findNode(metadata, "ImageDescriptor");
        IIOMetadataNode control = findNode(metadata, "GraphicControlExtension");
        return new FrameMetadata(
                intAttribute(descriptor, "imageLeftPosition", 0),
                intAttribute(descriptor, "imageTopPosition", 0),
                intAttribute(descriptor, "imageWidth", 0),
                intAttribute(descriptor, "imageHeight", 0),
                control == null ? "none" : control.getAttribute("disposalMethod"),
                intAttribute(control, "delayTime", 0));
    }

    private static int gifLoopCount(IIOMetadata metadata) {
        IIOMetadataNode applications = findNode(metadata, "ApplicationExtensions");
        if (applications == null) {
            return -1;
        }
        for (Node node = applications.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof IIOMetadataNode metadataNode
                    && "ApplicationExtension".equals(metadataNode.getNodeName())) {
                Object data = metadataNode.getUserObject();
                if (data instanceof byte[] bytes && bytes.length >= 3 && bytes[0] == 1) {
                    return (bytes[1] & 0xFF) | ((bytes[2] & 0xFF) << 8);
                }
            }
        }
        return -1;
    }

    private static IIOMetadataNode findNode(IIOMetadata metadata, String name) {
        if (metadata == null) {
            return null;
        }
        for (String format : metadata.getMetadataFormatNames()) {
            Node found = findNode(metadata.getAsTree(format), name);
            if (found instanceof IIOMetadataNode metadataNode) {
                return metadataNode;
            }
        }
        return null;
    }

    private static Node findNode(Node node, String name) {
        if (node == null) {
            return null;
        }
        if (name.equals(node.getNodeName())) {
            return node;
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            Node found = findNode(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static int intAttribute(IIOMetadataNode node, String name, int fallback) {
        if (node == null) {
            return fallback;
        }
        try {
            String value = node.getAttribute(name);
            return value == null || value.isEmpty() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public record DecodedFrame(int[] argbPixels, long durationMs) {
        public DecodedFrame {
            argbPixels = argbPixels.clone();
        }
    }

    public record DecodedAnimation(int width, int height, List<DecodedFrame> frames, int loopCount) {
        public DecodedAnimation {
            frames = List.copyOf(frames);
        }

        public long decodedPixels() {
            return (long) width * height * frames.size();
        }

        public int frameIndex(long elapsedMs) {
            if (frames.size() <= 1) {
                return 0;
            }
            long cycleDuration = frames.stream().mapToLong(DecodedFrame::durationMs).sum();
            if (cycleDuration <= 0L) {
                return 0;
            }
            long position;
            if (loopCount == 0) {
                position = Math.floorMod(elapsedMs, cycleDuration);
            } else {
                long totalDuration;
                try {
                    totalDuration = Math.multiplyExact(cycleDuration, Math.max(1, loopCount));
                } catch (ArithmeticException ignored) {
                    totalDuration = Long.MAX_VALUE;
                }
                if (elapsedMs >= totalDuration) {
                    return frames.size() - 1;
                }
                position = Math.floorMod(elapsedMs, cycleDuration);
            }
            long cursor = 0L;
            for (int index = 0; index < frames.size(); index++) {
                cursor += frames.get(index).durationMs();
                if (position < cursor) {
                    return index;
                }
            }
            return frames.size() - 1;
        }
    }

    private record FrameMetadata(int left, int top, int width, int height,
            String disposalMethod, int delayHundredths) {
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long maximum;
        private long read;

        private LimitedInputStream(InputStream input, long maximum) {
            super(input);
            this.maximum = maximum;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                account(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = super.read(bytes, offset, length);
            if (count > 0) {
                account(count);
            }
            return count;
        }

        private void account(int count) throws IOException {
            read += count;
            if (read > maximum) {
                throw new IOException("encoded image exceeds " + maximum + " bytes");
            }
        }
    }
}
