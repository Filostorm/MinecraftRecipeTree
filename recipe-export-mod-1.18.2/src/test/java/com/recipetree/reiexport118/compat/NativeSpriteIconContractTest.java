package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NativeSpriteIconContractTest {
    @Test
    void appliesOnlyToTheExactAuditedRuntimeTuple() {
        assertTrue(NativeSpriteIconContract.isApplicable(
                "1.18.2", "40.2.17", "8.4.778", "8.0.89", "4.12.94", "10.2.5"
        ));
        assertFalse(NativeSpriteIconContract.isApplicable(
                "1.18.2", "40.2.18", "8.4.778", "8.0.89", "4.12.94", "10.2.5"
        ));
        assertFalse(NativeSpriteIconContract.isApplicable(
                "1.18.2", "40.2.17", "8.4.779", "8.0.89", "4.12.94", "10.2.5"
        ));
        assertFalse(NativeSpriteIconContract.isApplicable(
                "1.18.2", "40.2.17", "8.4.778", "8.0.90", "4.12.94", "10.2.5"
        ));
        assertFalse(NativeSpriteIconContract.isApplicable(
                "1.18.2", "40.2.17", "8.4.778", "8.0.89", "4.12.95", "10.2.5"
        ));
        assertFalse(NativeSpriteIconContract.isApplicable(
                "1.18.2", "40.2.17", "8.4.778", "8.0.89", "4.12.94", "10.2.6"
        ));
    }

    @Test
    void acceptsOnlyTheExactStandardFluidAndMekanismGasRendererChains() {
        assertEquals(
                NativeSpriteIconContract.Kind.STANDARD_FLUID,
                NativeSpriteIconContract.requireRendererContract(
                        NativeSpriteIconContract.STANDARD_FLUID_TYPE_ID,
                        NativeSpriteIconContract.STANDARD_FLUID_VALUE_CLASS,
                        NativeSpriteIconContract.REI_FLUID_RENDERER_CLASS,
                        null
                )
        );
        assertEquals(
                NativeSpriteIconContract.Kind.STANDARD_FLUID,
                NativeSpriteIconContract.requireRendererContract(
                        NativeSpriteIconContract.STANDARD_FLUID_TYPE_ID,
                        NativeSpriteIconContract.STANDARD_FLUID_VALUE_CLASS,
                        NativeSpriteIconContract.JEI_RENDERER_WRAPPER_CLASS,
                        NativeSpriteIconContract.JEI_FLUID_RENDERER_CLASS
                )
        );
        assertEquals(
                NativeSpriteIconContract.Kind.MEKANISM_GAS,
                NativeSpriteIconContract.requireRendererContract(
                        NativeSpriteIconContract.MEKANISM_GAS_TYPE_ID,
                        NativeSpriteIconContract.MEKANISM_GAS_VALUE_CLASS,
                        NativeSpriteIconContract.JEI_RENDERER_WRAPPER_CLASS,
                        NativeSpriteIconContract.MEKANISM_CHEMICAL_RENDERER_CLASS
                )
        );
        assertEquals(
                NativeSpriteIconContract.Kind.NONE,
                NativeSpriteIconContract.requireRendererContract(
                        "minecraft:item",
                        "net.minecraft.world.item.ItemStack",
                        "future.ItemRenderer",
                        null
                )
        );
    }

    @Test
    void rejectsEveryKnownTypeValueAndRendererNearMiss() {
        assertThrows(IllegalStateException.class, () -> requireFluid(
                "future.FluidStack",
                NativeSpriteIconContract.REI_FLUID_RENDERER_CLASS,
                null
        ));
        assertThrows(IllegalStateException.class, () -> requireFluid(
                NativeSpriteIconContract.STANDARD_FLUID_VALUE_CLASS,
                "future.FluidRenderer",
                null
        ));
        assertThrows(IllegalStateException.class, () -> requireFluid(
                NativeSpriteIconContract.STANDARD_FLUID_VALUE_CLASS,
                NativeSpriteIconContract.JEI_RENDERER_WRAPPER_CLASS,
                "future.JEIFluidRenderer"
        ));
        assertThrows(IllegalStateException.class, () ->
                NativeSpriteIconContract.requireRendererContract(
                        NativeSpriteIconContract.MEKANISM_GAS_TYPE_ID,
                        NativeSpriteIconContract.MEKANISM_GAS_VALUE_CLASS,
                        NativeSpriteIconContract.JEI_RENDERER_WRAPPER_CLASS,
                        NativeSpriteIconContract.JEI_FLUID_RENDERER_CLASS
                ));
    }

    @Test
    void comparisonRequiresAnExactlyScaledCaptureAndValidCoverageCounts() {
        assertDoesNotThrow(() -> NativeSpriteIconContract.requireCaptureContract(
                1, 16, 16, 0
        ));
        assertDoesNotThrow(() -> NativeSpriteIconContract.requireCaptureContract(
                1, 16, 16, 256
        ));
        assertDoesNotThrow(() -> NativeSpriteIconContract.requireCaptureContract(
                3, 48, 48, 2304
        ));
        assertThrows(IllegalStateException.class, () ->
                NativeSpriteIconContract.requireCaptureContract(3, 32, 32, 0));
        assertThrows(IllegalStateException.class, () ->
                NativeSpriteIconContract.requireCaptureContract(1, 16, 15, 0));
        assertThrows(IllegalArgumentException.class, () ->
                NativeSpriteIconContract.requireCaptureContract(1, 16, 16, 257));
        assertThrows(IllegalArgumentException.class, () ->
                NativeSpriteIconContract.requireCaptureContract(3, 48, 48, 2305));
    }

    @Test
    void replacementRequiresTheExactSpriteToIncreaseAlphaCoverage() {
        assertTrue(NativeSpriteIconContract.shouldReplaceNativeCapture(0, 256));
        assertTrue(NativeSpriteIconContract.shouldReplaceNativeCapture(32, 256));
        assertFalse(NativeSpriteIconContract.shouldReplaceNativeCapture(256, 256));
        assertFalse(NativeSpriteIconContract.shouldReplaceNativeCapture(200, 180));
        assertThrows(IllegalArgumentException.class, () ->
                NativeSpriteIconContract.shouldReplaceNativeCapture(0, 0));
    }

    @Test
    void animatedNativeCaptureUsesTheValidatedFirstFrameEvenAtEqualCoverage() {
        assertEquals(
                NativeSpriteIconContract.CorrectionReason.ALPHA_COVERAGE_RESTORE,
                NativeSpriteIconContract.correctionReason(16, 256, 240, 1));
        assertEquals(
                NativeSpriteIconContract.CorrectionReason.CANONICAL_ANIMATION_FRAME,
                NativeSpriteIconContract.correctionReason(256, 256, 237, 32));
        assertEquals(
                NativeSpriteIconContract.CorrectionReason.CANONICAL_ANIMATION_FRAME,
                NativeSpriteIconContract.correctionReason(256, 240, 237, 32));
        assertEquals(null,
                NativeSpriteIconContract.correctionReason(256, 256, 0, 32));
        assertEquals(null,
                NativeSpriteIconContract.correctionReason(256, 256, 237, 1));
        assertThrows(IllegalArgumentException.class, () ->
                NativeSpriteIconContract.correctionReason(256, 256, 257, 32));
        assertThrows(IllegalArgumentException.class, () ->
                NativeSpriteIconContract.correctionReason(256, 256, 1, 0));
    }

    @Test
    void selectsTheFirstDeclaredAnimationFrameInsteadOfPhysicalFrameZero() {
        NativeSpriteIconContract.FrameSelection selection =
                NativeSpriteIconContract.requireFrameSelection(
                        16,
                        512,
                        16,
                        16,
                        false,
                        2,
                        List.of(
                                new NativeSpriteIconContract.DeclaredFrame(7, 2),
                                new NativeSpriteIconContract.DeclaredFrame(3, 4)
                        ),
                        2
                );
        assertEquals(7, selection.firstPhysicalFrame());
        assertEquals(2, selection.runtimeFrameCount());
        assertEquals(32, selection.physicalFrameCount());
        assertTrue(selection.explicitFrameSequence());
        assertFalse(selection.interpolatedFrames());

        NativeSpriteIconContract.FrameSelection implicit =
                NativeSpriteIconContract.requireFrameSelection(
                        16, 512, 16, 16, false, 2, List.of(), 32
                );
        assertEquals(0, implicit.firstPhysicalFrame());
        assertFalse(implicit.explicitFrameSequence());
        assertFalse(implicit.interpolatedFrames());
    }

    @Test
    void acceptsInterpolationMetadataWhileSelectingTheExactFirstPhysicalKeyframe() {
        NativeSpriteIconContract.FrameSelection selection = frameSelection(
                16,
                512,
                16,
                16,
                true,
                2,
                List.of(
                        new NativeSpriteIconContract.DeclaredFrame(7, 2),
                        new NativeSpriteIconContract.DeclaredFrame(3, 4)
                ),
                2
        );

        assertEquals(7, selection.firstPhysicalFrame());
        assertEquals(2, selection.runtimeFrameCount());
        assertEquals(32, selection.physicalFrameCount());
        assertTrue(selection.explicitFrameSequence());
        assertTrue(selection.interpolatedFrames());
    }

    @Test
    void rejectsMalformedAndRuntimeDriftedAnimationMetadata() {
        assertThrows(IllegalStateException.class, () -> frameSelection(
                17, 512, 16, 16, false, 2, List.of(), 32
        ));
        assertThrows(IllegalStateException.class, () -> frameSelection(
                16, 512, 32, 16, false, 2, List.of(), 16
        ));
        assertThrows(IllegalStateException.class, () -> frameSelection(
                16, 512, 16, 16, false, 0, List.of(), 32
        ));
        assertThrows(IllegalStateException.class, () -> frameSelection(
                16, 512, 16, 16, true, 2,
                List.of(new NativeSpriteIconContract.DeclaredFrame(32, 2)), 1
        ));
        assertThrows(IllegalStateException.class, () -> frameSelection(
                16, 512, 16, 16, true, 2,
                List.of(new NativeSpriteIconContract.DeclaredFrame(1, 0)), 1
        ));
        assertThrows(IllegalStateException.class, () -> frameSelection(
                16, 512, 16, 16, true, 2,
                List.of(new NativeSpriteIconContract.DeclaredFrame(1, 2)), 32
        ));
        assertThrows(IllegalStateException.class, () -> frameSelection(
                16, 512, 16, 16, false, 2,
                List.of(new NativeSpriteIconContract.DeclaredFrame(7, 2)), 1
        ));
    }

    @Test
    void multipliesRgbWithRoundingAndPreservesSourceAlphaExactly() {
        int sourceAbgr = 0x7f3264c8;
        int tintWithIgnoredAlpha = 0x0166ccff;
        assertEquals(
                0x7f325050,
                NativeSpriteIconContract.tintAbgrPreservingAlpha(
                        sourceAbgr,
                        tintWithIgnoredAlpha
                )
        );
        assertEquals(
                0x00325050,
                NativeSpriteIconContract.tintAbgrPreservingAlpha(
                        0x003264c8,
                        tintWithIgnoredAlpha
                )
        );
    }

    @Test
    void rejectsAZeroAlphaFrameEvenAfterTinting() {
        int[] transparent = new int[256];
        assertThrows(
                IllegalStateException.class,
                () -> NativeSpriteIconContract.tintFramePreservingAlpha(
                        transparent,
                        0xffffffff
                )
        );

        int[] oneVisiblePixel = new int[256];
        oneVisiblePixel[73] = 0x01010203;
        int[] tinted = NativeSpriteIconContract.tintFramePreservingAlpha(
                oneVisiblePixel,
                0x00000000
        );
        assertEquals(0x01000000, tinted[73]);
    }


    @Test
    void correctionEvidenceRejectsReasonSpecificNearMisses() {
        NativeSpriteIconContract.FrameSelection animated =
                new NativeSpriteIconContract.FrameSelection(0, 32, 32, false, false);
        assertThrows(IllegalArgumentException.class, () ->
                new NativeSpriteIconContract.CorrectionEvidence(
                        NativeSpriteIconContract.Kind.STANDARD_FLUID,
                        NativeSpriteIconContract.CorrectionReason.ALPHA_COVERAGE_RESTORE,
                        "pneumaticcraft:memory_essence",
                        NativeSpriteIconContract.STANDARD_FLUID_TYPE_ID,
                        NativeSpriteIconContract.REI_FLUID_RENDERER_CLASS,
                        null,
                        "pneumaticcraft:block/fluid/memory_essence_still",
                        0xffffffff,
                        "pneumaticcraft:textures/block/fluid/memory_essence_still.png",
                        "PneumaticCraft",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        animated,
                        256,
                        256,
                        237));
        assertThrows(IllegalArgumentException.class, () ->
                new NativeSpriteIconContract.CorrectionEvidence(
                        NativeSpriteIconContract.Kind.STANDARD_FLUID,
                        NativeSpriteIconContract.CorrectionReason.CANONICAL_ANIMATION_FRAME,
                        "pneumaticcraft:memory_essence",
                        NativeSpriteIconContract.STANDARD_FLUID_TYPE_ID,
                        NativeSpriteIconContract.REI_FLUID_RENDERER_CLASS,
                        null,
                        "pneumaticcraft:block/fluid/memory_essence_still",
                        0xffffffff,
                        "pneumaticcraft:textures/block/fluid/memory_essence_still.png",
                        "PneumaticCraft",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        animated,
                        256,
                        256,
                        0));
    }

    @Test
    void pinsEveryAuditedRendererAndValueBytecodeDigest() {
        assertEquals(64, NativeSpriteIconContract.REI_FLUID_RENDERER_SHA256.length());
        assertEquals(64, NativeSpriteIconContract.JEI_RENDERER_WRAPPER_SHA256.length());
        assertEquals(64, NativeSpriteIconContract.JEI_FLUID_RENDERER_SHA256.length());
        assertEquals(64, NativeSpriteIconContract.ARCHITECTURY_FLUID_STACK_SHA256.length());
        assertEquals(64, NativeSpriteIconContract.ARCHITECTURY_FLUID_HOOKS_SHA256.length());
        assertEquals(64, NativeSpriteIconContract.MEKANISM_GAS_STACK_SHA256.length());
        assertEquals(64, NativeSpriteIconContract.MEKANISM_CHEMICAL_SHA256.length());
        assertEquals(64, NativeSpriteIconContract.MEKANISM_CHEMICAL_RENDERER_SHA256.length());
    }

    private static NativeSpriteIconContract.Kind requireFluid(
            String valueClass,
            String outerRenderer,
            String innerRenderer
    ) {
        return NativeSpriteIconContract.requireRendererContract(
                NativeSpriteIconContract.STANDARD_FLUID_TYPE_ID,
                valueClass,
                outerRenderer,
                innerRenderer
        );
    }

    private static NativeSpriteIconContract.FrameSelection frameSelection(
            int sourceWidth,
            int sourceHeight,
            int frameWidth,
            int frameHeight,
            boolean interpolated,
            int defaultFrameTime,
            List<NativeSpriteIconContract.DeclaredFrame> declaredFrames,
            int runtimeFrameCount
    ) {
        return NativeSpriteIconContract.requireFrameSelection(
                sourceWidth,
                sourceHeight,
                frameWidth,
                frameHeight,
                interpolated,
                defaultFrameTime,
                declaredFrames,
                runtimeFrameCount
        );
    }
}
