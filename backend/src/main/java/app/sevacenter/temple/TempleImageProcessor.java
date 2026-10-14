package app.sevacenter.temple;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import app.sevacenter.web.InvalidFieldException;
import org.springframework.stereotype.Component;

/**
 * Turns an uploaded temple photo into a safe public image. The upload itself is never stored or
 * served: it is decoded to pixels and a fresh JPEG is written, so
 * <ul>
 *   <li>a polyglot (a valid image that is also HTML/JS) or anything after the image data is gone;</li>
 *   <li>EXIF and other metadata (GPS location, device) is gone;</li>
 *   <li>only PNG and JPEG are read: no SVG (script), no GIF, nothing ImageIO might add later;</li>
 *   <li>dimensions are read from the header before decoding, so a small file that would expand to
 *       a huge bitmap (decompression bomb) is refused before any memory is spent on it;</li>
 *   <li>the result is at most 1600 px on its longest side.</li>
 * </ul>
 */
@Component
public class TempleImageProcessor {

    static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    static final int MAX_INPUT_SIDE = 8000;
    static final long MAX_INPUT_PIXELS = 40_000_000L;
    static final int MAX_SIDE = 1600;
    static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private static final Set<String> FORMATS = Set.of("png", "jpeg");

    public record Processed(byte[] jpeg, int width, int height, String version) { }

    static {
        ImageIO.setUseCache(false); // decode in memory: no temp files on a read-only root
    }

    public Processed process(byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw invalid("Choose an image to upload");
        }
        if (upload.length > MAX_UPLOAD_BYTES) {
            throw invalid("The image must be 2 MB or less");
        }
        BufferedImage pixels = decode(upload);
        BufferedImage scaled = scale(pixels);
        byte[] jpeg = encode(scaled, 0.85f);
        if (jpeg.length > MAX_OUTPUT_BYTES) {
            jpeg = encode(scaled, 0.7f);
        }
        if (jpeg.length > MAX_OUTPUT_BYTES) {
            throw invalid("The image is too detailed to store; try a smaller one");
        }
        return new Processed(jpeg, scaled.getWidth(), scaled.getHeight(), version(jpeg));
    }

    private static BufferedImage decode(byte[] upload) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (in == null || !readers.hasNext()) {
                throw invalid("Only PNG and JPEG images are allowed");
            }
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw invalid("Only PNG and JPEG images are allowed");
                }
                reader.setInput(in, true, true); // ignore metadata: never parsed, never kept
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > MAX_INPUT_SIDE || h > MAX_INPUT_SIDE || (long) w * h > MAX_INPUT_PIXELS) {
                    throw invalid("The image must be at most " + MAX_INPUT_SIDE + " × " + MAX_INPUT_SIDE + " pixels");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalid("The image couldn't be read");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidFieldException ife) {
                throw ife;
            }
            throw invalid("The image couldn't be read; save it as PNG or JPEG and try again");
        }
    }

    /** Fits within MAX_SIDE; draws onto white so transparency becomes a plain background. */
    private static BufferedImage scale(BufferedImage src) {
        double factor = Math.min(1.0, (double) MAX_SIDE / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * factor));
        int h = Math.max(1, (int) Math.round(src.getHeight() * factor));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encode(BufferedImage image, float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param); // no metadata written
        } catch (IOException e) {
            throw new IllegalStateException("JPEG encoding failed", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static String version(byte[] jpeg) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jpeg)).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static InvalidFieldException invalid(String message) {
        return new InvalidFieldException("image", message);
    }
}
