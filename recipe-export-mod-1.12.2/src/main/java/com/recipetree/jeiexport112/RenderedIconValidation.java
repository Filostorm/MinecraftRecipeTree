package com.recipetree.jeiexport112;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;

/** Validates whether an ingredient render can contribute any visible pixels. */
final class RenderedIconValidation {
    static final String FULLY_TRANSPARENT = "rendered image is fully transparent";

    private RenderedIconValidation() {
    }

    /**
     * Returns a diagnostic reason when an icon is unusable, or {@code null} when it is visible.
     * A uniform image is valid: solid-color fluid swatches and deliberately translucent blocks
     * are real ingredient renders. Only an image whose alpha is exactly zero everywhere is
     * mathematically invisible and should defer to the viewer's named fallback.
     */
    static String unusableReason(BufferedImage image) {
        if (image == null) {
            return "renderer returned a null image";
        }
        return hasVisiblePixel(image) ? null : FULLY_TRANSPARENT;
    }

    private static boolean hasVisiblePixel(BufferedImage image) {
        Raster raster = image.getRaster();
        DataBuffer buffer = raster.getDataBuffer();
        if (image.getType() == BufferedImage.TYPE_INT_ARGB &&
                buffer instanceof DataBufferInt &&
                raster.getSampleModel() instanceof SinglePixelPackedSampleModel) {
            return hasVisibleIntArgbPixel(
                    image, raster, (DataBufferInt) buffer,
                    (SinglePixelPackedSampleModel) raster.getSampleModel());
        }

        // This is not used by OffscreenRenderer today, but keeps the validity rule correct if its
        // BufferedImage representation changes. No image-sized copy is allocated.
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasVisibleIntArgbPixel(BufferedImage image, Raster raster,
                                                   DataBufferInt buffer,
                                                   SinglePixelPackedSampleModel sampleModel) {
        int[] pixels = buffer.getData();
        int stride = sampleModel.getScanlineStride();
        int row = buffer.getOffset() +
                (raster.getMinY() - raster.getSampleModelTranslateY()) * stride +
                (raster.getMinX() - raster.getSampleModelTranslateX());
        for (int y = 0; y < image.getHeight(); y++, row += stride) {
            int end = row + image.getWidth();
            for (int index = row; index < end; index++) {
                if ((pixels[index] >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
