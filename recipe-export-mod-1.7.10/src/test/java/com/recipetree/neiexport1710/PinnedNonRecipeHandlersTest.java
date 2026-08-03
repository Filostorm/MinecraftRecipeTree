package com.recipetree.neiexport1710;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class PinnedNonRecipeHandlersTest {
    @Test
    public void exactThreeHandlerPolicyLedgerIsStable() {
        List<String> actual = new ArrayList<String>();
        for (PinnedNonRecipeHandlers.PolicyEntry policy
                : PinnedNonRecipeHandlers.policyEntries()) {
            actual.add(policy.contractRow());
        }

        assertEquals(Arrays.asList(
                "blockrenderer6343.integration.gregtech.GTNEIMultiblockHandler|"
                        + "blockrenderer6343.integration.gregtech.GTNEIMultiblockHandler|"
                        + "blockrenderer6343.integration.gregtech.GTNEIMultiblockHandler|"
                        + "QUERY_ONLY|excluded-non-recipe-query|"
                        + "query-only:blockrenderer-gregtech-multiblock-item-query-ui-state-v1|"
                        + "GTNEIMultiblockHandler.multiblocksList+multiBlockComponents|"
                        + "0a36e526ad8aafa5b47a2dd93752311965675edd1fbb087d45cae0ccb7927baf",
                "blockrenderer6343.integration.structurelib.StructureCompatNEIHandler|"
                        + "blockrenderer6343.integration.structurelib.StructureCompatNEIHandler|"
                        + "blockrenderer6343.integration.structurelib.StructureCompatNEIHandler|"
                        + "QUERY_ONLY|excluded-non-recipe-query|"
                        + "query-only:blockrenderer-structurelib-multiblock-item-query-ui-state-v1|"
                        + "IMultiblockInfoContainer.MULTIBLOCK_MAP+"
                        + "StructureCompatNEIHandler.stacks+multiBlockComponents|"
                        + "594556aaf33d4374a9d36d19380eb0372c7bcd5a3489931922c235bff81cd8d1",
                "codechicken.nei.recipe.InformationHandler|"
                        + "codechicken.nei.recipe.InformationHandler|information|"
                        + "PRESENTATION_ONLY|excluded-non-recipe-presentation|"
                        + "presentation-only:nei-item-filter-text-information-pages-v1|"
                        + "InformationHandler.ITEM_INFO[filter,info,items]|"
                        + "22fa8f40d61450aa51cabcfd8f36d90a44f76e3f994409ba8d473a90b37e248e"
        ), actual);
        assertTrue(PinnedNonRecipeHandlers.validateSpecLedgerForTest(
                PinnedNonRecipeHandlers.policyEntries()).isEmpty());
    }

    @Test
    public void integrationEntriesExposeExactDispositionAndNoTransferFallback()
            throws Exception {
        PinnedNonRecipeHandlers.PolicyEntry gregTech =
                PinnedNonRecipeHandlers.policyFor(
                        PinnedNonRecipeHandlers.GT_MULTIBLOCK_HANDLER);
        PinnedNonRecipeHandlers.PolicyEntry structureLib =
                PinnedNonRecipeHandlers.policyFor(
                        PinnedNonRecipeHandlers.STRUCTURELIB_MULTIBLOCK_HANDLER);
        PinnedNonRecipeHandlers.PolicyEntry information =
                PinnedNonRecipeHandlers.policyFor(
                        PinnedNonRecipeHandlers.INFORMATION_HANDLER);

        assertEquals(PinnedNonRecipeHandlers.Disposition.QUERY_ONLY,
                gregTech.disposition);
        assertEquals(PinnedNonRecipeHandlers.Disposition.QUERY_ONLY,
                structureLib.disposition);
        assertEquals(PinnedNonRecipeHandlers.Disposition.PRESENTATION_ONLY,
                information.disposition);
        for (PinnedNonRecipeHandlers.PolicyEntry policy
                : PinnedNonRecipeHandlers.policyEntries()) {
            assertEquals(policy.handlerClass, policy.handlerId);
            assertEquals(policy.disposition.action, policy.action);
            assertEquals(null, policy.expectedTransferSelector());
            assertEquals(null, policy.expectedTransferRect());
        }
    }

    @Test
    public void unknownHandlersNeverReceiveTheExclusionPolicy() throws Exception {
        assertTrue(PinnedNonRecipeHandlers.supports(
                PinnedNonRecipeHandlers.GT_MULTIBLOCK_HANDLER));
        assertTrue(PinnedNonRecipeHandlers.supports(
                PinnedNonRecipeHandlers.STRUCTURELIB_MULTIBLOCK_HANDLER));
        assertTrue(PinnedNonRecipeHandlers.supports(
                PinnedNonRecipeHandlers.INFORMATION_HANDLER));
        assertFalse(PinnedNonRecipeHandlers.supports("unknown.Handler"));
        try {
            PinnedNonRecipeHandlers.policyFor("unknown.Handler");
            fail("expected unknown handlers to fail closed");
        } catch (ExportFailure failure) {
            assertEquals("HANDLER_AMBIGUOUS", failure.code);
        }
    }

    @Test
    public void ledgerAuditRejectsDuplicateIdsClassDriftAndUnpinnedBytes() {
        PinnedNonRecipeHandlers.PolicyEntry valid =
                PinnedNonRecipeHandlers.policyEntries().get(0);
        PinnedNonRecipeHandlers.PolicyEntry drifted =
                new PinnedNonRecipeHandlers.PolicyEntry(
                        "fixture.OtherClass",
                        valid.handlerId,
                        valid.expectedOverlay,
                        valid.disposition,
                        valid.contract,
                        valid.sourceContract,
                        "not-a-sha");
        List<String> issues = PinnedNonRecipeHandlers.validateSpecLedgerForTest(
                Arrays.asList(valid, valid, drifted));

        assertFalse(issues.isEmpty());
        assertTrue(containsFragment(issues, "duplicate handler ID"));
        assertTrue(containsFragment(issues, "duplicate handler class"));
        assertTrue(containsFragment(issues, "handler class/ID mismatch"));
        assertTrue(containsFragment(issues, "invalid class digest"));
    }

    @Test
    public void sourceStateTelemetryIsCanonicalAndImmutable() {
        Map<String, String> firstMetrics = new LinkedHashMap<String, String>();
        firstMetrics.put("zeta", "3");
        firstMetrics.put("alpha", "1");
        firstMetrics.put("middle", "2");
        PinnedNonRecipeHandlers.SourceState first =
                new PinnedNonRecipeHandlers.SourceState(
                        "fixture.Handler", "fixture-contract", firstMetrics);

        Map<String, String> reordered = new LinkedHashMap<String, String>();
        reordered.put("middle", "2");
        reordered.put("zeta", "3");
        reordered.put("alpha", "1");
        PinnedNonRecipeHandlers.SourceState second =
                new PinnedNonRecipeHandlers.SourceState(
                        "fixture.Handler", "fixture-contract", reordered);

        assertEquals(first.canonical(), second.canonical());
        assertTrue(first.canonical().indexOf(" alpha=")
                < first.canonical().indexOf(" middle="));
        assertTrue(first.canonical().indexOf(" middle=")
                < first.canonical().indexOf(" zeta="));
        firstMetrics.put("lateMutation", "must-not-leak");
        assertFalse(first.metrics().containsKey("lateMutation"));
        try {
            first.metrics().put("mutation", "forbidden");
            fail("source-state metrics must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(expected);
        }
    }

    @Test
    public void validatorSourceContainsNoItemQueryOrPopulationInvocation()
            throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/recipetree/neiexport1710/"
                        + "PinnedNonRecipeHandlers.java")), StandardCharsets.UTF_8);

        for (String forbiddenInvocation : Arrays.asList(
                ".getRecipeHandler(",
                ".getUsageHandler(",
                "prototype.newInstance(",
                "inheritedNewInstance.invoke(",
                "constructor.newInstance(",
                ".loadCraftingRecipes(",
                ".loadUsageRecipes(",
                ".populateStacks(",
                ".clearCache(")) {
            assertFalse("read-only validator must not invoke " + forbiddenInvocation,
                    source.contains(forbiddenInvocation));
        }
        assertTrue(source.contains(
                PinnedNonRecipeHandlers.READ_ONLY_VALIDATION_MODE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void telemetryRejectsBlankMetricsInsteadOfDroppingThem() {
        PinnedNonRecipeHandlers.SourceState ignored =
                new PinnedNonRecipeHandlers.SourceState(
                        "fixture.Handler", "fixture-contract",
                        Collections.singletonMap("sourceState", " "));
        assertNotNull(ignored);
    }

    private static boolean containsFragment(List<String> values, String fragment) {
        for (String value : values) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
