package com.recipetree.neiexport1710;

import java.awt.image.BufferedImage;

final class RenderedImageValidation {
    private RenderedImageValidation() {
    }

    static String unusableReason(BufferedImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return "renderer returned a null/empty image";
        }
        boolean visible = false;
        for (int y = 0; y < image.getHeight() && !visible; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xff) != 0) {
                    visible = true;
                    break;
                }
            }
        }
        return visible ? null : "rendered image is fully transparent";
    }
}
