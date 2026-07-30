package com.recipetree.jeiexport112;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/** Exact source/render contract for the runtime {@code minecraft:paper} calibration sentinel. */
final class RendererCalibrationValidation {
    private static final int CANONICAL_MISSING_MAGENTA_RGB = 0x00f800f8;

    private RendererCalibrationValidation() {
    }

    static Report validatePaper(int[] sourceArgb, int sourceWidth, int sourceHeight,
                                BufferedImage rendered, int expectedWidth, int expectedHeight) {
        if (sourceArgb == null) {
            throw failure("runtime paper atlas frame is null");
        }
        int expectedSourcePixels;
        try {
            expectedSourcePixels = Math.multiplyExact(sourceWidth, sourceHeight);
        } catch (ArithmeticException exception) {
            throw failure("runtime paper atlas dimensions overflow: " +
                    sourceWidth + "x" + sourceHeight);
        }
        if (sourceWidth <= 0 || sourceHeight <= 0 || sourceArgb.length != expectedSourcePixels) {
            throw failure("runtime paper atlas frame has invalid geometry: dimensions=" +
                    sourceWidth + "x" + sourceHeight + ", pixels=" + sourceArgb.length);
        }
        if (rendered == null) {
            throw failure("HEI paper renderer returned a null image");
        }
        if (rendered.getWidth() != expectedWidth || rendered.getHeight() != expectedHeight) {
            throw failure("HEI paper renderer returned " + rendered.getWidth() + "x" +
                    rendered.getHeight() + "; expected " + expectedWidth + "x" + expectedHeight);
        }

        int sourceVisible = 0;
        int sourceTransparent = 0;
        int sourceNonBlack = 0;
        boolean sourceContainsMissingMagenta = false;
        Set<Integer> sourceVisibleRgb = new HashSet<Integer>();
        for (int argb : sourceArgb) {
            int alpha = argb >>> 24;
            int rgb = argb & 0x00ffffff;
            if (alpha == 0) {
                sourceTransparent++;
                continue;
            }
            sourceVisible++;
            sourceVisibleRgb.add(rgb);
            if (rgb != 0) {
                sourceNonBlack++;
            }
            if (rgb == CANONICAL_MISSING_MAGENTA_RGB) {
                sourceContainsMissingMagenta = true;
            }
        }
        if (sourceVisible == 0 || sourceTransparent == 0 || sourceNonBlack == 0) {
            throw failure("runtime paper atlas source is not a usable calibration contract: visible=" +
                    sourceVisible + ", transparent=" + sourceTransparent +
                    ", nonBlack=" + sourceNonBlack);
        }
        if (sourceContainsMissingMagenta) {
            throw failure("runtime paper atlas source contains Minecraft's canonical missing-texture magenta");
        }

        int renderedVisible = 0;
        int renderedTransparent = 0;
        int renderedNonBlack = 0;
        int matchingSourceColorPixels = 0;
        int unexpectedMissingMagentaPixels = 0;
        Set<Integer> renderedVisibleRgb = new HashSet<Integer>();
        for (int y = 0; y < rendered.getHeight(); y++) {
            for (int x = 0; x < rendered.getWidth(); x++) {
                int argb = rendered.getRGB(x, y);
                int alpha = argb >>> 24;
                int rgb = argb & 0x00ffffff;
                if (alpha == 0) {
                    renderedTransparent++;
                    continue;
                }
                renderedVisible++;
                renderedVisibleRgb.add(rgb);
                if (rgb != 0) {
                    renderedNonBlack++;
                }
                if (sourceVisibleRgb.contains(rgb)) {
                    matchingSourceColorPixels++;
                }
                if (rgb == CANONICAL_MISSING_MAGENTA_RGB) {
                    unexpectedMissingMagentaPixels++;
                }
            }
        }

        if (renderedVisible == 0) {
            throw failure("HEI paper calibration render is fully transparent");
        }
        if (renderedTransparent == 0) {
            throw failure("HEI paper calibration render is fully opaque; this is the signature of " +
                    "a stale/zero texture binding on the generated paper model");
        }
        if (renderedNonBlack == 0) {
            throw failure("HEI paper calibration render has no visible non-black pixel; refusing " +
                    "the opaque-black texture-binding degradation");
        }
        if (matchingSourceColorPixels == 0) {
            throw failure("HEI paper calibration render contains no visible RGB value from its " +
                    "runtime atlas source; the renderer sampled a different texture binding");
        }
        if (unexpectedMissingMagentaPixels > 0) {
            throw failure("HEI paper calibration render contains " +
                    unexpectedMissingMagentaPixels +
                    " canonical missing-texture magenta pixels absent from its runtime source");
        }

        return new Report(
                sourceWidth,
                sourceHeight,
                sourceVisible,
                sourceTransparent,
                sourceVisibleRgb.size(),
                rendered.getWidth(),
                rendered.getHeight(),
                renderedVisible,
                renderedTransparent,
                renderedVisibleRgb.size(),
                matchingSourceColorPixels
        );
    }

    private static IllegalStateException failure(String detail) {
        return new IllegalStateException("ITEM_RENDER_CALIBRATION_FAILED: " + detail);
    }

    static final class Report {
        final int sourceWidth;
        final int sourceHeight;
        final int sourceVisible;
        final int sourceTransparent;
        final int sourceColors;
        final int renderedWidth;
        final int renderedHeight;
        final int renderedVisible;
        final int renderedTransparent;
        final int renderedColors;
        final int matchingSourceColorPixels;

        Report(int sourceWidth, int sourceHeight, int sourceVisible, int sourceTransparent,
               int sourceColors, int renderedWidth, int renderedHeight, int renderedVisible,
               int renderedTransparent, int renderedColors, int matchingSourceColorPixels) {
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.sourceVisible = sourceVisible;
            this.sourceTransparent = sourceTransparent;
            this.sourceColors = sourceColors;
            this.renderedWidth = renderedWidth;
            this.renderedHeight = renderedHeight;
            this.renderedVisible = renderedVisible;
            this.renderedTransparent = renderedTransparent;
            this.renderedColors = renderedColors;
            this.matchingSourceColorPixels = matchingSourceColorPixels;
        }
    }
}
