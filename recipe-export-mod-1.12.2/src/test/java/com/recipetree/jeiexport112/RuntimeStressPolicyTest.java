package com.recipetree.jeiexport112;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RuntimeStressPolicyTest {
    @Test
    public void equivalentMeatballCraftSaturationBurstEmitsFourWarningCheckpoints() {
        int warnings = 0;
        for (int event = 1; event <= 3413; event++) {
            if (PngWriter.isSaturationLogCheckpoint(event)) {
                warnings++;
            }
        }

        assertEquals(4, warnings);
        assertTrue(PngWriter.isSaturationLogCheckpoint(1));
        assertTrue(PngWriter.isSaturationLogCheckpoint(1000));
        assertFalse(PngWriter.isSaturationLogCheckpoint(999));
        assertFalse(PngWriter.isSaturationLogCheckpoint(1001));
        assertFalse(PngWriter.isSaturationLogCheckpoint(0));
        assertFalse(PngWriter.isSaturationLogCheckpoint(-1000));
    }

    @Test
    public void plainTextRemovesVanillaAndBuildCraftFormattingWithoutChangingText() {
        assertEquals("Material Ranged Stats",
                Naming.plainText("\u00a76Material Ranged Stats\u00a7r"));
        assertEquals("White Paintbrush",
                Naming.plainText("\u00a7z\u00a70\u00a7fWhite\u00a7r Paintbrush"));
        assertEquals("dangling", Naming.plainText("dangling\u00a7"));
        assertEquals("Already plain", Naming.plainText("Already plain"));
        assertNull(Naming.plainText(null));
    }
}
