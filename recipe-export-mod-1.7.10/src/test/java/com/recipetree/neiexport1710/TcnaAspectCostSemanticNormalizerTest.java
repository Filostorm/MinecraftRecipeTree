package com.recipetree.neiexport1710;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TcnaAspectCostSemanticNormalizerTest {
    private static class AspectBase {
    }

    private static final class LocalizedAspectSubtype extends AspectBase {
    }

    private static final String VITREUS = "vitreus";
    private static final TcnaAspectCostSemanticNormalizer.AspectResolver VITREUS_RESOLVER =
            new TcnaAspectCostSemanticNormalizer.AspectResolver() {
                @Override
                public boolean isExactRegisteredAspect(String tag) {
                    return VITREUS.equals(tag);
                }
            };

    @Test
    public void exactProxyNormalizationPreservesCostAndPayloadOnADeepCopy()
            throws Exception {
        Item pinnedItem = new Item();
        ItemStack source = exactProxy(pinnedItem, 7, 1, VITREUS);
        NBTTagCompound sourceNbt = source.getTagCompound();
        String sourceCanonicalNbt = NbtCanonicalizer.canonical(sourceNbt);

        ItemStack normalized = TcnaAspectCostSemanticNormalizer.normalizeExactProxy(
                source, pinnedItem,
                TcnaAspectCostSemanticNormalizer.ITEM_ASPECT_REGISTRY_ID,
                VITREUS_RESOLVER);

        assertNotSame(source, normalized);
        assertSame(pinnedItem, normalized.getItem());
        assertEquals(7, normalized.stackSize);
        assertEquals(0, normalized.getItemDamage());
        assertNotSame(sourceNbt, normalized.getTagCompound());
        assertEquals(sourceCanonicalNbt,
                NbtCanonicalizer.canonical(normalized.getTagCompound()));
        assertEquals(2, aspectEntry(normalized).getInteger("amount"));
        assertEquals(VITREUS, aspectEntry(normalized).getString("key"));

        assertEquals(7, source.stackSize);
        assertEquals(1, source.getItemDamage());
        assertSame(sourceNbt, source.getTagCompound());
        assertEquals(sourceCanonicalNbt,
                NbtCanonicalizer.canonical(source.getTagCompound()));
        assertEquals(2, aspectEntry(source).getInteger("amount"));

        aspectEntry(normalized).setInteger("amount", 99);
        assertEquals(2, aspectEntry(source).getInteger("amount"));
    }

    @Test
    public void zeroCostProxyProducesCatalogIdentityWithoutInventingARequirement()
            throws Exception {
        Item pinnedItem = new Item();
        ItemStack source = exactProxy(pinnedItem, 0, 1, VITREUS);
        String sourceNbt = NbtCanonicalizer.canonical(source.getTagCompound());

        ItemStack identityStack =
                TcnaAspectCostSemanticNormalizer.normalizeZeroCostProxy(
                        source, pinnedItem,
                        TcnaAspectCostSemanticNormalizer.ITEM_ASPECT_REGISTRY_ID,
                        VITREUS_RESOLVER);

        assertEquals(0, source.stackSize);
        assertEquals(1, source.getItemDamage());
        assertEquals(sourceNbt, NbtCanonicalizer.canonical(source.getTagCompound()));
        assertEquals(1, identityStack.stackSize);
        assertEquals(0, identityStack.getItemDamage());
        assertEquals(sourceNbt,
                NbtCanonicalizer.canonical(identityStack.getTagCompound()));
        assertEquals(
                "thaumcraft-nei-zero-cost-aspect-input-exclusion-v1",
                TcnaAspectCostSemanticNormalizer.ZERO_COST_CONTRACT);
    }

    @Test
    public void zeroCostProxyRejectsNegativeOrPositiveCosts() {
        Item pinnedItem = new Item();
        assertIdentityFailure(() ->
                TcnaAspectCostSemanticNormalizer.normalizeZeroCostProxy(
                        exactProxy(pinnedItem, -1, 1, VITREUS), pinnedItem,
                        TcnaAspectCostSemanticNormalizer.ITEM_ASPECT_REGISTRY_ID,
                        VITREUS_RESOLVER));
        assertIdentityFailure(() ->
                TcnaAspectCostSemanticNormalizer.normalizeZeroCostProxy(
                        exactProxy(pinnedItem, 1, 1, VITREUS), pinnedItem,
                        TcnaAspectCostSemanticNormalizer.ITEM_ASPECT_REGISTRY_ID,
                        VITREUS_RESOLVER));
    }

    @Test
    public void rejectsWrongItemOrRegistryBinding() {
        Item pinnedItem = new Item();
        ItemStack wrongItem = exactProxy(new Item(), 4, 1, VITREUS);
        assertIdentityFailure(() ->
                TcnaAspectCostSemanticNormalizer.normalizeExactProxy(
                        wrongItem, pinnedItem,
                        TcnaAspectCostSemanticNormalizer.ITEM_ASPECT_REGISTRY_ID,
                        VITREUS_RESOLVER));

        ItemStack correctItem = exactProxy(pinnedItem, 4, 1, VITREUS);
        assertIdentityFailure(() ->
                TcnaAspectCostSemanticNormalizer.normalizeExactProxy(
                        correctItem, pinnedItem, "thaumcraftneiplugin:aspect",
                        VITREUS_RESOLVER));
    }

    @Test
    public void rejectsNonPresentationMetadataAndNonpositiveRecipeCost() {
        Item pinnedItem = new Item();
        assertIdentityFailure(() ->
                TcnaAspectCostSemanticNormalizer.normalizeExactProxy(
                        exactProxy(pinnedItem, 1, 0, VITREUS), pinnedItem,
                        TcnaAspectCostSemanticNormalizer.ITEM_ASPECT_REGISTRY_ID,
                        VITREUS_RESOLVER));
        assertIdentityFailure(() ->
                TcnaAspectCostSemanticNormalizer.normalizeExactProxy(
                        exactProxy(pinnedItem, 1, 2, VITREUS), pinnedItem,
                        TcnaAspectCostSemanticNormalizer.ITEM_ASPECT_REGISTRY_ID,
                        VITREUS_RESOLVER));
        assertIdentityFailure(() ->
                TcnaAspectCostSemanticNormalizer.normalizeExactProxy(
                        exactProxy(pinnedItem, 0, 1, VITREUS), pinnedItem,
                        TcnaAspectCostSemanticNormalizer.ITEM_ASPECT_REGISTRY_ID,
                        VITREUS_RESOLVER));
        assertIdentityFailure(() ->
                TcnaAspectCostSemanticNormalizer.normalizeExactProxy(
                        exactProxy(pinnedItem, -3, 1, VITREUS), pinnedItem,
                        TcnaAspectCostSemanticNormalizer.ITEM_ASPECT_REGISTRY_ID,
                        VITREUS_RESOLVER));
    }

    @Test
    public void rejectsEveryUnmodeledNbtShapeUsedByTheSemanticProxy() {
        Item pinnedItem = new Item();

        ItemStack missingNbt = new ItemStack(pinnedItem, 1, 1);
        assertIdentityFailure(() -> normalize(missingNbt, pinnedItem));

        ItemStack wrongListType = new ItemStack(pinnedItem, 1, 1);
        NBTTagCompound wrongListRoot = new NBTTagCompound();
        wrongListRoot.setString("Aspects", VITREUS);
        wrongListType.setTagCompound(wrongListRoot);
        assertIdentityFailure(() -> normalize(wrongListType, pinnedItem));

        ItemStack extraRootKey = exactProxy(pinnedItem, 1, 1, VITREUS);
        extraRootKey.getTagCompound().setBoolean("presentation", true);
        assertIdentityFailure(() -> normalize(extraRootKey, pinnedItem));

        ItemStack multipleAspects = exactProxy(pinnedItem, 1, 1, VITREUS);
        multipleAspects.getTagCompound().getTagList("Aspects", 10)
                .appendTag(aspectPayload("aer", 2));
        assertIdentityFailure(() -> normalize(multipleAspects, pinnedItem));

        ItemStack extraEntryKey = exactProxy(pinnedItem, 1, 1, VITREUS);
        aspectEntry(extraEntryKey).setBoolean("display", true);
        assertIdentityFailure(() -> normalize(extraEntryKey, pinnedItem));

        ItemStack wrongOwnerSentinel = exactProxy(pinnedItem, 1, 1, VITREUS);
        aspectEntry(wrongOwnerSentinel).setInteger("amount", 3);
        assertIdentityFailure(() -> normalize(wrongOwnerSentinel, pinnedItem));
    }

    @Test
    public void rejectsUnknownOrNoncanonicalAspectTags() {
        Item pinnedItem = new Item();
        assertIdentityFailure(() ->
                TcnaAspectCostSemanticNormalizer.normalizeExactProxy(
                        exactProxy(pinnedItem, 1, 1, "future_aspect"), pinnedItem,
                        TcnaAspectCostSemanticNormalizer.ITEM_ASPECT_REGISTRY_ID,
                        VITREUS_RESOLVER));
        assertIdentityFailure(() ->
                TcnaAspectCostSemanticNormalizer.normalizeExactProxy(
                        exactProxy(pinnedItem, 1, 1, " vitreus "), pinnedItem,
                        TcnaAspectCostSemanticNormalizer.ITEM_ASPECT_REGISTRY_ID,
                        VITREUS_RESOLVER));
    }

    @Test
    public void canonicalRegistryBindingAcceptsRegisteredAspectSubtypesOnly() {
        AspectBase base = new AspectBase();
        LocalizedAspectSubtype subtype = new LocalizedAspectSubtype();
        Map<String, AspectBase> registry = new HashMap<String, AspectBase>();
        registry.put("base", base);
        registry.put("custom2", subtype);

        assertTrue(TcnaAspectCostSemanticNormalizer.isCanonicalAspectRegistryBinding(
                AspectBase.class, registry, "base", base, "base"));
        assertTrue(TcnaAspectCostSemanticNormalizer.isCanonicalAspectRegistryBinding(
                AspectBase.class, registry, "custom2", subtype, "custom2"));
        assertFalse(TcnaAspectCostSemanticNormalizer.isCanonicalAspectRegistryBinding(
                LocalizedAspectSubtype.class, registry, "base", base, "base"));
        assertFalse(TcnaAspectCostSemanticNormalizer.isCanonicalAspectRegistryBinding(
                AspectBase.class, registry, "custom2", base, "custom2"));
        assertFalse(TcnaAspectCostSemanticNormalizer.isCanonicalAspectRegistryBinding(
                AspectBase.class, registry, "custom2", subtype, "renamed"));
        assertFalse(TcnaAspectCostSemanticNormalizer.isCanonicalAspectRegistryBinding(
                AspectBase.class, registry, "missing", subtype, "missing"));
    }

    @Test
    public void referenceAuditConsumesTheExactSnapshotAndSortsFingerprints()
            throws Exception {
        TcnaAspectCostSemanticNormalizer.ReferenceAudit audit =
                new TcnaAspectCostSemanticNormalizer.ReferenceAudit();
        audit.expect("handler#1/input/0/0", "z-fingerprint");
        audit.expect("handler#0/input/0/0", "a-fingerprint");

        assertEquals(2, audit.size());
        assertEquals(Arrays.asList("a-fingerprint", "z-fingerprint"),
                audit.sortedFingerprints());
        audit.consume("handler#0/input/0/0", "a-fingerprint");
        audit.consume("handler#1/input/0/0", "z-fingerprint");
        audit.requireExhausted("handler");
    }

    @Test
    public void referenceAuditRejectsDuplicateAndUnexpectedTraversal() throws Exception {
        TcnaAspectCostSemanticNormalizer.ReferenceAudit duplicateExpectation =
                new TcnaAspectCostSemanticNormalizer.ReferenceAudit();
        duplicateExpectation.expect("handler#0/input/0/0", "fingerprint");
        assertSemanticFailure(() -> duplicateExpectation.expect(
                "handler#0/input/0/0", "different"));

        TcnaAspectCostSemanticNormalizer.ReferenceAudit unexpected =
                new TcnaAspectCostSemanticNormalizer.ReferenceAudit();
        assertSemanticFailure(() -> unexpected.consume(
                "handler#0/input/0/0", "fingerprint"));

        TcnaAspectCostSemanticNormalizer.ReferenceAudit doubleConsumption =
                new TcnaAspectCostSemanticNormalizer.ReferenceAudit();
        doubleConsumption.expect("handler#0/input/0/0", "fingerprint");
        doubleConsumption.consume("handler#0/input/0/0", "fingerprint");
        assertSemanticFailure(() -> doubleConsumption.consume(
                "handler#0/input/0/0", "fingerprint"));
    }

    @Test
    public void referenceAuditRejectsFingerprintDriftAndMissingConsumption()
            throws Exception {
        TcnaAspectCostSemanticNormalizer.ReferenceAudit drifted =
                new TcnaAspectCostSemanticNormalizer.ReferenceAudit();
        drifted.expect("handler#0/input/0/0", "expected");
        assertSemanticFailure(() -> drifted.consume(
                "handler#0/input/0/0", "observed"));

        TcnaAspectCostSemanticNormalizer.ReferenceAudit missing =
                new TcnaAspectCostSemanticNormalizer.ReferenceAudit();
        missing.expect("handler#0/input/0/0", "fingerprint");
        assertSemanticFailure(() -> missing.requireExhausted("handler"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void handlerAllowlistContainsExactlyTheFourPinnedTcnaHandlers()
            throws Exception {
        Set<String> expected = new HashSet<String>(Arrays.asList(
                "ru.timeconqueror.tcneiadditions.nei.TCNACrucibleRecipeHandler",
                "ru.timeconqueror.tcneiadditions.nei.TCNAInfusionRecipeHandler",
                "ru.timeconqueror.tcneiadditions.nei.arcaneworkbench."
                        + "ArcaneCraftingShapedHandler",
                "ru.timeconqueror.tcneiadditions.nei.arcaneworkbench."
                        + "ArcaneCraftingShapelessHandler"));
        Field handlersField = TcnaAspectCostSemanticNormalizer.class
                .getDeclaredField("HANDLERS");
        handlersField.setAccessible(true);
        Set<String> actual = (Set<String>) handlersField.get(null);

        assertEquals(expected, actual);
        assertEquals(4, actual.size());
        for (String handler : expected) {
            assertTrue(TcnaAspectCostSemanticNormalizer.isPinnedHandler(handler));
        }
        assertFalse(TcnaAspectCostSemanticNormalizer.isPinnedHandler(
                "ru.timeconqueror.tcneiadditions.nei.FutureRecipeHandler"));
        assertFalse(TcnaAspectCostSemanticNormalizer.isPinnedHandler(null));
    }

    private static ItemStack normalize(ItemStack source, Item pinnedItem)
            throws Exception {
        return TcnaAspectCostSemanticNormalizer.normalizeExactProxy(
                source, pinnedItem,
                TcnaAspectCostSemanticNormalizer.ITEM_ASPECT_REGISTRY_ID,
                VITREUS_RESOLVER);
    }

    private static ItemStack exactProxy(
            Item item, int stackSize, int metadata, String tag) {
        ItemStack stack = new ItemStack(item, stackSize, metadata);
        NBTTagList aspects = new NBTTagList();
        aspects.appendTag(aspectPayload(tag, 2));
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("Aspects", aspects);
        stack.setTagCompound(root);
        return stack;
    }

    private static NBTTagCompound aspectPayload(String tag, int amount) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("amount", amount);
        entry.setString("key", tag);
        return entry;
    }

    private static NBTTagCompound aspectEntry(ItemStack stack) {
        return stack.getTagCompound().getTagList("Aspects", 10)
                .getCompoundTagAt(0);
    }

    private static void assertIdentityFailure(
            org.junit.function.ThrowingRunnable operation) {
        ExportFailure failure = assertThrows(ExportFailure.class, operation);
        assertEquals("ITEM_IDENTITY", failure.code);
    }

    private static void assertSemanticFailure(
            org.junit.function.ThrowingRunnable operation) {
        ExportFailure failure = assertThrows(ExportFailure.class, operation);
        assertEquals("RECIPE_SEMANTICS", failure.code);
    }
}
