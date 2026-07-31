package com.recipetree.jeiexport;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Repairs a macOS/OpenGL quirk seen in some translucent and custom item renderers:
 * the framebuffer receives the correct RGB values while every alpha byte remains zero.
 */
final class ImageVisibility {
    enum Result {
        VISIBLE,
        REPAIRED_HIDDEN_RGB,
        EMPTY
    }

    private ImageVisibility() {
    }

    static Result repairHiddenRgbAlpha(NativeImage image) {
        boolean hasVisibleAlpha = false;
        boolean hasHiddenRgb = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgba = image.getPixelRGBA(x, y);
                hasVisibleAlpha |= (rgba >>> 24) != 0;
                hasHiddenRgb |= (rgba & 0x00FFFFFF) != 0;
            }
        }
        Result result = resultFor(hasVisibleAlpha, hasHiddenRgb);
        if (result == Result.REPAIRED_HIDDEN_RGB) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgba = image.getPixelRGBA(x, y);
                    if ((rgba & 0x00FFFFFF) != 0) {
                        image.setPixelRGBA(x, y, withOpaqueAlpha(rgba));
                    }
                }
            }
        }
        return result;
    }

    static Result resultFor(boolean hasVisibleAlpha, boolean hasHiddenRgb) {
        if (hasVisibleAlpha) {
            return Result.VISIBLE;
        }
        return hasHiddenRgb ? Result.REPAIRED_HIDDEN_RGB : Result.EMPTY;
    }

    static int withOpaqueAlpha(int rgba) {
        return rgba | 0xFF000000;
    }
}
