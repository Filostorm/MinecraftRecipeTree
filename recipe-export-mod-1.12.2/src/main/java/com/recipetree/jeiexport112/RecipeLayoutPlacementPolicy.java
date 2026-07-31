package com.recipetree.jeiexport112;

/**
 * Pure coordinate policy for drawing HEI's externally-created recipe layouts into the exporter
 * framebuffer. Keeping this independent from Minecraft and HEI types makes every compatibility
 * boundary deterministic and unit-testable.
 */
final class RecipeLayoutPlacementPolicy {
    static final String MULTIBLOCKED_CATEGORY =
            "com.cleanroommc.multiblocked.jei.recipeppage.RecipeMapCategory";
    static final String MULTIBLOCKED_WRAPPER =
            "com.cleanroommc.multiblocked.jei.recipeppage.RecipeWrapper";
    static final String MULTIBLOCKED_UID_PREFIX = "multiblocked:";
    static final int MULTIBLOCKED_WIDTH = 176;
    static final int MULTIBLOCKED_HEIGHT = 84;

    private RecipeLayoutPlacementPolicy() {
    }

    enum Kind {
        DEFAULT("default"),
        MULTIBLOCKED_0_8_SCREEN_CENTERED_PARENT(
                "multiblocked08ScreenCenteredParent");

        final String diagnosticName;

        Kind(String diagnosticName) {
            this.diagnosticName = diagnosticName;
        }
    }

    /**
     * Plans two coordinate frames: HEI's layout position and the exporter-owned model-view
     * translation. Their sum must always place the recipe background at {@code padding}.
     */
    static Placement plan(String uid, String categoryClass, String wrapperClass,
                          int backgroundWidth, int backgroundHeight,
                          int scaledScreenWidth, int padding) {
        require(backgroundWidth > 0 && backgroundHeight > 0,
                "recipe background must have positive dimensions, found " +
                        backgroundWidth + "x" + backgroundHeight);
        require(scaledScreenWidth > 0,
                "scaled screen width must be positive, found " + scaledScreenWidth);
        require(padding >= 0, "framebuffer padding must be nonnegative, found " + padding);

        Kind kind = classify(uid, categoryClass, wrapperClass);
        if (kind == Kind.DEFAULT) {
            if (isConcreteMultiblockedUid(uid) &&
                    backgroundWidth == MULTIBLOCKED_WIDTH &&
                    backgroundHeight == MULTIBLOCKED_HEIGHT) {
                throw violation("Multiblocked 176x84 recipe-map shape has unaudited identity: " +
                        "uid=" + quoted(uid) + ", categoryClass=" + quoted(categoryClass) +
                        ", wrapperClass=" + quoted(wrapperClass));
            }
            return placement(kind, 0, 0, padding, padding, padding);
        }

        require(backgroundWidth == MULTIBLOCKED_WIDTH &&
                        backgroundHeight == MULTIBLOCKED_HEIGHT,
                "Multiblocked 0.8.0 recipe background changed from " +
                        MULTIBLOCKED_WIDTH + "x" + MULTIBLOCKED_HEIGHT + " to " +
                        backgroundWidth + "x" + backgroundHeight);
        require(scaledScreenWidth >= backgroundWidth,
                "Multiblocked 0.8.0 scaled screen width " + scaledScreenWidth +
                        " is smaller than its " + backgroundWidth + "-pixel recipe UI");

        // ModularUI.getGuiLeft() is exactly (screenWidth - uiWidth) / 2 in 0.8.0.
        int cachedParentX = (scaledScreenWidth - backgroundWidth) / 2;
        return placement(kind, cachedParentX, 0,
                padding - cachedParentX, padding, padding);
    }

    static Kind classify(String uid, String categoryClass, String wrapperClass) {
        boolean categoryMatches = MULTIBLOCKED_CATEGORY.equals(categoryClass);
        boolean wrapperMatches = MULTIBLOCKED_WRAPPER.equals(wrapperClass);
        if (categoryMatches && wrapperMatches) {
            require(isConcreteMultiblockedUid(uid),
                    "Multiblocked 0.8.0 category/wrapper pair has unexpected uid " + quoted(uid));
            return Kind.MULTIBLOCKED_0_8_SCREEN_CENTERED_PARENT;
        }
        if (categoryMatches || wrapperMatches) {
            throw violation("Multiblocked 0.8.0 layout identity drift: uid=" + quoted(uid) +
                    ", categoryClass=" + quoted(categoryClass) +
                    ", wrapperClass=" + quoted(wrapperClass));
        }
        return Kind.DEFAULT;
    }

    private static boolean isConcreteMultiblockedUid(String uid) {
        return uid != null && uid.startsWith(MULTIBLOCKED_UID_PREFIX) &&
                uid.length() > MULTIBLOCKED_UID_PREFIX.length();
    }

    private static Placement placement(Kind kind, int layoutX, int layoutY,
                                       int translateX, int translateY, int padding) {
        Placement result = new Placement(kind, layoutX, layoutY, translateX, translateY);
        require(result.layoutX + result.translateX == padding,
                "horizontal placement invariant failed for " + kind);
        require(result.layoutY + result.translateY == padding,
                "vertical placement invariant failed for " + kind);
        return result;
    }

    private static String quoted(String value) {
        return value == null ? "<null>" : '"' + value + '"';
    }

    private static void require(boolean condition, String detail) {
        if (!condition) {
            throw violation(detail);
        }
    }

    private static IllegalStateException violation(String detail) {
        return new IllegalStateException("RECIPE_LAYOUT_PLACEMENT_DRIFT: " + detail);
    }

    static final class Placement {
        final Kind kind;
        final int layoutX;
        final int layoutY;
        final int translateX;
        final int translateY;

        Placement(Kind kind, int layoutX, int layoutY, int translateX, int translateY) {
            this.kind = kind;
            this.layoutX = layoutX;
            this.layoutY = layoutY;
            this.translateX = translateX;
            this.translateY = translateY;
        }

        boolean repositionsLayout() {
            return layoutX != 0 || layoutY != 0;
        }
    }
}
