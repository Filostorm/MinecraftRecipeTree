package com.recipetree.reiexport118.compat;

import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Restores Compact Crafting structure multiplicities that its legacy JEI ingredient list omits.
 *
 * <p>The JEI category publishes one input entry per distinct structure component, then applies
 * {@code MiniaturizationRecipe.getComponentTotals()} only while drawing its material slots. REI's
 * JEI adapter consequently exposes unit-sized entries even when the structure requires many
 * copies. This compatibility boundary reads the backing recipe and joins those component totals
 * back onto the corresponding REI input slots by item identity.</p>
 */
public final class CompactCraftingInputAmounts {
    public static final String CATEGORY_ID = "compactcrafting:miniaturization";
    private static final String WRAPPED_RECIPE_CLASS =
            "dev.compactmods.crafting.recipes.MiniaturizationRecipe";

    public record Resolution(Map<Integer, Long> amountByIngredientIndex, String auditWarning) {
        public Resolution {
            amountByIngredientIndex = Map.copyOf(amountByIngredientIndex);
            if (amountByIngredientIndex.isEmpty()) {
                throw new IllegalArgumentException(
                        "Compact Crafting quantity resolution must override at least one input");
            }
            if (auditWarning == null || auditWarning.isBlank()) {
                throw new IllegalArgumentException(
                        "Compact Crafting quantity resolution requires an audit warning");
            }
        }
    }

    record ComponentAmount<T>(String componentKey, T identity, long amount) {
        ComponentAmount {
            if (componentKey == null || componentKey.isBlank()) {
                throw new IllegalArgumentException("Component key must be nonblank");
            }
            if (identity == null) {
                throw new IllegalArgumentException("Component identity must be nonnull");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("Component amount must be positive");
            }
        }
    }

    private CompactCraftingInputAmounts() {
    }

    public static Optional<Resolution> resolve(Display display, List<EntryIngredient> ingredients) {
        String categoryId = display.getCategoryIdentifier().getIdentifier().toString();
        if (!CATEGORY_ID.equals(categoryId)) {
            return Optional.empty();
        }

        Object recipe = invoke(display, "getBackingRecipe");
        if (!WRAPPED_RECIPE_CLASS.equals(recipe.getClass().getName())) {
            throw new IllegalStateException(
                    "Compact Crafting display exposed unexpected backing recipe class "
                            + recipe.getClass().getName());
        }

        Map<?, ?> componentTotals = requireMap(invoke(recipe, "getComponentTotals"),
                "MiniaturizationRecipe.getComponentTotals()");
        Object components = invoke(recipe, "getComponents");
        Map<?, ?> blockComponents = requireMap(invoke(components, "getBlockComponents"),
                "IRecipeComponents.getBlockComponents()");

        List<ComponentAmount<Item>> requiredComponents = new ArrayList<>();
        for (Map.Entry<?, ?> entry : blockComponents.entrySet()) {
            String componentKey = String.valueOf(entry.getKey());
            Object rawAmount = componentTotals.get(componentKey);
            if (!(rawAmount instanceof Number number) || number.longValue() <= 0) {
                continue;
            }

            Object rawBlock = invoke(entry.getValue(), "getBlock");
            if (!(rawBlock instanceof Block block)) {
                throw new IllegalStateException(
                        "Compact Crafting component " + componentKey
                                + " did not expose a Minecraft Block");
            }
            Item item = block.asItem();
            if (item != Items.AIR) {
                requiredComponents.add(
                        new ComponentAmount<>(componentKey, item, number.longValue()));
            }
        }

        List<List<Item>> ingredientItems = new ArrayList<>(ingredients.size());
        for (EntryIngredient ingredient : ingredients) {
            List<Item> alternatives = new ArrayList<>();
            for (EntryStack<?> stack : ingredient) {
                if (stack != null && !stack.isEmpty() && stack.getValue() instanceof ItemStack itemStack) {
                    alternatives.add(itemStack.getItem());
                }
            }
            ingredientItems.add(alternatives);
        }

        Map<Integer, Long> amountByIndex = matchComponents(requiredComponents, ingredientItems);
        Object recipeId = invoke(recipe, "getRecipeIdentifier");
        return Optional.of(new Resolution(
                amountByIndex,
                "COMPACT_CRAFTING_STRUCTURE_COUNTS recipe=" + recipeId
                        + " components=" + requiredComponents.size()
                        + " overrides=" + amountByIndex.size()
                        + " source=MiniaturizationRecipe.getComponentTotals()"
        ));
    }

    static <T> Map<Integer, Long> matchComponents(
            List<ComponentAmount<T>> components,
            List<? extends Collection<T>> ingredientAlternatives
    ) {
        Map<Integer, Long> amountByIndex = new LinkedHashMap<>();
        boolean[] claimedIngredients = new boolean[ingredientAlternatives.size()];

        for (ComponentAmount<T> component : components) {
            int matchedIndex = -1;
            for (int ingredientIndex = 0;
                 ingredientIndex < ingredientAlternatives.size();
                 ingredientIndex++) {
                if (!claimedIngredients[ingredientIndex]
                        && ingredientAlternatives.get(ingredientIndex)
                        .contains(component.identity())) {
                    matchedIndex = ingredientIndex;
                    break;
                }
            }
            if (matchedIndex < 0) {
                throw new IllegalStateException(
                        "Compact Crafting component " + component.componentKey()
                                + " could not be matched to a distinct REI input slot");
            }
            claimedIngredients[matchedIndex] = true;
            amountByIndex.put(matchedIndex, component.amount());
        }

        if (amountByIndex.isEmpty()) {
            throw new IllegalStateException(
                    "Compact Crafting recipe exposed no countable structure components");
        }
        return amountByIndex;
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            throw new IllegalStateException(
                    "Cannot invoke Compact Crafting compatibility method "
                            + methodName + " on null");
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Compact Crafting compatibility could not invoke "
                            + target.getClass().getName() + "." + methodName + "()",
                    exception);
        }
    }

    private static Map<?, ?> requireMap(Object value, String source) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalStateException(source + " did not return a Map");
    }
}
