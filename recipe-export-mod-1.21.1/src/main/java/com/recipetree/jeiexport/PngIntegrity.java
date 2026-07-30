package com.recipetree.jeiexport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;

/**
 * Performs a lightweight structural and compressed-stream check on PNG files.
 *
 * <p>Minecraft's native PNG writer can very rarely return successfully after
 * emitting an invalid zlib stream. Checking the stream before publishing keeps
 * a completed snapshot from silently containing an unreadable image.</p>
 */
final class PngIntegrity {
    private static final byte[] SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private PngIntegrity() {
    }

    static void verify(Path file) throws IOException {
        verify(Files.readAllBytes(file));
    }

    static void verify(byte[] png) throws IOException {
        if (png.length < SIGNATURE.length || !Arrays.equals(SIGNATURE, Arrays.copyOf(png, SIGNATURE.length))) {
            throw new IOException("invalid PNG signature");
        }

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        boolean sawHeader = false;
        boolean sawData = false;
        boolean sawEnd = false;
        int offset = SIGNATURE.length;
        while (offset < png.length) {
            if (png.length - offset < 12) {
                throw new IOException("truncated PNG chunk header");
            }
            long unsignedLength = Integer.toUnsignedLong(readInt(png, offset));
            if (unsignedLength > Integer.MAX_VALUE) {
                throw new IOException("PNG chunk is too large");
            }
            int length = (int) unsignedLength;
            long chunkEnd = (long) offset + 12L + length;
            if (chunkEnd > png.length) {
                throw new IOException("truncated PNG chunk");
            }

            int typeOffset = offset + 4;
            int dataOffset = offset + 8;
            String type = new String(png, typeOffset, 4, StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32();
            crc.update(png, typeOffset, 4 + length);
            long expectedCrc = Integer.toUnsignedLong(readInt(png, dataOffset + length));
            if (crc.getValue() != expectedCrc) {
                throw new IOException("invalid " + type + " chunk checksum");
            }

            switch (type) {
                case "IHDR" -> {
                    if (sawHeader || offset != SIGNATURE.length || length != 13) {
                        throw new IOException("invalid PNG header");
                    }
                    if (readInt(png, dataOffset) <= 0 || readInt(png, dataOffset + 4) <= 0) {
                        throw new IOException("invalid PNG dimensions");
                    }
                    sawHeader = true;
                }
                case "IDAT" -> {
                    compressed.write(png, dataOffset, length);
                    sawData = true;
                }
                case "IEND" -> {
                    if (length != 0) {
                        throw new IOException("invalid PNG end chunk");
                    }
                    sawEnd = true;
                }
                default -> {
                }
            }
            offset = (int) chunkEnd;
            if (sawEnd && offset != png.length) {
                throw new IOException("data follows PNG end chunk");
            }
        }
        if (!sawHeader || !sawData || !sawEnd) {
            throw new IOException("PNG is missing a required chunk");
        }

        try (InflaterInputStream inflater =
                     new InflaterInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
            byte[] buffer = new byte[8192];
            while (inflater.read(buffer) != -1) {
                // Reading the complete stream verifies its checksum.
            }
        } catch (IOException e) {
            throw new IOException("invalid PNG compressed stream", e);
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }
}
