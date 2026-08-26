package com.recipetree.jeiexport112;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ZeroQuantityPolicyTest {
    private static final String FLUID = "net.minecraftforge.fluids.FluidStack";
    private static final String WILL =
            "kport.modularmagic.common.integration.jei.ingredient.DemonWill";
    private static final String ENERGY =
            "crazypants.enderio.base.integration.jei.energy.EnergyIngredient";

    @Test
    public void exactLegacyPrerequisitesPublishThroughCatalystThresholds() {
        assertDecision("binnie.genetics.incubator", "input", FLUID,
                ZeroQuantityPolicy.Kind.NON_CONSUMED, "1", "ZERO_PREREQUISITE");
        assertDecision("thermalexpansion.extruder", "input", FLUID,
                ZeroQuantityPolicy.Kind.NON_CONSUMED, "1000", "ZERO_PREREQUISITE");
        assertDecision("thermalexpansion.extruder_sedimentary", "input", FLUID,
                ZeroQuantityPolicy.Kind.NON_CONSUMED, "1000", "ZERO_PREREQUISITE");
        assertDecision("EIOTank", "input", FLUID,
                ZeroQuantityPolicy.Kind.NON_CONSUMED, "20", "ZERO_PREREQUISITE");
        assertDecision("modularmachinery.recipes.berserker_forge", "input", WILL,
                ZeroQuantityPolicy.Kind.NON_CONSUMED, "1", "ZERO_THRESHOLD");
    }

    @Test
    public void dynamicAbsentAndInvalidLegacyContextsStayDistinct() {
        assertDecision("hatchery.fertilizermixer.recipe", "input", FLUID,
                ZeroQuantityPolicy.Kind.DYNAMIC_FLOW, "0", "ZERO_UNKNOWN_FLOW");
        assertDecision("hatchery.fertilizermixer.recipe", "output", FLUID,
                ZeroQuantityPolicy.Kind.DYNAMIC_FLOW, "0", "ZERO_UNKNOWN_FLOW");
        assertDecision("hatchery.generator.recipe", "input", FLUID,
                ZeroQuantityPolicy.Kind.DYNAMIC_FLOW, "0", "ZERO_UNKNOWN_FLOW");
        assertDecision("thermalexpansion.centrifuge_mobs", "output", FLUID,
                ZeroQuantityPolicy.Kind.ABSENT_OUTPUT, null, "ZERO_ABSENT_OUTPUT");
        ZeroQuantityPolicy.Decision stirlingTierAbsence = assertDecision(
                "StirlingGenerator", "output", ENERGY,
                ZeroQuantityPolicy.Kind.ABSENT_OUTPUT, null, "ZERO_ABSENT_OUTPUT");
        assertTrue(stirlingTierAbsence.explanation.contains("generator-tier"));
        assertTrue(stirlingTierAbsence.explanation.contains("positive tier outputs"));
        ZeroQuantityPolicy.Decision nuclearChanceAbsence = assertDecision(
                "nuclearcraft_centrifuge", "output", FLUID,
                true, ZeroQuantityPolicy.Kind.ABSENT_ALTERNATIVE, null,
                "ZERO_ABSENT_ALTERNATIVE");
        assertTrue(nuclearChanceAbsence.explanation.contains("no-result branch"));
        assertTrue(nuclearChanceAbsence.explanation.contains("positive alternatives"));
        assertDecision("forestry.bottler", "output", FLUID,
                ZeroQuantityPolicy.Kind.INVALID_RECIPE, null, "ZERO_INVALID_RECIPE");
        assertDecision("EIOWC", "input", ENERGY,
                ZeroQuantityPolicy.Kind.INVALID_RECIPE, null, "ZERO_INVALID_RECIPE");
    }

    @Test
    public void everyUnclassifiedZeroRemainsUnsupported() {
        assertDecision("newmod.machine", "input", FLUID,
                ZeroQuantityPolicy.Kind.UNSUPPORTED, null, "ZERO_UNCLASSIFIED");
        assertDecision("EIOTank", "output", FLUID,
                ZeroQuantityPolicy.Kind.UNSUPPORTED, null, "ZERO_UNCLASSIFIED");
        assertDecision("EIOWC", "input", "newmod.Energy",
                ZeroQuantityPolicy.Kind.UNSUPPORTED, null, "ZERO_UNCLASSIFIED");
        assertDecision("nuclearcraft_centrifuge", "output", FLUID,
                false, ZeroQuantityPolicy.Kind.UNSUPPORTED, null, "ZERO_UNCLASSIFIED");
    }

    private static ZeroQuantityPolicy.Decision assertDecision(
            String category, String role, String type,
            ZeroQuantityPolicy.Kind expectedKind, String expectedAmount,
            String expectedCode) {
        return assertDecision(category, role, type, false, expectedKind, expectedAmount, expectedCode);
    }

    private static ZeroQuantityPolicy.Decision assertDecision(
            String category, String role, String type, boolean hasMatchingPositiveAlternative,
            ZeroQuantityPolicy.Kind expectedKind, String expectedAmount,
            String expectedCode) {
        ZeroQuantityPolicy.Decision decision = ZeroQuantityPolicy.classify(
                category, role, type, hasMatchingPositiveAlternative);
        assertEquals(expectedKind, decision.kind);
        assertEquals(expectedCode, decision.diagnosticCode);
        if (expectedAmount == null) {
            assertNull(decision.publishedAmount);
        } else {
            assertEquals(new BigDecimal(expectedAmount), decision.publishedAmount);
        }
        return decision;
    }
}
