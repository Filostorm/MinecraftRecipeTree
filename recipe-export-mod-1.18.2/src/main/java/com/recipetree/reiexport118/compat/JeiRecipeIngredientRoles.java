package com.recipetree.reiexport118.compat;

import blusunrize.immersiveengineering.api.crafting.MetalPressRecipe;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.jeicompat.wrap.JEIRecipeLayoutBuilder;
import me.shedaniel.rei.jeicompat.wrap.JEIRecipeSlot;
import me.shedaniel.rei.jeicompat.wrap.JEIWrappedDisplay;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Restores JEI's INPUT/CATALYST distinction after REI's JEI adapter flattens
 * both roles into {@link Display#getInputEntries()}.
 */
public final class JeiRecipeIngredientRoles {
    private JeiRecipeIngredientRoles() {
    }

    public record Resolution(
            List<EntryIngredient> materialInputs,
            List<EntryIngredient> catalysts,
            String auditMessage
    ) {
        public Resolution {
            materialInputs = List.copyOf(materialInputs);
            catalysts = List.copyOf(catalysts);
            if (auditMessage == null || auditMessage.isBlank()) {
                throw new IllegalArgumentException("JEI role resolution requires an audit message");
            }
        }
    }

    record RoleSlot<T>(RecipeIngredientRole role, T ingredient) {
        RoleSlot {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(ingredient, "ingredient");
        }
    }

    record Partition<T>(List<T> materialInputs, List<T> catalysts, List<T> flattenedInputs) {
        Partition {
            materialInputs = List.copyOf(materialInputs);
            catalysts = List.copyOf(catalysts);
            flattenedInputs = List.copyOf(flattenedInputs);
        }
    }

    public static Optional<Resolution> resolve(Display display) {
        if (!(display instanceof JEIWrappedDisplay<?> wrapped)) {
            return Optional.empty();
        }

        JEIRecipeLayoutBuilder builder = new JEIRecipeLayoutBuilder(null);
        replayRecipe(wrapped, builder);
        String categoryId = display.getCategoryIdentifier().getIdentifier().toString();

        if (!builder.isDirty()) {
            return Optional.of(new Resolution(
                    display.getInputEntries(),
                    List.of(),
                    "JEI_ROLE_METADATA_UNAVAILABLE category=" + categoryId
                            + " adapter=legacy-IIngredients materialInputs=" + display.getInputEntries().size()
            ));
        }

        List<RoleSlot<EntryIngredient>> roleSlots = new ArrayList<>();
        for (JEIRecipeSlot slot : builder.slots) {
            roleSlots.add(new RoleSlot<>(
                    slot.role,
                    EntryIngredient.of(slot.slot.getEntries())
            ));
        }
        Partition<EntryIngredient> partition = partition(roleSlots);

        List<EntryIngredient> materialInputs = new ArrayList<>(partition.materialInputs());
        List<EntryIngredient> catalysts = new ArrayList<>(partition.catalysts());
        int semanticCatalysts = classifyRecipeSemanticCatalysts(
                wrapped.getBackingRecipe(),
                materialInputs,
                catalysts,
                categoryId
        );
        if (catalysts.isEmpty()) {
            return Optional.of(new Resolution(
                    display.getInputEntries(),
                    List.of(),
                    "JEI_RECIPE_ROLES_UNCHANGED_NO_CATALYSTS category=" + categoryId
                            + " materialInputs=" + display.getInputEntries().size()
                            + " replayedMaterialInputs=" + materialInputs.size()
            ));
        }

        requireSameFlattenedInputs(
                display.getInputEntries(),
                partition.flattenedInputs(),
                categoryId
        );

        return Optional.of(new Resolution(
                materialInputs,
                catalysts,
                "JEI_RECIPE_ROLES category=" + categoryId
                        + " materialInputs=" + materialInputs.size()
                        + " catalysts=" + catalysts.size()
                        + " recipeSemanticCatalysts=" + semanticCatalysts
        ));
    }

    private static int classifyRecipeSemanticCatalysts(
            Object backingRecipe,
            List<EntryIngredient> materialInputs,
            List<EntryIngredient> catalysts,
            String categoryId
    ) {
        if (!(backingRecipe instanceof MetalPressRecipe metalPressRecipe)) {
            return 0;
        }

        for (int inputIndex = 0; inputIndex < materialInputs.size(); inputIndex++) {
            EntryIngredient input = materialInputs.get(inputIndex);
            boolean isMold = input.stream().anyMatch(stack ->
                    stack.getValue() instanceof ItemStack itemStack
                            && itemStack.getItem() == metalPressRecipe.mold
            );
            if (isMold) {
                catalysts.add(materialInputs.remove(inputIndex));
                return 1;
            }
        }

        throw new IllegalStateException(
                "IE_METAL_PRESS_MOLD_SLOT_MISSING category=" + categoryId
                        + " recipe=" + metalPressRecipe.getId()
                        + " materialInputs=" + materialInputs.size()
        );
    }

    static <T> Partition<T> partition(List<RoleSlot<T>> slots) {
        List<T> materialInputs = new ArrayList<>();
        List<T> catalysts = new ArrayList<>();
        List<T> flattenedInputs = new ArrayList<>();
        for (RoleSlot<T> slot : slots) {
            if (slot.role() == RecipeIngredientRole.INPUT) {
                materialInputs.add(slot.ingredient());
                flattenedInputs.add(slot.ingredient());
            } else if (slot.role() == RecipeIngredientRole.CATALYST) {
                catalysts.add(slot.ingredient());
                flattenedInputs.add(slot.ingredient());
            }
        }
        return new Partition<>(materialInputs, catalysts, flattenedInputs);
    }

    /**
     * Verifies role reconstruction as a multiset rather than a sequence.
     *
     * JEI categories may rebuild shapeless recipe slots in a different order
     * from the order cached by REI. Slot order is not semantic for those
     * recipes, but membership and duplicate cardinality are, so both remain
     * fail-closed here.
     */
    static <T> void requireSameFlattenedInputs(
            List<T> reiInputs,
            List<T> reconstructedInputs,
            String categoryId
    ) {
        List<T> unmatched = new ArrayList<>(reconstructedInputs);
        for (T reiInput : reiInputs) {
            int match = unmatched.indexOf(reiInput);
            if (match < 0) {
                throw reconstructionMismatch(reiInputs, reconstructedInputs, categoryId);
            }
            unmatched.remove(match);
        }
        if (!unmatched.isEmpty()) {
            throw reconstructionMismatch(reiInputs, reconstructedInputs, categoryId);
        }
    }

    private static <T> IllegalStateException reconstructionMismatch(
            List<T> reiInputs,
            List<T> reconstructedInputs,
            String categoryId
    ) {
        return new IllegalStateException(
                "JEI_ROLE_RECONSTRUCTION_MISMATCH category=" + categoryId
                        + " reiInputs=" + reiInputs.size()
                        + " reconstructedInputs=" + reconstructedInputs.size()
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void replayRecipe(JEIWrappedDisplay<?> wrapped, JEIRecipeLayoutBuilder builder) {
        IRecipeCategory category = wrapped.getBackingCategory().getBackingCategory();
        category.setRecipe(
                builder,
                wrapped.getBackingRecipe(),
                JEIWrappedDisplay.getFoci()
        );
    }
}
