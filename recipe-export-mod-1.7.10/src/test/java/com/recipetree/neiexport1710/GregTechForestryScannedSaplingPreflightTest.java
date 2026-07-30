package com.recipetree.neiexport1710;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class GregTechForestryScannedSaplingPreflightTest {
    @Test
    public void pinsExactGtnhScannerSourceAndVisibleCorpus() {
        assertEquals(
                "gregtech-forestry-scanned-sapling-source-bound-display-name-v1",
                GregTechForestryScannedSaplingPreflight.CONTRACT);
        assertEquals(
                "gtnh:29f254947fc19d609c5b58f71a881be1",
                GregTechForestryScannedSaplingPreflight.CATEGORY_ID);
        assertEquals("gregtech.nei.GTNEIDefaultHandler",
                GregTechForestryScannedSaplingPreflight.HANDLER_CLASS);
        assertEquals("gregtech.nei.GTNEIDefaultHandler",
                GregTechForestryScannedSaplingPreflight.HANDLER_ID);
        assertEquals("gt.recipe.scanner",
                GregTechForestryScannedSaplingPreflight.OPERATION);
        assertEquals("gt.recipe.scanner",
                GregTechForestryScannedSaplingPreflight.OVERLAY);
        assertEquals(298,
                GregTechForestryScannedSaplingPreflight.CATEGORY_RECIPE_COUNT);
        assertEquals(3, GregTechForestryScannedSaplingPreflight.SOURCE_INDEX);
        assertEquals(1,
                GregTechForestryScannedSaplingPreflight.EXPECTED_RECIPE_OCCURRENCES);
        assertEquals("output", GregTechForestryScannedSaplingPreflight.ROLE);
        assertEquals(0, GregTechForestryScannedSaplingPreflight.SLOT_INDEX);
        assertEquals(0, GregTechForestryScannedSaplingPreflight.ALTERNATIVE_INDEX);
        assertEquals(132,
                GregTechForestryScannedSaplingPreflight
                        .VISIBLE_GENETIC_INPUT_ALTERNATIVES);
        assertEquals(
                "bbb486874a1b478f140b5b7b9c5ed6b9217a67d1b1076c4dadd850eb62142da7",
                GregTechForestryScannedSaplingPreflight
                        .VISIBLE_GENETIC_INPUT_SORTED_KEY_LF_SHA256);
        assertEquals(
                "item|Forestry:sapling|meta=32767|nbt=-",
                GregTechForestryScannedSaplingPreflight.RAW_WILDCARD_INPUT_KEY);
        assertEquals("for.honey",
                GregTechForestryScannedSaplingPreflight.HONEY_FLUID_NAME);
        assertEquals(100, GregTechForestryScannedSaplingPreflight.HONEY_AMOUNT);
        assertEquals(
                "item|Forestry:sapling|meta=0|nbt="
                        + "2ef7c2d8cc838349c0e3f86e385f092334f4f432cde0d20c2c29af8d6435ca31",
                GregTechForestryScannedSaplingPreflight.SCANNED_SAPLING_CANONICAL_KEY);
    }

    @Test
    public void sortedKeyLfDigestIsOrderIndependentAndIncludesTrailingLf() {
        assertEquals(
                "26307933a65605a2e4a148be339117ad51a075a9dba82f848cdcda09a4e518a3",
                GregTechForestryScannedSaplingPreflight.sortedKeyLfSha256(
                        Arrays.asList("item|b", "item|a")));
        assertEquals(
                GregTechForestryScannedSaplingPreflight.sortedKeyLfSha256(
                        Arrays.asList("item|a", "item|b")),
                GregTechForestryScannedSaplingPreflight.sortedKeyLfSha256(
                        Arrays.asList("item|b", "item|a")));
    }

    @Test
    public void ignoresOrdinaryAlternativesWithoutConsumingSource() throws Exception {
        GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate gate = gate();
        assertFalse(GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate
                .requiresDecision("gtnh:ordinary", 12, "input", 1, 7,
                        "item|minecraft:stone|meta=0|nbt=-"));
        assertNull(authorizeOrdinary(gate));
        assertMissingAtFinish(gate);
    }

    @Test
    public void exactSourceProducesOneUseCatalogAuthorizationAndCompletes()
            throws Exception {
        GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate gate = gate();
        GregTechForestryScannedSaplingPreflight.DisplayNameAuthorization authorization =
                authorizeExact(gate);
        assertNotNull(authorization);
        assertEquals(GregTechForestryScannedSaplingPreflight.CONTRACT,
                authorization.contract());
        assertFalse(authorization.isClaimed());
        assertEquals("Scanned Sapling", authorization.claimDisplayName(
                GregTechForestryScannedSaplingPreflight.SCANNED_SAPLING_CANONICAL_KEY));
        assertTrue(authorization.isClaimed());
        gate.requireComplete();
    }

    @Test
    public void rejectsCanonicalKeyOutsidePinnedSource() throws Exception {
        GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate gate = gate();
        try {
            gate.authorizeForTest(
                    "gtnh:other", 3, "output", 0, 0,
                    GregTechForestryScannedSaplingPreflight
                            .SCANNED_SAPLING_CANONICAL_KEY);
            fail("expected source-bound rejection");
        } catch (ExportFailure expected) {
            assertEquals("ITEM_IDENTITY", expected.code);
        }
        assertMissingAtFinish(gate);
    }

    @Test
    public void rejectsWrongIdentityAtPinnedSource() throws Exception {
        GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate gate = gate();
        try {
            gate.authorizeForTest(
                    GregTechForestryScannedSaplingPreflight.CATEGORY_ID,
                    GregTechForestryScannedSaplingPreflight.SOURCE_INDEX,
                    GregTechForestryScannedSaplingPreflight.ROLE,
                    GregTechForestryScannedSaplingPreflight.SLOT_INDEX,
                    GregTechForestryScannedSaplingPreflight.ALTERNATIVE_INDEX,
                    "item|Forestry:sapling|meta=0|nbt=-");
            fail("expected identity drift rejection");
        } catch (ExportFailure expected) {
            assertEquals("ITEM_IDENTITY", expected.code);
        }
        assertMissingAtFinish(gate);
    }

    @Test
    public void rejectsDuplicateSourceConsumption() throws Exception {
        GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate gate = gate();
        GregTechForestryScannedSaplingPreflight.DisplayNameAuthorization authorization =
                authorizeExact(gate);
        authorization.claimDisplayName(
                GregTechForestryScannedSaplingPreflight.SCANNED_SAPLING_CANONICAL_KEY);
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
        GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate gate = gate();
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
        GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate firstGate = gate();
        GregTechForestryScannedSaplingPreflight.DisplayNameAuthorization first =
                authorizeExact(firstGate);
        try {
            first.claimDisplayName("item|Forestry:sapling|meta=0|nbt=-");
            fail("expected token identity rejection");
        } catch (ExportFailure expected) {
            assertEquals("ITEM_IDENTITY", expected.code);
        }
        assertFalse(first.isClaimed());

        assertEquals("Scanned Sapling", first.claimDisplayName(
                GregTechForestryScannedSaplingPreflight.SCANNED_SAPLING_CANONICAL_KEY));
        try {
            first.claimDisplayName(
                    GregTechForestryScannedSaplingPreflight.SCANNED_SAPLING_CANONICAL_KEY);
            fail("expected duplicate token claim rejection");
        } catch (ExportFailure expected) {
            assertEquals("ITEM_IDENTITY", expected.code);
        }
    }

    private static GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate gate() {
        return new GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate();
    }

    private static GregTechForestryScannedSaplingPreflight.DisplayNameAuthorization
            authorizeExact(
                    GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate gate)
                    throws ExportFailure {
        return gate.authorizeForTest(
                GregTechForestryScannedSaplingPreflight.CATEGORY_ID,
                GregTechForestryScannedSaplingPreflight.SOURCE_INDEX,
                GregTechForestryScannedSaplingPreflight.ROLE,
                GregTechForestryScannedSaplingPreflight.SLOT_INDEX,
                GregTechForestryScannedSaplingPreflight.ALTERNATIVE_INDEX,
                GregTechForestryScannedSaplingPreflight.SCANNED_SAPLING_CANONICAL_KEY);
    }

    private static GregTechForestryScannedSaplingPreflight.DisplayNameAuthorization
            authorizeOrdinary(
                    GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate gate)
                    throws ExportFailure {
        if (!GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate.requiresDecision(
                "gtnh:ordinary", 12, "input", 1, 7,
                "item|minecraft:stone|meta=0|nbt=-")) {
            return null;
        }
        return gate.authorizeForTest(
                "gtnh:ordinary", 12, "input", 1, 7,
                "item|minecraft:stone|meta=0|nbt=-");
    }

    private static void assertMissingAtFinish(
            GregTechForestryScannedSaplingPreflight.SourceAuthorizationGate gate)
            throws Exception {
        try {
            gate.requireComplete();
            fail("expected missing-consumption rejection");
        } catch (ExportFailure expected) {
            assertEquals("ITEM_IDENTITY", expected.code);
        }
    }
}
