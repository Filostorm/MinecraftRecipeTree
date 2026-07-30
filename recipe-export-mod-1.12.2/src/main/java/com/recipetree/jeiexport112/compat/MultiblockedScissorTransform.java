package com.recipetree.jeiexport112.compat;

/**
 * Pure coordinate conversion for Multiblocked 0.8.0's raw framebuffer scissor calls.
 *
 * <p>Multiblocked converts GUI coordinates to the live window's physical-pixel coordinate
 * system before calling {@code glScissor}. OpenGL scissor rectangles do not participate in the
 * model-view transform, so an offscreen recipe capture must explicitly convert those physical
 * coordinates into the export framebuffer's coordinate system.</p>
 */
final class MultiblockedScissorTransform {
    private MultiblockedScissorTransform() {
    }

    static Box map(int rawX, int rawY, int rawWidth, int rawHeight,
                   int liveGuiScale, int liveScaledHeight,
                   int recipeScale, int targetLogicalHeight,
                   int translateX, int translateY) {
        require(liveGuiScale > 0,
                "live GUI scale must be positive, found " + liveGuiScale);
        require(liveScaledHeight > 0,
                "live scaled height must be positive, found " + liveScaledHeight);
        require(recipeScale > 0,
                "recipe scale must be positive, found " + recipeScale);
        require(targetLogicalHeight > 0,
                "target logical height must be positive, found " + targetLogicalHeight);
        require(rawWidth >= 0 && rawHeight >= 0,
                "raw scissor dimensions must be nonnegative, found " +
                        rawWidth + "x" + rawHeight);

        int logicalX = exactQuotient(rawX, liveGuiScale, "raw x");
        int liveBottom = exactQuotient(rawY, liveGuiScale, "raw y");
        int logicalWidth = exactQuotient(rawWidth, liveGuiScale, "raw width");
        int logicalHeight = exactQuotient(rawHeight, liveGuiScale, "raw height");
        int logicalY = exactInt(
                (long) liveScaledHeight - liveBottom - logicalHeight,
                "reconstructed logical y"
        );

        int mappedX = exactInt(
                ((long) logicalX + translateX) * recipeScale,
                "mapped x"
        );
        int mappedY = exactInt(
                ((long) targetLogicalHeight - ((long) logicalY + translateY) -
                        logicalHeight) * recipeScale,
                "mapped y"
        );
        int mappedWidth = exactInt((long) logicalWidth * recipeScale, "mapped width");
        int mappedHeight = exactInt((long) logicalHeight * recipeScale, "mapped height");
        require(mappedWidth >= 0 && mappedHeight >= 0,
                "mapped scissor dimensions must be nonnegative, found " +
                        mappedWidth + "x" + mappedHeight);
        return new Box(mappedX, mappedY, mappedWidth, mappedHeight);
    }

    private static int exactQuotient(int value, int divisor, String label) {
        require(value % divisor == 0,
                label + " " + value + " is not divisible by live GUI scale " + divisor);
        return value / divisor;
    }

    private static int exactInt(long value, String label) {
        require(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE,
                label + " overflows a signed 32-bit GL coordinate: " + value);
        return (int) value;
    }

    private static void require(boolean condition, String detail) {
        if (!condition) {
            throw new IllegalStateException(
                    "MULTIBLOCKED_SCISSOR_DRIFT: " + detail +
                            "; refusing an unvalidated offscreen clip rectangle."
            );
        }
    }

    static final class Box {
        final int x;
        final int y;
        final int width;
        final int height;

        Box(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
