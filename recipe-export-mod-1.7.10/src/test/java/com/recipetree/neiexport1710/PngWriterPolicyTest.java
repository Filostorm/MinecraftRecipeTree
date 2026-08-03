package com.recipetree.neiexport1710;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PngWriterPolicyTest {
    @Test
    public void saturationLoggingIsAggregated() {
        assertTrue(PngWriter.isSaturationLogCheckpoint(1));
        assertFalse(PngWriter.isSaturationLogCheckpoint(2));
        assertTrue(PngWriter.isSaturationLogCheckpoint(1000));
    }
}
