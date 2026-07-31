package com.recipetree.reiexport118.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/** Canonicalizes and audits only IF's six pinned Titanium recipe-list reads. */
public final class IndustrialForegoingRecipeListOrderCompatibility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<RecipeListAudit> VERIFIED_INVOCATIONS = new ArrayList<>();

    private IndustrialForegoingRecipeListOrderCompatibility() {
    }

    public static <T extends Recipe<?>> List<T> canonicalRecipes(
            RecipeType<T> recipeType,
            List<T> source
    ) {
        try {
            Mm2DeterminismCompatibility.requireArmed(
                    IndustrialForegoingOreTagOrderContract.MOD_ID);
            Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.TITANIUM.modId());
            if (recipeType == null) {
                throw new IllegalStateException(
                        "Industrial Foregoing JEICustomPlugin received a null recipe type");
            }
            ResourceLocation typeKey = Registry.RECIPE_TYPE.getKey(recipeType);
            if (typeKey == null) {
                throw new IllegalStateException(
                        "Industrial Foregoing JEICustomPlugin received an unregistered recipe type");
            }
            String typeId = typeKey.toString();
            IndustrialForegoingRecipeListOrderContract.CanonicalRecipeOrder<T> canonical =
                    IndustrialForegoingRecipeListOrderContract.canonicalize(
                            source,
                            recipe -> recipe.getId() == null
                                    ? null : recipe.getId().toString());
            auditInvocation(typeId, canonical);
            return canonical.values();
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.error(
                    "[reiexport] MM2 Industrial Foregoing recipe-list order repair failed; "
                            + "no unsorted fallback list was returned",
                    failure);
            throw failure;
        }
    }

    private static synchronized void auditInvocation(
            String typeId,
            IndustrialForegoingRecipeListOrderContract.CanonicalRecipeOrder<?> canonical
    ) {
        int invocation = VERIFIED_INVOCATIONS.size();
        int cycleIndex = invocation
                % IndustrialForegoingRecipeListOrderContract.EXPECTED_GET_RECIPES_CALLS;
        IndustrialForegoingRecipeListOrderContract.RecipeListExpectation expected =
                IndustrialForegoingRecipeListOrderContract.EXPECTED_RECIPE_LISTS.get(cycleIndex);
        if (!expected.recipeTypeId().equals(typeId)
                || expected.recipeCount() != canonical.values().size()) {
            throw new IllegalStateException(
                    "Industrial Foregoing recipe-list invocation drift: invocation=" + invocation
                            + ", cycleIndex=" + cycleIndex
                            + ", expectedType=" + expected.recipeTypeId()
                            + ", actualType=" + typeId
                            + ", expectedCount=" + expected.recipeCount()
                            + ", actualCount=" + canonical.values().size());
        }
        if (!expected.orderedIdSha256().equals(canonical.orderedIdSha256())) {
            throw new IllegalStateException(
                    "Industrial Foregoing ordered recipe domain drift: invocation="
                            + invocation + ", type=" + typeId
                            + ", expectedSha256=" + expected.orderedIdSha256()
                            + ", actualSha256=" + canonical.orderedIdSha256()
                            + ", orderedIds=" + canonical.orderedIds());
        }
        VERIFIED_INVOCATIONS.add(new RecipeListAudit(
                typeId,
                canonical.values().size(),
                canonical.orderedIdSha256(),
                canonical.inputAlreadyCanonical()));
        LOGGER.info(
                "[reiexport] Canonicalized exact MM2 Industrial Foregoing recipe list: "
                        + "invocation={}, cycleIndex={}, type={}, recipes={}, orderedIdSha256={}, "
                        + "inputAlreadyCanonical={}",
                invocation + 1,
                cycleIndex,
                typeId,
                canonical.values().size(),
                canonical.orderedIdSha256(),
                canonical.inputAlreadyCanonical());
    }

    /** Accepts complete repeated REI reload cycles, never partial or differently ordered cycles. */
    public static synchronized void requireObservedBeforePublication() {
        int invocations = VERIFIED_INVOCATIONS.size();
        int sites = IndustrialForegoingRecipeListOrderContract.EXPECTED_GET_RECIPES_CALLS;
        if (invocations == 0 || invocations % sites != 0) {
            String message = "MM2 Industrial Foregoing recipe-list seam did not complete exact "
                    + "six-invocation cycles before publication: invocations=" + invocations
                    + ", requiredCycleSize=" + sites;
            LOGGER.error("[reiexport] {}", message);
            throw new IllegalStateException(message);
        }
        LOGGER.info(
                "[reiexport] Verified exact MM2 Industrial Foregoing recipe-list ordering "
                        + "before publication: successfulInvocations={}, completeCycles={}",
                invocations,
                invocations / sites);
    }

    private record RecipeListAudit(
            String recipeTypeId,
            int recipeCount,
            String orderedIdSha256,
            boolean inputAlreadyCanonical
    ) {
    }
}
