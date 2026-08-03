package com.recipetree.neiexport1710;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WrcbeTriangulatorIconRendererTest {
    @Test
    public void ownerResourceAndCompositeFingerprintsArePinned() {
        assertEquals(194, WrcbeTriangulatorIconRenderer.RING_RESOURCE_BYTES);
        assertEquals(
                "bd71f38f4b6ee4cc86455691cc3ed6d85867acea4f6ca941b1436f993be3a0ac",
                WrcbeTriangulatorIconRenderer.RING_RESOURCE_SHA256);
        assertEquals(224, WrcbeTriangulatorIconRenderer.GRADIENT_RESOURCE_BYTES);
        assertEquals(
                "a35f5e487b0dfa713cca7860841a88de33bd80d54429097763b20dad32a787fa",
                WrcbeTriangulatorIconRenderer.GRADIENT_RESOURCE_SHA256);
        assertEquals(140, WrcbeTriangulatorIconRenderer.OWNER_DEFAULT_VISIBLE_PIXELS);
        assertEquals(
                "8a1f28ad4d608520b4e8baa13e99ca170012c7cc14aa798b5ad03723862869df",
                WrcbeTriangulatorIconRenderer.OWNER_DEFAULT_ARGB_SHA256);
    }

    @Test
    public void ownerMergeUsesRingOnlyWhereGradientAlphaIsZero() {
        int[] ring = new int[256];
        int[] gradient = new int[256];
        ring[0] = 0xff102030;
        gradient[0] = 0x00112233;
        ring[1] = 0xff405060;
        gradient[1] = 0x7f708090;

        int[] merged = WrcbeTriangulatorIconRenderer.composeOwnerDefaultPixels(
                ring, gradient);

        assertEquals(ring[0], merged[0]);
        assertEquals(gradient[1], merged[1]);
    }

    @Test
    public void boundedPinnedResourceRequiresExactLengthAndDigest() throws Exception {
        byte[] bytes = {1, 2, 3, 4, 5};
        String digest = "74f81fe167d99b4cb41d6d0ccda82278caee9f3e2f25d5e5a3936ff3dcec60d0";
        assertArrayEquals(bytes, WrcbeTriangulatorIconRenderer.readExactPinnedResource(
                new ByteArrayInputStream(bytes), bytes.length, digest));

        try {
            WrcbeTriangulatorIconRenderer.readExactPinnedResource(
                    new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6}),
                    bytes.length, digest);
            fail("oversize owner resources must fail closed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("length mismatch"));
        }

        try {
            WrcbeTriangulatorIconRenderer.readExactPinnedResource(
                    new ByteArrayInputStream(bytes), bytes.length,
                    "0000000000000000000000000000000000000000000000000000000000000000");
            fail("owner resource digest drift must fail closed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("SHA-256 mismatch"));
        }
    }

    @Test
    public void ownerFingerprintRejectsTransparentDrift() {
        try {
            WrcbeTriangulatorIconRenderer.verifyOwnerPixels(new int[256]);
            fail("transparent owner pixels must fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("fingerprint drifted"));
        }
    }
}
