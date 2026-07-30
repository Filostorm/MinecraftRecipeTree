package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.ItemList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumChatFormatting;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CropGraphSemanticContractTest {
    private static final Item FIXTURE_ITEM = new Item();
    private static final CropGraphSemanticContract.StackCanonicalizer TEST_STACK_CANONICALIZER =
            new CropGraphSemanticContract.StackCanonicalizer() {
                @Override
                public String canonicalize(ItemStack stack, int amount, String role)
                        throws ExportFailure {
                    if (stack == null || stack.getItem() != FIXTURE_ITEM) {
                        throw new ExportFailure("ITEM_IDENTITY",
                                "fixture received an unexpected item for " + role);
                    }
                    if (stack.stackSize != amount) {
                        throw new ExportFailure("QUANTITY_INVALID",
                                "fixture amount mismatch for " + role);
                    }
                    String nbt = stack.getTagCompound() == null
                            ? "-" : NbtCanonicalizer.canonical(stack.getTagCompound());
                    return "fixture-item|meta=" + stack.getItemDamage()
                            + "|amount=" + amount + "|nbt=" + nbt;
                }
            };

    public abstract static class ApiCropCard {
        public String owner() {
            return "fixture";
        }

        public abstract String name();
    }

    public static final class FixtureCrop extends ApiCropCard {
        private final String name;

        FixtureCrop(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    public static final class FixtureCropApi {
        static final Map<String, ApiCropCard> CROPS =
                new HashMap<String, ApiCropCard>();

        public static ApiCropCard getCrop(ItemStack stack) {
            if (stack == null || stack.getTagCompound() == null) {
                return null;
            }
            return CROPS.get(stack.getTagCompound().getString("fixtureCrop"));
        }
    }

    public static final class FixtureBreedResult {
        private final ApiCropCard result;
        int points;
        int total;
        private final ApiCropCard[] input;
        private final ItemStack[] itemInputs;
        private final ItemStack itemResult;
        private Float chanceOverride;

        FixtureBreedResult(ApiCropCard result, int points, int total,
                           ApiCropCard[] input, ItemStack[] itemInputs,
                           ItemStack itemResult) {
            this.result = result;
            this.points = points;
            this.total = total;
            this.input = input;
            this.itemInputs = itemInputs;
            this.itemResult = itemResult;
        }

        public ApiCropCard[] getInput() {
            return input;
        }

        public int getPoints() {
            return points;
        }

        public float getChance() {
            return chanceOverride == null
                    ? (points / (float) total) * 100.0F
                    : chanceOverride.floatValue();
        }

        public ApiCropCard getResult() {
            return result;
        }

        public ItemStack[] getItemInputs() {
            return itemInputs;
        }

        public ItemStack getItemResult() {
            return itemResult;
        }

        public boolean matches(FixtureBreedResult other) {
            return other != null
                    && input.length == other.input.length
                    && points == other.points
                    && result == other.result;
        }
    }

    private FixtureCrop alpha;
    private FixtureCrop omega;
    private FixtureCrop result;
    private CropGraphSemanticContract contract;

    @Before
    public void setUp() throws Exception {
        alpha = new FixtureCrop("Alpha");
        omega = new FixtureCrop("Omega");
        result = new FixtureCrop("Result");
        FixtureCropApi.CROPS.clear();
        FixtureCropApi.CROPS.put(alpha.name(), alpha);
        FixtureCropApi.CROPS.put(omega.name(), omega);
        FixtureCropApi.CROPS.put(result.name(), result);
        CropIdentityContract identities =
                CropIdentityContract.bindForTesting(ApiCropCard.class);
        contract = CropGraphSemanticContract.bindForTesting(
                FixtureCropApi.class, FixtureBreedResult.class, identities,
                TEST_STACK_CANONICALIZER);
    }

    @Test
    public void capturesCleanStacksWithDeterministicInputOrderAndDeepCopies()
            throws Exception {
        ItemStack omegaStack = stack(omega, 2, "omega-payload");
        ItemStack alphaStack = stack(alpha, 3, "alpha-payload");
        ItemStack resultStack = stack(result, 4, "result-payload");
        FixtureBreedResult source = recipe(
                new ApiCropCard[]{omega, alpha},
                new ItemStack[]{omegaStack, alphaStack}, resultStack);

        CropGraphSemanticContract.GraphRecipe graph = contract.capture(
                source, new HashMap<String, Object>());

        assertSame(alpha, graph.inputs.get(0).crop);
        assertSame(omega, graph.inputs.get(1).crop);
        assertEquals(3, graph.inputs.get(0).amount);
        assertEquals(2, graph.inputs.get(1).amount);
        assertEquals(4, graph.output.amount);
        assertEquals(25, graph.points);
        assertEquals(100, graph.total);
        assertEquals(25.0F, graph.chance, 0.0F);
        assertTrue(graph.canonical.contains("T100;"));
        assertNotSame(alphaStack, graph.inputs.get(0).stack);
        assertNotSame(omegaStack, graph.inputs.get(1).stack);
        assertNotSame(resultStack, graph.output.stack);
        assertNotSame(resultStack.getTagCompound(),
                graph.output.stack.getTagCompound());

        String graphCanonical = graph.canonical;
        resultStack.stackSize = 99;
        resultStack.getTagCompound().setString("payload", "mutated");
        assertEquals(4, graph.output.stack.stackSize);
        assertEquals("result-payload",
                graph.output.stack.getTagCompound().getString("payload"));
        assertEquals(graphCanonical, graph.canonical);
    }

    @Test
    public void exactPreviewLoreIsValidatedButNeverEntersGraphOutput()
            throws Exception {
        FixtureBreedResult source = recipe(
                new ApiCropCard[]{omega, alpha},
                new ItemStack[]{stack(omega, 2, "omega"), stack(alpha, 3, "alpha")},
                stack(result, 4, "result"));
        CropGraphSemanticContract.GraphRecipe graph = contract.capture(
                source, new HashMap<String, Object>());
        ItemStack previewOutput = graph.output.stack.copy();
        appendPreviewLore(previewOutput, graph.points, graph.chance);
        CropGraphSemanticContract.PresentationDigestStream presentationDigest =
                presentationDigest();
        List<PositionedStack> renderedInputs = Arrays.asList(
                new PositionedStack(graph.inputs.get(1).stack.copy(), 43, 40),
                new PositionedStack(graph.inputs.get(0).stack.copy(), 107, 40));
        PositionedStack renderedPreview = new PositionedStack(
                previewOutput.copy(), 75, 40);

        CropGraphSemanticContract.PreviewAudit audit =
                contract.validateLoreOnlyPreview(
                renderedInputs,
                renderedPreview, graph,
                7, "fixture-preview", presentationDigest);
        assertEquals(1, audit.renderedAlternativeCount);
        assertEquals(1, audit.renderedGraphCropAlternativeCount);
        assertTrue(audit.preservesGraphCropInEveryAlternative());
        assertEquals(CropGraphSemanticContract.PermutationSource.DIRECT_STACK,
                audit.permutationSource);
        assertEquals(2, audit.renderedInputAlternativeCount);
        assertEquals(2, audit.renderedGraphCropInputAlternativeCount);
        assertEquals(2, audit.cropPreservingInputSlots);
        assertEquals(0, audit.lossyInputSlots);
        assertEquals(2, audit.directInputSlots);
        assertEquals(0, audit.wildcardItemListInputSlots);
        assertEquals(1, audit.minimumInputAlternativesPerSlot);
        assertEquals(1, audit.maximumInputAlternativesPerSlot);
        String semanticCanonical = TEST_STACK_CANONICALIZER.canonicalize(
                previewOutput, previewOutput.stackSize, "legacy semantic");
        String streamingCorpus = "ic2-crop-nei-presentation-corpus-v4\n"
                + "ic2-crop-nei-presentation-v4;"
                + frame(graph.canonical)
                + frame(semanticCanonical)
                + "I2;"
                + "I0;" + renderedShape(renderedInputs.get(0), 1)
                + "I1;" + renderedShape(renderedInputs.get(1), 1)
                + "O;" + renderedShape(renderedPreview, 1);
        MessageDigest expectedDigest = MessageDigest.getInstance("SHA-256");
        expectedDigest.update(streamingCorpus.getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(expectedDigest.digest(), presentationDigest.finish());

        NBTTagCompound cleanDisplay = graph.output.stack.getTagCompound()
                .getCompoundTag("display");
        assertFalse(cleanDisplay.hasKey("Lore"));

        appendPreviewLore(previewOutput, graph.points, graph.chance);
        assertFailure("RECIPE_SEMANTICS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                contract.validateLoreOnlyPreview(
                        Arrays.asList(
                                new PositionedStack(graph.inputs.get(1).stack.copy(), 43, 40),
                                new PositionedStack(graph.inputs.get(0).stack.copy(), 107, 40)),
                        new PositionedStack(previewOutput.copy(), 75, 40), graph,
                        8, "fixture-double-lore", presentationDigest());
            }
        });
    }

    @Test
    public void canonicalInputOrderAndPreviewPositionsAreEnforced()
            throws Exception {
        final FixtureBreedResult runtimeHashOrder = recipe(
                new ApiCropCard[]{omega, alpha},
                new ItemStack[]{stack(omega, 2, "omega"), stack(alpha, 3, "alpha")},
                stack(result, 4, "result"));
        assertFailure("RECIPE_SEMANTICS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                contract.validateCanonicalInputOrder(runtimeHashOrder);
            }
        });

        FixtureBreedResult canonical = recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{stack(alpha, 3, "alpha"), stack(omega, 2, "omega")},
                stack(result, 4, "result"));
        contract.validateCanonicalInputOrder(canonical);
        final CropGraphSemanticContract.GraphRecipe graph = contract.capture(
                canonical, new HashMap<String, Object>());
        final ItemStack previewOutput = graph.output.stack.copy();
        appendPreviewLore(previewOutput, graph.points, graph.chance);

        assertFailure("RECIPE_SEMANTICS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                contract.validateLoreOnlyPreview(
                        Arrays.asList(
                                new PositionedStack(graph.inputs.get(1).stack.copy(), 43, 40),
                                new PositionedStack(graph.inputs.get(0).stack.copy(), 107, 40)),
                        new PositionedStack(previewOutput.copy(), 75, 40), graph,
                        9, "fixture-wrong-input-order", presentationDigest());
            }
        });
    }

    @Test
    public void wildcardNeiPermutationMayLoseCropNbtButCannotChangeCleanGraph()
            throws Exception {
        FixtureBreedResult source = recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{stack(alpha, 1, "alpha"), stack(omega, 1, "omega")},
                stackWithMetadata(result, 1, 32767, "wildcard-result"));
        CropGraphSemanticContract.GraphRecipe graph = contract.capture(
                source, new HashMap<String, Object>());
        ItemStack semanticPreview = graph.output.stack.copy();
        appendPreviewLore(semanticPreview, graph.points, graph.chance);

        List<ItemStack> priorPermutations = new ArrayList<ItemStack>(
                ItemList.itemMap.removeAll(FIXTURE_ITEM));
        try {
            ItemList.itemMap.put(FIXTURE_ITEM, new ItemStack(FIXTURE_ITEM, 1, 0));
            PositionedStack rendered = new PositionedStack(
                    semanticPreview.copy(), 75, 40);

            CropGraphSemanticContract.PreviewAudit audit =
                    contract.validateLoreOnlyPreview(
                            canonicalPreviewInputs(graph), rendered, graph,
                            10, "fixture-wildcard-loss", presentationDigest());

            assertSame(result, graph.output.crop);
            assertEquals(32767, graph.output.stack.getItemDamage());
            assertEquals(1, audit.renderedAlternativeCount);
            assertEquals(0, audit.renderedGraphCropAlternativeCount);
            assertFalse(audit.preservesGraphCropInEveryAlternative());
            assertEquals(
                    CropGraphSemanticContract.PermutationSource.WILDCARD_ITEM_LIST,
                    audit.permutationSource);
            assertNull(FixtureCropApi.getCrop(rendered.items[0]));
        } finally {
            ItemList.itemMap.removeAll(FIXTURE_ITEM);
            ItemList.itemMap.putAll(FIXTURE_ITEM, priorPermutations);
        }
    }

    @Test
    public void wildcardInputMayUseExactNbtFreeItemListPermutationAsLossyPresentation()
            throws Exception {
        FixtureBreedResult source = recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{
                        stackWithMetadata(alpha, 1, 32767, "wildcard-alpha"),
                        stack(omega, 1, "omega")},
                stack(result, 1, "result"));
        CropGraphSemanticContract.GraphRecipe graph = contract.capture(
                source, new HashMap<String, Object>());
        ItemStack semanticPreview = graph.output.stack.copy();
        appendPreviewLore(semanticPreview, graph.points, graph.chance);

        List<ItemStack> priorPermutations = new ArrayList<ItemStack>(
                ItemList.itemMap.removeAll(FIXTURE_ITEM));
        try {
            ItemList.itemMap.put(FIXTURE_ITEM,
                    new ItemStack(FIXTURE_ITEM, 99, 7));
            List<PositionedStack> renderedInputs = canonicalPreviewInputs(graph);
            PositionedStack renderedOutput = new PositionedStack(
                    semanticPreview.copy(), 75, 40);

            CropGraphSemanticContract.PreviewAudit audit =
                    contract.validateLoreOnlyPreview(
                            renderedInputs, renderedOutput, graph,
                            14487, "fixture-wildcard-input-loss",
                            presentationDigest());

            assertSame(alpha, graph.inputs.get(0).crop);
            assertTrue(graph.inputs.get(0).stack.hasTagCompound());
            assertEquals(32767, graph.inputs.get(0).stack.getItemDamage());
            assertEquals(1, renderedInputs.get(0).items[0].stackSize);
            assertEquals(7, renderedInputs.get(0).items[0].getItemDamage());
            assertNull(renderedInputs.get(0).items[0].getTagCompound());
            assertNull(FixtureCropApi.getCrop(renderedInputs.get(0).items[0]));
            assertEquals(2, audit.renderedInputAlternativeCount);
            assertEquals(1, audit.renderedGraphCropInputAlternativeCount);
            assertEquals(1, audit.cropPreservingInputSlots);
            assertEquals(1, audit.lossyInputSlots);
            assertEquals(1, audit.directInputSlots);
            assertEquals(1, audit.wildcardItemListInputSlots);
            assertEquals(0, audit.wildcardEmptyFallbackInputSlots);
            assertEquals(0, audit.wildcardFireFallbackInputSlots);
        } finally {
            ItemList.itemMap.removeAll(FIXTURE_ITEM);
            ItemList.itemMap.putAll(FIXTURE_ITEM, priorPermutations);
        }
    }

    @Test
    public void directInputStillRejectsDroppedCropNbt() throws Exception {
        FixtureBreedResult source = recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{stack(alpha, 1, "alpha"), stack(omega, 1, "omega")},
                stack(result, 1, "result"));
        final CropGraphSemanticContract.GraphRecipe graph = contract.capture(
                source, new HashMap<String, Object>());
        final ItemStack semanticPreview = graph.output.stack.copy();
        appendPreviewLore(semanticPreview, graph.points, graph.chance);
        final List<PositionedStack> renderedInputs = canonicalPreviewInputs(graph);
        renderedInputs.get(1).items[0].setTagCompound(null);

        assertFailure("RECIPE_SEMANTICS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                contract.validateLoreOnlyPreview(
                        renderedInputs,
                        new PositionedStack(semanticPreview.copy(), 75, 40), graph,
                        14488, "fixture-direct-input-nbt-loss",
                        presentationDigest());
            }
        });
    }

    @Test
    public void rejectsItemListAlternativeAliasingItsNonnullSourceNbt()
            throws Exception {
        FixtureBreedResult source = recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{stack(alpha, 1, "alpha"), stack(omega, 1, "omega")},
                stackWithMetadata(result, 1, 32767, "wildcard-result"));
        final CropGraphSemanticContract.GraphRecipe graph = contract.capture(
                source, new HashMap<String, Object>());
        ItemStack semanticPreview = graph.output.stack.copy();
        appendPreviewLore(semanticPreview, graph.points, graph.chance);
        final ItemStack livePermutation = stack(result, 1, "item-list-source");

        List<ItemStack> priorPermutations = new ArrayList<ItemStack>(
                ItemList.itemMap.removeAll(FIXTURE_ITEM));
        try {
            ItemList.itemMap.put(FIXTURE_ITEM, livePermutation);
            final PositionedStack rendered = new PositionedStack(
                    semanticPreview.copy(), 75, 40);
            rendered.items[0].setTagCompound(livePermutation.getTagCompound());

            assertFailure("RECIPE_SEMANTICS", new CheckedCall() {
                @Override
                public void run() throws Exception {
                    contract.validateLoreOnlyPreview(
                            canonicalPreviewInputs(graph), rendered, graph,
                            11, "fixture-item-list-nbt-alias", presentationDigest());
                }
            });
        } finally {
            ItemList.itemMap.removeAll(FIXTURE_ITEM);
            ItemList.itemMap.putAll(FIXTURE_ITEM, priorPermutations);
        }
    }

    @Test
    public void semanticPreviewMismatchFailsWithPageSpecificDiagnostics()
            throws Exception {
        FixtureBreedResult source = recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{stack(alpha, 1, "alpha"), stack(omega, 1, "omega")},
                stack(result, 1, "result"));
        final CropGraphSemanticContract.GraphRecipe graph = contract.capture(
                source, new HashMap<String, Object>());
        final ItemStack wrongSemanticPreview = stack(alpha, 1, "wrong-result");
        appendPreviewLore(wrongSemanticPreview, graph.points, graph.chance);

        try {
            contract.validateLoreOnlyPreview(
                    canonicalPreviewInputs(graph),
                    new PositionedStack(wrongSemanticPreview.copy(), 75, 40), graph,
                    314, "fixture-semantic-id", presentationDigest());
        } catch (ExportFailure failure) {
            assertEquals("RECIPE_SEMANTICS", failure.code);
            assertTrue(failure.getMessage().contains("page index=314"));
            assertTrue(failure.getMessage().contains("semanticId=fixture-semantic-id"));
            assertTrue(failure.getMessage().contains("graphOutput=O7:fixtureN6:Result"));
            assertTrue(failure.getMessage().contains(
                    "rendered preview output alternative[0]"));
            assertTrue(failure.getMessage().contains("wrong-result"));
            return;
        }
        throw new AssertionError("Expected page-specific semantic preview failure");
    }

    @Test
    public void renderedPermutationCoordinatesRemainExact() throws Exception {
        FixtureBreedResult source = recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{stack(alpha, 1, "alpha"), stack(omega, 1, "omega")},
                stack(result, 1, "result"));
        final CropGraphSemanticContract.GraphRecipe graph = contract.capture(
                source, new HashMap<String, Object>());
        final ItemStack semanticPreview = graph.output.stack.copy();
        appendPreviewLore(semanticPreview, graph.points, graph.chance);
        final PositionedStack wrongPosition = new PositionedStack(
                semanticPreview.copy(), 74, 40);

        assertFailure("RECIPE_SEMANTICS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                contract.validateLoreOnlyPreview(
                        canonicalPreviewInputs(graph),
                        wrongPosition, graph, 11, "fixture-wrong-position",
                        presentationDigest());
            }
        });
    }

    @Test
    public void rejectsAliasedNonnullNbtOnInputAndOutputDefensiveCopies()
            throws Exception {
        FixtureBreedResult source = recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{stack(alpha, 1, "alpha"), stack(omega, 1, "omega")},
                stack(result, 1, "result"));
        final CropGraphSemanticContract.GraphRecipe graph = contract.capture(
                source, new HashMap<String, Object>());
        final ItemStack semanticPreview = graph.output.stack.copy();
        appendPreviewLore(semanticPreview, graph.points, graph.chance);

        final List<PositionedStack> aliasedInput = canonicalPreviewInputs(graph);
        aliasedInput.get(0).item.setTagCompound(
                aliasedInput.get(0).items[0].getTagCompound());
        assertFailure("RECIPE_SEMANTICS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                contract.validateLoreOnlyPreview(
                        aliasedInput,
                        new PositionedStack(semanticPreview.copy(), 75, 40), graph,
                        12, "fixture-input-nbt-alias", presentationDigest());
            }
        });

        final PositionedStack aliasedOutput = new PositionedStack(
                semanticPreview.copy(), 75, 40);
        aliasedOutput.item.setTagCompound(
                aliasedOutput.items[0].getTagCompound());
        assertFailure("RECIPE_SEMANTICS", new CheckedCall() {
            @Override
            public void run() throws Exception {
                contract.validateLoreOnlyPreview(
                        canonicalPreviewInputs(graph), aliasedOutput, graph,
                        13, "fixture-output-nbt-alias", presentationDigest());
            }
        });
    }

    @Test
    public void rejectsCropRoundTripMismatch() throws Exception {
        final FixtureBreedResult mismatch = recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{stack(alpha, 1, "alpha"), stack(omega, 1, "omega")},
                stack(alpha, 1, "wrong-result-crop"));

        assertCaptureFailure("RECIPE_SEMANTICS", mismatch);
    }

    @Test
    public void rejectsAliasedOrNonPositiveCleanStacks() throws Exception {
        final ItemStack aliased = stack(alpha, 1, "aliased");
        assertCaptureFailure("RECIPE_SEMANTICS", recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{aliased, aliased}, stack(result, 1, "result")));
        assertCaptureFailure("QUANTITY_INVALID", recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{stack(alpha, 0, "zero"), stack(omega, 1, "omega")},
                stack(result, 1, "result")));
    }

    @Test
    public void rejectsResultCropAsInputAndChanceOrTotalDrift() throws Exception {
        assertCaptureFailure("RECIPE_SEMANTICS", recipe(
                new ApiCropCard[]{result, omega},
                new ItemStack[]{stack(result, 1, "result-input"), stack(omega, 1, "omega")},
                stack(result, 1, "result")));

        final FixtureBreedResult badChance = recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{stack(alpha, 1, "alpha"), stack(omega, 1, "omega")},
                stack(result, 1, "result"));
        badChance.chanceOverride = Float.valueOf(24.0F);
        assertCaptureFailure("RECIPE_SEMANTICS", badChance);

        final FixtureBreedResult badTotal = recipe(
                new ApiCropCard[]{alpha, omega},
                new ItemStack[]{stack(alpha, 1, "alpha"), stack(omega, 1, "omega")},
                stack(result, 1, "result"));
        badTotal.total = 0;
        assertCaptureFailure("RECIPE_SEMANTICS", badTotal);
    }

    @Test
    public void canonicalGraphIncludesFullNbtAndDistinguishesAmounts()
            throws Exception {
        CropGraphSemanticContract.GraphRecipe first = contract.capture(
                recipe(new ApiCropCard[]{alpha, omega},
                        new ItemStack[]{stack(alpha, 1, "alpha"), stack(omega, 2, "omega")},
                        stack(result, 3, "result")),
                new HashMap<String, Object>());
        CropGraphSemanticContract.GraphRecipe changedAmount = contract.capture(
                recipe(new ApiCropCard[]{alpha, omega},
                        new ItemStack[]{stack(alpha, 1, "alpha"), stack(omega, 5, "omega")},
                        stack(result, 3, "result")),
                new HashMap<String, Object>());
        CropGraphSemanticContract.GraphRecipe changedNbt = contract.capture(
                recipe(new ApiCropCard[]{alpha, omega},
                        new ItemStack[]{stack(alpha, 1, "alpha"), stack(omega, 2, "changed")},
                        stack(result, 3, "result")),
                new HashMap<String, Object>());

        assertNotEquals(first.canonical, changedAmount.canonical);
        assertNotEquals(first.canonical, changedNbt.canonical);
    }

    private FixtureBreedResult recipe(ApiCropCard[] inputs,
                                      ItemStack[] itemInputs,
                                      ItemStack itemResult) {
        return new FixtureBreedResult(
                result, 25, 100, inputs, itemInputs, itemResult);
    }

    private static ItemStack stack(FixtureCrop crop, int amount, String payload) {
        return stackWithMetadata(crop, amount, 0, payload);
    }

    private static ItemStack stackWithMetadata(
            FixtureCrop crop, int amount, int metadata, String payload) {
        ItemStack stack = new ItemStack(FIXTURE_ITEM, amount, metadata);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("fixtureCrop", crop.name());
        tag.setString("payload", payload);
        NBTTagCompound exactNestedPayload = new NBTTagCompound();
        exactNestedPayload.setInteger("value", 7);
        tag.setTag("exactNestedPayload", exactNestedPayload);
        stack.setTagCompound(tag);
        return stack;
    }

    private static List<PositionedStack> canonicalPreviewInputs(
            CropGraphSemanticContract.GraphRecipe graph) {
        return Arrays.asList(
                new PositionedStack(graph.inputs.get(0).stack.copy(), 43, 40),
                new PositionedStack(graph.inputs.get(1).stack.copy(), 107, 40));
    }

    private static void appendPreviewLore(ItemStack stack, int points, float chance) {
        NBTTagCompound root = stack.hasTagCompound()
                ? stack.getTagCompound() : new NBTTagCompound();
        stack.setTagCompound(root);
        NBTTagCompound display = root.getCompoundTag("display");
        root.setTag("display", display);
        NBTTagList lore = display.getTagList("Lore", 8);
        display.setTag("Lore", lore);
        String prefix = EnumChatFormatting.RESET.toString()
                + EnumChatFormatting.GOLD.toString();
        lore.appendTag(new NBTTagString(prefix + "Breeding Points: " + points));
        lore.appendTag(new NBTTagString(prefix + "Breeding Chance: "
                + ItemStack.field_111284_a.format(chance) + "%"));
    }

    private static CropGraphSemanticContract.PresentationDigestStream presentationDigest()
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return CropGraphSemanticContract.beginPresentationDigest(digest);
    }

    private static String frame(String value) {
        return value.length() + ":" + value;
    }

    private static String renderedShape(PositionedStack positioned,
                                        int graphCropAlternatives)
            throws Exception {
        StringBuilder value = new StringBuilder();
        value.append('X').append(positioned.relx)
                .append(";Y").append(positioned.rely)
                .append(";A").append(positioned.items.length).append(';');
        for (int index = 0; index < positioned.items.length; index++) {
            ItemStack alternative = positioned.items[index];
            value.append(frame(TEST_STACK_CANONICALIZER.canonicalize(
                    alternative, alternative.stackSize,
                    "manual alternative[" + index + "]")));
        }
        value.append('C').append(frame(TEST_STACK_CANONICALIZER.canonicalize(
                positioned.item, positioned.item.stackSize, "manual current")))
                .append('G').append(graphCropAlternatives).append(';');
        return value.toString();
    }

    private void assertCaptureFailure(String code, final FixtureBreedResult source)
            throws Exception {
        assertFailure(code, new CheckedCall() {
            @Override
            public void run() throws Exception {
                contract.capture(source, new HashMap<String, Object>());
            }
        });
    }

    private static void assertFailure(String code, CheckedCall call) throws Exception {
        try {
            call.run();
        } catch (ExportFailure failure) {
            assertEquals(code, failure.code);
            return;
        }
        throw new AssertionError("Expected " + code);
    }

    private interface CheckedCall {
        void run() throws Exception;
    }
}
