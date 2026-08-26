package com.recipetree.jeiexport.rei;

import com.recipetree.jeiexport.JeiExportMod;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.display.DisplayCategoryView;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.InputIngredient;
import me.shedaniel.rei.jeicompat.JEIPluginDetector;
import me.shedaniel.rei.jeicompat.wrap.JEIDisplaySetup;
import me.shedaniel.rei.jeicompat.wrap.JEIRecipeSlot;
import me.shedaniel.rei.jeicompat.wrap.JEIWrappedCategory;
import me.shedaniel.rei.jeicompat.wrap.JEIWrappedDisplay;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCraftingDisplay;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategoriesLookup;
import mezz.jei.api.recipe.IRecipeCatalystLookup;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IEditModeConfig;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.runtime.IJeiKeyMappings;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.api.runtime.IScreenHelper;
import mezz.jei.api.runtime.config.IJeiConfigManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Completes the recipe-layout portion of REI Plugin Compatibilities' JEI API.
 *
 * <p>REI already translates native and JEI-provided categories, lookups,
 * ingredients, fluids, and recipes. Its layout factory is a deliberate TODO,
 * so Recipe Tree builds the drawable directly from the translated REI display
 * and preserves the JEI slot view used by the planner and exporter.</p>
 */
public final class ReiRuntimeAdapter implements IJeiRuntime {
    private final IJeiRuntime delegate;
    private final IRecipeManager recipeManager;

    private ReiRuntimeAdapter(IJeiRuntime delegate) {
        this.delegate = delegate;
        this.recipeManager = new ReiRecipeManager(delegate.getRecipeManager());
    }

    public static IJeiRuntime wrap(IJeiRuntime runtime) {
        return runtime instanceof ReiRuntimeAdapter ? runtime : new ReiRuntimeAdapter(runtime);
    }

    @Override
    public IRecipeManager getRecipeManager() {
        return recipeManager;
    }

    @Override public IRecipesGui getRecipesGui() { return delegate.getRecipesGui(); }
    @Override public IIngredientFilter getIngredientFilter() { return delegate.getIngredientFilter(); }
    @Override public IIngredientListOverlay getIngredientListOverlay() { return delegate.getIngredientListOverlay(); }
    @Override public IBookmarkOverlay getBookmarkOverlay() { return delegate.getBookmarkOverlay(); }
    @Override public IJeiHelpers getJeiHelpers() { return delegate.getJeiHelpers(); }
    @Override public IIngredientManager getIngredientManager() { return delegate.getIngredientManager(); }
    @Override public IIngredientVisibility getIngredientVisibility() { return delegate.getIngredientVisibility(); }
    @Override public IJeiKeyMappings getKeyMappings() { return delegate.getKeyMappings(); }
    @Override public IScreenHelper getScreenHelper() { return delegate.getScreenHelper(); }
    @Override public IRecipeTransferManager getRecipeTransferManager() { return delegate.getRecipeTransferManager(); }
    @Override public IEditModeConfig getEditModeConfig() { return delegate.getEditModeConfig(); }
    @Override public IJeiConfigManager getConfigManager() { return delegate.getConfigManager(); }

    private static final class ReiRecipeManager implements IRecipeManager {
        private final IRecipeManager delegate;

        private ReiRecipeManager(IRecipeManager delegate) {
            this.delegate = delegate;
        }

        @Override public <R> IRecipeLookup<R> createRecipeLookup(RecipeType<R> type) {
            return new ReiRecipeLookup<>(delegate.createRecipeLookup(type), type);
        }

        @Override public IRecipeCategoriesLookup createRecipeCategoryLookup() {
            return new ReiCategoriesLookup(delegate.createRecipeCategoryLookup());
        }

        @Override public IRecipeCatalystLookup createRecipeCatalystLookup(RecipeType<?> type) {
            return delegate.createRecipeCatalystLookup(type);
        }

        @Override public <T> void hideRecipes(RecipeType<T> type, Collection<T> recipes) {
            delegate.hideRecipes(type, recipes);
        }

        @Override public <T> void unhideRecipes(RecipeType<T> type, Collection<T> recipes) {
            delegate.unhideRecipes(type, recipes);
        }

        @Override public <T> void addRecipes(RecipeType<T> type, List<T> recipes) {
            delegate.addRecipes(type, recipes);
        }

        @Override public void hideRecipeCategory(RecipeType<?> type) {
            delegate.hideRecipeCategory(type);
        }

        @Override public void unhideRecipeCategory(RecipeType<?> type) {
            delegate.unhideRecipeCategory(type);
        }

        @Override
        public <T> Optional<IRecipeLayoutDrawable<T>> createRecipeLayoutDrawable(
                IRecipeCategory<T> category,
                T recipe,
                IFocusGroup focuses) {
            try {
                Display display = JEIPluginDetector.asDisplay(recipe);
                return Optional.of(new ReiRecipeLayout<>(category, recipe, focuses, display));
            } catch (RuntimeException error) {
                JeiExportMod.LOGGER.debug("Unable to create native REI recipe layout", error);
                return Optional.empty();
            }
        }

        @Override
        public IRecipeSlotDrawable createRecipeSlotDrawable(
                RecipeIngredientRole role,
                List<Optional<ITypedIngredient<?>>> ingredients,
                Set<Integer> focusedIngredientIndexes,
                int x,
                int y,
                int cycleOffset) {
            return delegate.createRecipeSlotDrawable(
                    role, ingredients, focusedIngredientIndexes, x, y, cycleOffset);
        }

        @Override public Optional<RecipeType<?>> getRecipeType(ResourceLocation uid) {
            return delegate.getRecipeType(uid);
        }
    }

    /**
     * REIPC 12 starts a category-only lookup with only a category filter. REI
     * requires the category to also be selected, so an un-focused lookup can
     * incorrectly return no native displays. Focused lookups already work and
     * remain delegated; the registry fallback completes category enumeration
     * for the exporter and Recipe Tree's recipe picker.
     */
    private static final class ReiRecipeLookup<R> implements IRecipeLookup<R> {
        private final IRecipeLookup<R> delegate;
        private final RecipeType<R> type;
        private boolean focused;
        private boolean includeHidden;

        private ReiRecipeLookup(IRecipeLookup<R> delegate, RecipeType<R> type) {
            this.delegate = delegate;
            this.type = type;
        }

        @Override
        public IRecipeLookup<R> limitFocus(Collection<? extends IFocus<?>> focuses) {
            focused |= !focuses.isEmpty();
            delegate.limitFocus(focuses);
            return this;
        }

        @Override
        public IRecipeLookup<R> includeHidden() {
            includeHidden = true;
            delegate.includeHidden();
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Stream<R> get() {
            List<R> delegated = delegate.get().toList();
            if (focused || !delegated.isEmpty()) return delegated.stream();

            DisplayRegistry registry = DisplayRegistry.getInstance();
            return registry.get(JEIPluginDetector.categoryId(type)).stream()
                    .filter(display -> includeHidden || registry.isDisplayVisible(display))
                    .map(display -> {
                        Object value = JEIPluginDetector.jeiValue(display);
                        return (R) (value == null ? display : value);
                    });
        }
    }

    private static final class ReiCategoriesLookup implements IRecipeCategoriesLookup {
        private final IRecipeCategoriesLookup delegate;

        private ReiCategoriesLookup(IRecipeCategoriesLookup delegate) {
            this.delegate = delegate;
        }

        @Override
        public IRecipeCategoriesLookup limitTypes(Collection<RecipeType<?>> types) {
            delegate.limitTypes(types);
            return this;
        }

        @Override
        public IRecipeCategoriesLookup limitFocus(Collection<? extends IFocus<?>> focuses) {
            delegate.limitFocus(focuses);
            return this;
        }

        @Override
        public IRecipeCategoriesLookup includeHidden() {
            delegate.includeHidden();
            return this;
        }

        @Override
        public Stream<IRecipeCategory<?>> get() {
            return delegate.get().map(ReiCategoriesLookup::completeNativeCategory);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static IRecipeCategory<?> completeNativeCategory(IRecipeCategory<?> category) {
            if (!category.getClass().getName().equals(
                    "me.shedaniel.rei.jeicompat.unwrap.JEIUnwrappedCategory")) {
                return category;
            }
            try {
                CategoryRegistry.CategoryConfiguration configuration = CategoryRegistry.getInstance()
                        .get(JEIPluginDetector.categoryId(category.getRecipeType()));
                return new ReiCategory<>((IRecipeCategory) category,
                        (DisplayCategory<Display>) configuration.getCategory());
            } catch (RuntimeException ignored) {
                return category;
            }
        }
    }

    private static final class ReiCategory<T> implements IRecipeCategory<T> {
        private final IRecipeCategory<T> delegate;
        private final DisplayCategory<Display> reiCategory;
        private final IDrawable background;
        private final IDrawable icon;

        private ReiCategory(IRecipeCategory<T> delegate, DisplayCategory<Display> reiCategory) {
            this.delegate = delegate;
            this.reiCategory = reiCategory;
            this.background = new ReiDrawable(null, 150, Math.max(1, reiCategory.getDisplayHeight()));
            this.icon = new ReiDrawable(reiCategory.getIcon(), 16, 16);
        }

        @Override public RecipeType<T> getRecipeType() { return delegate.getRecipeType(); }
        @Override public Component getTitle() { return reiCategory.getTitle(); }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
            // Native REI layouts are created by ReiRecipeLayout, not a JEI builder.
        }

        @Override public boolean isHandled(T recipe) { return true; }

        @Override
        public ResourceLocation getRegistryName(T recipe) {
            try {
                return JEIPluginDetector.asDisplay(recipe).getDisplayLocation().orElse(null);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private static final class ReiDrawable implements IDrawable {
        private final Renderer renderer;
        private final int width;
        private final int height;

        private ReiDrawable(Renderer renderer, int width, int height) {
            this.renderer = renderer;
            this.width = width;
            this.height = height;
        }

        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }

        @Override
        public void draw(GuiGraphics graphics, int x, int y) {
            if (renderer != null) {
                renderer.render(graphics, new Rectangle(x, y, width, height), -1, -1, 0.0F);
            }
        }
    }

    private static final class ReiRecipeLayout<T> implements IRecipeLayoutDrawable<T> {
        private final IRecipeCategory<T> jeiCategory;
        private final T recipe;
        private final IFocusGroup focuses;
        private final Display display;
        private final DisplayCategory<Display> reiCategory;
        private final DisplayCategoryView<Display> reiView;
        private final int width;
        private final int height;
        private List<Widget> widgets = List.of();
        private JEIDisplaySetup.Result slots;
        private int x;
        private int y;

        @SuppressWarnings({"unchecked", "rawtypes"})
        private ReiRecipeLayout(
                IRecipeCategory<T> jeiCategory,
                T recipe,
                IFocusGroup focuses,
                Display display) {
            this.jeiCategory = jeiCategory;
            this.recipe = recipe;
            this.focuses = focuses;
            this.display = display;
            CategoryRegistry.CategoryConfiguration configuration =
                    CategoryRegistry.getInstance().get(display.getCategoryIdentifier().cast());
            this.reiCategory = (DisplayCategory<Display>) configuration.getCategory();
            this.reiView = (DisplayCategoryView<Display>) configuration.getView(display);
            this.width = Math.max(1, reiCategory.getDisplayWidth(display));
            this.height = Math.max(1, reiCategory.getDisplayHeight());
            rebuild();
        }

        @Override
        public void setPosition(int x, int y) {
            if (this.x == x && this.y == y && !widgets.isEmpty()) return;
            this.x = x;
            this.y = y;
            rebuild();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void rebuild() {
            Rectangle bounds = new Rectangle(x, y, width, height);
            if (display instanceof JEIWrappedDisplay<?> wrappedDisplay) {
                JEIWrappedCategory wrappedCategory = wrappedDisplay.getBackingCategory();
                slots = JEIDisplaySetup.create(jeiCategory, (JEIWrappedDisplay<T>) wrappedDisplay, focuses);
                Supplier<IDrawable> background = wrappedCategory.background;
                widgets = List.copyOf(JEIWrappedCategory.setupDisplay(
                        slots,
                        jeiCategory,
                        (JEIWrappedDisplay<T>) wrappedDisplay,
                        bounds,
                        background));
            } else {
                List<Widget> nativeWidgets = List.copyOf(reiView.setupDisplay(display, bounds));
                JEIDisplaySetup.Result nativeSlots = JEIDisplaySetup.Result.fromREI(
                        display, nativeWidgets);
                if (isCraftingDisplay(display)
                        && !hasPopulatedCraftingSlots(nativeSlots)) {
                    List<Widget> fallbackWidgets = manualCraftingWidgets(display, bounds);
                    JEIDisplaySetup.Result fallbackSlots = JEIDisplaySetup.Result.fromREI(
                            display, fallbackWidgets);
                    if (hasPopulatedCraftingSlots(fallbackSlots)) {
                        widgets = List.copyOf(fallbackWidgets);
                        slots = fallbackSlots;
                        JeiExportMod.LOGGER.debug(
                                "REI returned no populated crafting widgets for {}; "
                                        + "using Recipe Tree's native REI crafting layout",
                                display.getDisplayLocation().map(ResourceLocation::toString)
                                        .orElse("unregistered recipe"));
                        return;
                    }
                }
                widgets = nativeWidgets;
                slots = nativeSlots;
            }
        }

        private static boolean isCraftingDisplay(Display display) {
            return BuiltinPlugin.CRAFTING.equals(display.getCategoryIdentifier());
        }

        private static boolean hasPopulatedCraftingSlots(JEIDisplaySetup.Result result) {
            if (result == null || result.slots == null) return false;
            boolean input = false;
            boolean output = false;
            for (JEIRecipeSlot slot : result.slots) {
                if (slot == null || slot.isEmpty()) continue;
                if (slot.getRole() == RecipeIngredientRole.INPUT) input = true;
                if (slot.getRole() == RecipeIngredientRole.OUTPUT) output = true;
            }
            return input && output;
        }

        /**
         * Builds the vanilla REI crafting card directly from a native display.
         *
         * <p>This is intentionally REI-only. Some REI Plugin Compat versions
         * return a valid display and panel widgets but omit the populated slot
         * widgets when the JEI facade requests a drawable. Reading the indexed
         * native inputs keeps shaped recipes, tag alternatives, and outputs
         * intact without loading standalone JEI.</p>
         */
        private static List<Widget> manualCraftingWidgets(Display display, Rectangle bounds) {
            Point origin = new Point(bounds.getCenterX() - 58, bounds.getCenterY() - 27);
            List<Widget> fallback = new ArrayList<>(14);
            fallback.add(Widgets.createRecipeBase(bounds));
            fallback.add(Widgets.createArrow(new Point(origin.x + 60, origin.y + 18)));
            fallback.add(Widgets.createResultSlotBackground(
                    new Point(origin.x + 95, origin.y + 19)));

            List<Slot> inputs = new ArrayList<>(9);
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    Slot slot = Widgets.createSlot(new Point(
                            origin.x + 1 + column * 18,
                            origin.y + 1 + row * 18)).markInput();
                    inputs.add(slot);
                    fallback.add(slot);
                }
            }

            if (display instanceof DefaultCraftingDisplay<?> craftingDisplay) {
                for (InputIngredient<EntryStack<?>> ingredient
                        : craftingDisplay.getInputIngredients(3, 3)) {
                    int index = ingredient.getIndex();
                    if (index >= 0 && index < inputs.size()) {
                        inputs.get(index).entries(ingredient.get());
                    }
                }
                if (craftingDisplay.isShapeless()) {
                    fallback.add(Widgets.createShapelessIcon(bounds));
                }
            } else {
                List<EntryIngredient> ingredients = display.getInputEntries();
                for (int index = 0; index < Math.min(inputs.size(), ingredients.size()); index++) {
                    inputs.get(index).entries(ingredients.get(index));
                }
            }

            Slot output = Widgets.createSlot(new Point(origin.x + 95, origin.y + 19))
                    .disableBackground()
                    .markOutput();
            if (!display.getOutputEntries().isEmpty()) {
                output.entries(display.getOutputEntries().get(0));
            }
            fallback.add(output);
            return fallback;
        }

        @Override
        public void drawRecipe(GuiGraphics graphics, int mouseX, int mouseY) {
            for (Widget widget : widgets) {
                widget.render(graphics, mouseX, mouseY, 0.0F);
            }
        }

        @Override public void drawOverlays(GuiGraphics graphics, int mouseX, int mouseY) { }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        @Override
        public <V> Optional<V> getIngredientUnderMouse(
                int mouseX,
                int mouseY,
                IIngredientType<V> ingredientType) {
            return slotUnderMouse(mouseX, mouseY)
                    .flatMap(slot -> slot.getDisplayedIngredient(ingredientType));
        }

        @Override
        public Optional<ItemStack> getItemStackUnderMouse(int mouseX, int mouseY) {
            return slotUnderMouse(mouseX, mouseY).flatMap(JEIRecipeSlot::getDisplayedItemStack);
        }

        @Override
        public Optional<IRecipeSlotDrawable> getRecipeSlotUnderMouse(double mouseX, double mouseY) {
            return slotUnderMouse(mouseX, mouseY).map(ReiSlotDrawable::new);
        }

        private Optional<JEIRecipeSlot> slotUnderMouse(double mouseX, double mouseY) {
            if (slots == null || slots.slots == null) return Optional.empty();
            return slots.slots.stream()
                    .filter(slot -> slot.slot.getBounds().contains(mouseX, mouseY))
                    .findFirst();
        }

        @Override public Rect2i getRect() { return new Rect2i(x, y, width, height); }
        @Override public Rect2i getRecipeTransferButtonArea() { return new Rect2i(0, 0, 0, 0); }
        @Override public IRecipeSlotsView getRecipeSlotsView() { return slots; }
        @Override public IRecipeCategory<T> getRecipeCategory() { return jeiCategory; }
        @Override public T getRecipe() { return recipe; }
    }

    private static final class ReiSlotDrawable implements IRecipeSlotDrawable {
        private final JEIRecipeSlot delegate;

        private ReiSlotDrawable(JEIRecipeSlot delegate) {
            this.delegate = delegate;
        }

        @Override
        public Rect2i getRect() {
            Rectangle bounds = delegate.slot.getBounds();
            return new Rect2i(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override public void draw(GuiGraphics graphics) { }
        @Override public void drawHoverOverlays(GuiGraphics graphics) { }
        @Override public List<Component> getTooltip() { return List.of(); }
        @Override public void addTooltipCallback(IRecipeSlotTooltipCallback callback) {
            delegate.addTooltipCallback(callback);
        }

        @Override public <V> Stream<V> getIngredients(IIngredientType<V> type) {
            return delegate.getIngredients(type);
        }

        @Override public Stream<ITypedIngredient<?>> getAllIngredients() {
            return delegate.getAllIngredients();
        }

        @Override public boolean isEmpty() { return delegate.isEmpty(); }
        @Override public <V> Optional<V> getDisplayedIngredient(IIngredientType<V> type) {
            return delegate.getDisplayedIngredient(type);
        }

        @Override public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
            return delegate.getDisplayedIngredient();
        }

        @Override public Optional<String> getSlotName() { return delegate.getSlotName(); }
        @Override public RecipeIngredientRole getRole() { return delegate.getRole(); }
        @Override public void drawHighlight(GuiGraphics graphics, int color) {
            delegate.drawHighlight(graphics, color);
        }
    }
}
