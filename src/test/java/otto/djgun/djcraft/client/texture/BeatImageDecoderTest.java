package otto.djgun.djcraft.client.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

class BeatImageDecoderTest {
    @Test
    void preservesPngArgbPixels() throws Exception {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x80402010);
        image.setRGB(1, 0, 0xFF11AAEE);

        BeatImageDecoder.DecodedAnimation decoded = BeatImageDecoder.decode(
                new ByteArrayInputStream(writePng(image)), "beats/test.png");

        assertEquals(2, decoded.width());
        assertEquals(1, decoded.height());
        assertEquals(0x80402010, decoded.frames().getFirst().argbPixels()[0]);
        assertEquals(0xFF11AAEE, decoded.frames().getFirst().argbPixels()[1]);
    }

    @Test
    void decodesGifDelaysPartialFramesAndDisposal() throws Exception {
        byte[] gif = writeAnimatedGif();

        BeatImageDecoder.DecodedAnimation decoded = BeatImageDecoder.decode(
                new ByteArrayInputStream(gif), "beats/test.gif");

        assertEquals(3, decoded.frames().size());
        assertEquals(50L, decoded.frames().get(0).durationMs());
        assertEquals(70L, decoded.frames().get(1).durationMs());
        assertEquals(90L, decoded.frames().get(2).durationMs());
        assertEquals(0xFFFF0000, decoded.frames().get(0).argbPixels()[0]);
        assertEquals(0xFF0000FF, decoded.frames().get(1).argbPixels()[1]);
        assertEquals(0xFF00FF00, decoded.frames().get(2).argbPixels()[0]);
        assertEquals(0x00000000, decoded.frames().get(2).argbPixels()[1]);
        assertEquals(1, decoded.frameIndex(50L));
        assertEquals(0, decoded.frameIndex(210L));
    }

    @Test
    void rejectsOversizedImages() throws Exception {
        BufferedImage image = new BufferedImage(BeatImageDecoder.MAX_DIMENSION + 1, 1,
                BufferedImage.TYPE_INT_ARGB);

        assertThrows(IOException.class, () -> BeatImageDecoder.decode(
                new ByteArrayInputStream(writePng(image)), "beats/too-wide.png"));
    }

    private static byte[] writePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static byte[] writeAnimatedGif() throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            writer.prepareWriteSequence(null);

            BufferedImage first = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
            first.setRGB(0, 0, 0xFFFF0000);
            first.setRGB(1, 0, 0xFFFF0000);
            writeGifFrame(writer, first, 0, 0, 5, "none", true);

            BufferedImage second = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            second.setRGB(0, 0, 0xFF0000FF);
            writeGifFrame(writer, second, 1, 0, 7, "restoreToBackgroundColor", false);

            BufferedImage third = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            third.setRGB(0, 0, 0xFF00FF00);
            writeGifFrame(writer, third, 0, 0, 9, "none", false);

            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static void writeGifFrame(ImageWriter writer, BufferedImage image, int left, int top,
            int delayHundredths, String disposalMethod, boolean addLoopExtension) throws IOException {
        ImageWriteParam parameter = writer.getDefaultWriteParam();
        IIOMetadata metadata = writer.getDefaultImageMetadata(
                ImageTypeSpecifier.createFromRenderedImage(image), parameter);
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);

        IIOMetadataNode control = child(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", disposalMethod);
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "TRUE");
        control.setAttribute("delayTime", Integer.toString(delayHundredths));
        control.setAttribute("transparentColorIndex", "0");

        IIOMetadataNode descriptor = child(root, "ImageDescriptor");
        descriptor.setAttribute("imageLeftPosition", Integer.toString(left));
        descriptor.setAttribute("imageTopPosition", Integer.toString(top));

        if (addLoopExtension) {
            IIOMetadataNode applications = child(root, "ApplicationExtensions");
            IIOMetadataNode extension = new IIOMetadataNode("ApplicationExtension");
            extension.setAttribute("applicationID", "NETSCAPE");
            extension.setAttribute("authenticationCode", "2.0");
            extension.setUserObject(new byte[] { 1, 0, 0 });
            applications.appendChild(extension);
        }

        metadata.setFromTree(format, root);
        writer.writeToSequence(new IIOImage(image, null, metadata), parameter);
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) {
                return (IIOMetadataNode) node;
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
