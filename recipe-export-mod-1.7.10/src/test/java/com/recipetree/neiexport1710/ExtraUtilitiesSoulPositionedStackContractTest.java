package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class ExtraUtilitiesSoulPositionedStackContractTest {
    private static final Item FIXTURE_ITEM = new Item();

    @Test
    public void acceptsNeiDefensiveCurrentCopyWithExactSerializedIdentity()
            throws Exception {
        PositionedStack positioned = positioned(47, 3);

        assertNotSame(positioned.items[0], positioned.item);
        assertNotSame(positioned.items[0].getTagCompound(),
                positioned.item.getTagCompound());
        CompleteCategoryAdapters.requireDefensivePositionedStackShape(
                positioned, 47, 3, "fixture soul input");
    }

    @Test
    public void rejectsCurrentStackAliasingTheSerializedAlternative()
            throws Exception {
        final PositionedStack positioned = positioned(47, 3);
        positioned.item = positioned.items[0];

        assertFailure(new CheckedCall() {
            @Override
            public void run() throws Exception {
                CompleteCategoryAdapters.requireDefensivePositionedStackShape(
                        positioned, 47, 3, "fixture soul input");
            }
        });
    }

    @Test
    public void rejectsAliasedOrDroppedNonnullNbtOnDefensiveCurrentCopy()
            throws Exception {
        final PositionedStack aliasedTag = positioned(47, 3);
        aliasedTag.item.setTagCompound(
                aliasedTag.items[0].getTagCompound());
        assertFailure(new CheckedCall() {
            @Override
            public void run() throws Exception {
                CompleteCategoryAdapters.requireDefensivePositionedStackShape(
                        aliasedTag, 47, 3, "fixture soul input");
            }
        });

        final PositionedStack droppedTag = positioned(47, 3);
        droppedTag.item.setTagCompound(null);
        assertFailure(new CheckedCall() {
            @Override
            public void run() throws Exception {
                CompleteCategoryAdapters.requireDefensivePositionedStackShape(
                        droppedTag, 47, 3, "fixture soul input");
            }
        });
    }

    @Test
    public void rejectsCoordinatesAndAlternativeCardinalityDrift()
            throws Exception {
        final PositionedStack wrongCoordinates = positioned(48, 3);
        assertFailure(new CheckedCall() {
            @Override
            public void run() throws Exception {
                CompleteCategoryAdapters.requireDefensivePositionedStackShape(
                        wrongCoordinates, 47, 3, "fixture soul input");
            }
        });

        final PositionedStack multipleAlternatives = positioned(47, 3);
        multipleAlternatives.items = new ItemStack[] {
                multipleAlternatives.items[0], fixtureStack()
        };
        assertFailure(new CheckedCall() {
            @Override
            public void run() throws Exception {
                CompleteCategoryAdapters.requireDefensivePositionedStackShape(
                        multipleAlternatives, 47, 3, "fixture soul input");
            }
        });
    }

    @Test
    public void rejectsCurrentCopyAmountMetadataAndNbtDrift() throws Exception {
        assertCurrentDrift(new StackMutation() {
            @Override
            public void mutate(ItemStack stack) {
                stack.stackSize = 2;
            }
        });
        assertCurrentDrift(new StackMutation() {
            @Override
            public void mutate(ItemStack stack) {
                stack.setItemDamage(4);
            }
        });
        assertCurrentDrift(new StackMutation() {
            @Override
            public void mutate(ItemStack stack) {
                stack.getTagCompound().setString("payload", "changed");
            }
        });
    }

    private static void assertCurrentDrift(StackMutation mutation) throws Exception {
        final PositionedStack positioned = positioned(47, 3);
        mutation.mutate(positioned.item);
        assertFailure(new CheckedCall() {
            @Override
            public void run() throws Exception {
                CompleteCategoryAdapters.requireDefensivePositionedStackShape(
                        positioned, 47, 3, "fixture soul input");
            }
        });
    }

    private static PositionedStack positioned(int x, int y) {
        return new PositionedStack(fixtureStack(), x, y);
    }

    private static ItemStack fixtureStack() {
        ItemStack stack = new ItemStack(FIXTURE_ITEM, 1, 0);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("payload", "exact");
        stack.setTagCompound(tag);
        return stack;
    }

    private static void assertFailure(CheckedCall call) throws Exception {
        try {
            call.run();
        } catch (ExportFailure failure) {
            assertEquals("RECIPE_SEMANTICS", failure.code);
            return;
        }
        throw new AssertionError("Expected RECIPE_SEMANTICS failure");
    }

    private interface CheckedCall {
        void run() throws Exception;
    }

    private interface StackMutation {
        void mutate(ItemStack stack);
    }
}
