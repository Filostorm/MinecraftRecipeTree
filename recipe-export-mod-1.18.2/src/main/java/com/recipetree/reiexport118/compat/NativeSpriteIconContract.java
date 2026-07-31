package com.recipetree.reiexport118.compat;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Fail-closed contract for the exact MM2 fluid and Mekanism gas icon renderers whose native
 * off-screen output can be transparent or quantity-clipped in a catalog-sized capture.
 */
public final class NativeSpriteIconContract {
    public static final int ICON_SIZE = 16;

    public static final String MINECRAFT_VERSION = "1.18.2";
    public static final String FORGE_VERSION = "40.2.17";
    public static final String REI_VERSION = "8.4.778";
    public static final String REI_JEI_COMPAT_VERSION = "8.0.89";
    public static final String ARCHITECTURY_VERSION = "4.12.94";
    public static final String MEKANISM_VERSION = "10.2.5";

    public static final String STANDARD_FLUID_TYPE_ID = "minecraft:fluid";
    public static final String STANDARD_FLUID_VALUE_CLASS = "dev.architectury.fluid.FluidStack";
    public static final String MEKANISM_GAS_TYPE_ID =
            "mekanism:jei_plugin_jei_compat_gasstack";
    public static final String MEKANISM_GAS_VALUE_CLASS =
            "mekanism.api.chemical.gas.GasStack";

    public static final String REI_FLUID_RENDERER_CLASS =
            "me.shedaniel.rei.plugin.client.entry.FluidEntryDefinition$FluidEntryRenderer";
    public static final String JEI_RENDERER_WRAPPER_CLASS =
            "me.shedaniel.rei.jeicompat.wrap.JEIEntryDefinition$Renderer";
    public static final String JEI_FLUID_RENDERER_CLASS =
            "me.shedaniel.rei.jeicompat.imitator.JEIFluidStackRendererImitator";
    public static final String MEKANISM_CHEMICAL_RENDERER_CLASS =
            "mekanism.client.jei.ChemicalStackRenderer";

    public static final String REI_FLUID_RENDERER_RESOURCE =
            "me/shedaniel/rei/plugin/client/entry/FluidEntryDefinition$FluidEntryRenderer.class";
    public static final String REI_FLUID_RENDERER_SHA256 =
            "1174cd441255379f1ea580abdee37f59e28f3ea7acf31306dcf1b9ddc634bb0f";
    public static final String JEI_RENDERER_WRAPPER_RESOURCE =
            "me/shedaniel/rei/jeicompat/wrap/JEIEntryDefinition$Renderer.class";
    public static final String JEI_RENDERER_WRAPPER_SHA256 =
            "ac08206a5f906d030c7a457eeab3b78102b872d66256a601f26354137d771008";
    public static final String JEI_FLUID_RENDERER_RESOURCE =
            "me/shedaniel/rei/jeicompat/imitator/JEIFluidStackRendererImitator.class";
    public static final String JEI_FLUID_RENDERER_SHA256 =
            "e0e1b1b9e9f64c1a35c77bc6f4afaeca9cb3e5df154d813cab511e00f7c17b8e";
    public static final String ARCHITECTURY_FLUID_STACK_RESOURCE =
            "dev/architectury/fluid/FluidStack.class";
    public static final String ARCHITECTURY_FLUID_STACK_SHA256 =
            "0e6a6d942c46945e9c67e91a472c4c5adae19c18dd76545773b8d8e6c1351065";
    public static final String ARCHITECTURY_FLUID_HOOKS_RESOURCE =
            "dev/architectury/hooks/fluid/FluidStackHooks.class";
    public static final String ARCHITECTURY_FLUID_HOOKS_SHA256 =
            "7a4f54f6407ad1ea056115ca04eeb37437228582fc3a65ce826ed1a85ec1a061";
    public static final String MEKANISM_GAS_STACK_RESOURCE =
            "mekanism/api/chemical/gas/GasStack.class";
    public static final String MEKANISM_GAS_STACK_SHA256 =
            "18f7e26c464c065fd0a7229dace89bd521dc16c763bb7d9307fe69e39301555c";
    public static final String MEKANISM_CHEMICAL_RESOURCE =
            "mekanism/api/chemical/Chemical.class";
    public static final String MEKANISM_CHEMICAL_SHA256 =
            "7a0ba0ee16ede629c44b03073db1cf8f0440e3cd2494eced76d40185dc4249b6";
    public static final String MEKANISM_CHEMICAL_RENDERER_RESOURCE =
            "mekanism/client/jei/ChemicalStackRenderer.class";
    public static final String MEKANISM_CHEMICAL_RENDERER_SHA256 =
            "3387f09fc35c4a832ea046982ce299c7425b19516453b7ef9c81eb1d057e141e";

    public enum Kind {
        NONE,
        STANDARD_FLUID,
        MEKANISM_GAS
    }

    public enum CorrectionReason {
        ALPHA_COVERAGE_RESTORE,
        CANONICAL_ANIMATION_FRAME
    }

    public record DeclaredFrame(int index, int duration) {
    }

    public record FrameSelection(
            int firstPhysicalFrame,
            int runtimeFrameCount,
            int physicalFrameCount,
            boolean explicitFrameSequence,
            boolean interpolatedFrames
    ) {
    }

    public record CorrectionEvidence(
            Kind kind,
            CorrectionReason reason,
            String entryId,
            String typeId,
            String outerRendererClass,
            String innerRendererClass,
            String spriteId,
            int tint,
            String textureResource,
            String resourceSource,
            String sourceSha256,
            FrameSelection frameSelection,
            int nativeVisiblePixels,
            int correctedVisiblePixels,
            int differingPixels
    ) {
        public CorrectionEvidence {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(entryId, "entryId");
            Objects.requireNonNull(typeId, "typeId");
            Objects.requireNonNull(outerRendererClass, "outerRendererClass");
            Objects.requireNonNull(spriteId, "spriteId");
            Objects.requireNonNull(textureResource, "textureResource");
            Objects.requireNonNull(resourceSource, "resourceSource");
            Objects.requireNonNull(sourceSha256, "sourceSha256");
            Objects.requireNonNull(frameSelection, "frameSelection");
            if (kind == Kind.NONE) {
                throw new IllegalArgumentException("Correction evidence cannot use kind=NONE");
            }
            requireVisiblePixelCount("nativeVisiblePixels", nativeVisiblePixels, true);
            requireVisiblePixelCount("correctedVisiblePixels", correctedVisiblePixels, false);
            requirePixelDifferenceCount(differingPixels);
            if (reason == CorrectionReason.ALPHA_COVERAGE_RESTORE
                    && correctedVisiblePixels <= nativeVisiblePixels) {
                throw new IllegalArgumentException(
                        "Alpha-coverage correction evidence must increase coverage: native="
                                + nativeVisiblePixels + ", corrected=" + correctedVisiblePixels);
            }
            if (reason == CorrectionReason.CANONICAL_ANIMATION_FRAME
                    && (frameSelection.runtimeFrameCount() <= 1 || differingPixels == 0)) {
                throw new IllegalArgumentException(
                        "Animation-frame correction evidence requires multiple runtime frames and "
                                + "at least one changed pixel: runtimeFrameCount="
                                + frameSelection.runtimeFrameCount()
                                + ", differingPixels=" + differingPixels);
            }
        }

        public String warningMessage() {
            String action = reason == CorrectionReason.ALPHA_COVERAGE_RESTORE
                    ? "Corrected transparent or quantity-clipped native REI catalog icon"
                    : "Canonicalized animated native REI catalog icon to its first physical source keyframe";
            return action + ": entry=" + entryId
                    + ", type=" + typeId
                    + ", kind=" + kind
                    + ", reason=" + reason
                    + ", outerRenderer=" + outerRendererClass
                    + ", innerRenderer=" + displayInnerRenderer(innerRendererClass)
                    + ", sprite=" + spriteId
                    + ", tint=" + String.format(Locale.ROOT, "0x%08X", tint)
                    + ", textureResource=" + textureResource
                    + ", resourceSource=" + resourceSource
                    + ", sourceSha256=" + sourceSha256
                    + ", firstPhysicalFrame=" + frameSelection.firstPhysicalFrame()
                    + ", runtimeFrameCount=" + frameSelection.runtimeFrameCount()
                    + ", physicalFrameCount=" + frameSelection.physicalFrameCount()
                    + ", explicitFrameSequence=" + frameSelection.explicitFrameSequence()
                    + ", interpolatedFrames=" + frameSelection.interpolatedFrames()
                    + ", nativeVisiblePixels=" + nativeVisiblePixels
                    + ", correctedVisiblePixels=" + correctedVisiblePixels
                    + ", differingPixels=" + differingPixels
                    + "; used exact runtime sprite pixels with RGB tint and preserved source alpha"
                    + "; selected the exact first physical source keyframe"
                    + "; interpolation metadata was recorded but no interpolated frame was synthesized"
                    + "; no placeholder, scaling, or generated replacement art";
        }
    }

    private NativeSpriteIconContract() {
    }

    public static boolean isApplicable(
            String minecraftVersion,
            String forgeVersion,
            String reiVersion,
            String reiJeiCompatVersion,
            String architecturyVersion,
            String mekanismVersion
    ) {
        return MINECRAFT_VERSION.equals(minecraftVersion)
                && FORGE_VERSION.equals(forgeVersion)
                && REI_VERSION.equals(reiVersion)
                && REI_JEI_COMPAT_VERSION.equals(reiJeiCompatVersion)
                && ARCHITECTURY_VERSION.equals(architecturyVersion)
                && MEKANISM_VERSION.equals(mekanismVersion);
    }

    /**
     * Returns {@link Kind#NONE} only for unrelated entry types. A known type with any value or
     * renderer drift is rejected explicitly so the caller cannot silently broaden this repair.
     */
    public static Kind requireRendererContract(
            String typeId,
            String valueClass,
            String outerRendererClass,
            String innerRendererClass
    ) {
        if (STANDARD_FLUID_TYPE_ID.equals(typeId)) {
            requireEqual("standard fluid value class", STANDARD_FLUID_VALUE_CLASS, valueClass);
            if (REI_FLUID_RENDERER_CLASS.equals(outerRendererClass)) {
                if (innerRendererClass != null) {
                    throw drift("direct REI fluid renderer unexpectedly has an inner renderer",
                            null, innerRendererClass);
                }
                return Kind.STANDARD_FLUID;
            }
            if (JEI_RENDERER_WRAPPER_CLASS.equals(outerRendererClass)) {
                requireEqual("JEI fluid inner renderer", JEI_FLUID_RENDERER_CLASS,
                        innerRendererClass);
                return Kind.STANDARD_FLUID;
            }
            throw drift("standard fluid outer renderer", REI_FLUID_RENDERER_CLASS
                    + " or " + JEI_RENDERER_WRAPPER_CLASS, outerRendererClass);
        }

        if (MEKANISM_GAS_TYPE_ID.equals(typeId)) {
            requireEqual("Mekanism gas value class", MEKANISM_GAS_VALUE_CLASS, valueClass);
            requireEqual("Mekanism gas outer renderer", JEI_RENDERER_WRAPPER_CLASS,
                    outerRendererClass);
            requireEqual("Mekanism gas inner renderer", MEKANISM_CHEMICAL_RENDERER_CLASS,
                    innerRendererClass);
            return Kind.MEKANISM_GAS;
        }

        return Kind.NONE;
    }

    public static FrameSelection requireFrameSelection(
            int sourceWidth,
            int sourceHeight,
            int frameWidth,
            int frameHeight,
            boolean interpolated,
            int defaultFrameTime,
            List<DeclaredFrame> declaredFrames,
            int runtimeFrameCount
    ) {
        Objects.requireNonNull(declaredFrames, "declaredFrames");
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalStateException("Malformed sprite dimensions: "
                    + sourceWidth + "x" + sourceHeight);
        }
        if (frameWidth != ICON_SIZE || frameHeight != ICON_SIZE) {
            throw new IllegalStateException("Unsupported sprite frame geometry: "
                    + frameWidth + "x" + frameHeight + "; required 16x16");
        }
        if (sourceWidth % frameWidth != 0 || sourceHeight % frameHeight != 0) {
            throw new IllegalStateException("Malformed sprite sheet geometry: source="
                    + sourceWidth + "x" + sourceHeight + ", frame="
                    + frameWidth + "x" + frameHeight);
        }
        if (defaultFrameTime <= 0) {
            throw new IllegalStateException("Malformed default animation frame time: "
                    + defaultFrameTime);
        }

        int physicalFrameCount = (sourceWidth / frameWidth) * (sourceHeight / frameHeight);
        if (physicalFrameCount <= 0) {
            throw new IllegalStateException("Sprite contains no physical 16x16 frames");
        }

        for (DeclaredFrame frame : declaredFrames) {
            if (frame.index() < 0 || frame.index() >= physicalFrameCount) {
                throw new IllegalStateException("Animation frame index out of range: index="
                        + frame.index() + ", physicalFrameCount=" + physicalFrameCount);
            }
            if (frame.duration() <= 0) {
                throw new IllegalStateException("Malformed animation frame duration: index="
                        + frame.index() + ", duration=" + frame.duration());
            }
        }

        boolean explicit = !declaredFrames.isEmpty();
        if (explicit && declaredFrames.size() == 1 && declaredFrames.get(0).index() != 0) {
            throw new IllegalStateException(
                    "Unsupported single-entry explicit animation selecting nonzero physical frame "
                            + declaredFrames.get(0).index()
                            + "; Minecraft omits AnimatedTexture for one-entry sequences and exposes "
                            + "physical frame 0, so the declared frame cannot be verified"
            );
        }
        int expectedRuntimeFrameCount = explicit ? declaredFrames.size() : physicalFrameCount;
        if (runtimeFrameCount != expectedRuntimeFrameCount) {
            throw new IllegalStateException("Runtime sprite frame-count drift: expected="
                    + expectedRuntimeFrameCount + ", actual=" + runtimeFrameCount
                    + ", explicitFrameSequence=" + explicit);
        }
        int firstPhysicalFrame = explicit ? declaredFrames.get(0).index() : 0;
        return new FrameSelection(
                firstPhysicalFrame,
                runtimeFrameCount,
                physicalFrameCount,
                explicit,
                interpolated
        );
    }

    public static void requireCaptureContract(
            int iconScale,
            int captureWidth,
            int captureHeight,
            int nativeVisiblePixels
    ) {
        if (iconScale < 1 || iconScale > 4
                || captureWidth != ICON_SIZE * iconScale
                || captureHeight != ICON_SIZE * iconScale) {
            throw new IllegalStateException("Native sprite comparison requires an exact scaled 16x16 capture; "
                    + "iconScale=" + iconScale + ", capture=" + captureWidth + "x"
                    + captureHeight);
        }
        int maximumVisiblePixels = ICON_SIZE * ICON_SIZE * iconScale * iconScale;
        if (nativeVisiblePixels < 0 || nativeVisiblePixels > maximumVisiblePixels) {
            throw new IllegalArgumentException("nativeVisiblePixels must be in [0,"
                    + maximumVisiblePixels + "]; actual=" + nativeVisiblePixels);
        }
    }

    public static boolean shouldReplaceNativeCapture(
            int nativeVisiblePixels,
            int correctedVisiblePixels
    ) {
        requireVisiblePixelCount("nativeVisiblePixels", nativeVisiblePixels, true);
        requireVisiblePixelCount("correctedVisiblePixels", correctedVisiblePixels, false);
        return correctedVisiblePixels > nativeVisiblePixels;
    }

    /**
     * Selects the only two allowed replacement cases. Static, fully covered native icons retain
     * their renderer output; animated icons are replaced only when that output differs from the
     * byte-validated first declared source frame.
     */
    public static CorrectionReason correctionReason(
            int nativeVisiblePixels,
            int correctedVisiblePixels,
            int differingPixels,
            int runtimeFrameCount
    ) {
        requireVisiblePixelCount("nativeVisiblePixels", nativeVisiblePixels, true);
        requireVisiblePixelCount("correctedVisiblePixels", correctedVisiblePixels, false);
        requirePixelDifferenceCount(differingPixels);
        if (runtimeFrameCount < 1) {
            throw new IllegalArgumentException(
                    "runtimeFrameCount must be positive; actual=" + runtimeFrameCount);
        }
        if (correctedVisiblePixels > nativeVisiblePixels) {
            return CorrectionReason.ALPHA_COVERAGE_RESTORE;
        }
        if (runtimeFrameCount > 1 && differingPixels > 0) {
            return CorrectionReason.CANONICAL_ANIMATION_FRAME;
        }
        return null;
    }

    /** Applies an RGB multiplier to an ABGR NativeImage pixel while preserving source alpha. */
    public static int tintAbgrPreservingAlpha(int sourceAbgr, int tintArgbOrRgb) {
        int alpha = sourceAbgr >>> 24 & 0xff;
        int sourceBlue = sourceAbgr >>> 16 & 0xff;
        int sourceGreen = sourceAbgr >>> 8 & 0xff;
        int sourceRed = sourceAbgr & 0xff;
        int tintRed = tintArgbOrRgb >>> 16 & 0xff;
        int tintGreen = tintArgbOrRgb >>> 8 & 0xff;
        int tintBlue = tintArgbOrRgb & 0xff;
        int red = multiplyChannel(sourceRed, tintRed);
        int green = multiplyChannel(sourceGreen, tintGreen);
        int blue = multiplyChannel(sourceBlue, tintBlue);
        return alpha << 24 | blue << 16 | green << 8 | red;
    }

    public static int[] tintFramePreservingAlpha(int[] sourceAbgr, int tintArgbOrRgb) {
        Objects.requireNonNull(sourceAbgr, "sourceAbgr");
        if (sourceAbgr.length != ICON_SIZE * ICON_SIZE) {
            throw new IllegalStateException("Corrective source frame must contain exactly 256 pixels; actual="
                    + sourceAbgr.length);
        }
        int[] tinted = new int[sourceAbgr.length];
        for (int index = 0; index < sourceAbgr.length; index++) {
            tinted[index] = tintAbgrPreservingAlpha(sourceAbgr[index], tintArgbOrRgb);
        }
        requireVisible(tinted);
        return tinted;
    }

    public static void requireVisible(int[] abgrPixels) {
        Objects.requireNonNull(abgrPixels, "abgrPixels");
        for (int pixel : abgrPixels) {
            if ((pixel >>> 24 & 0xff) != 0) {
                return;
            }
        }
        throw new IllegalStateException(
                "Corrective runtime sprite frame is fully transparent; refusing publication");
    }

    private static int multiplyChannel(int source, int tint) {
        return (source * tint + 127) / 255;
    }

    private static void requireVisiblePixelCount(String label, int value, boolean allowZero) {
        int minimum = allowZero ? 0 : 1;
        if (value < minimum || value > ICON_SIZE * ICON_SIZE) {
            throw new IllegalArgumentException(label + " must be in [" + minimum + ","
                    + (ICON_SIZE * ICON_SIZE) + "]; actual=" + value);
        }
    }

    private static void requirePixelDifferenceCount(int value) {
        if (value < 0 || value > ICON_SIZE * ICON_SIZE) {
            throw new IllegalArgumentException("differingPixels must be in [0,"
                    + (ICON_SIZE * ICON_SIZE) + "]; actual=" + value);
        }
    }

    private static void requireEqual(String label, String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw drift(label, expected, actual);
        }
    }

    private static IllegalStateException drift(String label, String expected, String actual) {
        return new IllegalStateException("Native sprite icon contract drift: " + label
                + "; expected=" + displayInnerRenderer(expected)
                + ", actual=" + displayInnerRenderer(actual));
    }

    private static String displayInnerRenderer(String value) {
        return value == null ? "<none>" : value;
    }
}
