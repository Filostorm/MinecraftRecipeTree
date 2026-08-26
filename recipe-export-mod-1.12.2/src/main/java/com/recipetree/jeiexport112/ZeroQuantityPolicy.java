package com.recipetree.jeiexport112;

import java.math.BigDecimal;

/**
 * Exact adapters for legacy HEI integrations that use a numeric zero for several incompatible
 * meanings. A zero is never classified from its value alone: new type/category/role combinations
 * remain unsupported and therefore publication-blocking.
 */
final class ZeroQuantityPolicy {
    private static final String FLUID_STACK = "net.minecraftforge.fluids.FluidStack";
    private static final String DEMON_WILL =
            "kport.modularmagic.common.integration.jei.ingredient.DemonWill";
    private static final String ENDER_IO_ENERGY =
            "crazypants.enderio.base.integration.jei.energy.EnergyIngredient";

    enum Kind {
        NON_CONSUMED,
        DYNAMIC_FLOW,
        ABSENT_OUTPUT,
        ABSENT_ALTERNATIVE,
        INVALID_RECIPE,
        UNSUPPORTED
    }

    static final class Decision {
        final Kind kind;
        final BigDecimal publishedAmount;
        final String diagnosticCode;
        final String explanation;

        Decision(Kind kind, BigDecimal publishedAmount, String diagnosticCode, String explanation) {
            this.kind = kind;
            this.publishedAmount = publishedAmount;
            this.diagnosticCode = diagnosticCode;
            this.explanation = explanation;
        }
    }

    private ZeroQuantityPolicy() {
    }

    static Decision classify(String categoryUid, String role, String ingredientClassName,
                             boolean hasMatchingPositiveAlternative) {
        if ("input".equals(role) && FLUID_STACK.equals(ingredientClassName)) {
            if ("binnie.genetics.incubator".equals(categoryUid)) {
                return nonConsumed("ZERO_PREREQUISITE", 1,
                        "matching culture fluid must be present but is not drained");
            }
            if ("thermalexpansion.extruder".equals(categoryUid)
                    || "thermalexpansion.extruder_sedimentary".equals(categoryUid)) {
                return nonConsumed("ZERO_PREREQUISITE", 1000,
                        "the hot/cold reservoir requires at least 1000 mB but this recipe drains none");
            }
            if ("EIOTank".equals(categoryUid)) {
                return nonConsumed("ZERO_PREREQUISITE", 20,
                        "XP Juice must be present to derive one XP while the one-durability repair drains 0 mB");
            }
            if ("hatchery.fertilizermixer.recipe".equals(categoryUid)
                    || "hatchery.generator.recipe".equals(categoryUid)) {
                return dynamicFlow(
                        "Hatchery's JEI wrapper hides a positive runtime fluid transfer behind amount 0");
            }
        }

        if ("input".equals(role) && DEMON_WILL.equals(ingredientClassName)
                && "modularmachinery.recipes.berserker_forge".equals(categoryUid)) {
            return nonConsumed("ZERO_THRESHOLD", 1,
                    "at least 1 matching Demon Will is required while the recipe consumes none");
        }

        if ("output".equals(role) && FLUID_STACK.equals(ingredientClassName)) {
            if ("hatchery.fertilizermixer.recipe".equals(categoryUid)) {
                return dynamicFlow(
                        "Hatchery's JEI wrapper hides a positive runtime fluid transfer behind amount 0");
            }
            if ("thermalexpansion.centrifuge_mobs".equals(categoryUid)) {
                return new Decision(Kind.ABSENT_OUTPUT, null, "ZERO_ABSENT_OUTPUT",
                        "the mob recipe yields zero XP and therefore produces no XP fluid");
            }
            if ("nuclearcraft_centrifuge".equals(categoryUid)) {
                if (!hasMatchingPositiveAlternative) {
                    return new Decision(Kind.UNSUPPORTED, null, "ZERO_UNCLASSIFIED",
                            "NuclearCraft zero-volume chance output did not have a positive " +
                                    "same-fluid, same-NBT sibling alternative in its HEI slot");
                }
                return new Decision(Kind.ABSENT_ALTERNATIVE, null,
                        "ZERO_ABSENT_ALTERNATIVE",
                        "NuclearCraft ChanceFluidIngredient encodes the no-result branch of a " +
                                "probabilistic output as a zero-volume FluidStack alternative; " +
                                "only that absent alternative is omitted and positive alternatives " +
                                "in the same HEI output slot remain published");
            }
            if ("forestry.bottler".equals(categoryUid)) {
                return new Decision(Kind.INVALID_RECIPE, null, "ZERO_INVALID_RECIPE",
                        "Forestry generated an item self-loop from a non-null zero-volume drain");
            }
        }

        if ("output".equals(role) && ENDER_IO_ENERGY.equals(ingredientClassName)
                && "StirlingGenerator".equals(categoryUid)) {
            return new Decision(Kind.ABSENT_OUTPUT, null, "ZERO_ABSENT_OUTPUT",
                    "Ender IO reports a zero generator-tier energy result when that fuel has no " +
                            "output for the tier; positive tier outputs remain published");
        }

        if ("input".equals(role) && ENDER_IO_ENERGY.equals(ingredientClassName)
                && "EIOWC".equals(categoryUid)) {
            return new Decision(Kind.INVALID_RECIPE, null, "ZERO_INVALID_RECIPE",
                    "Ender IO generated an item self-loop after receiveEnergy returned zero");
        }

        return new Decision(Kind.UNSUPPORTED, null, "ZERO_UNCLASSIFIED",
                "no exact semantic adapter exists for this zero-valued ingredient context");
    }

    private static Decision nonConsumed(String code, int minimum, String explanation) {
        return new Decision(Kind.NON_CONSUMED, BigDecimal.valueOf(minimum), code, explanation);
    }

    private static Decision dynamicFlow(String explanation) {
        return new Decision(Kind.DYNAMIC_FLOW, BigDecimal.ZERO, "ZERO_UNKNOWN_FLOW", explanation);
    }
}
