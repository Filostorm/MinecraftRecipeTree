package com.recipetree.jeiexport112;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.Test;
import org.junit.BeforeClass;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecipePhaseZeroAlternativeTest {
    private static final ResourceLocation STILL = new ResourceLocation("test", "still");
    private static final ResourceLocation FLOWING = new ResourceLocation("test", "flowing");

    @BeforeClass
    public static void initializeVanillaRegistries() {
        Bootstrap.register();
    }

    @Test
    public void acceptsOnlyPositiveSameFluidAndNbtSibling() {
        Fluid expectedFluid = new Fluid("expected", STILL, FLOWING);
        Fluid otherFluid = new Fluid("other", STILL, FLOWING);
        assertTrue(FluidRegistry.registerFluid(expectedFluid));
        assertTrue(FluidRegistry.registerFluid(otherFluid));
        FluidStack zero = tagged(expectedFluid, 0, "grade-a");

        assertTrue(RecipePhase.hasMatchingPositiveFluidAlternative(
                zero, Arrays.asList(zero, tagged(expectedFluid, 20, "grade-a"))));
        assertFalse(RecipePhase.hasMatchingPositiveFluidAlternative(
                zero, Arrays.asList(zero, tagged(expectedFluid, 20, "grade-b"))));
        assertFalse(RecipePhase.hasMatchingPositiveFluidAlternative(
                zero, Arrays.asList(zero, tagged(otherFluid, 20, "grade-a"))));
        assertFalse(RecipePhase.hasMatchingPositiveFluidAlternative(
                zero, Collections.singletonList(tagged(expectedFluid, 0, "grade-a"))));
    }

    private static FluidStack tagged(Fluid fluid, int amount, String grade) {
        FluidStack stack = new FluidStack(fluid, amount);
        stack.tag = new NBTTagCompound();
        stack.tag.setString("grade", grade);
        return stack;
    }
}
