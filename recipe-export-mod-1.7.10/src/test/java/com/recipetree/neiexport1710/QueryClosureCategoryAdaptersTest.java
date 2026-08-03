package com.recipetree.neiexport1710;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class QueryClosureCategoryAdaptersTest {
    @Test
    public void exactFiveHandlerInventoryAndContractsAreStable() throws Exception {
        List<String> expected = Arrays.asList(
                QueryClosureCategoryAdapters.PROJECT_BLUE_HANDLER,
                QueryClosureCategoryAdapters.PROJECT_RED_SHAPED_HANDLER,
                QueryClosureCategoryAdapters.PROJECT_RED_SHAPELESS_HANDLER,
                QueryClosureCategoryAdapters.GENDUSTRY_TEMPLATE_HANDLER,
                QueryClosureCategoryAdapters.BOTANIA_FLOATING_FLOWER_HANDLER);
        Collections.sort(expected);

        assertEquals(expected, QueryClosureCategoryAdapters.supportedHandlerClasses());
        for (String handler : expected) {
            assertNotNull(QueryClosureCategoryAdapters.contractFor(handler));
            assertFalse(QueryClosureCategoryAdapters.contractFor(handler).trim().isEmpty());
            assertEquals(true, QueryClosureCategoryAdapters.supports(handler));
        }
        assertEquals(
                "adapter:projectred-shaped-builder-registry-query-closure-v2",
                QueryClosureCategoryAdapters.PROJECT_RED_SHAPED_CONTRACT);
        assertEquals(
                "adapter:projectred-shapeless-builder-registry-query-closure-v2",
                QueryClosureCategoryAdapters.PROJECT_RED_SHAPELESS_CONTRACT);
        assertEquals(false, QueryClosureCategoryAdapters.supports("unknown.Handler"));
        assertFailure("HANDLER_AMBIGUOUS", new ThrowingAction() {
            @Override
            public void run() throws Exception {
                QueryClosureCategoryAdapters.contractFor("unknown.Handler");
            }
        });
    }

    @Test
    public void corpusFingerprintIgnoresTraversalOrderButPreservesMultiplicity() {
        String first = QueryClosureCategoryAdapters.fingerprintForTesting(
                "test-domain", Arrays.asList("gamma", "alpha", "beta"));
        String reordered = QueryClosureCategoryAdapters.fingerprintForTesting(
                "test-domain", Arrays.asList("beta", "gamma", "alpha"));
        String duplicated = QueryClosureCategoryAdapters.fingerprintForTesting(
                "test-domain", Arrays.asList("beta", "gamma", "alpha", "alpha"));

        assertEquals(first, reordered);
        assertNotEquals(first, duplicated);
    }

    @Test
    public void canonicalPageRowsSortAndRejectDuplicates() throws Exception {
        assertEquals(Arrays.asList("alpha", "beta", "gamma"),
                QueryClosureCategoryAdapters.uniqueSortedForTesting(
                        Arrays.asList("gamma", "alpha", "beta")));

        assertFailure("HANDLER_DUPLICATE", new ThrowingAction() {
            @Override
            public void run() throws Exception {
                QueryClosureCategoryAdapters.uniqueSortedForTesting(
                        Arrays.asList("same", "same"));
            }
        });
        assertFailure("HANDLER_DUPLICATE", new ThrowingAction() {
            @Override
            public void run() throws Exception {
                QueryClosureCategoryAdapters.uniqueSortedForTesting(
                        Arrays.asList("valid", null));
            }
        });
    }

    @Test
    public void promotionBindsBothCountsAndBothFingerprints() {
        QueryClosureCategoryAdapters.AuditRow observed =
                new QueryClosureCategoryAdapters.AuditRow(
                        "fixture.Handler", "fixture-contract",
                        3, "source-sha", 3, "loaded-sha", "fixture-state");
        QueryClosureCategoryAdapters.Promotion exact =
                new QueryClosureCategoryAdapters.Promotion(
                        3, "source-sha", 3, "loaded-sha");

        assertNull(exact.mismatch(observed));
        assertNotNull(QueryClosureCategoryAdapters.Promotion.unpromoted()
                .mismatch(observed));
        assertNotNull(new QueryClosureCategoryAdapters.Promotion(
                4, "source-sha", 3, "loaded-sha").mismatch(observed));
        assertNotNull(new QueryClosureCategoryAdapters.Promotion(
                3, "changed", 3, "loaded-sha").mismatch(observed));
        assertNotNull(new QueryClosureCategoryAdapters.Promotion(
                3, "source-sha", 4, "loaded-sha").mismatch(observed));
        assertNotNull(new QueryClosureCategoryAdapters.Promotion(
                3, "source-sha", 3, "changed").mismatch(observed));
    }

    @Test
    public void runtimePromotionPinsAllFiveObservedCorporaExactly() {
        assertPromotion(QueryClosureCategoryAdapters.PROJECT_BLUE_HANDLER,
                7357,
                "5e0f358246d3d4780f48e8a591b248864fe3f399a1de41cd88699050368d0e91",
                7357,
                "5033685bf19ab87f41262a104a2e5e161eb014f2c4951b2826f2e87eaa4c3723");
        assertPromotion(QueryClosureCategoryAdapters.PROJECT_RED_SHAPED_HANDLER,
                2,
                "ff3ff344a5a428125af41db0f77e545de893109b6a1160c067b8024fd58718c8",
                2,
                "a1e9609ef004b9c1b465f9be96a4453da45f5e0a87211a8e94998143052a5006");
        assertPromotion(QueryClosureCategoryAdapters.PROJECT_RED_SHAPELESS_HANDLER,
                1,
                "b62b0181ce81e6ea6db44135cbd9f622f93c1d71443b37d427966c193461f4de",
                1,
                "e8cb1a76003cb24a701795eac64dfac9815491677203d47ff55898f7142da631");
        assertPromotion(QueryClosureCategoryAdapters.GENDUSTRY_TEMPLATE_HANDLER,
                1,
                "6f3af73521057201c9d72113fd4122c60f492fd2fbb5ebe52eee63d8b1e2cf32",
                1,
                "cf2397f8610d83968d9a19069f46be23dd1d623dbde16ec4d1ecdb8f29bc5ec7");
        assertPromotion(QueryClosureCategoryAdapters.BOTANIA_FLOATING_FLOWER_HANDLER,
                61,
                "38d9115db74936bdf0afb1618da9be14d914634de2a7742a4a705a604187613c",
                61,
                "46e4a96b068bbc1679b1cbd99a23908a73586938d2dfc0e0f66e04a6e10df8e3");
    }

    @Test
    public void auditCanonicalBindsRegistryDiagnostics() {
        QueryClosureCategoryAdapters.AuditRow first =
                new QueryClosureCategoryAdapters.AuditRow(
                        "fixture.Handler", "fixture-contract",
                        2, "source", 2, "loaded", "registry=a");
        QueryClosureCategoryAdapters.AuditRow changed =
                new QueryClosureCategoryAdapters.AuditRow(
                        "fixture.Handler", "fixture-contract",
                        2, "source", 2, "loaded", "registry=b");

        assertNotEquals(first.canonical(), changed.canonical());
    }

    private static void assertFailure(String code, ThrowingAction action)
            throws Exception {
        try {
            action.run();
            fail("expected ExportFailure " + code);
        } catch (ExportFailure failure) {
            assertEquals(code, failure.code);
        }
    }

    private static void assertPromotion(
            String handlerClass, int sourceCount, String sourceFingerprint,
            int loadedCount, String loadedFingerprint) {
        QueryClosureCategoryAdapters.Promotion promotion =
                QueryClosureCategoryAdapters.promotionForTesting(handlerClass);
        assertNotNull(promotion);
        assertEquals(sourceCount, promotion.sourceCount);
        assertEquals(sourceFingerprint, promotion.sourceFingerprint);
        assertEquals(loadedCount, promotion.loadedCount);
        assertEquals(loadedFingerprint, promotion.loadedFingerprint);
    }

    private interface ThrowingAction {
        void run() throws Exception;
    }

}
