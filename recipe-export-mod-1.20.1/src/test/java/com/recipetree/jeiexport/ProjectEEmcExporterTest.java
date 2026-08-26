package com.recipetree.jeiexport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectEEmcExporterTest {
    @Test
    void exportsOnlyPositiveSafeValuesWithoutNormalProducer() {
        assertTrue(ProjectEEmcExporter.shouldExport(false, true, 8_192L));
        assertFalse(ProjectEEmcExporter.shouldExport(true, true, 8_192L));
        assertFalse(ProjectEEmcExporter.shouldExport(false, false, 8_192L));
        assertFalse(ProjectEEmcExporter.shouldExport(false, true, 0L));
        assertFalse(ProjectEEmcExporter.shouldExport(false, true, 9_007_199_254_740_992L));
    }
}
