package com.recipetree.neiexport1710;

import java.nio.Buffer;
import java.nio.IntBuffer;

final class PixelReadback {
    private PixelReadback() {
    }

    static void copyBgraRevBottomUpToArgbTopDown(
            IntBuffer source, int width, int height, int[] destination) {
        if (source == null || destination == null) {
            throw new IllegalArgumentException("source and destination are required");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        int count = Math.multiplyExact(width, height);
        if (source.remaining() < count || destination.length < count) {
            throw new IllegalArgumentException("pixel buffers are smaller than " + count);
        }
        int initial = source.position();
        Buffer state = source;
        try {
            for (int destinationY = 0; destinationY < height; destinationY++) {
                int sourceY = height - 1 - destinationY;
                state.position(initial + sourceY * width);
                source.get(destination, destinationY * width, width);
            }
        } finally {
            state.position(initial);
        }
    }
}
