package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler.RecipeTransferRect;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.junit.Test;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HandlerCategoryPlanTest {
    @Test
    public void resolvesMatchingOverlayAndTransferContract() throws Exception {
        assertEquals("gregtech:assembler", HandlerCategoryPlan.resolveLoadIdentifier(
                "handler", "gregtech:assembler", "gregtech:assembler"));
    }

    @Test(expected = ExportFailure.class)
    public void rejectsAmbiguousIdentifiers() throws Exception {
        HandlerCategoryPlan.resolveLoadIdentifier("handler", "one", "two");
    }

    @Test(expected = ExportFailure.class)
    public void rejectsMissingCategoryWideContract() throws Exception {
        HandlerCategoryPlan.resolveLoadIdentifier("handler", null, "  ");
    }

    @Test
    public void permitsRepeatedRawHandlerIdsForDistinctSemanticCategories()
            throws Exception {
        List<HandlerCategoryPlan> plans = HandlerCategoryPlan.create(
                Arrays.<ICraftingHandler>asList(
                        new DummyHandler("shared.lineage", "category.one", 1),
                        new DummyHandler("shared.lineage", "category.two", 1)));

        assertEquals(2, plans.size());
        assertEquals("shared.lineage", plans.get(0).handlerId);
        assertEquals("shared.lineage", plans.get(1).handlerId);
        assertFalse(plans.get(0).categoryId.equals(plans.get(1).categoryId));
    }

    @Test(expected = ExportFailure.class)
    public void rejectsDuplicateSemanticCategoryKeys() throws Exception {
        HandlerCategoryPlan.create(Arrays.<ICraftingHandler>asList(
                new DummyHandler("duplicate", "category", 1),
                new DummyHandler("duplicate", "category", 1)));
    }

    @Test
    public void categoryIdIsTruncatedSha256OfLengthFramedSemanticKey()
            throws Exception {
        HandlerCategoryPlan plan = HandlerCategoryPlan.create(
                Collections.<ICraftingHandler>singletonList(
                        new DummyHandler("lineage", "overlay", 1))).get(0);

        assertTrue(plan.categoryKey.startsWith("20:gtnh-category-key-v1"));
        assertEquals("gtnh:" + Naming.sha256(plan.categoryKey).substring(0, 32),
                plan.categoryId);
        assertTrue(plan.categoryId.matches("gtnh:[0-9a-f]{32}"));
        assertFalse(plan.categoryKey.contains("registryIndex"));
    }

    @Test
    public void loadsCompleteCategoryThroughOverlayIdentifier() throws Exception {
        HandlerCategoryPlan plan = HandlerCategoryPlan.create(
                Collections.<ICraftingHandler>singletonList(
                        new DummyHandler("unique", "category", 3))).get(0);
        assertEquals(3, plan.loadCompleteCategory().numRecipes());
    }

    @Test
    public void ordinaryCategoryLoaderStillRejectsEveryUnpromotedZeroResult()
            throws Exception {
        HandlerCategoryPlan plan = HandlerCategoryPlan.create(
                Collections.<ICraftingHandler>singletonList(
                        new DummyHandler(
                                "unpromoted.zero", "category.zero", 1,
                                LoadBehavior.ZERO))).get(0);
        try {
            plan.loadCompleteCategory();
            fail("expected the global zero-recipe invariant to reject the category");
        } catch (ExportFailure failure) {
            assertEquals("HANDLER_UNLOADED", failure.code);
            assertTrue(failure.getMessage().contains("loaded 0 recipes"));
        }
    }

    @Test(expected = ExportFailure.class)
    public void rejectsLoadedOverlayDiscriminatorDrift() throws Exception {
        HandlerCategoryPlan plan = HandlerCategoryPlan.create(
                Collections.<ICraftingHandler>singletonList(
                        new DummyHandler(
                                "unique", "category", 3,
                                LoadBehavior.WRONG_OVERLAY))).get(0);
        plan.loadCompleteCategory();
    }

    @Test(expected = ExportFailure.class)
    public void rejectsLoadedRuntimeSubclass() throws Exception {
        HandlerCategoryPlan plan = HandlerCategoryPlan.create(
                Collections.<ICraftingHandler>singletonList(
                        new DummyHandler(
                                "unique", "category", 3,
                                LoadBehavior.WRONG_CLASS))).get(0);
        plan.loadCompleteCategory();
    }

    @Test
    public void classifiesAe2WorldCraftingAsInformationalQueryClosure() throws Exception {
        CompleteCategoryAdapters.Policy policy = CompleteCategoryAdapters.classify(
                CompleteCategoryAdapters.AE2_WORLD_CRAFTING_HANDLER,
                CompleteCategoryAdapters.AE2_WORLD_CRAFTING_HANDLER,
                null,
                null);
        assertNotNull(policy);
        assertEquals(CompleteCategoryAdapters.Adapter.AE2_WORLD_CRAFTING, policy.adapter);
        assertEquals("adapted-informational-category", policy.action);
        assertEquals("adapter:ae2-in-world-crafting-wildcard-query-closure-v1",
                policy.contract);
    }

    @Test
    public void keepsQueryAndPresentationExclusionsDistinctAndCounted() throws Exception {
        CompleteCategoryAdapters.Policy queryOnly = CompleteCategoryAdapters.classify(
                CompleteCategoryAdapters.IC2_LATHE_HANDLER,
                CompleteCategoryAdapters.IC2_LATHE_HANDLER,
                null,
                null,
                null);
        CompleteCategoryAdapters.Policy presentationOnly = CompleteCategoryAdapters.classify(
                CompleteCategoryAdapters.ORE_DICTIONARY_INFORMATION_HANDLER,
                CompleteCategoryAdapters.ORE_DICTIONARY_INFORMATION_HANDLER,
                null,
                null,
                "forge.oreDictionary");

        assertNotNull(queryOnly);
        assertNotNull(presentationOnly);
        assertEquals(CompleteCategoryAdapters.Adapter.EXCLUDED_QUERY_ONLY,
                queryOnly.adapter);
        assertEquals("excluded-non-recipe-query", queryOnly.action);
        assertEquals(CompleteCategoryAdapters.Adapter.EXCLUDED_PRESENTATION_ONLY,
                presentationOnly.adapter);
        assertEquals("excluded-non-recipe-presentation", presentationOnly.action);
        assertTrue(CompleteCategoryAdapters.isExcludedFromCategoryExport(queryOnly.adapter));
        assertTrue(CompleteCategoryAdapters.isExcludedFromCategoryExport(
                presentationOnly.adapter));
        assertFalse(CompleteCategoryAdapters.isExcludedFromCategoryExport(
                CompleteCategoryAdapters.Adapter.STANDARD));

        HandlerCategoryPlan.PlanningResult exclusions =
                new HandlerCategoryPlan.PlanningResult(
                        2,
                        Collections.<HandlerCategoryPlan>emptyList(),
                        Arrays.asList(queryOnly, presentationOnly),
                        0,
                        0);
        assertEquals(2, exclusions.excludedNonRecipeHandlers());
    }

    @Test
    public void keepsUnboundTemplateExclusionDistinctFromNonRecipePolicies()
            throws Exception {
        CompleteCategoryAdapters.Policy policy = CompleteCategoryAdapters.classify(
                PinnedUnboundTemplateRecipeHandlers.HANDLER_CLASS,
                PinnedUnboundTemplateRecipeHandlers.HANDLER_ID,
                PinnedUnboundTemplateRecipeHandlers.OVERLAY,
                null,
                PinnedUnboundTemplateRecipeHandlers.OPERATION);

        assertNotNull(policy);
        assertEquals(CompleteCategoryAdapters.Adapter.EXCLUDED_UNBOUND_TEMPLATE,
                policy.adapter);
        assertEquals(PinnedUnboundTemplateRecipeHandlers.ACTION, policy.action);
        assertEquals(PinnedUnboundTemplateRecipeHandlers.CONTRACT, policy.contract);
        assertTrue(CompleteCategoryAdapters.isExcludedFromCategoryExport(policy.adapter));

        HandlerCategoryPlan.PlanningResult exclusions =
                new HandlerCategoryPlan.PlanningResult(
                        1,
                        Collections.<HandlerCategoryPlan>emptyList(),
                        Collections.singletonList(policy),
                        0,
                        1);
        assertEquals(0, exclusions.excludedNonRecipeHandlers());
        assertEquals(1, exclusions.excludedUnboundTemplateRecipeHandlers);
    }

    @Test
    public void finalPinnedInventoryPartitionsAllRegisteredHandlers() {
        HandlerCategoryPlan.PlanningResult finalInventory =
                new HandlerCategoryPlan.PlanningResult(
                        330,
                        Collections.nCopies(287, (HandlerCategoryPlan) null),
                        Collections.<CompleteCategoryAdapters.Policy>emptyList(),
                        22,
                        1);

        assertEquals(20, finalInventory.excludedNonRecipeHandlers());
        assertEquals(330, finalInventory.categories.size()
                + finalInventory.excludedNonRecipeHandlers()
                + finalInventory.excludedEmptyRecipeHandlers
                + finalInventory.excludedUnboundTemplateRecipeHandlers);
    }

    @Test
    public void pinnedPolicyLedgerHasExactReleaseCardinalityAndOrder() {
        List<CompleteCategoryAdapters.Policy> policies =
                CompleteCategoryAdapters.expectedPoliciesForContract();
        int adapted = 0;
        int excluded = 0;
        int excludedUnboundTemplates = 0;
        String previousClass = null;
        String previousId = null;
        for (CompleteCategoryAdapters.Policy policy : policies) {
            if (previousClass != null) {
                int byClass = previousClass.compareTo(policy.handlerClass);
                assertTrue(byClass < 0
                        || (byClass == 0 && previousId.compareTo(policy.handlerId) < 0));
            }
            previousClass = policy.handlerClass;
            previousId = policy.handlerId;
            if (CompleteCategoryAdapters.isExcludedFromCategoryExport(policy.adapter)) {
                excluded++;
                if (policy.adapter
                        == CompleteCategoryAdapters.Adapter.EXCLUDED_UNBOUND_TEMPLATE) {
                    excludedUnboundTemplates++;
                }
            } else {
                adapted++;
            }
        }
        assertEquals(66, policies.size());
        assertEquals(45, adapted);
        assertEquals(21, excluded);
        assertEquals(1, excludedUnboundTemplates);
    }

    @Test
    public void enderStorageLiveQueryDescriptionsRemainDistinctAndPinned() {
        assertEquals(
                "This diagram displays ender chest used frequencies and contents.\n"
                        + "Unfortunately, it doesn't work well on servers.",
                CompleteCategoryAdapters.ENDER_STORAGE_CHEST_DIAGRAM_DESCRIPTION);
        assertEquals(
                "This diagram displays ender tank used frequencies and contents.\n"
                        + "Unfortunately, it doesn't work on servers.",
                CompleteCategoryAdapters.ENDER_STORAGE_TANK_DIAGRAM_DESCRIPTION);
    }

    @Test(expected = ExportFailure.class)
    public void rejectsPresentationExclusionWhenTransferRectIdDrifts() throws Exception {
        CompleteCategoryAdapters.classify(
                CompleteCategoryAdapters.ORE_DICTIONARY_INFORMATION_HANDLER,
                CompleteCategoryAdapters.ORE_DICTIONARY_INFORMATION_HANDLER,
                null,
                null,
                "forge.wrongDictionary");
    }

    @Test
    public void pinnedPolicyRequiresItsExactZeroArgumentTransferVector()
            throws Exception {
        CompleteCategoryAdapters.Policy policy = CompleteCategoryAdapters.classify(
                CompleteCategoryAdapters.ORE_DICTIONARY_INFORMATION_HANDLER,
                CompleteCategoryAdapters.ORE_DICTIONARY_INFORMATION_HANDLER,
                null, null, "forge.oreDictionary");

        CompleteCategoryAdapters.validateStructuralPolicyTransferOperations(
                policy, Collections.singletonList(
                        new HandlerCategoryPlan.TransferOperation(
                                "forge.oreDictionary", 0)));
    }

    @Test(expected = ExportFailure.class)
    public void pinnedPolicyRejectsAdditionalSameIdTransferRectangle()
            throws Exception {
        CompleteCategoryAdapters.Policy policy = CompleteCategoryAdapters.classify(
                CompleteCategoryAdapters.ORE_DICTIONARY_INFORMATION_HANDLER,
                CompleteCategoryAdapters.ORE_DICTIONARY_INFORMATION_HANDLER,
                null, null, "forge.oreDictionary");

        CompleteCategoryAdapters.validateStructuralPolicyTransferOperations(
                policy, Arrays.asList(
                        new HandlerCategoryPlan.TransferOperation(
                                "forge.oreDictionary", 0),
                        new HandlerCategoryPlan.TransferOperation(
                                "forge.oreDictionary", 0)));
    }

    @Test(expected = ExportFailure.class)
    public void pinnedPolicyRejectsTransferArguments() throws Exception {
        CompleteCategoryAdapters.Policy policy = CompleteCategoryAdapters.classify(
                CompleteCategoryAdapters.ORE_DICTIONARY_INFORMATION_HANDLER,
                CompleteCategoryAdapters.ORE_DICTIONARY_INFORMATION_HANDLER,
                null, null, "forge.oreDictionary");

        CompleteCategoryAdapters.validateStructuralPolicyTransferOperations(
                policy, Collections.singletonList(
                        new HandlerCategoryPlan.TransferOperation(
                                "forge.oreDictionary", 1)));
    }

    @Test
    public void acceptsRepeatedZeroArgumentTransferRectIdentifier() throws Exception {
        assertEquals("rect.category", HandlerCategoryPlan.uniqueTransferRectIdentifier(
                Arrays.<Object>asList(
                        new RecipeTransferRect(
                                new Rectangle(0, 0, 1, 1), "rect.category"),
                        new RecipeTransferRect(
                                new Rectangle(1, 1, 1, 1), "rect.category")),
                "rect.handler"));
    }

    @Test(expected = ExportFailure.class)
    public void rejectsUnselectedMultipleTransferRectIdentifiers() throws Exception {
        HandlerCategoryPlan.resolveGenericOperation(
                "rect.handler", null, null,
                Arrays.<Object>asList(
                        new RecipeTransferRect(new Rectangle(0, 0, 1, 1), "rect.one"),
                        new RecipeTransferRect(new Rectangle(1, 1, 1, 1), "rect.two")));
    }

    @Test(expected = ExportFailure.class)
    public void rejectsTransferRectResultArgumentsWithoutExactAdapter() throws Exception {
        HandlerCategoryPlan.uniqueTransferRectIdentifier(
                Collections.<Object>singletonList(new RecipeTransferRect(
                        new Rectangle(0, 0, 1, 1), "rect.category", "argument")),
                "rect.handler");
    }

    @Test(expected = ExportFailure.class)
    public void rejectsNonzeroTransferOperationEvenWhenOverlaySelectsIt()
            throws Exception {
        HandlerCategoryPlan.resolveGenericOperation(
                "rect.handler", "rect.category", null,
                Collections.<Object>singletonList(new RecipeTransferRect(
                        new Rectangle(0, 0, 1, 1), "rect.category", "argument")));
    }

    @Test
    public void oneZeroArgumentTransferOperationWinsDespiteOverlayMismatch()
            throws Exception {
        HandlerCategoryPlan.ResolvedOperation operation =
                HandlerCategoryPlan.resolveGenericOperation(
                        "rect.handler", "overlay.category", null,
                        Collections.<Object>singletonList(new RecipeTransferRect(
                                new Rectangle(0, 0, 1, 1), "rect.category")));

        assertEquals("rect.category", operation.outputId);
        assertEquals(HandlerCategoryPlan.OPERATION_SOURCE_TRANSFER_RECT,
                operation.source);
    }

    @Test
    public void uniqueZeroArgumentOperationWinsAlongsideNonzeroUiOperation()
            throws Exception {
        HandlerCategoryPlan.ResolvedOperation operation =
                HandlerCategoryPlan.resolveGenericOperation(
                        "rect.handler", "ui.operation", null,
                        Arrays.<Object>asList(
                                new RecipeTransferRect(
                                        new Rectangle(0, 0, 1, 1),
                                        "ui.operation", "selected-stack"),
                                new RecipeTransferRect(
                                        new Rectangle(1, 1, 1, 1),
                                        "complete.category")));

        assertEquals("complete.category", operation.outputId);
    }

    @Test
    public void multipleZeroArgumentOperationsPreferOverlayMatch()
            throws Exception {
        HandlerCategoryPlan.ResolvedOperation operation =
                HandlerCategoryPlan.resolveGenericOperation(
                        "rect.handler", "rect.two", "unmatched.selector",
                        Arrays.<Object>asList(
                                new RecipeTransferRect(
                                        new Rectangle(0, 0, 1, 1), "rect.one"),
                                new RecipeTransferRect(
                                        new Rectangle(1, 1, 1, 1), "rect.two")));

        assertEquals("rect.two", operation.outputId);
    }

    @Test
    public void multipleZeroArgumentOperationsUseSelectorWhenOverlayDoesNotMatch()
            throws Exception {
        HandlerCategoryPlan.ResolvedOperation operation =
                HandlerCategoryPlan.resolveGenericOperation(
                        "rect.handler", "unmatched.overlay", "rect.one",
                        Arrays.<Object>asList(
                                new RecipeTransferRect(
                                        new Rectangle(0, 0, 1, 1), "rect.one"),
                                new RecipeTransferRect(
                                        new Rectangle(1, 1, 1, 1), "rect.two")));

        assertEquals("rect.one", operation.outputId);
    }

    @Test(expected = ExportFailure.class)
    public void rejectsOverlayAndSelectorSelectingDifferentZeroArgumentOperations()
            throws Exception {
        HandlerCategoryPlan.resolveGenericOperation(
                "rect.handler", "rect.one", "rect.two",
                Arrays.<Object>asList(
                        new RecipeTransferRect(
                                new Rectangle(0, 0, 1, 1), "rect.one"),
                        new RecipeTransferRect(
                                new Rectangle(1, 1, 1, 1), "rect.two")));
    }

    @Test
    public void collapsesDuplicateZeroArgumentIdsBeforeSelection()
            throws Exception {
        HandlerCategoryPlan.ResolvedOperation operation =
                HandlerCategoryPlan.resolveGenericOperation(
                        "rect.handler", "unmatched", null,
                        Arrays.<Object>asList(
                                new RecipeTransferRect(
                                        new Rectangle(0, 0, 1, 1), "rect.same"),
                                new RecipeTransferRect(
                                        new Rectangle(1, 1, 1, 1), "rect.same")));

        assertEquals("rect.same", operation.outputId);
    }

    @Test
    public void noRectanglesPermitEqualOverlayAndSelector() throws Exception {
        assertEquals("same.operation", HandlerCategoryPlan.resolveLoadIdentifier(
                "handler", "same.operation", "same.operation"));
    }

    @Test
    public void exactTransferOperationParticipatesInSemanticCategoryKey() {
        String first = HandlerCategoryPlan.buildCategoryKey(
                "same.RuntimeClass", "shared", "same.overlay",
                "output-id:operation.one",
                "generic:getRecipeHandler-zero-arguments-v1");
        String second = HandlerCategoryPlan.buildCategoryKey(
                "same.RuntimeClass", "shared", "same.overlay",
                "output-id:operation.two",
                "generic:getRecipeHandler-zero-arguments-v1");

        assertFalse(first.equals(second));
        assertFalse(Naming.sha256(first).substring(0, 32)
                .equals(Naming.sha256(second).substring(0, 32)));
    }

    @Test
    public void aggregatesEveryUnsupportedStructuralHandler() throws Exception {
        try {
            HandlerCategoryPlan.validateStructuralContracts(
                    Arrays.<ICraftingHandler>asList(
                            new DummyHandler("missing.second", null, 0),
                            new DummyHandler("missing.first", null, 0)),
                    false);
            fail("expected an aggregate structural failure");
        } catch (ExportFailure failure) {
            assertEquals("HANDLER_UNLOADED", failure.code);
            assertTrue(failure.getMessage().contains("found 2 issue(s)"));
            assertTrue(failure.getMessage().contains("missing.first"));
            assertTrue(failure.getMessage().contains("missing.second"));
        }
    }

    @Test
    public void successfulCategoryLoadDoesNotProbeRemainingPlans() throws Exception {
        DummyHandler first = new DummyHandler(
                "a.success", "category.a", 2, LoadBehavior.SUCCESS);
        DummyHandler laterThrow = new DummyHandler(
                "b.throw", "category.b", 1, LoadBehavior.THROW);
        DummyHandler laterZero = new DummyHandler(
                "c.zero", "category.c", 1, LoadBehavior.ZERO);
        List<HandlerCategoryPlan> created = HandlerCategoryPlan.create(
                Arrays.<ICraftingHandler>asList(first, laterThrow, laterZero));
        List<HandlerCategoryPlan> plans = Arrays.asList(
                planByHandlerId(created, "a.success"),
                planByHandlerId(created, "b.throw"),
                planByHandlerId(created, "c.zero"));

        ICraftingHandler loaded = HandlerCategoryPlan.loadCompleteCategoryWithFailureAudit(
                plans, 0);

        assertEquals("a.success", loaded.getHandlerId());
        assertEquals(1, first.loadAttempts);
        assertEquals(0, laterThrow.loadAttempts);
        assertEquals(0, laterZero.loadAttempts);
    }

    @Test
    public void firstLoadFailureAuditsEveryRemainingFailureInCanonicalOrder()
            throws Exception {
        DummyHandler firstNull = new DummyHandler(
                "a.null", "category.a", 1, LoadBehavior.NULL);
        DummyHandler successfulProbe = new DummyHandler(
                "b.success", "category.b", 1, LoadBehavior.SUCCESS);
        DummyHandler throwingProbe = new DummyHandler(
                "c.throw", "category.c", 1, LoadBehavior.THROW);
        DummyHandler zeroProbe = new DummyHandler(
                "d.zero", "category.d", 1, LoadBehavior.ZERO);
        DummyHandler wrongIdProbe = new DummyHandler(
                "e.wrong", "category.e", 1, LoadBehavior.WRONG_ID);
        List<HandlerCategoryPlan> created = HandlerCategoryPlan.create(
                Arrays.<ICraftingHandler>asList(
                        firstNull, successfulProbe, throwingProbe, zeroProbe, wrongIdProbe));
        List<HandlerCategoryPlan> auditOrder = Arrays.asList(
                planByHandlerId(created, "a.null"),
                planByHandlerId(created, "b.success"),
                planByHandlerId(created, "c.throw"),
                planByHandlerId(created, "d.zero"),
                planByHandlerId(created, "e.wrong"));

        try {
            HandlerCategoryPlan.loadCompleteCategoryWithFailureAudit(auditOrder, 0);
            fail("expected aggregate category-load failure");
        } catch (ExportFailure aggregate) {
            assertEquals("HANDLER_UNLOADED", aggregate.code);
            String message = aggregate.getMessage();
            assertTrue(message.contains("found 4 failing handler(s)"));
            assertTrue(message.contains("handlerId=a.null"));
            assertTrue(message.contains("handlerId=c.throw"));
            assertTrue(message.contains("handlerId=d.zero"));
            assertTrue(message.contains("handlerId=e.wrong"));
            assertFalse(message.contains("handlerId=b.success"));
            assertTrue(message.indexOf("handlerId=a.null")
                    < message.indexOf("handlerId=c.throw"));
            assertTrue(message.indexOf("handlerId=c.throw")
                    < message.indexOf("handlerId=d.zero"));
            assertTrue(message.indexOf("handlerId=d.zero")
                    < message.indexOf("handlerId=e.wrong"));
            assertEquals(3, aggregate.getSuppressed().length);
        }

        assertEquals(1, firstNull.loadAttempts);
        assertEquals(1, successfulProbe.loadAttempts);
        assertEquals(1, throwingProbe.loadAttempts);
        assertEquals(1, zeroProbe.loadAttempts);
        assertEquals(1, wrongIdProbe.loadAttempts);
    }

    private enum LoadBehavior {
        SUCCESS,
        NULL,
        THROW,
        ZERO,
        WRONG_ID,
        WRONG_OVERLAY,
        WRONG_CLASS
    }

    private static class DummyHandler implements ICraftingHandler {
        private final String id;
        private final String overlay;
        private final int count;
        private final LoadBehavior loadBehavior;
        private int loadAttempts;

        DummyHandler(String id, String overlay, int count) {
            this(id, overlay, count, LoadBehavior.SUCCESS);
        }

        DummyHandler(String id, String overlay, int count, LoadBehavior loadBehavior) {
            this.id = id;
            this.overlay = overlay;
            this.count = count;
            this.loadBehavior = loadBehavior;
        }

        @Override
        public String getHandlerId() {
            return id;
        }

        @Override
        public String getRecipeName() {
            return id;
        }

        @Override
        public int numRecipes() {
            return count;
        }

        @Override
        public void drawBackground(int recipe) {
        }

        @Override
        public void drawForeground(int recipe) {
        }

        @Override
        public List<PositionedStack> getIngredientStacks(int recipe) {
            return Collections.emptyList();
        }

        @Override
        public List<PositionedStack> getOtherStacks(int recipe) {
            return Collections.emptyList();
        }

        @Override
        public PositionedStack getResultStack(int recipe) {
            return null;
        }

        @Override
        public void onUpdate() {
        }

        @Override
        public boolean hasOverlay(GuiContainer gui, Container container, int recipe) {
            return false;
        }

        @Override
        public IRecipeOverlayRenderer getOverlayRenderer(GuiContainer gui, int recipe) {
            return null;
        }

        @Override
        public IOverlayHandler getOverlayHandler(GuiContainer gui, int recipe) {
            return null;
        }

        @Override
        public String getOverlayIdentifier() {
            return overlay;
        }

        @Override
        public List<String> handleTooltip(GuiRecipe<?> gui, List<String> tooltip, int recipe) {
            return tooltip;
        }

        @Override
        public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack,
                                              List<String> tooltip, int recipe) {
            return tooltip;
        }

        @Override
        public boolean keyTyped(GuiRecipe<?> gui, char character, int keyCode, int recipe) {
            return false;
        }

        @Override
        public boolean mouseClicked(GuiRecipe<?> gui, int button, int recipe) {
            return false;
        }

        @Override
        public ICraftingHandler getRecipeHandler(String outputId, Object... results) {
            loadAttempts++;
            if (overlay == null || !overlay.equals(outputId)) {
                return null;
            }
            if (loadBehavior == LoadBehavior.NULL) {
                return null;
            }
            if (loadBehavior == LoadBehavior.THROW) {
                throw new IllegalStateException("deliberate loader failure for " + id);
            }
            if (loadBehavior == LoadBehavior.ZERO) {
                return new DummyHandler(id, overlay, 0);
            }
            if (loadBehavior == LoadBehavior.WRONG_ID) {
                return new DummyHandler(id + ".loaded", overlay, count);
            }
            if (loadBehavior == LoadBehavior.WRONG_OVERLAY) {
                return new DummyHandler(id, overlay + ".loaded", count);
            }
            if (loadBehavior == LoadBehavior.WRONG_CLASS) {
                return new DerivedDummyHandler(id, overlay, count);
            }
            return new DummyHandler(id, overlay, count);
        }
    }

    private static final class DerivedDummyHandler extends DummyHandler {
        DerivedDummyHandler(String id, String overlay, int count) {
            super(id, overlay, count);
        }
    }

    private static HandlerCategoryPlan planByHandlerId(
            List<HandlerCategoryPlan> plans, String handlerId) {
        for (HandlerCategoryPlan plan : plans) {
            if (handlerId.equals(plan.handlerId)) {
                return plan;
            }
        }
        throw new AssertionError("missing plan for " + handlerId);
    }
}
