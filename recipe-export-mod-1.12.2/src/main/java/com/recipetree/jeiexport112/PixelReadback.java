package com.recipetree.jeiexport112;

import java.nio.Buffer;
import java.nio.IntBuffer;

/** Converts the native packed-pixel contract used by the offscreen framebuffer readback. */
final class PixelReadback {
    private PixelReadback() {
    }

    /**
     * Copies pixels returned by {@code GL_BGRA + GL_UNSIGNED_INT_8_8_8_8_REV} into the
     * {@code TYPE_INT_ARGB} backing array. That OpenGL format/type pair already produces ARGB
     * integer values in a native-order IntBuffer, so only the OpenGL bottom-up row order needs
     * conversion. Rows are copied in bulk to avoid four buffer reads and one setRGB call per pixel.
     */
    static void copyBgraRevBottomUpToArgbTopDown(
            IntBuffer source, int width, int height, int[] destination) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("dimensions must be positive: " + width + "x" + height);
        }
        int pixelCount = Math.multiplyExact(width, height);
        if (source.remaining() < pixelCount) {
            throw new IllegalArgumentException("source has " + source.remaining() +
                    " pixels remaining; " + pixelCount + " required");
        }
        if (destination.length < pixelCount) {
            throw new IllegalArgumentException("destination has " + destination.length +
                    " pixels; " + pixelCount + " required");
        }

        int initialPosition = source.position();
        Buffer sourceState = source;
        try {
            for (int destinationY = 0; destinationY < height; destinationY++) {
                int sourceY = height - 1 - destinationY;
                sourceState.position(initialPosition + sourceY * width);
                source.get(destination, destinationY * width, width);
            }
        } finally {
            sourceState.position(initialPosition);
        }
    }
}
