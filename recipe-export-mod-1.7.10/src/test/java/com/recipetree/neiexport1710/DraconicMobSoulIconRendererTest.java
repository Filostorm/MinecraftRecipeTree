package com.recipetree.neiexport1710;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DraconicMobSoulIconRendererTest {
    @Test
    public void acceptsDistinctCatalogAndMobsInfoCoverageDomains() throws Exception {
        DraconicMobSoulIconRenderer.requireCompleteCoverage(363, 362);
    }

    @Test
    public void rejectsTheFormerConflatedCatalogCount() throws Exception {
        try {
            DraconicMobSoulIconRenderer.requireCompleteCoverage(363, 363);
            fail("Expected conflated MobsInfo count to be rejected");
        } catch (ExportFailure expected) {
            assertTrue(expected.getMessage().contains("expected catalog=363, MobsInfo=362"));
            assertTrue(expected.getMessage().contains("observed catalog=363, MobsInfo=363"));
        }
    }

    @Test
    public void rejectsMissingRecipeOnlyCatalogSoul() throws Exception {
        try {
            DraconicMobSoulIconRenderer.requireCompleteCoverage(362, 362);
            fail("Expected missing recipe-only catalog identity to be rejected");
        } catch (ExportFailure expected) {
            assertTrue(expected.getMessage().contains("observed catalog=362, MobsInfo=362"));
        }
    }
}
