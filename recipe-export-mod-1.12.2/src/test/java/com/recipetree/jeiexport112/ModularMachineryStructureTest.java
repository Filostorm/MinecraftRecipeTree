package com.recipetree.jeiexport112;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class ModularMachineryStructureTest {
    private static class CompatibleWrapperParent {
        @SuppressWarnings("unused")
        private final Object machine = new Object();
    }

    private static final class CompatibleWrapperChild extends CompatibleWrapperParent {}

    private static final class IncompatibleWrapper {}

    public static final class MmceBlockInformation {
        public Object getDescriptiveStack(long selector) {
            return null;
        }
    }

    public static final class LegacyBlockInformation {
        public Object getDescriptiveStack(Optional<Long> selector) {
            return null;
        }
    }

    public static final class UnsupportedBlockInformation {}

    public static final class MmceItemlessFirstSampleInformation {
        private final ItemStack fallback = new ItemStack(new Item());

        public ItemStack getDescriptiveStack(long selector) {
            return ItemStack.EMPTY;
        }

        public Iterable<ItemStack> getIngredientList() {
            return Arrays.asList(ItemStack.EMPTY, fallback);
        }
    }

    private static final class TestMachine {}

    private static final class TestControllerBlock extends Block {
        TestControllerBlock() {
            super(Material.ROCK);
        }
    }

    public static final class MmceControllerOwner {
        private static final Block CONTROLLER = new TestControllerBlock();

        public static Block getMocControllerWithMachine(TestMachine machine) {
            return null;
        }

        public static Block getControllerWithMachine(TestMachine machine) {
            return CONTROLLER;
        }
    }

    @Test
    public void recognizesOnlyTheModularMachineryPreviewCategory() {
        assertTrue(ModularMachineryStructure.isPreviewCategory("modularmachinery.preview"));
        assertFalse(ModularMachineryStructure.isPreviewCategory("modularmachinery.recipe"));
        assertFalse(ModularMachineryStructure.isPreviewCategory(null));
    }

    @Test
    public void findsMachineFieldDeclaredByAWrapperSuperclass() throws Exception {
        Field field = ModularMachineryStructure.findMachineField(CompatibleWrapperChild.class);
        assertEquals("machine", field.getName());
        assertEquals(CompatibleWrapperParent.class, field.getDeclaringClass());
    }

    @Test
    public void reportsTheActualUnsupportedWrapperClass() throws Exception {
        try {
            ModularMachineryStructure.findMachineField(IncompatibleWrapper.class);
            fail("Expected an incompatible wrapper to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains(IncompatibleWrapper.class.getName()));
            assertTrue(expected.getMessage().contains("machine field"));
        }
    }

    @Test
    public void resolvesMmcePrimitiveLongDescriptiveStackContract() throws Exception {
        ModularMachineryStructure.DescriptiveStackCall call =
                ModularMachineryStructure.resolveDescriptiveStackCall(
                        MmceBlockInformation.class);
        assertEquals(long.class, call.method.getParameterTypes()[0]);
        assertEquals(Long.valueOf(0L), call.argument);
    }

    @Test
    public void resolvesOriginalOptionalDescriptiveStackContract() throws Exception {
        ModularMachineryStructure.DescriptiveStackCall call =
                ModularMachineryStructure.resolveDescriptiveStackCall(
                        LegacyBlockInformation.class);
        assertEquals(Optional.class, call.method.getParameterTypes()[0]);
        assertEquals(Optional.of(Long.valueOf(0L)), call.argument);
    }

    @Test
    public void rejectsUnknownDescriptiveStackContractWithSupportedSignatures() throws Exception {
        try {
            ModularMachineryStructure.resolveDescriptiveStackCall(
                    UnsupportedBlockInformation.class);
            fail("Expected an unsupported block-information API to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains(UnsupportedBlockInformation.class.getName()));
            assertTrue(expected.getMessage().contains("getDescriptiveStack(long)"));
            assertTrue(expected.getMessage().contains("getDescriptiveStack(Optional)"));
        }
    }

    @Test
    public void fallsBackToFirstDisplayableMmceIngredient() throws Exception {
        MmceItemlessFirstSampleInformation information =
                new MmceItemlessFirstSampleInformation();
        ModularMachineryStructure.DescriptiveStackCall call =
                ModularMachineryStructure.resolveDescriptiveStackCall(information.getClass());

        ItemStack selected = ModularMachineryStructure.resolveRepresentativeStack(
                information, call);

        assertFalse(selected.isEmpty());
        assertEquals(information.fallback.getItem(), selected.getItem());
    }

    @Test
    public void skipsNullMmceControllerRegistryBeforeUsingOrdinaryController() throws Exception {
        Block selected = ModularMachineryStructure.resolveControllerFromOwners(
                new TestMachine(), new Class<?>[] {MmceControllerOwner.class});

        assertEquals(MmceControllerOwner.CONTROLLER, selected);
    }
}
