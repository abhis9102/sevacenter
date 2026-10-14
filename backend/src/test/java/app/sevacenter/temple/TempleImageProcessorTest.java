package app.sevacenter.temple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import app.sevacenter.web.InvalidFieldException;
import org.junit.jupiter.api.Test;

/** Only a fresh, bounded, metadata-free JPEG ever comes out, whatever goes in. */
class TempleImageProcessorTest {

    private final TempleImageProcessor processor = new TempleImageProcessor();

    @Test
    void aPhotoComesOutAsAFreshJpeg() throws IOException {
        TempleImageProcessor.Processed p = processor.process(image("png", 800, 600, BufferedImage.TYPE_INT_RGB));
        assertThat(p.jpeg()).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
        assertThat(p.width()).isEqualTo(800);
        assertThat(p.height()).isEqualTo(600);
        assertThat(p.version()).matches("[0-9a-f]{16}");
        assertThat(ImageIO.read(new ByteArrayInputStream(p.jpeg()))).isNotNull();
    }

    @Test
    void metadataSuchAsGpsLocationIsDropped() throws IOException {
        byte[] jpeg = image("jpeg", 400, 300, BufferedImage.TYPE_INT_RGB);
        byte[] withExif = withApp1(jpeg, "Exif\0\0GPS-SECRET-18.5204N-73.8567E");
        assertThat(new String(withExif, StandardCharsets.ISO_8859_1)).contains("GPS-SECRET");
        byte[] out = processor.process(withExif).jpeg();
        String text = new String(out, StandardCharsets.ISO_8859_1);
        assertThat(text).doesNotContain("GPS-SECRET").doesNotContain("Exif");
    }

    @Test
    void anythingHiddenAfterTheImageIsGone() throws IOException {
        byte[] polyglot = concat(image("jpeg", 200, 200, BufferedImage.TYPE_INT_RGB),
                "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.US_ASCII));
        String out = new String(processor.process(polyglot).jpeg(), StandardCharsets.ISO_8859_1);
        assertThat(out).doesNotContain("<script").doesNotContain("<html");
    }

    @Test
    void onlyPngAndJpegAreRead() throws IOException {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] html = "<!doctype html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        byte[] gif = image("gif", 50, 50, BufferedImage.TYPE_INT_RGB);
        for (byte[] bad : new byte[][] {svg, html, gif, {1, 2, 3}}) {
            assertThatThrownBy(() -> processor.process(bad)).isInstanceOf(InvalidFieldException.class);
        }
    }

    @Test
    void hugeDimensionsAreRefusedBeforeDecoding() throws IOException {
        // Tiny files (one colour compresses to almost nothing) that would decode to a huge bitmap.
        byte[] tooWide = image("png", TempleImageProcessor.MAX_INPUT_SIDE + 1, 1, BufferedImage.TYPE_BYTE_BINARY);
        byte[] tooManyPixels = image("png", 7000, 7000, BufferedImage.TYPE_BYTE_BINARY);
        assertThat(tooManyPixels.length).isLessThan(TempleImageProcessor.MAX_UPLOAD_BYTES);
        for (byte[] bomb : new byte[][] {tooWide, tooManyPixels}) {
            assertThatThrownBy(() -> processor.process(bomb)).isInstanceOf(InvalidFieldException.class)
                    .hasMessageContaining("pixels");
        }
    }

    @Test
    void bigPhotosAreScaledDownAndTransparencyBecomesWhite() throws IOException {
        TempleImageProcessor.Processed p = processor.process(image("png", 3000, 2000, BufferedImage.TYPE_INT_ARGB));
        assertThat(p.width()).isEqualTo(TempleImageProcessor.MAX_SIDE);
        assertThat(p.height()).isEqualTo(1067);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(p.jpeg()));
        Color corner = new Color(out.getRGB(0, 0));
        assertThat(corner.getRed()).isGreaterThan(240); // transparent -> white, not black
    }

    @Test
    void emptyAndOversizedUploadsAreRefused() {
        assertThatThrownBy(() -> processor.process(new byte[0])).isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> processor.process(new byte[TempleImageProcessor.MAX_UPLOAD_BYTES + 1]))
                .isInstanceOf(InvalidFieldException.class).hasMessageContaining("2 MB");
    }

    // --- helpers -----------------------------------------------------------------------------

    private static byte[] image(String format, int w, int h, int type) throws IOException {
        BufferedImage img = new BufferedImage(w, h, type);
        if (type != BufferedImage.TYPE_INT_ARGB && type != BufferedImage.TYPE_BYTE_BINARY) {
            var g = img.createGraphics();
            g.setColor(new Color(200, 80, 20));
            g.fillRect(0, 0, w, h);
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(img, format, out)).as("writer for " + format).isTrue();
        return out.toByteArray();
    }

    /** Inserts an APP1 segment right after the JPEG's SOI marker. */
    private static byte[] withApp1(byte[] jpeg, String payload) {
        byte[] p = payload.getBytes(StandardCharsets.ISO_8859_1);
        int len = p.length + 2;
        byte[] seg = concat(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) (len >> 8), (byte) len}, p);
        return concat(concat(new byte[] {jpeg[0], jpeg[1]}, seg), java.util.Arrays.copyOfRange(jpeg, 2, jpeg.length));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
