package com.recipetree.neiexport1710;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class PinnedEmptyRecipeHandlersTest {
    @Test
    public void exactSourceBackedLedgerContainsOnlyTheTwentyPromotedHandlers() {
        List<String> actual = new ArrayList<String>();
        for (PinnedEmptyRecipeHandlers.Spec spec
                : PinnedEmptyRecipeHandlers.specsForTest()) {
            actual.add(spec.contractRow());
        }

        assertEquals(Arrays.asList(
                "advsolar.client.nei.MTRecipeHandler|"
                        + "gtnh:b341f365ec598b1de1b29d7cb220e127|Molecular Transformer|"
                        + "advsolar.utils.MTRecipeManager.transformerRecipes",
                "com.creativemd.creativecore.api.nei.NEIRecipeInfoHandler|"
                        + "gtnh:efee6636690a91bd6872061e56f8b9a1|crafting|"
                        + "CraftingManager[IRecipeInfo]",
                "de.katzenpapst.amunra.nei.recipehandler.ARCircuitFab|"
                        + "gtnh:bf00eca074beaf59e38398c095134942|galacticraft.circuits|"
                        + "NEIAmunRaConfig.getCircuitFabricatorRecipes()",
                "fox.spiteful.avaritia.compat.nei.CompressionHandler|"
                        + "gtnh:50bed5582200627d3c47cc9f2ecbb311|extreme_compression|"
                        + "CompressorManager.getRecipes()",
                "galaxyspace.core.nei.AssemblyMachineRecipeHandler|"
                        + "gtnh:4895de922a294ac794e2c3bc98d0961d|galaxyspace.assemblymachine|"
                        + "AssemblyRecipes.getRecipeList()+prototype.recipes",
                "ic2.neiIntegration.core.recipehandler.AdvShapelessRecipeHandler|"
                        + "gtnh:18a3e33fafe4bbf5e9e89c826370d0b5|crafting|"
                        + "CraftingManager[instanceof IC2 AdvShapelessRecipe; "
                        + "canShow; cacheable]",
                "ic2.neiIntegration.core.recipehandler.BlastFurnaceRecipeHandler|"
                        + "gtnh:8e4b18ec28873142ed3648785ba5137b|ic2.blockBlastFurnace|"
                        + "prototype.getRecipeList()",
                "ic2.neiIntegration.core.recipehandler.BlockCutterRecipeHandler|"
                        + "gtnh:27ee7caaac8900d9e2af2432f0d6493e|ic2.blockBlockCutter|"
                        + "prototype.getRecipeList()",
                "ic2.neiIntegration.core.recipehandler.CentrifugeRecipeHandler|"
                        + "gtnh:fe3f1695a1fdb622df6bb5be69c6147f|ic2.blockCentrifuge|"
                        + "prototype.getRecipeList()",
                "ic2.neiIntegration.core.recipehandler.CompressorRecipeHandler|"
                        + "gtnh:caded561c42e8c86671f791fe4d1d1d9|ic2.compressor|"
                        + "prototype.getRecipeList()",
                "ic2.neiIntegration.core.recipehandler.ExtractorRecipeHandler|"
                        + "gtnh:93f7d8c81ca5c8713ea5a6228491372c|ic2.extractor|"
                        + "prototype.getRecipeList()",
                "ic2.neiIntegration.core.recipehandler.MaceratorRecipeHandler|"
                        + "gtnh:ed887b00833278a76ffe6de88d2d8f2c|ic2.macerator|"
                        + "prototype.getRecipeList()",
                "ic2.neiIntegration.core.recipehandler.MetalFormerRecipeHandlerExtruding|"
                        + "gtnh:72ce2165e7556b701bf8653f61e03566|ic2.MetalFormer|"
                        + "prototype.getRecipeList()",
                "ic2.neiIntegration.core.recipehandler.MetalFormerRecipeHandlerRolling|"
                        + "gtnh:5a03860bc5d9ad54422817502e026976|ic2.MetalFormer|"
                        + "prototype.getRecipeList()",
                "ic2.neiIntegration.core.recipehandler.OreWashingRecipeHandler|"
                        + "gtnh:ae358c3bf9b1e4d49c4f86bcfcd07e83|"
                        + "ic2.blockOreWashingPlant|prototype.getRecipeList()",
                "logisticspipes.nei.NEISolderingStationRecipeManager|"
                        + "gtnh:c24b6bae304e56e161dd197a989721bc|solderingstation|"
                        + "SolderingStationRecipes.getRecipes()",
                "net.bdew.neiaddons.forestry.butterflies.ButterflyBreedingHandler|"
                        + "gtnh:ba80b526b4e00764afa8417f9b7e9f83|butterflybreeding|"
                        + "speciesRoot.getMutations(false)",
                "net.p455w0rd.wirelesscraftingterminal.integration.modules.NEIHelpers."
                        + "NEIAEShapedRecipeHandler|"
                        + "gtnh:0fac080545d4857e9831dacf921ffb38|crafting|"
                        + "CraftingManager[instanceof WCT ShapedRecipe; isEnabled]",
                "tonius.neiintegration.mods.railcraft.RecipeHandlerRockCrusher|"
                        + "gtnh:f234e57e943d9b9e7351bb438a080db5|railcraft.rockcrusher|"
                        + "RailcraftCraftingManager.rockCrusher.getRecipes()",
                "tonius.neiintegration.mods.railcraft.RecipeHandlerRollingMachineShapeless|"
                        + "gtnh:c470269e68872e2425dff03d794a9dfc|railcraft.rollingmachine|"
                        + "RailcraftCraftingManager.rollingMachine.getRecipeList()"
                        + "[instanceof shapeless; ore lists nonempty]"
        ), actual);
        assertTrue(PinnedEmptyRecipeHandlers.validateSpecLedgerForTest(
                PinnedEmptyRecipeHandlers.specsForTest()).isEmpty());
        for (PinnedEmptyRecipeHandlers.Spec spec
                : PinnedEmptyRecipeHandlers.specsForTest()) {
            assertTrue(spec.promotion != null);
        }
        assertEquals("excluded-empty-category",
                PinnedEmptyRecipeHandlers.POLICY_ACTION);
        assertEquals("empty-category:gtnh-2.8.4-source-backed-exact-zero-v1",
                PinnedEmptyRecipeHandlers.POLICY_CONTRACT);
    }

    @Test
    public void promotedEvidenceRowsReproduceTheReviewedRuntimeInventory() {
        List<String> rows = PinnedEmptyRecipeHandlers.promotedEvidenceRowsForTest();

        assertEquals(20, rows.size());
        assertEquals(PinnedEmptyRecipeHandlers.EXPECTED_INVENTORY_SHA256,
                PinnedEmptyRecipeHandlers.promotedInventoryFingerprintForTest(rows));
        for (String row : rows) {
            assertTrue(row.contains("adapter=\"STANDARD\""));
            assertTrue(row.contains("operationSource=\"transfer-rect\""));
            assertTrue(row.contains("prototypeCount=0"));
            assertTrue(row.contains("eligibleCount=0"));
            assertTrue(row.contains("status=observed issues=[]"));
        }
    }

    @Test
    public void inventoryGateRejectsMissingAndMutatedPromotions() throws Exception {
        Set<String> classes = new TreeSet<String>();
        for (PinnedEmptyRecipeHandlers.Spec spec
                : PinnedEmptyRecipeHandlers.specsForTest()) {
            classes.add(spec.handlerClass);
        }
        List<String> missing = new ArrayList<String>(
                PinnedEmptyRecipeHandlers.promotedEvidenceRowsForTest());
        missing.remove(0);
        assertInventoryFailure(classes, missing, "reviewed promotion rows differ");

        List<String> mutated = new ArrayList<String>(
                PinnedEmptyRecipeHandlers.promotedEvidenceRowsForTest());
        mutated.set(0, mutated.get(0).replace(
                "sourceRegistryCount=0", "sourceRegistryCount=1"));
        assertInventoryFailure(classes, mutated, "inventory SHA-256 drifted");

        Set<String> missingClass = new TreeSet<String>(classes);
        missingClass.remove(missingClass.iterator().next());
        assertInventoryFailure(missingClass,
                PinnedEmptyRecipeHandlers.promotedEvidenceRowsForTest(),
                "handler inventory drifted");
    }

    @Test
    public void inventoryFingerprintIsPermutationInvariantButMultiplicitySensitive() {
        String first = PinnedEmptyRecipeHandlers.stableMultisetFingerprint(
                "test-domain", Arrays.asList("row-a", "row-b"));
        String permuted = PinnedEmptyRecipeHandlers.stableMultisetFingerprint(
                "test-domain", Arrays.asList("row-b", "row-a"));
        String duplicated = PinnedEmptyRecipeHandlers.stableMultisetFingerprint(
                "test-domain", Arrays.asList("row-a", "row-b", "row-a"));
        String eligibleDomain = PinnedEmptyRecipeHandlers.stableMultisetFingerprint(
                "test-domain/eligible", Arrays.asList("row-a", "row-b"));

        assertEquals(first, permuted);
        assertNotEquals(first, duplicated);
        assertNotEquals(first, eligibleDomain);
    }

    @Test
    public void sourcePartitionUsesInstanceofAndAppliesEligibilityToSubclasses() throws Exception {
        final Candidate visibleBase = new Candidate(true);
        final Candidate visibleSubclass = new CandidateSubclass(true);
        final Candidate hiddenSubclass = new CandidateSubclass(false);

        PinnedEmptyRecipeHandlers.FilteredRows rows =
                PinnedEmptyRecipeHandlers.partitionAssignableRows(
                        Arrays.<Object>asList(
                                "unrelated", visibleBase, visibleSubclass, hiddenSubclass),
                        Candidate.class,
                        new PinnedEmptyRecipeHandlers.EligibilityProbe() {
                            @Override
                            public boolean isEligible(Object row) {
                                return ((Candidate) row).isEnabled();
                            }
                        });

        assertEquals(
                Arrays.<Object>asList(visibleBase, visibleSubclass, hiddenSubclass),
                rows.rawRows);
        assertEquals(
                Arrays.<Object>asList(visibleBase, visibleSubclass),
                rows.eligibleRows);
    }

    @Test
    public void railcraftOreEligibilityMatchesPinnedListOnlyEmptyCheck() {
        assertTrue(PinnedEmptyRecipeHandlers.railcraftOreInputsEligible(
                Arrays.<Object>asList("oreName", Arrays.asList("candidate"), null)));
        assertFalse(PinnedEmptyRecipeHandlers.railcraftOreInputsEligible(
                Arrays.<Object>asList("oreName", Collections.emptyList())));

        // The pinned bytecode checks instanceof List, not Collection generally.
        assertTrue(PinnedEmptyRecipeHandlers.railcraftOreInputsEligible(
                Arrays.<Object>asList(new HashSet<Object>())));
    }

    private static void assertInventoryFailure(
            Set<String> classes, List<String> rows, String expectedMessage)
            throws Exception {
        try {
            PinnedEmptyRecipeHandlers.requirePromotedInventory(classes, rows);
            fail("expected promoted inventory mismatch");
        } catch (ExportFailure failure) {
            assertEquals("HANDLER_UNLOADED", failure.code);
            assertTrue(failure.getMessage().contains(expectedMessage));
        }
    }

    private static class Candidate {
        private final boolean enabled;

        Candidate(boolean enabled) {
            this.enabled = enabled;
        }

        boolean isEnabled() {
            return enabled;
        }
    }

    private static final class CandidateSubclass extends Candidate {
        CandidateSubclass(boolean enabled) {
            super(enabled);
        }
    }
}
