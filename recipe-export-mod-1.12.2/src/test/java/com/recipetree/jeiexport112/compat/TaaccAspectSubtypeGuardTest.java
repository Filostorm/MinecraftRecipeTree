package com.recipetree.jeiexport112.compat;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class TaaccAspectSubtypeGuardTest {
    private String previous;

    @Before
    public void reset() {
        previous = System.getProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY);
        System.setProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY, "true");
        TaaccAspectSubtypeGuard.resetForTests();
    }

    @After
    public void restore() {
        TaaccAspectSubtypeGuard.resetForTests();
        if (previous == null) {
            System.clearProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY);
        } else {
            System.setProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY, previous);
        }
    }

    @Test
    public void acceptsOnlyTheAuditedRuntimeTuple() {
        TaaccAspectSubtypeGuard.validateRuntimeVersions(
                "1.12.2", "14.23.5.2860", "0.0.3", "4.25.0"
        );
    }

    @Test
    public void rejectsEveryRuntimeIdentityDrift() {
        expectVersionFailure("1.12.1", "14.23.5.2860", "0.0.3", "4.25.0");
        expectVersionFailure("1.12.2", "14.23.5.2859", "0.0.3", "4.25.0");
        expectVersionFailure("1.12.2", "14.23.5.2860", "0.0.4", "4.25.0");
        expectVersionFailure("1.12.2", "14.23.5.2860", "0.0.3", "4.25.1");
        expectVersionFailure("1.12.2", "14.23.5.2860", "<missing>", "4.25.0");
    }

    @Test
    public void presentAspectUsesNativeNbtGetStringExactly() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setString("Aspect", "aer");

        assertEquals(
                compound.getString("Aspect"),
                TaaccAspectSubtypeGuard.getStringOrEmptyAfterRuntimeValidation(
                        compound, "Aspect"
                )
        );
        assertEquals(0, TaaccAspectSubtypeGuard.missingAspectNormalizationCount());
    }

    @Test
    public void presentCompoundWithoutAspectRetainsNativeEmptyStringSemantics() {
        NBTTagCompound compound = new NBTTagCompound();

        assertEquals(
                compound.getString("Aspect"),
                TaaccAspectSubtypeGuard.getStringOrEmptyAfterRuntimeValidation(
                        compound, "Aspect"
                )
        );
        assertEquals(0, TaaccAspectSubtypeGuard.missingAspectNormalizationCount());
    }

    @Test
    public void nullCompoundIsExplicitlyCountedAndNormalizedWithoutMutation() {
        assertEquals(
                "",
                TaaccAspectSubtypeGuard.getStringOrEmptyAfterRuntimeValidation(null, "Aspect")
        );
        assertEquals(1, TaaccAspectSubtypeGuard.missingAspectNormalizationCount());
    }

    @Test(expected = IllegalStateException.class)
    public void unexpectedNbtKeyIsRejected() {
        TaaccAspectSubtypeGuard.getStringOrEmptyAfterRuntimeValidation(
                new NBTTagCompound(), "ChangedAspect"
        );
    }

    private static void expectVersionFailure(String minecraft, String forge,
                                             String taacc, String hei) {
        try {
            TaaccAspectSubtypeGuard.validateRuntimeVersions(minecraft, forge, taacc, hei);
            fail("Expected runtime identity drift to fail closed");
        } catch (IllegalStateException expected) {
            // Exact refusal is the behavior under test.
        }
    }
}
