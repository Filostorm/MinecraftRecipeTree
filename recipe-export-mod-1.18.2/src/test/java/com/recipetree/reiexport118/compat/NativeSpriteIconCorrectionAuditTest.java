package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NativeSpriteIconCorrectionAuditTest {
    @Test
    void countsAndWarningLogsEverySuccessfulCorrection() {
        NativeSpriteIconCorrectionAudit audit = new NativeSpriteIconCorrectionAudit();
        List<String> warnings = new ArrayList<>();
        NativeSpriteIconContract.CorrectionEvidence evidence =
                new NativeSpriteIconContract.CorrectionEvidence(
                        NativeSpriteIconContract.Kind.MEKANISM_GAS,
                        NativeSpriteIconContract.CorrectionReason.ALPHA_COVERAGE_RESTORE,
                        "mekanism:steam",
                        NativeSpriteIconContract.MEKANISM_GAS_TYPE_ID,
                        NativeSpriteIconContract.JEI_RENDERER_WRAPPER_CLASS,
                        NativeSpriteIconContract.MEKANISM_CHEMICAL_RENDERER_CLASS,
                        "mekanism:liquid/steam",
                        0x00ffffff,
                        "mekanism:textures/liquid/steam.png",
                        "Mekanism",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        new NativeSpriteIconContract.FrameSelection(3, 2, 32, true, true),
                        16,
                        256,
                        240
                );

        audit.record(evidence, warnings::add);
        audit.record(evidence, warnings::add);

        assertEquals(2, audit.correctionCount());
        assertEquals(2, warnings.size());
        assertEquals(warnings.get(0), warnings.get(1));
        assertTrue(warnings.get(0).contains("entry=mekanism:steam"));
        assertTrue(warnings.get(0).contains("tint=0x00FFFFFF"));
        assertTrue(warnings.get(0).contains("firstPhysicalFrame=3"));
        assertTrue(warnings.get(0).contains("interpolatedFrames=true"));
        assertTrue(warnings.get(0).contains("nativeVisiblePixels=16"));
        assertTrue(warnings.get(0).contains("correctedVisiblePixels=256"));
        assertTrue(warnings.get(0).contains("differingPixels=240"));
        assertTrue(warnings.get(0).contains("sourceSha256=0123456789abcdef"));
        assertTrue(warnings.get(0).contains("selected the exact first physical source keyframe"));
        assertTrue(warnings.get(0).contains(
                "interpolation metadata was recorded but no interpolated frame was synthesized"));
        assertTrue(warnings.get(0).contains(
                "no placeholder, scaling, or generated replacement art"));
    }
}
