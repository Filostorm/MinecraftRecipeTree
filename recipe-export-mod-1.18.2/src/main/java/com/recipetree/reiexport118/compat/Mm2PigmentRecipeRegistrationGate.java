package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.registry.ReloadStage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Runtime bridge for the exact Mekanism pigment recipe-registration census. */
public final class Mm2PigmentRecipeRegistrationGate {
    private static final ResourceLocation PIGMENT_EXTRACTOR =
            new ResourceLocation("mekanism", "pigment_extractor");
    private static final Mm2PigmentRecipeRegistrationSequence SEQUENCE =
            new Mm2PigmentRecipeRegistrationSequence();

    private Mm2PigmentRecipeRegistrationGate() {
    }

    static void beginStage(ReloadStage stage) {
        if (stage == ReloadStage.END) {
            SEQUENCE.beginEnd(Thread.currentThread());
        }
    }

    static void finishStage(ReloadStage stage) {
        if (stage != ReloadStage.END) {
            return;
        }
        Mm2PigmentRecipeRegistrationSequence.Summary summary =
                SEQUENCE.finishEnd(Thread.currentThread());
        ReiExportMod.LOGGER.info(
                "[reiexport] Verified exact Mekanism pigment JEI registration "
                        + "queuedCalls={} executionStarts={} executionFinishes={} "
                        + "serialOverrideCalls={} upstreamMultithreadEligible={} "
                        + "optimizedStarts={} optimizedFinishes={} "
                        + "collectionIdentity={} collectionSize={} "
                        + "distinctRecipeIds={} distinctRecipeIdentities={} "
                        + "orderedRecipeIdsSha256={} sortedRecipeIdsSha256={} path={}",
                summary.queuedCalls(), summary.executionStarts(),
                summary.executionFinishes(), summary.serialOverrideCalls(),
                summary.upstreamMultithreadEligible(), summary.optimizedStarts(),
                summary.optimizedFinishes(), summary.collectionIdentityHash(),
                summary.collectionSize(), summary.distinctRecipeIds(),
                summary.distinctRecipeIdentities(), summary.orderedRecipeIdsSha256(),
                summary.sortedRecipeIdsSha256(), summary.executionPath());
    }

    public static Collection<?> canonicalizeQueued(
            Object registration,
            Collection<?> recipes,
            ResourceLocation categoryId
    ) {
        if (!isOwnedPigment(categoryId)) {
            return recipes;
        }
        Mm2PigmentRecipeRegistrationSequence.Census source = census(recipes);
        Mm2PigmentRecipeRegistrationSequence.Census canonical =
                SEQUENCE.canonicalizeQueued(
                        registration, source, Thread.currentThread());
        ReiExportMod.LOGGER.info(
                "[reiexport] Canonicalized exact Mekanism pigment JEI queue "
                        + "sourceCollectionIdentity={} canonicalCollectionIdentity={} "
                        + "recipes={} orderedRecipeIdsSha256={}",
                System.identityHashCode(source.collectionIdentity()),
                System.identityHashCode(canonical.collectionIdentity()),
                canonical.collectionSize(),
                Mm2PigmentRecipeRegistrationSequence.EXPECTED_SORTED_RECIPE_IDS_SHA256);
        return (Collection<?>) canonical.collectionIdentity();
    }

    public static void beginExecution(
            Collection<?> recipes,
            ResourceLocation categoryId
    ) {
        if (isOwnedPigment(categoryId)) {
            SEQUENCE.beginExecution(census(recipes), Thread.currentThread());
        }
    }

    public static void finishExecution(
            Collection<?> recipes,
            ResourceLocation categoryId
    ) {
        if (isOwnedPigment(categoryId)) {
            SEQUENCE.finishExecution(census(recipes), Thread.currentThread());
        }
    }

    public static boolean forceSerialExecution(
            Collection<?> recipes,
            CategoryIdentifier<?> categoryId,
            boolean originalDecision
    ) {
        if (!isOwnedPigment(identifier(categoryId))) {
            return originalDecision;
        }
        return SEQUENCE.forceSerialExecution(
                census(recipes), originalDecision, Thread.currentThread());
    }

    public static void beginOptimized(
            List<?> recipes,
            CategoryIdentifier<?> categoryId
    ) {
        if (isOwnedPigment(identifier(categoryId))) {
            SEQUENCE.beginOptimized(census(recipes), Thread.currentThread());
        }
    }

    public static void finishOptimized(
            List<?> recipes,
            CategoryIdentifier<?> categoryId
    ) {
        if (isOwnedPigment(identifier(categoryId))) {
            SEQUENCE.finishOptimized(census(recipes), Thread.currentThread());
        }
    }

    static void requireComplete() {
        SEQUENCE.requireComplete();
    }

    static void fail(Throwable failure) {
        SEQUENCE.fail(failure);
    }

    private static boolean isOwnedPigment(ResourceLocation categoryId) {
        return Mm2ReiLifecycleGate.isOwnedReloadActiveForCompatibility()
                && PIGMENT_EXTRACTOR.equals(categoryId);
    }

    private static ResourceLocation identifier(CategoryIdentifier<?> categoryId) {
        if (categoryId == null) {
            throw new IllegalStateException(
                    "JEI optimized recipe registration received a null category identifier");
        }
        return categoryId.getIdentifier();
    }

    private static Mm2PigmentRecipeRegistrationSequence.Census census(
            Collection<?> recipes
    ) {
        if (recipes == null) {
            throw new IllegalStateException(
                    "Mekanism pigment JEI recipe collection must not be null");
        }
        List<Mm2PigmentRecipeRegistrationSequence.RecipeOrigin> origins =
                new ArrayList<>(recipes.size());
        for (Object value : recipes) {
            if (!(value instanceof Recipe<?> recipe)) {
                throw new IllegalStateException(
                        "Mekanism pigment JEI recipe collection contains a non-Recipe value: "
                                + (value == null ? "null" : value.getClass().getName()));
            }
            origins.add(new Mm2PigmentRecipeRegistrationSequence.RecipeOrigin(
                    recipe, recipe.getId()));
        }
        return new Mm2PigmentRecipeRegistrationSequence.Census(
                recipes, recipes.size(), List.copyOf(origins));
    }
}
