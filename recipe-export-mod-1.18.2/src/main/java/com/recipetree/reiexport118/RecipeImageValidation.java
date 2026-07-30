package com.recipetree.reiexport118;

import com.mojang.blaze3d.platform.NativeImage;

/** Fail-closed pixel validation for recipe layouts rendered over a known attachment clear. */
final class RecipeImageValidation {
    private RecipeImageValidation() {
    }

    static long countPixelsDifferentFrom(NativeImage image, int clearArgb) {
        return countPixelsDifferentFrom(image, clearArgb, Long.MAX_VALUE);
    }

    static long countPixelsDifferentFrom(NativeImage image, int clearArgb, long stopAfter) {
        if (stopAfter < 1) {
            throw new IllegalArgumentException("Pixel scan stopAfter must be positive");
        }
        int clearRgba = argbToNativeRgba(clearArgb);
        long different = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getPixelRGBA(x, y) != clearRgba) {
                    different++;
                    if (different >= stopAfter) {
                        return different;
                    }
                }
            }
        }
        return different;
    }

    static long minimumNativePixels(int scale) {
        if (scale < 1) {
            throw new IllegalArgumentException("Recipe scale must be positive");
        }
        return Math.multiplyExact((long) scale, scale);
    }

    static int argbToNativeRgba(int argb) {
        int alpha = (argb >>> 24) & 0xff;
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        // NativeImage stores RGBA bytes in native order, represented as ABGR in a Java int.
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }
}
