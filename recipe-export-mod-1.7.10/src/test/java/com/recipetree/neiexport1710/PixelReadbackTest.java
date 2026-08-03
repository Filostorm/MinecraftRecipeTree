package com.recipetree.neiexport1710;

import org.junit.Test;

import java.nio.IntBuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class PixelReadbackTest {
    @Test
    public void flipsOpenGlRowsWithoutChangingArgbWords() {
        IntBuffer source = IntBuffer.wrap(new int[]{3, 4, 1, 2});
        int[] destination = new int[4];
        PixelReadback.copyBgraRevBottomUpToArgbTopDown(source, 2, 2, destination);
        assertArrayEquals(new int[]{1, 2, 3, 4}, destination);
        assertEquals(0, source.position());
    }
}
