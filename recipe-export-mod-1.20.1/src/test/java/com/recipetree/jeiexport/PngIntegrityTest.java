package com.recipetree.jeiexport;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PngIntegrityTest {
    @Test
    void acceptsACompletePngAndRejectsCorruptedCompressedData() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF123456);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        byte[] valid = output.toByteArray();

        assertDoesNotThrow(() -> PngIntegrity.verify(valid));

        byte[] corrupted = valid.clone();
        int idat = findChunk(corrupted, "IDAT");
        corrupted[idat + 8] ^= 0x01;
        assertThrows(IOException.class, () -> PngIntegrity.verify(corrupted));
    }

    private static int findChunk(byte[] png, String expectedType) {
        int offset = 8;
        while (offset + 12 <= png.length) {
            int length = ((png[offset] & 0xFF) << 24)
                    | ((png[offset + 1] & 0xFF) << 16)
                    | ((png[offset + 2] & 0xFF) << 8)
                    | (png[offset + 3] & 0xFF);
            String type = new String(png, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (type.equals(expectedType)) {
                return offset;
            }
            offset += 12 + length;
        }
        throw new AssertionError("Missing " + expectedType + " chunk");
    }
}
