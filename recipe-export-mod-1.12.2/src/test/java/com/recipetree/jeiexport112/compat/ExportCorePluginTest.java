package com.recipetree.jeiexport112.compat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public final class ExportCorePluginTest {
    private static final String GRAPHICS_PROPERTY = "jeiexport.disableStencil";
    private String previousWorld;
    private String previousGraphics;
    private String previousTaacc;
    private String previousTinkersComplement;

    @Before
    public void saveProperties() {
        previousWorld = System.getProperty(WorldStartupConfiguration.ENABLE_PROPERTY);
        previousGraphics = System.getProperty(GRAPHICS_PROPERTY);
        previousTaacc = System.getProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY);
        previousTinkersComplement = System.getProperty(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY
        );
        System.clearProperty(WorldStartupConfiguration.ENABLE_PROPERTY);
        System.clearProperty(GRAPHICS_PROPERTY);
        System.clearProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY);
        System.clearProperty(TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY);
    }

    @After
    public void restoreProperties() {
        restore(WorldStartupConfiguration.ENABLE_PROPERTY, previousWorld);
        restore(GRAPHICS_PROPERTY, previousGraphics);
        restore(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY, previousTaacc);
        restore(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY,
                previousTinkersComplement
        );
    }

    @Test
    public void disabledWorldPolicyDoesNotRegisterItsTransformer() {
        assertArrayEquals(new String[0], new ExportCorePlugin().getASMTransformerClass());
    }

    @Test
    public void enabledWorldPolicyRegistersTheExactTransformer() {
        System.setProperty(WorldStartupConfiguration.ENABLE_PROPERTY, "true");
        assertArrayEquals(
                new String[]{ExportWorldStartupTransformer.class.getName()},
                new ExportCorePlugin().getASMTransformerClass()
        );
    }

    @Test
    public void graphicsAndWorldTransformsCanBeEnabledTogether() {
        System.setProperty(GRAPHICS_PROPERTY, "true");
        System.setProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY, "true");
        System.setProperty(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY, "true"
        );
        System.setProperty(WorldStartupConfiguration.ENABLE_PROPERTY, "true");
        assertArrayEquals(
                new String[]{
                        ExportGraphicsTransformer.class.getName(),
                        TaaccAspectSubtypeTransformer.class.getName(),
                        TinkersComplementFluidBlacklistTransformer.class.getName(),
                        ExportWorldStartupTransformer.class.getName()
                },
                new ExportCorePlugin().getASMTransformerClass()
        );
    }

    @Test
    public void taaccRepairRegistersOnlyItsExactTransformer() {
        System.setProperty(TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY, "true");
        assertArrayEquals(
                new String[]{TaaccAspectSubtypeTransformer.class.getName()},
                new ExportCorePlugin().getASMTransformerClass()
        );
    }

    @Test
    public void tinkersComplementRepairRegistersOnlyItsExactTransformer() {
        System.setProperty(
                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY, "true"
        );
        assertArrayEquals(
                new String[]{TinkersComplementFluidBlacklistTransformer.class.getName()},
                new ExportCorePlugin().getASMTransformerClass()
        );
    }

    @Test(expected = IllegalStateException.class)
    public void malformedGraphicsPropertyFailsInsteadOfSilentlyDisabling() {
        System.setProperty(GRAPHICS_PROPERTY, "TRUE");
        new ExportCorePlugin().getASMTransformerClass();
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
