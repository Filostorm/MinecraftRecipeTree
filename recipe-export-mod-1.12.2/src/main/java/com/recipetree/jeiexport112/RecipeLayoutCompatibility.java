package com.recipetree.jeiexport112;

import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exact, layout-only compatibility decorator for three upstream HEI category bugs. Recipe
 * semantics are collected separately before this class is used; this view changes only the
 * ingredient shape supplied to the original category's renderer.
 */
final class RecipeLayoutCompatibility {
    private RecipeLayoutCompatibility() {
    }

    static Prepared prepare(IRecipeCategory category, IRecipeWrapper wrapper,
                            int sourceIndex, int exportedIndex) throws DriftException {
        if (category == null || wrapper == null) {
            throw new IllegalArgumentException("Recipe layout category and wrapper must not be null");
        }
        String categoryClass = category.getClass().getName();
        String wrapperClass = wrapper.getClass().getName();
        final String uid;
        try {
            uid = category.getUid();
        } catch (RuntimeException error) {
            try {
                RecipeLayoutCompatibilityPolicy.classify(null, categoryClass, wrapperClass);
            } catch (IllegalStateException drift) {
                throw drift(uidContext(null, categoryClass, wrapperClass, sourceIndex, exportedIndex),
                        drift);
            }
            throw error;
        }

        final RecipeLayoutCompatibilityPolicy.Kind kind;
        try {
            kind = RecipeLayoutCompatibilityPolicy.classify(uid, categoryClass, wrapperClass);
        } catch (IllegalStateException error) {
            throw drift(uidContext(uid, categoryClass, wrapperClass, sourceIndex, exportedIndex), error);
        }
        if (kind == RecipeLayoutCompatibilityPolicy.Kind.NONE) {
            return new Prepared(category, null, kind, uid, categoryClass, wrapperClass,
                    sourceIndex, exportedIndex);
        }

        DecoratingCategory decorated = new DecoratingCategory(category, kind, uid,
                categoryClass, wrapperClass, sourceIndex, exportedIndex);
        return new Prepared(decorated, decorated, kind, uid, categoryClass, wrapperClass,
                sourceIndex, exportedIndex);
    }

    private static String uidContext(String uid, String categoryClass, String wrapperClass,
                                     int sourceIndex, int exportedIndex) {
        return "uid=" + value(uid) + ", categoryClass=" + value(categoryClass) +
                ", wrapperClass=" + value(wrapperClass) + ", sourceIndex=" + sourceIndex +
                ", exportedIndex=" + exportedIndex;
    }

    private static DriftException drift(String context, IllegalStateException cause) {
        return new DriftException(cause.getMessage() + "; " + context, cause);
    }

    private static String value(String value) {
        return value == null ? "<null>" : value;
    }

    static final class Prepared {
        private final IRecipeCategory category;
        private final DecoratingCategory decorator;
        private final RecipeLayoutCompatibilityPolicy.Kind expectedKind;
        private final String uid;
        private final String categoryClass;
        private final String wrapperClass;
        private final int sourceIndex;
        private final int exportedIndex;
        private boolean recorded;

        Prepared(IRecipeCategory category, DecoratingCategory decorator,
                 RecipeLayoutCompatibilityPolicy.Kind expectedKind, String uid,
                 String categoryClass, String wrapperClass, int sourceIndex, int exportedIndex) {
            this.category = category;
            this.decorator = decorator;
            this.expectedKind = expectedKind;
            this.uid = uid;
            this.categoryClass = categoryClass;
            this.wrapperClass = wrapperClass;
            this.sourceIndex = sourceIndex;
            this.exportedIndex = exportedIndex;
        }

        IRecipeCategory category() {
            return category;
        }

        void rethrowDriftIfPresent(RuntimeException outer) throws DriftException {
            if (decorator == null || decorator.drift == null) {
                return;
            }
            if (outer != null && outer != decorator.drift.getCause()) {
                decorator.drift.addSuppressed(outer);
            }
            throw decorator.drift;
        }

        void recordApplied(ExportContext context) throws DriftException {
            rethrowDriftIfPresent(null);
            if (decorator == null) {
                return;
            }
            RecipeLayoutCompatibilityPolicy.Kind applied = decorator.appliedKind;
            if (applied == RecipeLayoutCompatibilityPolicy.Kind.NONE) {
                return;
            }
            if (applied != expectedKind) {
                throw new DriftException("RECIPE_LAYOUT_COMPAT_DRIFT: decorator applied " + applied +
                        " while identity selected " + expectedKind + "; " +
                        uidContext(uid, categoryClass, wrapperClass, sourceIndex, exportedIndex));
            }
            if (recorded) {
                throw new DriftException("RECIPE_LAYOUT_COMPAT_DRIFT: layout intervention was " +
                        "recorded twice; " + uidContext(uid, categoryClass, wrapperClass,
                        sourceIndex, exportedIndex));
            }
            context.recordRecipeLayoutCompatibility(applied, uid, categoryClass, wrapperClass,
                    sourceIndex, exportedIndex);
            recorded = true;
        }
    }

    static final class DriftException extends IOException {
        DriftException(String message) {
            super(message);
        }

        DriftException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class DecoratingCategory implements IRecipeCategory<IRecipeWrapper> {
        private final IRecipeCategory delegate;
        private final RecipeLayoutCompatibilityPolicy.Kind kind;
        private final String uid;
        private final String categoryClass;
        private final String wrapperClass;
        private final int sourceIndex;
        private final int exportedIndex;
        private DriftException drift;
        private RecipeLayoutCompatibilityPolicy.Kind appliedKind =
                RecipeLayoutCompatibilityPolicy.Kind.NONE;

        DecoratingCategory(IRecipeCategory delegate, RecipeLayoutCompatibilityPolicy.Kind kind,
                           String uid, String categoryClass, String wrapperClass,
                           int sourceIndex, int exportedIndex) {
            this.delegate = delegate;
            this.kind = kind;
            this.uid = uid;
            this.categoryClass = categoryClass;
            this.wrapperClass = wrapperClass;
            this.sourceIndex = sourceIndex;
            this.exportedIndex = exportedIndex;
        }

        @Override
        public String getUid() {
            return delegate.getUid();
        }

        @Override
        public String getTitle() {
            return delegate.getTitle();
        }

        @Override
        public String getModName() {
            return delegate.getModName();
        }

        @Override
        public IDrawable getBackground() {
            return delegate.getBackground();
        }

        @Override
        public IDrawable getIcon() {
            return delegate.getIcon();
        }

        @Override
        public void drawExtras(Minecraft minecraft) {
            delegate.drawExtras(minecraft);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void setRecipe(IRecipeLayout recipeLayout, IRecipeWrapper wrapper,
                              IIngredients ingredients) {
            final Patch patch;
            try {
                patch = patchFor(ingredients);
            } catch (IllegalStateException error) {
                drift = RecipeLayoutCompatibility.drift(
                        uidContext(uid, categoryClass, wrapperClass, sourceIndex, exportedIndex), error);
                throw new LayoutDriftRuntimeException(drift);
            } catch (RuntimeException error) {
                IllegalStateException wrapped = new IllegalStateException(
                        "RECIPE_LAYOUT_COMPAT_DRIFT: ingredient-shape inspection failed", error);
                drift = RecipeLayoutCompatibility.drift(
                        uidContext(uid, categoryClass, wrapperClass, sourceIndex, exportedIndex), wrapped);
                throw new LayoutDriftRuntimeException(drift);
            }

            try {
                delegate.setRecipe(recipeLayout, wrapper, patch.ingredients);
            } catch (RuntimeException error) {
                if (patch.appliedKind != RecipeLayoutCompatibilityPolicy.Kind.NONE) {
                    drift = new DriftException(
                            "RECIPE_LAYOUT_COMPAT_DRIFT: validated layout-only intervention " +
                                    patch.appliedKind.diagnosticName +
                                    " did not restore the original category renderer; " +
                                    uidContext(uid, categoryClass, wrapperClass,
                                            sourceIndex, exportedIndex),
                            error);
                    throw new LayoutDriftRuntimeException(drift);
                }
                throw error;
            }
            appliedKind = patch.appliedKind;
        }

        @Override
        public List<String> getTooltipStrings(int mouseX, int mouseY) {
            return delegate.getTooltipStrings(mouseX, mouseY);
        }

        private Patch patchFor(IIngredients ingredients) {
            if (ingredients == null) {
                throw new IllegalStateException(
                        "RECIPE_LAYOUT_COMPAT_DRIFT: HEI supplied null IIngredients");
            }
            if (kind == RecipeLayoutCompatibilityPolicy.Kind.
                    ADVANCED_ROCKETRY_EMPTY_WILDCARD_INPUT) {
                return advancedRocketryPatch(ingredients);
            }
            if (kind == RecipeLayoutCompatibilityPolicy.Kind.BUILDCRAFT_HEATABLE_ABSENT_OUTPUT ||
                    kind == RecipeLayoutCompatibilityPolicy.Kind.BUILDCRAFT_COOLABLE_ABSENT_OUTPUT) {
                return buildCraftPatch(ingredients);
            }
            throw new IllegalStateException(
                    "RECIPE_LAYOUT_COMPAT_DRIFT: decorator received unsupported mode " + kind);
        }

        private Patch advancedRocketryPatch(IIngredients ingredients) {
            List<List<ItemStack>> itemInputs = ingredients.getInputs(ItemStack.class);
            List<List<ItemStack>> itemOutputs = ingredients.getOutputs(ItemStack.class);
            List<List<FluidStack>> fluidInputs = ingredients.getInputs(FluidStack.class);
            List<List<FluidStack>> fluidOutputs = ingredients.getOutputs(FluidStack.class);
            boolean required = RecipeLayoutCompatibilityPolicy.requiresAdvancedRocketryPatch(
                    describeItems(itemInputs), describeItems(itemOutputs), slotCount(fluidInputs),
                    slotCount(fluidOutputs));
            if (!required) {
                return new Patch(ingredients, RecipeLayoutCompatibilityPolicy.Kind.NONE);
            }
            List<List<ItemStack>> normalized =
                    RecipeLayoutCompatibilityPolicy.replaceFirstEmptySlot(
                            itemInputs, ItemStack.EMPTY);
            return new Patch(new LayoutIngredientsView(ingredients, normalized, null), kind);
        }

        private Patch buildCraftPatch(IIngredients ingredients) {
            List<List<ItemStack>> itemInputs = ingredients.getInputs(ItemStack.class);
            List<List<ItemStack>> itemOutputs = ingredients.getOutputs(ItemStack.class);
            List<List<FluidStack>> fluidInputs = ingredients.getInputs(FluidStack.class);
            List<List<FluidStack>> fluidOutputs = ingredients.getOutputs(FluidStack.class);
            boolean required = RecipeLayoutCompatibilityPolicy.requiresBuildCraftPatch(
                    kind, slotCount(itemInputs), slotCount(itemOutputs), describeFluids(fluidInputs),
                    describeFluids(fluidOutputs));
            if (!required) {
                return new Patch(ingredients, RecipeLayoutCompatibilityPolicy.Kind.NONE);
            }
            List<List<FluidStack>> normalized =
                    RecipeLayoutCompatibilityPolicy.singletonEmptySlot();
            return new Patch(new LayoutIngredientsView(ingredients, null, normalized), kind);
        }
    }

    private static final class Patch {
        final IIngredients ingredients;
        final RecipeLayoutCompatibilityPolicy.Kind appliedKind;

        Patch(IIngredients ingredients, RecipeLayoutCompatibilityPolicy.Kind appliedKind) {
            this.ingredients = ingredients;
            this.appliedKind = appliedKind;
        }
    }

    private static final class LayoutDriftRuntimeException extends IllegalStateException {
        LayoutDriftRuntimeException(DriftException cause) {
            super(cause.getMessage(), cause);
        }
    }

    private static int slotCount(List<?> slots) {
        return slots == null ? -1 : slots.size();
    }

    private static List<List<RecipeLayoutCompatibilityPolicy.StackRef>> describeItems(
            List<List<ItemStack>> slots) {
        if (slots == null) {
            return null;
        }
        List<List<RecipeLayoutCompatibilityPolicy.StackRef>> result =
                new ArrayList<List<RecipeLayoutCompatibilityPolicy.StackRef>>(slots.size());
        for (List<ItemStack> slot : slots) {
            if (slot == null) {
                result.add(null);
                continue;
            }
            List<RecipeLayoutCompatibilityPolicy.StackRef> described =
                    new ArrayList<RecipeLayoutCompatibilityPolicy.StackRef>(slot.size());
            for (ItemStack stack : slot) {
                if (stack == null || stack.isEmpty() || stack.getItem() == null) {
                    described.add(null);
                    continue;
                }
                ResourceLocation registryName = stack.getItem().getRegistryName();
                described.add(new RecipeLayoutCompatibilityPolicy.StackRef(
                        registryName == null ? null : registryName.toString(), stack.getCount()));
            }
            result.add(described);
        }
        return result;
    }

    private static List<List<RecipeLayoutCompatibilityPolicy.FluidRef>> describeFluids(
            List<List<FluidStack>> slots) {
        if (slots == null) {
            return null;
        }
        List<List<RecipeLayoutCompatibilityPolicy.FluidRef>> result =
                new ArrayList<List<RecipeLayoutCompatibilityPolicy.FluidRef>>(slots.size());
        for (List<FluidStack> slot : slots) {
            if (slot == null) {
                result.add(null);
                continue;
            }
            List<RecipeLayoutCompatibilityPolicy.FluidRef> described =
                    new ArrayList<RecipeLayoutCompatibilityPolicy.FluidRef>(slot.size());
            for (FluidStack stack : slot) {
                if (stack == null || stack.getFluid() == null) {
                    described.add(null);
                    continue;
                }
                described.add(new RecipeLayoutCompatibilityPolicy.FluidRef(
                        stack.getFluid().getName(), stack.amount));
            }
            result.add(described);
        }
        return result;
    }

    /** Delegates all mutation and unrelated reads; only the one renderer-facing shape is replaced. */
    private static final class LayoutIngredientsView implements IIngredients {
        private final IIngredients delegate;
        private final List<List<ItemStack>> normalizedItemInputs;
        private final List<List<FluidStack>> normalizedFluidOutputs;

        LayoutIngredientsView(IIngredients delegate, List<List<ItemStack>> normalizedItemInputs,
                              List<List<FluidStack>> normalizedFluidOutputs) {
            this.delegate = delegate;
            this.normalizedItemInputs = normalizedItemInputs;
            this.normalizedFluidOutputs = normalizedFluidOutputs;
        }

        @Override
        public <T> void setInput(IIngredientType<T> type, T input) {
            delegate.setInput(type, input);
        }

        @Override
        public <T> void setInputs(IIngredientType<T> type, List<T> inputs) {
            delegate.setInputs(type, inputs);
        }

        @Override
        public <T> void setInputLists(IIngredientType<T> type, List<List<T>> inputs) {
            delegate.setInputLists(type, inputs);
        }

        @Override
        public <T> void setOutput(IIngredientType<T> type, T output) {
            delegate.setOutput(type, output);
        }

        @Override
        public <T> void setOutputs(IIngredientType<T> type, List<T> outputs) {
            delegate.setOutputs(type, outputs);
        }

        @Override
        public <T> void setOutputLists(IIngredientType<T> type, List<List<T>> outputs) {
            delegate.setOutputLists(type, outputs);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<List<T>> getInputs(IIngredientType<T> type) {
            if (normalizedItemInputs != null && type != null &&
                    ItemStack.class.equals(type.getIngredientClass())) {
                return (List<List<T>>) (List<?>) normalizedItemInputs;
            }
            return delegate.getInputs(type);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<List<T>> getOutputs(IIngredientType<T> type) {
            if (normalizedFluidOutputs != null && type != null &&
                    FluidStack.class.equals(type.getIngredientClass())) {
                return (List<List<T>>) (List<?>) normalizedFluidOutputs;
            }
            return delegate.getOutputs(type);
        }

        @Override
        public <T> void setInput(Class<? extends T> ingredientClass, T input) {
            delegate.setInput(ingredientClass, input);
        }

        @Override
        public <T> void setInputs(Class<? extends T> ingredientClass, List<T> inputs) {
            delegate.setInputs(ingredientClass, inputs);
        }

        @Override
        public <T> void setInputLists(Class<? extends T> ingredientClass, List<List<T>> inputs) {
            delegate.setInputLists(ingredientClass, inputs);
        }

        @Override
        public <T> void setOutput(Class<? extends T> ingredientClass, T output) {
            delegate.setOutput(ingredientClass, output);
        }

        @Override
        public <T> void setOutputs(Class<? extends T> ingredientClass, List<T> outputs) {
            delegate.setOutputs(ingredientClass, outputs);
        }

        @Override
        public <T> void setOutputLists(Class<? extends T> ingredientClass, List<List<T>> outputs) {
            delegate.setOutputLists(ingredientClass, outputs);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<List<T>> getInputs(Class<? extends T> ingredientClass) {
            if (normalizedItemInputs != null && ItemStack.class.equals(ingredientClass)) {
                return (List<List<T>>) (List<?>) normalizedItemInputs;
            }
            return delegate.getInputs(ingredientClass);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<List<T>> getOutputs(Class<? extends T> ingredientClass) {
            if (normalizedFluidOutputs != null && FluidStack.class.equals(ingredientClass)) {
                return (List<List<T>>) (List<?>) normalizedFluidOutputs;
            }
            return delegate.getOutputs(ingredientClass);
        }
    }
}
