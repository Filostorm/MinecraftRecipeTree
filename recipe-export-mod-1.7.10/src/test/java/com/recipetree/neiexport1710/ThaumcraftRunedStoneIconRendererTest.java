package com.recipetree.neiexport1710;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ThaumcraftRunedStoneIconRendererTest {
    @Test
    public void pinsExactOwnerIdentityAndResource() {
        assertEquals("thaumcraft-runed-stone-meta10-owner-atlas-face-on-catalog-v1",
                ThaumcraftRunedStoneIconRenderer.CONTRACT);
        assertEquals("Thaumcraft:blockEldritch",
                ThaumcraftRunedStoneIconRenderer.REGISTRY_ID);
        assertEquals("thaumcraft.common.blocks.BlockEldritchItem",
                ThaumcraftRunedStoneIconRenderer.ITEM_CLASS);
        assertEquals("thaumcraft.common.blocks.BlockEldritch",
                ThaumcraftRunedStoneIconRenderer.BLOCK_CLASS);
        assertEquals("thaumcraft.client.renderers.block.BlockEldritchRenderer",
                ThaumcraftRunedStoneIconRenderer.BLOCK_RENDERER_CLASS);
        assertEquals(10, ThaumcraftRunedStoneIconRenderer.METADATA);
        assertEquals("thaumcraft:es_5", ThaumcraftRunedStoneIconRenderer.ICON_NAME);
        assertEquals("thaumcraft:obsidiantile",
                ThaumcraftRunedStoneIconRenderer.DEFAULT_ITEM_ICON_NAME);
        assertEquals("textures/blocks/es_5.png",
                ThaumcraftRunedStoneIconRenderer.RESOURCE_PATH);
        assertEquals(781, ThaumcraftRunedStoneIconRenderer.RESOURCE_BYTES);
        assertEquals(
                "5269a9d75f4d5e95d44efbb1537e9f66866061436cbf2e49b3a4b2844ec3298e",
                ThaumcraftRunedStoneIconRenderer.RESOURCE_SHA256);
        assertEquals(
                "4e6a681e782660126af3f84a7d3eaedb56dc0d3877a542c835c5956b49326385",
                ThaumcraftRunedStoneIconRenderer.OWNER_ARGB_SHA256);
        assertEquals(1, ThaumcraftRunedStoneIconRenderer.EXPECTED_ITEM_ICONS);
        assertEquals(256, ThaumcraftRunedStoneIconRenderer.OWNER_VISIBLE_PIXELS);
    }

    @Test
    public void verifiesFullyOpaqueOwnerPixelPayload() throws Exception {
        BufferedImage image = opaqueImage();
        byte[] bytes = encode(image);
        BufferedImage decoded = ThaumcraftRunedStoneIconRenderer.decodePinnedOwnerPng(
                bytes, bytes.length,
                ThaumcraftRunedStoneIconRenderer.sha256(bytes), argbSha256(image));
        assertEquals(16, decoded.getWidth());
        assertEquals(16, decoded.getHeight());
        assertEquals(image.getRGB(15, 15), decoded.getRGB(15, 15));

        image.setRGB(8, 8, 0x00112233);
        byte[] transparent = encode(image);
        IllegalArgumentException alpha = assertThrows(IllegalArgumentException.class,
                () -> ThaumcraftRunedStoneIconRenderer.decodePinnedOwnerPng(
                        transparent, transparent.length,
                        ThaumcraftRunedStoneIconRenderer.sha256(transparent),
                        argbSha256(image)));
        assertTrue(alpha.getMessage().contains("pixel alpha drifted"));
    }

    @Test
    public void rejectsEncodedAndDecodedDigestDrift() throws Exception {
        BufferedImage image = opaqueImage();
        byte[] bytes = encode(image);
        String encoded = ThaumcraftRunedStoneIconRenderer.sha256(bytes);
        String pixels = argbSha256(image);
        assertThrows(IllegalArgumentException.class,
                () -> ThaumcraftRunedStoneIconRenderer.decodePinnedOwnerPng(
                        bytes, bytes.length + 1, encoded, pixels));
        assertThrows(IllegalArgumentException.class,
                () -> ThaumcraftRunedStoneIconRenderer.decodePinnedOwnerPng(
                        bytes, bytes.length, zeros(), pixels));
        assertThrows(IllegalArgumentException.class,
                () -> ThaumcraftRunedStoneIconRenderer.decodePinnedOwnerPng(
                        bytes, bytes.length, encoded, zeros()));
    }

    private static BufferedImage opaqueImage() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, 0xff000000 | x << 16 | y << 8 | (x ^ y));
            }
        }
        return image;
    }

    private static byte[] encode(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new AssertionError("PNG writer unavailable");
        }
        return output.toByteArray();
    }

    private static String argbSha256(BufferedImage image) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                digest.update((byte) (argb >>> 24));
                digest.update((byte) (argb >>> 16));
                digest.update((byte) (argb >>> 8));
                digest.update((byte) argb);
            }
        }
        StringBuilder value = new StringBuilder(64);
        for (byte part : digest.digest()) {
            value.append(String.format(Locale.ROOT, "%02x", part & 0xff));
        }
        return value.toString();
    }

    private static String zeros() {
        return "0000000000000000000000000000000000000000000000000000000000000000";
    }
}
