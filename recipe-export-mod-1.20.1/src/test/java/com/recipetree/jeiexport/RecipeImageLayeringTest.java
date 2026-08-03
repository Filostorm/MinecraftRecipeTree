package com.recipetree.jeiexport;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RecipeImageLayeringTest {
    @Test
    void differencePreservesChangedPixelsAndClearsSharedPixels() {
        try (NativeImage base = new NativeImage(2, 1, true);
             NativeImage complete = new NativeImage(2, 1, true)) {
            base.setPixelRGBA(0, 0, 0xFF112233);
            base.setPixelRGBA(1, 0, 0xFF445566);
            complete.setPixelRGBA(0, 0, 0xFF112233);
            complete.setPixelRGBA(1, 0, 0xFF778899);

            try (NativeImage overlay = RecipeImageLayering.difference(base, complete).overlay()) {
                assertEquals(0, overlay.getPixelRGBA(0, 0));
                assertEquals(complete.getPixelRGBA(1, 0), overlay.getPixelRGBA(1, 0));
            }
        }
    }

    @Test
    void identicalImagesRetainOneVisibleValidationPixel() {
        try (NativeImage base = new NativeImage(1, 1, true);
             NativeImage complete = new NativeImage(1, 1, true)) {
            base.setPixelRGBA(0, 0, 0xFF112233);
            complete.copyFrom(base);

            try (NativeImage overlay = RecipeImageLayering.difference(base, complete).overlay()) {
                assertEquals(complete.getPixelRGBA(0, 0), overlay.getPixelRGBA(0, 0));
            }
        }
    }
}
