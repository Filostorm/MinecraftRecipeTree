package com.recipetree.neiexport1710;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModernMarkingsCrossingIconRendererTest {
    @Test
    public void pinsExactlySixOwnerAssets() {
        Map<String, ModernMarkingsCrossingIconRenderer.AssetPin> pins =
                ModernMarkingsCrossingIconRenderer.assetPinsForTest();
        assertEquals(6, pins.size());
        assertArrayEquals(StackIdentity.MODERN_MARKINGS_CROSSING_REGISTRY_IDS,
                pins.keySet().toArray(new String[pins.size()]));
        assertPin(pins, "blue",
                "7d365900c2c3a0c9eac463c0783faa72478e12ac0b28346f30bc80aa3a64eb5b",
                0xff031e88);
        assertPin(pins, "green",
                "62da7a16181e4fb736dd72235ee2df7ca1f79721bd2f3bfb973451a0cf59c0e8",
                0xff038803);
        assertPin(pins, "orange",
                "43530736d736528dda12d3bff105007a836864548c72a782f2f8ffed311fbedc",
                0xffff5f00);
        assertPin(pins, "red",
                "cdf8266c0375ef0fd774d89d63833e81fa8863b145cb8802bc01541280454c5e",
                0xffde0000);
        assertPin(pins, "white",
                "e3a6b9a404c035d20607b883d09a3a60b13700b67649bcae6b3f720456d3101e",
                0xffffffff);
        assertPin(pins, "yellow",
                "a672e92a42dd52b77b30cd34bd2951fce06ac6b2182075893068524ac9a2acbe",
                0xffffb600);
    }

    @Test
    public void registryPredicateIsExact() {
        for (String registryId : StackIdentity.MODERN_MARKINGS_CROSSING_REGISTRY_IDS) {
            assertTrue(StackIdentity.isPinnedModernMarkingsCrossingRegistryId(registryId));
        }
        assertFalse(StackIdentity.isPinnedModernMarkingsCrossingRegistryId(
                "modernmarkings:tile.floor_marking_blue_singleline"));
        assertFalse(StackIdentity.isPinnedModernMarkingsCrossingRegistryId(
                "ModernMarkings:tile.floor_marking_blue_crossing"));
        assertFalse(StackIdentity.isPinnedModernMarkingsCrossingRegistryId(null));
    }

    @Test
    public void verifiesFourCornerOwnerPixelTopology() throws Exception {
        int color = 0xff031e88;
        BufferedImage image = fourCornerImage(color);
        byte[] bytes = encode(image);
        String sha256 = ModernMarkingsCrossingIconRenderer.sha256(bytes);

        BufferedImage decoded = ModernMarkingsCrossingIconRenderer.decodePinnedOwnerPng(
                bytes, bytes.length, sha256, color, "test");
        assertEquals(16, decoded.getWidth());
        assertEquals(color, decoded.getRGB(15, 15));

        image.setRGB(8, 8, color);
        byte[] drifted = encode(image);
        IllegalArgumentException topology = assertThrows(IllegalArgumentException.class,
                () -> ModernMarkingsCrossingIconRenderer.decodePinnedOwnerPng(
                        drifted, drifted.length,
                        ModernMarkingsCrossingIconRenderer.sha256(drifted), color, "test"));
        assertTrue(topology.getMessage().contains("pixel topology drifted"));
    }

    @Test
    public void rejectsDigestAndLengthDrift() throws Exception {
        int color = 0xffffb600;
        byte[] bytes = encode(fourCornerImage(color));
        assertThrows(IllegalArgumentException.class,
                () -> ModernMarkingsCrossingIconRenderer.decodePinnedOwnerPng(
                        bytes, bytes.length + 1,
                        ModernMarkingsCrossingIconRenderer.sha256(bytes), color, "test"));
        assertThrows(IllegalArgumentException.class,
                () -> ModernMarkingsCrossingIconRenderer.decodePinnedOwnerPng(
                        bytes, bytes.length,
                        "0000000000000000000000000000000000000000000000000000000000000000",
                        color, "test"));
    }

    private static void assertPin(
            Map<String, ModernMarkingsCrossingIconRenderer.AssetPin> pins,
            String color, String sha256, int argb) {
        String registryId = "modernmarkings:tile.floor_marking_" + color + "_crossing";
        ModernMarkingsCrossingIconRenderer.AssetPin pin = pins.get(registryId);
        assertEquals(registryId, pin.registryId);
        assertEquals("modernmarkings:marking_" + color + "_crossing", pin.iconName);
        assertEquals("textures/blocks/marking_" + color + "_crossing.png",
                pin.resourcePath);
        assertEquals(sha256, pin.sha256);
        assertEquals(argb, pin.opaqueArgb);
    }

    private static BufferedImage fourCornerImage(int argb) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, argb);
        image.setRGB(15, 0, argb);
        image.setRGB(0, 15, argb);
        image.setRGB(15, 15, argb);
        return image;
    }

    private static byte[] encode(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new AssertionError("PNG writer unavailable");
        }
        return output.toByteArray();
    }
}
