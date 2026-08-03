package com.recipetree.neiexport1710;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForestryFluidSemanticAdapterTest {
    private static final String SHA =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void supportsExactlySevenPinnedForestryFluidHandlers() {
        Set<String> handlers =
                ForestryFluidSemanticAdapter.supportedHandlerClasses();
        assertEquals(7, handlers.size());
        assertTrue(handlers.contains(ForestryFluidSemanticAdapter.BOTTLER));
        assertTrue(handlers.contains(ForestryFluidSemanticAdapter.CARPENTER));
        assertTrue(handlers.contains(ForestryFluidSemanticAdapter.FABRICATOR));
        assertTrue(handlers.contains(ForestryFluidSemanticAdapter.FERMENTER));
        assertTrue(handlers.contains(ForestryFluidSemanticAdapter.MOISTENER));
        assertTrue(handlers.contains(ForestryFluidSemanticAdapter.SQUEEZER));
        assertTrue(handlers.contains(ForestryFluidSemanticAdapter.STILL));
    }

    @Test
    public void pageMultisetFingerprintIgnoresInsertionOrderButPreservesMultiplicity()
            throws Exception {
        List<String> source = Arrays.asList(
                "page-c", "page-a", "page-b", "page-a");
        List<String> originalOrder = Arrays.asList(
                "page-c", "page-a", "page-b", "page-a");
        String first = ForestryFluidSemanticAdapter.stablePageMultisetFingerprint(
                "test.ForestryHandler", "pages=4", source, "extra");
        String reordered = ForestryFluidSemanticAdapter.stablePageMultisetFingerprint(
                "test.ForestryHandler", "pages=4",
                Arrays.asList("page-a", "page-b", "page-a", "page-c"),
                "extra");
        String changedMultiplicity =
                ForestryFluidSemanticAdapter.stablePageMultisetFingerprint(
                        "test.ForestryHandler", "pages=4",
                        Arrays.asList("page-a", "page-b", "page-b", "page-c"),
                        "extra");

        assertEquals(first, reordered);
        assertEquals(originalOrder, source);
        assertTrue(!first.equals(changedMultiplicity));
    }

    @Test
    public void pageMultisetFingerprintRejectsNullRows() throws Exception {
        try {
            ForestryFluidSemanticAdapter.stablePageMultisetFingerprint(
                    "test.ForestryHandler", "pages=2",
                    Arrays.asList("page-a", null), "extra");
        } catch (ExportFailure failure) {
            assertEquals("INTERNAL_ERROR", failure.code);
            assertTrue(failure.getMessage().contains("null page #1"));
            return;
        }
        throw new AssertionError("Expected a null Forestry page row to fail closed");
    }

    @Test
    public void fabricatorRepresentativeUsesAUniqueMinimumSemanticId()
            throws Exception {
        assertEquals("forestry:a", ForestryFluidSemanticAdapter.uniqueMinimumSemanticId(
                Arrays.asList("forestry:c", "forestry:a", "forestry:b"),
                "test Fabricator preview"));
        try {
            ForestryFluidSemanticAdapter.uniqueMinimumSemanticId(
                    Arrays.asList("forestry:b", "forestry:a", "forestry:a"),
                    "test Fabricator preview");
        } catch (ExportFailure failure) {
            assertEquals("RECIPE_SEMANTICS", failure.code);
            assertTrue(failure.getMessage().contains(
                    "multiple render candidates sharing minimum semantic ID"));
            return;
        }
        throw new AssertionError(
                "Expected an ambiguous Fabricator preview minimum to fail closed");
    }

    @Test
    public void promotedCorpusConstantsAreCompleteAndSelfConsistent()
            throws Exception {
        assertFalse(ForestryFluidSemanticAdapter.requiresDiscovery());
        ForestryFluidSemanticAdapter.requirePromotionForTest(
                new ForestryFluidSemanticAdapter.CorpusObservation(
                        ForestryFluidSemanticAdapter.EXPECTED_COUNT_VECTOR,
                        ForestryFluidSemanticAdapter.EXPECTED_SHA256),
                ForestryFluidSemanticAdapter.EXPECTED_COUNT_VECTOR,
                ForestryFluidSemanticAdapter.EXPECTED_SHA256);
    }

    @Test
    public void explicitUnpromotedSeamReportsBothValuesAndFailsClosed()
            throws Exception {
        ForestryFluidSemanticAdapter.CorpusObservation observed =
                new ForestryFluidSemanticAdapter.CorpusObservation(
                        "pages=42;dynamicInputs=7", SHA);
        try {
            ForestryFluidSemanticAdapter.requirePromotionForTest(
                    observed,
                    ForestryFluidSemanticAdapter.UNPROMOTED,
                    ForestryFluidSemanticAdapter.UNPROMOTED);
        } catch (ExportFailure failure) {
            assertEquals("HANDLER_UNLOADED", failure.code);
            assertTrue(failure.getMessage().contains(observed.countVector));
            assertTrue(failure.getMessage().contains(observed.fingerprint));
            assertTrue(failure.getMessage().contains("abort before rendering"));
            return;
        }
        throw new AssertionError("Expected the unpromoted Forestry gate to abort");
    }

    @Test
    public void promotionRequiresExactCountVectorAndShaTogether()
            throws Exception {
        ForestryFluidSemanticAdapter.CorpusObservation observed =
                new ForestryFluidSemanticAdapter.CorpusObservation(
                        "pages=42;dynamicInputs=7", SHA);
        ForestryFluidSemanticAdapter.requirePromotionForTest(
                observed, observed.countVector, observed.fingerprint);

        assertFailure("HANDLER_AMBIGUOUS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                ForestryFluidSemanticAdapter.requirePromotionForTest(
                        observed, observed.countVector,
                        ForestryFluidSemanticAdapter.UNPROMOTED);
            }
        });
        assertFailure("HANDLER_UNLOADED", new CheckedCall() {
            @Override
            public void run() throws Exception {
                ForestryFluidSemanticAdapter.requirePromotionForTest(
                        observed, "pages=41;dynamicInputs=7", observed.fingerprint);
            }
        });
    }

    @Test
    public void taggedFluidsAreExplicitlyRejectedBeforeProxyConversion()
            throws Exception {
        NBTTagCompound tagged = new NBTTagCompound();
        tagged.setString("variant", "must-not-collapse");
        try {
            ForestryFluidSemanticAdapter.requireNoFluidTagForTest(tagged);
        } catch (ExportFailure failure) {
            assertEquals("RECIPE_SEMANTICS", failure.code);
            assertTrue(failure.getMessage().contains("tagged fluid"));
            assertTrue(failure.getMessage().contains("never silently collapsed"));
            return;
        }
        throw new AssertionError("Expected tagged fluid rejection");
    }

    @Test
    public void chanceFingerprintUsesRawFloatBits() {
        int payloadNaN = 0x7fa12345;
        float chance = Float.intBitsToFloat(payloadNaN);
        assertEquals(payloadNaN,
                ForestryFluidSemanticAdapter.rawChanceBitsForTest(chance));
    }

    @Test
    public void fermenterPinsTheUnassignedLegacyResourceField() throws Exception {
        ForestryFluidSemanticAdapter.requireLegacyFermenterResourceNullForTest(null);
        assertFailure("HANDLER_AMBIGUOUS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                ForestryFluidSemanticAdapter
                        .requireLegacyFermenterResourceNullForTest(new Object());
            }
        });
    }

    @Test
    public void squeezerClassifiesNullableAndProbabilisticRemnantsExactly()
            throws Exception {
        assertEquals("ABSENT_IDENTITY",
                ForestryFluidSemanticAdapter.classifySqueezerRemnantForTest(
                        false, 0.6f));
        assertEquals("ZERO_PROBABILITY_PREVIEW",
                ForestryFluidSemanticAdapter.classifySqueezerRemnantForTest(
                        true, 0.0f));
        assertEquals("STOCHASTIC",
                ForestryFluidSemanticAdapter.classifySqueezerRemnantForTest(
                        true, 0.6f));
        assertEquals("DETERMINISTIC",
                ForestryFluidSemanticAdapter.classifySqueezerRemnantForTest(
                        true, 1.0f));
        assertFailure("RECIPE_SEMANTICS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                ForestryFluidSemanticAdapter.classifySqueezerRemnantForTest(
                        true, Float.NaN);
            }
        });
    }

    @Test
    public void bottlerSeparatesExactDynamicAndDegenerateFlows()
            throws Exception {
        assertEquals("EXACT_FIXED_FLOW",
                ForestryFluidSemanticAdapter.classifyBottlerFlowForTest(
                        false, 1000, 1000));
        assertEquals("DYNAMIC_UNKNOWN_FLOW",
                ForestryFluidSemanticAdapter.classifyBottlerFlowForTest(
                        true, 250, 16000));
        assertEquals("DYNAMIC_UNKNOWN_FLOW",
                ForestryFluidSemanticAdapter.classifyBottlerFlowForTest(
                        true, 0, 16000));
        assertEquals("EXCLUDED_ZERO_CAPACITY_PAGE",
                ForestryFluidSemanticAdapter.classifyBottlerFlowForTest(
                        false, 0, 0));
        assertEquals("EXACT_FIXED_FLOW",
                ForestryFluidSemanticAdapter.classifyBottlerFlowForTest(
                        false, 0, 1000));
        assertEquals("EXACT_FIXED_FLOW",
                ForestryFluidSemanticAdapter.classifyBottlerFlowForTest(
                        false, 500, 1000));
        assertEquals("EXCLUDED_ZERO_CAPACITY_PAGE",
                ForestryFluidSemanticAdapter.classifyBottlerFlowForTest(
                        false, 500, 0));
    }

    @Test
    public void canonicalCrossWalkIgnoresBackingIterationOrderAndPreservesDuplicates()
            throws Exception {
        List<String> first = ForestryFluidSemanticAdapter.deterministicCrossWalkForTest(
                Arrays.asList("shape-c", "shape-a", "shape-b", "shape-a"),
                Arrays.asList("shape-a", "shape-c", "shape-a", "shape-b"));
        List<String> second = ForestryFluidSemanticAdapter.deterministicCrossWalkForTest(
                Arrays.asList("shape-a", "shape-b", "shape-a", "shape-c"),
                Arrays.asList("shape-b", "shape-a", "shape-c", "shape-a"));
        List<String> expected = Arrays.asList(
                "shape-a", "shape-a", "shape-b", "shape-c");
        assertEquals(expected, first);
        assertEquals(expected, second);
    }

    @Test
    public void canonicalCrossWalkFailsOnMissingOrUnmatchedRows() throws Exception {
        assertFailure("RECIPE_SEMANTICS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                ForestryFluidSemanticAdapter.deterministicCrossWalkForTest(
                        Collections.singletonList("shape-a"),
                        Collections.singletonList("shape-b"));
            }
        });
        assertFailure("RECIPE_SEMANTICS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                ForestryFluidSemanticAdapter.deterministicCrossWalkForTest(
                        Arrays.asList("shape-a", "shape-b"),
                        Collections.singletonList("shape-a"));
            }
        });
    }

    private static void assertFailure(String code, CheckedCall call) throws Exception {
        try {
            call.run();
        } catch (ExportFailure failure) {
            assertEquals(code, failure.code);
            return;
        }
        throw new AssertionError("Expected " + code);
    }

    private interface CheckedCall {
        void run() throws Exception;
    }
}
