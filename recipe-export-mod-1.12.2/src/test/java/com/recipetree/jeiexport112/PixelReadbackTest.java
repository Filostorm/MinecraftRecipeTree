package com.recipetree.jeiexport112;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class PixelReadbackTest {
    @Test
    public void preservesArgbChannelsAndFlipsTwoByTwoFramebufferRows() {
        int bottomLeft = 0x7f112233;
        int bottomRight = 0xffabcdef;
        int topLeft = 0x80445566;
        int topRight = 0x00010203;
        IntBuffer source = ByteBuffer.allocateDirect(4 * Integer.BYTES)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        source.put(bottomLeft).put(bottomRight).put(topLeft).put(topRight);
        source.flip();

        int[] destination = new int[4];
        PixelReadback.copyBgraRevBottomUpToArgbTopDown(source, 2, 2, destination);

        assertArrayEquals(new int[]{topLeft, topRight, bottomLeft, bottomRight}, destination);
        assertEquals("alpha channel", 0x80, destination[0] >>> 24);
        assertEquals("red channel", 0x44, (destination[0] >>> 16) & 0xff);
        assertEquals("green channel", 0x55, (destination[0] >>> 8) & 0xff);
        assertEquals("blue channel", 0x66, destination[0] & 0xff);
        assertEquals("source position is reusable", 0, source.position());
    }
}
