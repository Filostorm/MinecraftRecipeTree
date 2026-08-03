package com.recipetree.neiexport1710;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class BetterQuestingSemanticPolicyTest {
    @Test
    public void handlerPolicyExplicitlyMarksQuestPagesInformational() throws Exception {
        CompleteCategoryAdapters.Policy policy = CompleteCategoryAdapters.classify(
                CompleteCategoryAdapters.BETTER_QUESTING_HANDLER,
                CompleteCategoryAdapters.BETTER_QUESTING_HANDLER,
                "bq_quest", null, "bq_quest");

        assertEquals("adapted-informational-category", policy.action);
        assertEquals("adapter:betterquesting-complete-item-reference-pages-v1",
                policy.contract);
        assertEquals(true, policy.adapter.allowsInformationalEmptyOutputs());
    }

    @Test
    public void binnieAcclimatiserPolicyExplicitlyAllowsNoMaterialOutput() throws Exception {
        CompleteCategoryAdapters.Policy policy = CompleteCategoryAdapters.classify(
                CompleteCategoryAdapters.BINNIE_ACCLIMATISER_HANDLER,
                CompleteCategoryAdapters.BINNIE_ACCLIMATISER_HANDLER,
                BinnieAcclimatiserInformationalAdapter.OPERATION, null,
                BinnieAcclimatiserInformationalAdapter.OPERATION);

        assertEquals("adapted-informational-category", policy.action);
        assertEquals(BinnieAcclimatiserInformationalAdapter.CONTRACT, policy.contract);
        assertEquals(true, policy.adapter.allowsInformationalEmptyOutputs());
    }

    @Test
    public void informationalReferencePageRetainsMoreThanTheNeiPreviewLimit() {
        List<CompleteCategoryAdapters.SemanticSlot> inputs =
                new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
        for (int index = 0; index < 55; index++) {
            inputs.add(slot(index + 1));
        }

        CompleteCategoryAdapters.RecipeSemanticOverride page =
                new CompleteCategoryAdapters.RecipeSemanticOverride(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        inputs, Collections.<CompleteCategoryAdapters.SemanticSlot>emptyList());

        assertEquals(55, page.inputs.size());
        inputs.clear();
        assertEquals("the semantic page must own an immutable structural snapshot",
                55, page.inputs.size());
    }

    @Test
    public void canonicalIdentityIncludesExactQuantityAndCanonicalNbt() {
        ItemStack firstDisplay = new ItemStack(new Item(), 1);
        NBTTagCompound firstTag = new NBTTagCompound();
        firstTag.setString("zeta", "last");
        firstTag.setInteger("alpha", 7);
        StackIdentity first = StackIdentity.fluidProxy(
                firstDisplay, "test_fluid", "test:test_fluid",
                firstTag, "Test Fluid", 1000);

        ItemStack reorderedDisplay = new ItemStack(new Item(), 1);
        NBTTagCompound reorderedTag = new NBTTagCompound();
        reorderedTag.setInteger("alpha", 7);
        reorderedTag.setString("zeta", "last");
        StackIdentity reordered = StackIdentity.fluidProxy(
                reorderedDisplay, "test_fluid", "test:test_fluid",
                reorderedTag, "Test Fluid", 1000);

        String canonical = CompleteCategoryAdapters.canonicalStackIdentity(first, 64);
        assertEquals(canonical,
                CompleteCategoryAdapters.canonicalStackIdentity(reordered, 64));
        assertNotEquals(canonical,
                CompleteCategoryAdapters.canonicalStackIdentity(reordered, 65));
    }

    @Test
    public void rewardChoiceIsOneSlotWithHeterogeneousQuantitiesAndFlatPreviewGroups()
            throws Exception {
        CompleteCategoryAdapters.SemanticSlot grouped =
                CompleteCategoryAdapters.groupChoiceSlots(
                        Arrays.asList(slot(8), slot(1)));

        assertEquals(Arrays.asList(Integer.valueOf(1), Integer.valueOf(1)),
                grouped.previewGroupSizes);
        assertEquals(2, grouped.alternatives.size());
        assertEquals(8, grouped.alternatives.get(0).amount);
        assertEquals(1, grouped.alternatives.get(1).amount);

        List<List<CompleteCategoryAdapters.SemanticAlternative>> preview =
                CompleteCategoryAdapters.previewAlternativeGroups(
                        Collections.singletonList(grouped));
        assertEquals(2, preview.size());
        assertEquals(8, preview.get(0).get(0).amount);
        assertEquals(1, preview.get(1).get(0).amount);
    }

    @Test
    public void choiceBoundaryOnlyDriftChangesFingerprintCanonicalForm() {
        List<CompleteCategoryAdapters.SemanticAlternative> alternatives = Arrays.asList(
                alternative(2), alternative(3), alternative(5));
        CompleteCategoryAdapters.SemanticSlot twoThenOne =
                new CompleteCategoryAdapters.SemanticSlot(
                        alternatives, Arrays.asList(Integer.valueOf(2), Integer.valueOf(1)));
        CompleteCategoryAdapters.SemanticSlot oneThenTwo =
                new CompleteCategoryAdapters.SemanticSlot(
                        alternatives, Arrays.asList(Integer.valueOf(1), Integer.valueOf(2)));

        String first = CompleteCategoryAdapters.canonicalSemanticSlotsForFingerprint(
                'O', Collections.singletonList(twoThenOne));
        String changed = CompleteCategoryAdapters.canonicalSemanticSlotsForFingerprint(
                'O', Collections.singletonList(oneThenTwo));

        assertNotEquals(first, changed);
    }

    private static CompleteCategoryAdapters.SemanticSlot slot(int amount) {
        return new CompleteCategoryAdapters.SemanticSlot(
                Collections.singletonList(alternative(amount)));
    }

    private static CompleteCategoryAdapters.SemanticAlternative alternative(int amount) {
        ItemStack display = new ItemStack(new Item(), 1);
        StackIdentity identity = StackIdentity.fluidProxy(
                display, "fixture_" + amount, "fixture:fluid_" + amount,
                null, "Fixture", amount);
        return new CompleteCategoryAdapters.SemanticAlternative(
                display, amount,
                CompleteCategoryAdapters.canonicalStackIdentity(identity, amount));
    }
}
