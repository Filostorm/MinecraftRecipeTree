package com.recipetree.neiexport1710;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GendustryMachineSemanticAdapterTest {
    @Test
    public void supportsExactlyTheEightPinnedMachineHandlers() {
        Set<String> expected = new HashSet<String>(Arrays.asList(
                GendustryMachineSemanticAdapter.LIQUIFIER,
                GendustryMachineSemanticAdapter.MUTAGEN_PRODUCER,
                GendustryMachineSemanticAdapter.EXTRACTOR,
                GendustryMachineSemanticAdapter.REPLICATOR,
                GendustryMachineSemanticAdapter.TRANSPOSER,
                GendustryMachineSemanticAdapter.MUTATRON,
                GendustryMachineSemanticAdapter.SAMPLER,
                GendustryMachineSemanticAdapter.IMPRINTER));

        assertEquals(expected,
                GendustryMachineSemanticAdapter.supportedHandlerClasses());
        for (String handler : expected) {
            assertTrue(GendustryMachineSemanticAdapter.supports(handler));
            assertNotNull(GendustryMachineSemanticAdapter.operationId(handler));
            assertNotNull(GendustryMachineSemanticAdapter.semanticKind(handler));
        }
        assertFalse(GendustryMachineSemanticAdapter.supports(
                "net.bdew.gendustry.nei.TemplateCraftingHandler"));
    }

    @Test
    public void exposesPinnedStateDependentOutcomeBoundary() {
        assertEquals(
                "degradeChanceNatural=30.0%,deathChanceArtificial=80.0%,secretChance=10.0%",
                GendustryMachineSemanticAdapter.stateDependentOutcomeScope(
                        GendustryMachineSemanticAdapter.MUTATRON));
        assertEquals("deathChanceNatural=20%,deathChanceArtificial=40%",
                GendustryMachineSemanticAdapter.stateDependentOutcomeScope(
                        GendustryMachineSemanticAdapter.IMPRINTER));
        assertNull(GendustryMachineSemanticAdapter.stateDependentOutcomeScope(
                GendustryMachineSemanticAdapter.TRANSPOSER));
    }

    @Test
    public void pinsExactPublicCategoryOperations() {
        assertEquals("Liquifier", GendustryMachineSemanticAdapter.operationId(
                GendustryMachineSemanticAdapter.LIQUIFIER));
        assertEquals("MutagenProducer", GendustryMachineSemanticAdapter.operationId(
                GendustryMachineSemanticAdapter.MUTAGEN_PRODUCER));
        assertEquals("Extractor", GendustryMachineSemanticAdapter.operationId(
                GendustryMachineSemanticAdapter.EXTRACTOR));
        assertEquals("Replicator", GendustryMachineSemanticAdapter.operationId(
                GendustryMachineSemanticAdapter.REPLICATOR));
        assertEquals("Transposer", GendustryMachineSemanticAdapter.operationId(
                GendustryMachineSemanticAdapter.TRANSPOSER));
        assertEquals("Mutatron", GendustryMachineSemanticAdapter.operationId(
                GendustryMachineSemanticAdapter.MUTATRON));
        assertEquals("Sampler", GendustryMachineSemanticAdapter.operationId(
                GendustryMachineSemanticAdapter.SAMPLER));
        assertEquals("Imprinter", GendustryMachineSemanticAdapter.operationId(
                GendustryMachineSemanticAdapter.IMPRINTER));
    }

    @Test
    public void pinsReviewedPageCardinalitiesAndPromotedSemanticCorpus() {
        assertEquals(40, GendustryMachineSemanticAdapter.expectedPages(
                GendustryMachineSemanticAdapter.LIQUIFIER));
        assertEquals(15, GendustryMachineSemanticAdapter.expectedPages(
                GendustryMachineSemanticAdapter.MUTAGEN_PRODUCER));
        assertEquals(1578, GendustryMachineSemanticAdapter.expectedPages(
                GendustryMachineSemanticAdapter.EXTRACTOR));
        assertEquals(3, GendustryMachineSemanticAdapter.expectedPages(
                GendustryMachineSemanticAdapter.REPLICATOR));
        assertEquals(8, GendustryMachineSemanticAdapter.expectedPages(
                GendustryMachineSemanticAdapter.TRANSPOSER));
        assertEquals(705, GendustryMachineSemanticAdapter.expectedPages(
                GendustryMachineSemanticAdapter.MUTATRON));
        assertEquals(9216, GendustryMachineSemanticAdapter.expectedPages(
                GendustryMachineSemanticAdapter.SAMPLER));
        assertEquals(1, GendustryMachineSemanticAdapter.expectedPages(
                GendustryMachineSemanticAdapter.IMPRINTER));
        assertFalse(GendustryMachineSemanticAdapter.requiresDiscovery());
    }

    @Test
    public void stableFingerprintIgnoresPageOrderButPreservesMultiplicity()
            throws Exception {
        String forward = GendustryMachineSemanticAdapter
                .stablePageMultisetFingerprint(
                        GendustryMachineSemanticAdapter.EXTRACTOR,
                        "pages=3", Arrays.asList("b", "a", "b"));
        String reordered = GendustryMachineSemanticAdapter
                .stablePageMultisetFingerprint(
                        GendustryMachineSemanticAdapter.EXTRACTOR,
                        "pages=3", Arrays.asList("b", "b", "a"));
        String deduplicated = GendustryMachineSemanticAdapter
                .stablePageMultisetFingerprint(
                        GendustryMachineSemanticAdapter.EXTRACTOR,
                        "pages=2", Arrays.asList("b", "a"));

        assertEquals(forward, reordered);
        assertNotEquals(forward, deduplicated);
        assertTrue(forward.matches("[0-9a-f]{64}"));
    }

    @Test
    public void promotionGateRequiresBothReviewedConstantsTogether()
            throws Exception {
        GendustryMachineSemanticAdapter.CorpusObservation observation =
                new GendustryMachineSemanticAdapter.CorpusObservation(
                        "all-eight-counts", repeat('a', 64));
        try {
            GendustryMachineSemanticAdapter.requirePromotionForTest(
                    observation,
                    GendustryMachineSemanticAdapter.UNPROMOTED,
                    GendustryMachineSemanticAdapter.UNPROMOTED);
            fail("expected the observation build to fail closed");
        } catch (ExportFailure failure) {
            assertEquals("HANDLER_UNLOADED", failure.code);
            assertTrue(failure.getMessage().contains("observedCountVector="));
            assertTrue(failure.getMessage().contains("observedSha256="));
        }

        try {
            GendustryMachineSemanticAdapter.requirePromotionForTest(
                    observation, "all-eight-counts",
                    GendustryMachineSemanticAdapter.UNPROMOTED);
            fail("expected partial promotion to fail closed");
        } catch (ExportFailure failure) {
            assertEquals("HANDLER_AMBIGUOUS", failure.code);
            assertTrue(failure.getMessage().contains("partially promoted"));
        }

        GendustryMachineSemanticAdapter.requirePromotionForTest(
                observation, observation.countVector, observation.fingerprint);
    }

    @Test
    public void taggedFluidsAreRejectedInsteadOfSilentlyFlattened() throws Exception {
        try {
            GendustryMachineSemanticAdapter.requireNoFluidTagForTest(
                    new NBTTagCompound());
            fail("expected tagged fluid rejection");
        } catch (ExportFailure failure) {
            assertEquals("RECIPE_SEMANTICS", failure.code);
            assertTrue(failure.getMessage().contains("format 2 cannot preserve"));
        }
        GendustryMachineSemanticAdapter.requireNoFluidTagForTest(null);
    }

    @Test
    public void semanticOverrideCarriesCatalystsAndInputProbability() {
        CompleteCategoryAdapters.SemanticAlternative alternative =
                new CompleteCategoryAdapters.SemanticAlternative(null, 1, "one");
        CompleteCategoryAdapters.SemanticSlot stochasticInput =
                new CompleteCategoryAdapters.SemanticSlot(
                        Collections.singletonList(alternative), 0.5d);
        CompleteCategoryAdapters.SemanticSlot deterministic =
                new CompleteCategoryAdapters.SemanticSlot(
                        Collections.singletonList(alternative));
        CompleteCategoryAdapters.RecipeSemanticOverride page =
                new CompleteCategoryAdapters.RecipeSemanticOverride(
                        "gendustry:test", Collections.singletonList(stochasticInput),
                        Collections.singletonList(deterministic),
                        Collections.singletonList(deterministic));

        assertEquals(Double.valueOf(0.5d), page.inputs.get(0).probability);
        assertEquals(1, page.outputs.size());
        assertEquals(1, page.catalysts.size());
    }

    @Test
    public void allEightPoliciesAreExactAdaptedCompleteCategories()
            throws Exception {
        List<String> handlers = Arrays.asList(
                GendustryMachineSemanticAdapter.LIQUIFIER,
                GendustryMachineSemanticAdapter.MUTAGEN_PRODUCER,
                GendustryMachineSemanticAdapter.EXTRACTOR,
                GendustryMachineSemanticAdapter.REPLICATOR,
                GendustryMachineSemanticAdapter.TRANSPOSER,
                GendustryMachineSemanticAdapter.MUTATRON,
                GendustryMachineSemanticAdapter.SAMPLER,
                GendustryMachineSemanticAdapter.IMPRINTER);
        for (String handler : handlers) {
            String operation = GendustryMachineSemanticAdapter.operationId(handler);
            CompleteCategoryAdapters.Policy policy =
                    CompleteCategoryAdapters.classify(
                            handler, handler, null, null, operation);
            assertNotNull(policy);
            assertEquals(CompleteCategoryAdapters.Adapter
                    .GENDUSTRY_MACHINE_SEMANTICS, policy.adapter);
            assertEquals("adapted-complete-category", policy.action);
            assertTrue(policy.contract.startsWith(
                    "adapter:gendustry-1.9.4-machine-semantics-v1/"));
        }
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
