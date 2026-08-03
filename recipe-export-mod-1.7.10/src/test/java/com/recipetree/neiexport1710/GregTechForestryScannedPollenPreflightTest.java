package com.recipetree.neiexport1710;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class GregTechForestryScannedPollenPreflightTest {
    @Test
    public void pinsExactGtnhScannerSourceAndVisibleCorpus() {
        assertEquals(
                "gregtech-forestry-scanned-pollen-source-bound-display-name-v1",
                GregTechForestryScannedPollenPreflight.CONTRACT);
        assertEquals(
                "gtnh:29f254947fc19d609c5b58f71a881be1",
                GregTechForestryScannedPollenPreflight.CATEGORY_ID);
        assertEquals("gregtech.nei.GTNEIDefaultHandler",
                GregTechForestryScannedPollenPreflight.HANDLER_CLASS);
        assertEquals("gregtech.nei.GTNEIDefaultHandler",
                GregTechForestryScannedPollenPreflight.HANDLER_ID);
        assertEquals("gt.recipe.scanner",
                GregTechForestryScannedPollenPreflight.OPERATION);
        assertEquals("gt.recipe.scanner",
                GregTechForestryScannedPollenPreflight.OVERLAY);
        assertEquals(298,
                GregTechForestryScannedPollenPreflight.CATEGORY_RECIPE_COUNT);
        assertEquals(8, GregTechForestryScannedPollenPreflight.SOURCE_INDEX);
        assertEquals(1,
                GregTechForestryScannedPollenPreflight.EXPECTED_RECIPE_OCCURRENCES);
        assertEquals("output", GregTechForestryScannedPollenPreflight.ROLE);
        assertEquals(0, GregTechForestryScannedPollenPreflight.SLOT_INDEX);
        assertEquals(0, GregTechForestryScannedPollenPreflight.ALTERNATIVE_INDEX);
        assertEquals(132,
                GregTechForestryScannedPollenPreflight
                        .VISIBLE_GENETIC_INPUT_ALTERNATIVES);
        assertEquals(
                "9c4c911cf12afc90588044a3255d648747ddfdacb2a01f4f8c33d9ff1443eaf5",
                GregTechForestryScannedPollenPreflight
                        .VISIBLE_GENETIC_INPUT_SORTED_KEY_LF_SHA256);
        assertEquals(
                "item|Forestry:pollenFertile|meta=32767|nbt=-",
                GregTechForestryScannedPollenPreflight.RAW_WILDCARD_INPUT_KEY);
        assertEquals("for.honey",
                GregTechForestryScannedPollenPreflight.HONEY_FLUID_NAME);
        assertEquals(100, GregTechForestryScannedPollenPreflight.HONEY_AMOUNT);
        assertEquals(
                "item|Forestry:pollenFertile|meta=0|nbt="
                        + "0357c93060885ca4cb111bf921d3f6d9deb31eb0891f92218fe2d306b8b8dfae",
                GregTechForestryScannedPollenPreflight.SCANNED_POLLEN_CANONICAL_KEY);
    }

    @Test
    public void sortedKeyLfDigestIsOrderIndependentAndIncludesTrailingLf() {
        assertEquals(
                "26307933a65605a2e4a148be339117ad51a075a9dba82f848cdcda09a4e518a3",
                GregTechForestryScannedPollenPreflight.sortedKeyLfSha256(
                        Arrays.asList("item|b", "item|a")));
        assertEquals(
                GregTechForestryScannedPollenPreflight.sortedKeyLfSha256(
                        Arrays.asList("item|a", "item|b")),
                GregTechForestryScannedPollenPreflight.sortedKeyLfSha256(
                        Arrays.asList("item|b", "item|a")));
    }

    @Test
    public void ignoresOrdinaryAlternativesWithoutConsumingSource() throws Exception {
        GregTechForestryScannedPollenPreflight.SourceAuthorizationGate gate = gate();
        assertFalse(GregTechForestryScannedPollenPreflight.SourceAuthorizationGate
                .requiresDecision("gtnh:ordinary", 12, "input", 1, 7,
                        "item|minecraft:stone|meta=0|nbt=-"));
        assertNull(authorizeOrdinary(gate));
        assertMissingAtFinish(gate);
    }

    @Test
    public void exactSourceProducesOneUseCatalogAuthorizationAndCompletes()
            throws Exception {
        GregTechForestryScannedPollenPreflight.SourceAuthorizationGate gate = gate();
        GregTechForestryScannedPollenPreflight.DisplayNameAuthorization authorization =
                authorizeExact(gate);
        assertNotNull(authorization);
        assertEquals(GregTechForestryScannedPollenPreflight.CONTRACT,
                authorization.contract());
        assertFalse(authorization.isClaimed());
        assertEquals("Scanned Pollen", authorization.claimDisplayName(
                GregTechForestryScannedPollenPreflight.SCANNED_POLLEN_CANONICAL_KEY));
        assertTrue(authorization.isClaimed());
        gate.requireComplete();
    }

    @Test
    public void rejectsCanonicalKeyOutsidePinnedSource() throws Exception {
        GregTechForestryScannedPollenPreflight.SourceAuthorizationGate gate = gate();
        try {
            gate.authorizeForTest(
                    "gtnh:other", 3, "output", 0, 0,
                    GregTechForestryScannedPollenPreflight
                            .SCANNED_POLLEN_CANONICAL_KEY);
            fail("expected source-bound rejection");
        } catch (ExportFailure expected) {
            assertEquals("ITEM_IDENTITY", expected.code);
        }
        assertMissingAtFinish(gate);
    }

    @Test
    public void rejectsWrongIdentityAtPinnedSource() throws Exception {
        GregTechForestryScannedPollenPreflight.SourceAuthorizationGate gate = gate();
        try {
            gate.authorizeForTest(
                    GregTechForestryScannedPollenPreflight.CATEGORY_ID,
                    GregTechForestryScannedPollenPreflight.SOURCE_INDEX,
                    GregTechForestryScannedPollenPreflight.ROLE,
                    GregTechForestryScannedPollenPreflight.SLOT_INDEX,
                    GregTechForestryScannedPollenPreflight.ALTERNATIVE_INDEX,
                    "item|Forestry:pollenFertile|meta=0|nbt=-");
            fail("expected identity drift rejection");
        } catch (ExportFailure expected) {
            assertEquals("ITEM_IDENTITY", expected.code);
        }
        assertMissingAtFinish(gate);
    }

    @Test
    public void rejectsDuplicateSourceConsumption() throws Exception {
        GregTechForestryScannedPollenPreflight.SourceAuthorizationGate gate = gate();
        GregTechForestryScannedPollenPreflight.DisplayNameAuthorization authorization =
                authorizeExact(gate);
        authorization.claimDisplayName(
                GregTechForestryScannedPollenPreflight.SCANNED_POLLEN_CANONICAL_KEY);
        try {
            authorizeExact(gate);
            fail("expected duplicate consumption rejection");
        } catch (ExportFailure expected) {
            assertEquals("ITEM_IDENTITY", expected.code);
        }
        gate.requireComplete();
    }

    @Test
    public void finishRejectsAnUnclaimedCatalogAuthorization() throws Exception {
        GregTechForestryScannedPollenPreflight.SourceAuthorizationGate gate = gate();
        authorizeExact(gate);
        try {
            gate.requireComplete();
            fail("expected unclaimed catalog authorization rejection");
        } catch (ExportFailure expected) {
            assertEquals("ITEM_IDENTITY", expected.code);
        }
    }

    @Test
    public void authorizationTokenCannotBeClaimedTwiceOrForAnotherKey()
            throws Exception {
        GregTechForestryScannedPollenPreflight.SourceAuthorizationGate firstGate = gate();
        GregTechForestryScannedPollenPreflight.DisplayNameAuthorization first =
                authorizeExact(firstGate);
        try {
            first.claimDisplayName("item|Forestry:pollenFertile|meta=0|nbt=-");
            fail("expected token identity rejection");
        } catch (ExportFailure expected) {
            assertEquals("ITEM_IDENTITY", expected.code);
        }
        assertFalse(first.isClaimed());

        assertEquals("Scanned Pollen", first.claimDisplayName(
                GregTechForestryScannedPollenPreflight.SCANNED_POLLEN_CANONICAL_KEY));
        try {
            first.claimDisplayName(
                    GregTechForestryScannedPollenPreflight.SCANNED_POLLEN_CANONICAL_KEY);
            fail("expected duplicate token claim rejection");
        } catch (ExportFailure expected) {
            assertEquals("ITEM_IDENTITY", expected.code);
        }
    }

    private static GregTechForestryScannedPollenPreflight.SourceAuthorizationGate gate() {
        return new GregTechForestryScannedPollenPreflight.SourceAuthorizationGate();
    }

    private static GregTechForestryScannedPollenPreflight.DisplayNameAuthorization
            authorizeExact(
                    GregTechForestryScannedPollenPreflight.SourceAuthorizationGate gate)
                    throws ExportFailure {
        return gate.authorizeForTest(
                GregTechForestryScannedPollenPreflight.CATEGORY_ID,
                GregTechForestryScannedPollenPreflight.SOURCE_INDEX,
                GregTechForestryScannedPollenPreflight.ROLE,
                GregTechForestryScannedPollenPreflight.SLOT_INDEX,
                GregTechForestryScannedPollenPreflight.ALTERNATIVE_INDEX,
                GregTechForestryScannedPollenPreflight.SCANNED_POLLEN_CANONICAL_KEY);
    }

    private static GregTechForestryScannedPollenPreflight.DisplayNameAuthorization
            authorizeOrdinary(
                    GregTechForestryScannedPollenPreflight.SourceAuthorizationGate gate)
                    throws ExportFailure {
        if (!GregTechForestryScannedPollenPreflight.SourceAuthorizationGate.requiresDecision(
                "gtnh:ordinary", 12, "input", 1, 7,
                "item|minecraft:stone|meta=0|nbt=-")) {
            return null;
        }
        return gate.authorizeForTest(
                "gtnh:ordinary", 12, "input", 1, 7,
                "item|minecraft:stone|meta=0|nbt=-");
    }

    private static void assertMissingAtFinish(
            GregTechForestryScannedPollenPreflight.SourceAuthorizationGate gate)
            throws Exception {
        try {
            gate.requireComplete();
            fail("expected missing-consumption rejection");
        } catch (ExportFailure expected) {
            assertEquals("ITEM_IDENTITY", expected.code);
        }
    }
}
