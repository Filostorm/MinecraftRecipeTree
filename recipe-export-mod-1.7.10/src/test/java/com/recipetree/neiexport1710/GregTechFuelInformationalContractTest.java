package com.recipetree.neiexport1710;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GregTechFuelInformationalContractTest {
    private static final String OVERLAY = "gt.recipe.hugenaquadahreactor";

    @Test
    public void acceptsCanonicalItemAndFluidFuelSinkRows() {
        assertTrue(new Binding().matches());

        Binding fluidOnly = new Binding();
        fluidOnly.rawItemInputs = 0;
        fluidOnly.rawFluidInputs = 1;
        assertTrue(fluidOnly.matches());

        Binding anotherRegisteredFuelMap = new Binding();
        anotherRegisteredFuelMap.overlay = "gt.recipe.rocketenginefuel";
        anotherRegisteredFuelMap.unlocalizedName = anotherRegisteredFuelMap.overlay;
        assertTrue(anotherRegisteredFuelMap.matches());
    }

    @Test
    public void rejectsWrongRuntimeClassesIncludingBackendSubclasses() {
        Binding wrongHandler = new Binding();
        wrongHandler.handler = new OtherHandler();
        assertFalse(wrongHandler.matches());

        Binding wrongCachedRecipe = new Binding();
        wrongCachedRecipe.cachedRecipe = new OtherCachedRecipe();
        assertFalse(wrongCachedRecipe.matches());

        Binding wrongRecipeMap = new Binding();
        wrongRecipeMap.recipeMap = new OtherRecipeMap();
        wrongRecipeMap.handlerRecipeMap = wrongRecipeMap.recipeMap;
        assertFalse(wrongRecipeMap.matches());

        Binding wrongBackend = new Binding();
        wrongBackend.backend = new OtherFuelBackend();
        wrongBackend.mapBackend = wrongBackend.backend;
        assertFalse(wrongBackend.matches());
    }

    @Test
    public void rejectsBrokenSourceAndOwnerIdentityLinks() {
        Binding unverifiedSourceIndex = new Binding();
        unverifiedSourceIndex.sourceIndexIdentityVerified = false;
        assertFalse(unverifiedSourceIndex.matches());

        Binding handlerMapCopy = new Binding();
        handlerMapCopy.handlerRecipeMap = new RecipeMapFixture();
        assertFalse(handlerMapCopy.matches());

        Binding mapBackendCopy = new Binding();
        mapBackendCopy.mapBackend = new FuelBackendFixture();
        assertFalse(mapBackendCopy.matches());
    }

    @Test
    public void rejectsOverlayDriftFromTheRecipeMapName() {
        Binding wrongOverlay = new Binding();
        wrongOverlay.overlay = "gt.recipe.extrahugenaquadahreactor";
        assertFalse(wrongOverlay.matches());

        Binding wrongMapName = new Binding();
        wrongMapName.unlocalizedName = "gt.recipe.largenaquadahreactor";
        assertFalse(wrongMapName.matches());
    }

    @Test
    public void requiresBothRawAndNeiInputEvidence() {
        Binding noRawInputs = new Binding();
        noRawInputs.rawItemInputs = 0;
        noRawInputs.rawFluidInputs = 0;
        assertFalse(noRawInputs.matches());

        Binding noNeiIngredients = new Binding();
        noNeiIngredients.neiIngredients = 0;
        assertFalse(noNeiIngredients.matches());
    }

    @Test
    public void requiresEveryRawAndNeiOutputChannelToBeEmpty() {
        Binding rawItemOutput = new Binding();
        rawItemOutput.rawItemOutputs = 1;
        assertFalse(rawItemOutput.matches());

        Binding rawFluidOutput = new Binding();
        rawFluidOutput.rawFluidOutputs = 1;
        assertFalse(rawFluidOutput.matches());

        Binding neiResult = new Binding();
        neiResult.resultPresent = true;
        assertFalse(neiResult.matches());

        Binding neiOtherStack = new Binding();
        neiOtherStack.neiOtherStacks = 1;
        assertFalse(neiOtherStack.matches());
    }

    @Test
    public void rejectsNonpositiveFuelValuesAndSpecialItemPayloads() {
        Binding zeroFuelValue = new Binding();
        zeroFuelValue.specialValue = 0;
        assertFalse(zeroFuelValue.matches());

        Binding negativeFuelValue = new Binding();
        negativeFuelValue.specialValue = -1;
        assertFalse(negativeFuelValue.matches());

        Binding specialItemPayload = new Binding();
        specialItemPayload.specialItemPresent = true;
        assertFalse(specialItemPayload.matches());
    }

    @Test
    public void requiresVisibleEnabledRecipesAndNeiRegistration() {
        Binding disabled = new Binding();
        disabled.enabled = false;
        assertFalse(disabled.matches());

        Binding hidden = new Binding();
        hidden.hidden = true;
        assertFalse(hidden.matches());

        Binding notRegisteredWithNei = new Binding();
        notRegisteredWithNei.registerNei = false;
        assertFalse(notRegisteredWithNei.matches());
    }

    @Test
    public void rejectsImpossibleNegativeCardinalities() {
        Binding negativeItemInput = new Binding();
        negativeItemInput.rawItemInputs = -1;
        assertFalse(negativeItemInput.matches());

        Binding negativeFluidInput = new Binding();
        negativeFluidInput.rawFluidInputs = -1;
        assertFalse(negativeFluidInput.matches());

        Binding negativeNeiInput = new Binding();
        negativeNeiInput.neiIngredients = -1;
        assertFalse(negativeNeiInput.matches());

        Binding negativeItemOutput = new Binding();
        negativeItemOutput.rawItemOutputs = -1;
        assertFalse(negativeItemOutput.matches());

        Binding negativeFluidOutput = new Binding();
        negativeFluidOutput.rawFluidOutputs = -1;
        assertFalse(negativeFluidOutput.matches());

        Binding negativeNeiOther = new Binding();
        negativeNeiOther.neiOtherStacks = -1;
        assertFalse(negativeNeiOther.matches());
    }

    @Test
    public void acceptsExactLargeBoilerSolidFuelRowsIncludingZeroSpecialValue() {
        LargeBoilerSolidBinding minimumFuel = new LargeBoilerSolidBinding();
        minimumFuel.resolvedFurnaceFuelValue = 400;
        minimumFuel.specialValue = 0;
        assertTrue(minimumFuel.matches());

        LargeBoilerSolidBinding upperZeroQuotient = new LargeBoilerSolidBinding();
        upperZeroQuotient.resolvedFurnaceFuelValue = 1599;
        upperZeroQuotient.specialValue = 0;
        assertTrue(upperZeroQuotient.matches());

        LargeBoilerSolidBinding positiveQuotient = new LargeBoilerSolidBinding();
        positiveQuotient.resolvedFurnaceFuelValue = 1600;
        positiveQuotient.specialValue = 1;
        assertTrue(positiveQuotient.matches());
    }

    @Test
    public void rejectsLargeBoilerRowsOutsideTheExactSolidFuelFactoryShape() {
        LargeBoilerSolidBinding belowThreshold = new LargeBoilerSolidBinding();
        belowThreshold.resolvedFurnaceFuelValue = 399;
        assertFalse(belowThreshold.matches());

        LargeBoilerSolidBinding wrongQuotient = new LargeBoilerSolidBinding();
        wrongQuotient.specialValue = 1;
        assertFalse(wrongQuotient.matches());

        LargeBoilerSolidBinding fluidInput = new LargeBoilerSolidBinding();
        fluidInput.rawFluidInputs = 1;
        assertFalse(fluidInput.matches());

        LargeBoilerSolidBinding wrongDuration = new LargeBoilerSolidBinding();
        wrongDuration.duration = 2;
        assertFalse(wrongDuration.matches());

        LargeBoilerSolidBinding wrongEut = new LargeBoilerSolidBinding();
        wrongEut.eut = 1;
        assertFalse(wrongEut.matches());

        LargeBoilerSolidBinding fake = new LargeBoilerSolidBinding();
        fake.fakeRecipe = true;
        assertFalse(fake.matches());

        LargeBoilerSolidBinding shortDescription = new LargeBoilerSolidBinding();
        shortDescription.neiDescriptionLines = 4;
        assertFalse(shortDescription.matches());

        LargeBoilerSolidBinding blankDescription = new LargeBoilerSolidBinding();
        blankDescription.allNeiDescriptionLinesNonblank = false;
        assertFalse(blankDescription.matches());
    }

    private static final class Binding {
        final GregTechFuelInformationalContract.Pins pins =
                new GregTechFuelInformationalContract.Pins(
                        HandlerFixture.class,
                        CachedRecipeFixture.class,
                        RecipeMapFixture.class,
                        FuelBackendFixture.class);

        Object handler = new HandlerFixture();
        Object cachedRecipe = new CachedRecipeFixture();
        boolean sourceIndexIdentityVerified = true;
        Object recipeMap = new RecipeMapFixture();
        Object handlerRecipeMap = recipeMap;
        Object backend = new FuelBackendFixture();
        Object mapBackend = backend;
        String overlay = OVERLAY;
        String unlocalizedName = OVERLAY;
        boolean registerNei = true;
        int rawItemInputs = 1;
        int rawFluidInputs;
        int neiIngredients = 1;
        int rawItemOutputs;
        int rawFluidOutputs;
        boolean resultPresent;
        int neiOtherStacks;
        int specialValue = 31_250;
        boolean specialItemPresent;
        boolean enabled = true;
        boolean hidden;

        boolean matches() {
            GregTechFuelInformationalContract.Observation observation =
                    new GregTechFuelInformationalContract.Observation(
                            handler,
                            cachedRecipe,
                            sourceIndexIdentityVerified,
                            recipeMap,
                            handlerRecipeMap,
                            backend,
                            mapBackend,
                            overlay,
                            unlocalizedName,
                            registerNei,
                            rawItemInputs,
                            rawFluidInputs,
                            neiIngredients,
                            rawItemOutputs,
                            rawFluidOutputs,
                            resultPresent,
                            neiOtherStacks,
                            specialValue,
                            specialItemPresent,
                            enabled,
                            hidden);
            return GregTechFuelInformationalContract.isCanonicalFuelRowBinding(
                    pins, observation);
        }
    }

    private static final class LargeBoilerSolidBinding {
        int rawItemInputs = 1;
        int rawFluidInputs;
        int neiIngredients = 1;
        int rawItemOutputs;
        int rawFluidOutputs;
        boolean resultPresent;
        int neiOtherStacks;
        int duration = 1;
        int eut;
        int specialValue;
        int resolvedFurnaceFuelValue = 400;
        boolean specialItemPresent;
        boolean fakeRecipe;
        boolean enabled = true;
        boolean hidden;
        int neiDescriptionLines = 5;
        boolean allNeiDescriptionLinesNonblank = true;

        boolean matches() {
            return GregTechFuelInformationalContract.isCanonicalLargeBoilerSolidFuelRow(
                    new GregTechFuelInformationalContract.LargeBoilerSolidObservation(
                            rawItemInputs,
                            rawFluidInputs,
                            neiIngredients,
                            rawItemOutputs,
                            rawFluidOutputs,
                            resultPresent,
                            neiOtherStacks,
                            duration,
                            eut,
                            specialValue,
                            resolvedFurnaceFuelValue,
                            specialItemPresent,
                            fakeRecipe,
                            enabled,
                            hidden,
                            neiDescriptionLines,
                            allNeiDescriptionLinesNonblank));
        }
    }

    private static class HandlerFixture {
    }

    private static final class OtherHandler extends HandlerFixture {
    }

    private static class CachedRecipeFixture {
    }

    private static final class OtherCachedRecipe extends CachedRecipeFixture {
    }

    private static class RecipeMapFixture {
    }

    private static final class OtherRecipeMap extends RecipeMapFixture {
    }

    private static class FuelBackendFixture {
    }

    private static final class OtherFuelBackend extends FuelBackendFixture {
    }
}
