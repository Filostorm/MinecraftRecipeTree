package com.recipetree.neiexport1710;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class Ic2FluidCannerWidgetScreenAdapterTest {
    @Test
    public void pinsExactGtnhIc2Contract() {
        assertEquals(
                "ic2.neiIntegration.core.recipehandler.FluidCannerRecipeHandler",
                Ic2FluidCannerWidgetScreenAdapter.HANDLER);
        assertEquals(
                "ic2.fluidcanner",
                Ic2FluidCannerWidgetScreenAdapter.LOAD_IDENTIFIER);
        assertEquals(
                "ic2-2.2.828-fluid-canner-nei-screen-context-v2",
                Ic2FluidCannerWidgetScreenAdapter.CONTRACT);
    }
}
