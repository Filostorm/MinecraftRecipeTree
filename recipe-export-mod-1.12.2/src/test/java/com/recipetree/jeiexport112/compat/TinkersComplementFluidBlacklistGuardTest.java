package com.recipetree.jeiexport112.compat;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class TinkersComplementFluidBlacklistGuardTest {
    private String previous;

    @Before
    public void reset() {
        previous = System.getProperty(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY
        );
        System.setProperty(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY, "true"
        );
        TinkersComplementFluidBlacklistGuard.resetForTests();
    }

    @After
    public void restore() {
        TinkersComplementFluidBlacklistGuard.resetForTests();
        if (previous == null) {
            System.clearProperty(TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY);
        } else {
            System.setProperty(
                    TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY, previous
            );
        }
    }

    @Test
    public void acceptsOnlyTheAuditedRuntimeTuple() {
        TinkersComplementFluidBlacklistGuard.validateRuntimeVersions(
                "1.12.2", "14.23.5.2860", "1.12.2-0.4.3", "4.25.0"
        );
    }

    @Test
    public void rejectsEveryRuntimeIdentityDrift() {
        expectVersionFailure("1.12.1", "14.23.5.2860", "1.12.2-0.4.3", "4.25.0");
        expectVersionFailure("1.12.2", "14.23.5.2859", "1.12.2-0.4.3", "4.25.0");
        expectVersionFailure("1.12.2", "14.23.5.2860", "1.12.2-0.4.2", "4.25.0");
        expectVersionFailure("1.12.2", "14.23.5.2860", "1.12.2-0.4.3", "4.25.1");
        expectVersionFailure("1.12.2", "14.23.5.2860", "<missing>", "4.25.0");
    }

    @Test
    public void nonNullInverseRegistryNameUsesTheNativePathWithoutCounting() {
        Fluid fluid = fluid("valid");

        assertTrue(
                TinkersComplementFluidBlacklistGuard
                        .shouldRunNativeBlacklistAfterRuntimeValidation(fluid, fluid, "valid")
        );
        assertTrue(
                TinkersComplementFluidBlacklistGuard
                        .shouldRunNativeBlacklistAfterRuntimeValidation(fluid, fluid, "")
        );
        assertTrue(TinkersComplementFluidBlacklistGuard.skippedUnboundFluidCount() == 0);
    }

    @Test
    public void nullInverseRegistryNameIsExplicitlyCountedAndSkipped() {
        assertFalse(
                TinkersComplementFluidBlacklistGuard
                        .shouldRunNativeBlacklistAfterRuntimeValidation(
                                fluid("late_alternate"), fluid("resolved_alternate"), null
                        )
        );
        assertTrue(TinkersComplementFluidBlacklistGuard.skippedUnboundFluidCount() == 1);
    }

    @Test
    public void unexpectedNullFluidObjectIsExplicitlyCountedAndSkipped() {
        assertFalse(
                TinkersComplementFluidBlacklistGuard
                        .shouldRunNativeBlacklistAfterRuntimeValidation(null, null, null)
        );
        assertTrue(TinkersComplementFluidBlacklistGuard.skippedUnboundFluidCount() == 1);
    }

    @Test(expected = IllegalStateException.class)
    public void impossibleNullFluidWithNameIsRejected() {
        TinkersComplementFluidBlacklistGuard
                .shouldRunNativeBlacklistAfterRuntimeValidation(null, null, "impossible");
    }

    private static Fluid fluid(String name) {
        ResourceLocation texture = new ResourceLocation("jeiexport", "fluids/" + name);
        return new Fluid(name, texture, texture);
    }

    private static void expectVersionFailure(
            String minecraft, String forge, String tcomplement, String hei
    ) {
        try {
            TinkersComplementFluidBlacklistGuard.validateRuntimeVersions(
                    minecraft, forge, tcomplement, hei
            );
            fail("Expected runtime identity drift to fail closed");
        } catch (IllegalStateException expected) {
            // Exact refusal is the behavior under test.
        }
    }
}
