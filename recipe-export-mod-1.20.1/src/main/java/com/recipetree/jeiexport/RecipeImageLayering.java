package com.recipetree.jeiexport;

import com.mojang.blaze3d.platform.NativeImage;

/** Exact pixel-difference layers for JEI recipe screenshots. */
final class RecipeImageLayering {
    /** Avoid a second image request unless at least this fraction of the pixels is shared. */
    static final double MINIMUM_SHARED_PIXEL_RATIO = 0.20;

    record Result(NativeImage overlay, int sharedPixels, int totalPixels) {
        double sharedPixelRatio() {
            return totalPixels == 0 ? 0.0 : (double) sharedPixels / totalPixels;
        }
    }

    private RecipeImageLayering() {
    }

    static Result difference(NativeImage base, NativeImage complete) {
        if (base.getWidth() != complete.getWidth() || base.getHeight() != complete.getHeight()) {
            throw new IllegalArgumentException("Recipe base and complete image dimensions must match");
        }
        NativeImage overlay = new NativeImage(complete.getWidth(), complete.getHeight(), true);
        int shared = 0;
        for (int y = 0; y < complete.getHeight(); y++) {
            for (int x = 0; x < complete.getWidth(); x++) {
                int pixel = complete.getPixelRGBA(x, y);
                if (pixel == base.getPixelRGBA(x, y)) {
                    overlay.setPixelRGBA(x, y, 0);
                    shared++;
                } else {
                    overlay.setPixelRGBA(x, y, pixel);
                }
            }
        }
        // The raw-export validator intentionally rejects fully transparent images. Preserve one
        // identical base pixel in the overlay when the first recipe establishes a new base.
        if (shared == complete.getWidth() * complete.getHeight() && shared > 0) {
            overlay.setPixelRGBA(0, 0, complete.getPixelRGBA(0, 0));
            shared--;
        }
        return new Result(overlay, shared, complete.getWidth() * complete.getHeight());
    }

    static NativeImage copy(NativeImage source) {
        NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), true);
        copy.copyFrom(source);
        return copy;
    }
}
