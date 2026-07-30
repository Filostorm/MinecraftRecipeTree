package com.recipetree.jeiexport112;

import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class RendererCalibrationValidationTest {
    private static final int[] PAPER_SOURCE = {
            0x00000000, 0xffd6d6d6,
            0xffeaeaea, 0xff515151
    };

    @Test
    public void acceptsVisiblePaperRenderThatRetainsRuntimeSourceColorsAndTransparency() {
        BufferedImage rendered = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        rendered.setRGB(0, 0, 0x00000000);
        rendered.setRGB(1, 0, 0xffd6d6d6);
        rendered.setRGB(0, 1, 0xffeaeaea);
        rendered.setRGB(1, 1, 0xff515151);

        RendererCalibrationValidation.Report report =
                RendererCalibrationValidation.validatePaper(
                        PAPER_SOURCE, 2, 2, rendered, 2, 2);

        assertEquals(3, report.sourceVisible);
        assertEquals(1, report.sourceTransparent);
        assertEquals(3, report.renderedVisible);
        assertEquals(1, report.renderedTransparent);
        assertEquals(3, report.matchingSourceColorPixels);
    }

    @Test
    public void rejectsTheObservedUniformOpaqueBlackRegression() {
        BufferedImage rendered = solid(16, 16, 0xff000000);

        assertCalibrationFailure(rendered, "fully opaque");
    }

    @Test
    public void rejectsBlackSilhouetteEvenWhenTheClearPixelsRemainTransparent() {
        BufferedImage rendered = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        rendered.setRGB(1, 0, 0xff000000);

        assertCalibrationFailure(rendered, "no visible non-black pixel");
    }

    @Test
    public void rejectsAVisibleButDifferentStaleTexture() {
        BufferedImage rendered = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        rendered.setRGB(1, 0, 0xff123456);

        assertCalibrationFailure(rendered, "no visible RGB value from its runtime atlas source");
    }

    @Test
    public void rejectsCanonicalMissingTextureMagentaAbsentFromPaperSource() {
        BufferedImage rendered = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        rendered.setRGB(1, 0, 0xffd6d6d6);
        rendered.setRGB(0, 1, 0xfff800f8);

        assertCalibrationFailure(rendered, "canonical missing-texture magenta pixels");
    }

    @Test
    public void rejectsMalformedOrUnusableRuntimeSourceInsteadOfSkippingCalibration() {
        BufferedImage rendered = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        rendered.setRGB(1, 0, 0xffffffff);

        try {
            RendererCalibrationValidation.validatePaper(
                    new int[]{0xff000000, 0xff000000}, 2, 2,
                    rendered, 2, 2);
            fail("Expected malformed source geometry to fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("invalid geometry"));
        }
    }

    private static void assertCalibrationFailure(BufferedImage rendered, String detail) {
        try {
            RendererCalibrationValidation.validatePaper(
                    PAPER_SOURCE, 2, 2, rendered,
                    rendered.getWidth(), rendered.getHeight());
            fail("Expected paper renderer calibration to fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().startsWith("ITEM_RENDER_CALIBRATION_FAILED:"));
            assertTrue(expected.getMessage(), expected.getMessage().contains(detail));
        }
    }

    private static BufferedImage solid(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }
}
