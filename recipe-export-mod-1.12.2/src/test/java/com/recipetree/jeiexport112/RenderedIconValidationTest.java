package com.recipetree.jeiexport112;

import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class RenderedIconValidationTest {
    @Test
    public void rejectsExactlyFullyTransparentArgbRender() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

        assertEquals(RenderedIconValidation.FULLY_TRANSPARENT,
                RenderedIconValidation.unusableReason(image));
    }

    @Test
    public void acceptsSingleBarelyVisiblePixel() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(37, 19, 0x01000000);

        assertNull(RenderedIconValidation.unusableReason(image));
    }

    @Test
    public void acceptsOpaqueUniformSwatch() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0xff000000);

        assertNull(RenderedIconValidation.unusableReason(image));
    }

    @Test
    public void acceptsTranslucentUniformSwatch() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0x03202020);

        assertNull(RenderedIconValidation.unusableReason(image));
    }

    @Test
    public void appliesSameRuleToNonArgbBackingImages() {
        BufferedImage transparent = new BufferedImage(16, 16, BufferedImage.TYPE_4BYTE_ABGR);
        BufferedImage visible = new BufferedImage(16, 16, BufferedImage.TYPE_4BYTE_ABGR);
        visible.setRGB(0, 0, 0x01010203);

        assertEquals(RenderedIconValidation.FULLY_TRANSPARENT,
                RenderedIconValidation.unusableReason(transparent));
        assertNull(RenderedIconValidation.unusableReason(visible));
    }

    private static void fill(BufferedImage image, int argb) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, argb);
            }
        }
    }
}
