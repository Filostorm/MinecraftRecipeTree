package com.recipetree.jeiexport;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lightweight, lazy in-game planner. JEI remains the recipe renderer and source of truth. */
public final class RecipeTreeScreen extends Screen {
    private static final int PANEL_WIDTH = 440;
    private static final int PANEL_HEIGHT = 300;
    private static final int MAX_RECIPE_PAGES = 64;
    private static final int MAX_MACHINE_CATALYSTS = 8;
    private static final int MAX_MACHINE_RECIPES_CHECKED = 12;

    private final ItemStack target;
    private final IJeiRuntime runtime;
    private final IFocus<ItemStack> targetFocus;
    private final IFocusGroup targetFocusGroup;
    private final List<RecipePage<?>> pages = new ArrayList<>();
    private final Map<String, Boolean> craftableMachineCache = new HashMap<>();
    private final RecipeTreeProgress progress = RecipeTreeProgress.get();

    private int pageIndex;
    private int panelLeft;
    private int panelTop;
    private EditBox amountBox;
    private EditBox minutesBox;
    private EditBox cycleBox;
    private Button previousButton;
    private Button nextButton;
    private Button progressionButton;
    private Button machineButton;
    private String status = "";

    public RecipeTreeScreen(ItemStack target, IJeiRuntime runtime) {
        super(Component.translatable("screen.jeiexport.recipe_tree"));
        this.target = target;
        this.runtime = runtime;
        this.targetFocus = runtime.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, target);
        this.targetFocusGroup = runtime.getJeiHelpers().getFocusFactory()
                .createFocusGroup(List.of(targetFocus));
        collectPages();
        restorePlan();
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        int left = panelLeft + 12;

        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(left, panelTop + 32, 22, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(left + 26, panelTop + 32, 22, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("button.jeiexport.open_jei"), button -> openJei())
                .bounds(left + 54, panelTop + 32, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("button.jeiexport.save_plan"), button -> savePlan())
                .bounds(left + 136, panelTop + 32, 72, 20).build());

        progressionButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    progress.setProgressionEnabled(!progress.isProgressionEnabled());
                    updateButtons();
                }).bounds(panelLeft + PANEL_WIDTH - 166, panelTop + 32, 154, 20).build());

        amountBox = numericBox(left, panelTop + 76, 92, "Requested output", "64");
        minutesBox = numericBox(left + 104, panelTop + 76, 92, "Time window (minutes)", "10");
        cycleBox = numericBox(left + 208, panelTop + 76, 92, "Cycle time (seconds)", defaultCycleText());
        RecipeTreeProgress.SavedPlan saved = progress.plan(target);
        if (saved != null) {
            amountBox.setValue(Long.toString(saved.amount()));
            minutesBox.setValue(trimNumber(saved.minutes()));
            cycleBox.setValue(trimNumber(saved.cycleSeconds()));
            status = "Loaded local plan";
        }
        amountBox.setResponder(value -> status = "");
        minutesBox.setResponder(value -> status = "");
        cycleBox.setResponder(value -> status = "");

        machineButton = addRenderableWidget(Button.builder(Component.empty(), button -> toggleMachine())
                .bounds(panelLeft + PANEL_WIDTH - 128, panelTop + 76, 116, 20).build());
        updateButtons();
    }

    private EditBox numericBox(int x, int y, int boxWidth, String narration, String value) {
        EditBox box = new EditBox(font, x, y, boxWidth, 20, Component.literal(narration));
        box.setValue(value);
        box.setFilter(text -> text.isEmpty() || text.matches("[0-9]{0,9}(\\.[0-9]{0,4})?"));
        return addRenderableWidget(box);
    }

    @Override
    public void tick() {
        super.tick();
        currentPage().ifPresent(page -> page.layout.tick());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xf0181a1b);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + 2, 0xff69a847);

        graphics.renderItem(target, panelLeft + 12, panelTop + 9);
        graphics.drawString(font, target.getHoverName(), panelLeft + 34, panelTop + 13, 0xffffffff, false);

        Optional<RecipePage<?>> current = currentPage();
        String pageText = pages.isEmpty() ? "No JEI recipe found" : (pageIndex + 1) + " / " + pages.size();
        graphics.drawString(font, pageText, panelLeft + 226, panelTop + 13, 0xffaeb7aa, false);
        current.ifPresent(page -> graphics.drawString(
                font, page.category.getTitle(), panelLeft + PANEL_WIDTH - 12 - font.width(page.category.getTitle()),
                panelTop + 13, 0xffd7e6ce, false));

        graphics.drawString(font, "AMOUNT", panelLeft + 12, panelTop + 64, 0xff8f9b8b, false);
        graphics.drawString(font, "MINUTES", panelLeft + 116, panelTop + 64, 0xff8f9b8b, false);
        graphics.drawString(font, "CYCLE SEC", panelLeft + 220, panelTop + 64, 0xff8f9b8b, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (current.isEmpty()) {
            graphics.drawCenteredString(font, "Use Open JEI to inspect uses and alternatives.",
                    width / 2, panelTop + 148, 0xffb9c0b5);
            return;
        }

        RecipePage<?> page = current.get();
        renderMachineSummary(graphics, page);
        renderRecipe(graphics, page);
        page.layout.getRecipeSlotUnderMouse(mouseX, mouseY)
                .ifPresent(slot -> graphics.renderComponentTooltip(font, slot.getTooltip(), mouseX, mouseY));
        if (!status.isEmpty()) {
            graphics.drawCenteredString(font, status, width / 2, panelTop + PANEL_HEIGHT - 14, 0xff9fcf7f);
        }
    }

    private void renderMachineSummary(GuiGraphics graphics, RecipePage<?> page) {
        int y = panelTop + 105;
        Optional<ItemStack> machine = selectedMachine(page);
        if (machine.isPresent()) {
            graphics.renderItem(machine.get(), panelLeft + 12, y - 3);
            boolean available = isPageAvailable(page);
            int color = available ? 0xff91d36c : 0xffe0a25d;
            String state = available ? "available" : "locked by progression";
            graphics.drawString(font, machine.get().getHoverName(), panelLeft + 34, y, 0xffe8eee4, false);
            graphics.drawString(font, state, panelLeft + 34, y + 11, color, false);
        } else {
            graphics.drawString(font, "No machine catalyst declared by JEI", panelLeft + 12, y, 0xffaeb7aa, false);
        }

        try {
            MachineParallelPlan plan = calculate(page);
            String label = plan.machinesRequired() == 1 ? "1 machine" : plan.machinesRequired() + " machines in parallel";
            graphics.drawString(font, label, panelLeft + 224, y, 0xffffffff, false);
            graphics.drawString(font,
                    plan.cyclesRequired() + " cycles · " + page.outputPerCycle + " output/cycle",
                    panelLeft + 224, y + 11, 0xff9fcf7f, false);
            if (!page.measuredCycleSeconds) {
                graphics.drawString(font, "cycle time is an editable estimate",
                        panelLeft + 224, y + 22, 0xff8f9b8b, false);
            }
        } catch (IllegalArgumentException error) {
            graphics.drawString(font, "Enter positive amount and timing values", panelLeft + 224, y, 0xffe0a25d, false);
        }
    }

    private void renderRecipe(GuiGraphics graphics, RecipePage<?> page) {
        int availableWidth = PANEL_WIDTH - 24;
        int recipeX = panelLeft + 12 + Math.max(0, (availableWidth - page.layout.getRect().getWidth()) / 2);
        int recipeY = panelTop + 142;
        page.layout.setPosition(recipeX, recipeY);
        if (progress.isProgressionEnabled() && !isPageAvailable(page)) {
            graphics.fill(recipeX - 4, recipeY - 4,
                    recipeX + page.layout.getRect().getWidth() + 4,
                    recipeY + page.layout.getRect().getHeight() + 4, 0x66000000);
        }
        page.layout.drawRecipe(graphics, mouseXForLayout(), mouseYForLayout());
    }

    private int mouseXForLayout() {
        return minecraft == null ? 0 : (int) (minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth());
    }

    private int mouseYForLayout() {
        return minecraft == null ? 0 : (int) (minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        Optional<ItemStack> selected = currentPage()
                .flatMap(page -> page.layout.getRecipeSlotUnderMouse(mouseX, mouseY))
                .flatMap(slot -> slot.getDisplayedItemStack())
                .filter(stack -> !stack.isEmpty());
        if (selected.isEmpty()) return false;
        minecraft.setScreen(new RecipeTreeScreen(selected.get().copyWithCount(1), runtime));
        return true;
    }

    public void onDiscoveriesChanged() {
        craftableMachineCache.clear();
        updateButtons();
    }

    private void changePage(int delta) {
        if (pages.isEmpty()) return;
        pageIndex = Math.floorMod(pageIndex + delta, pages.size());
        cycleBox.setValue(defaultCycleText());
        status = "";
        updateButtons();
    }

    private void openJei() {
        runtime.getRecipesGui().show(targetFocus);
    }

    private void savePlan() {
        RecipePage<?> page = currentPage().orElse(null);
        if (page == null) return;
        try {
            long amount = parseLong(amountBox.getValue());
            double minutes = parseDouble(minutesBox.getValue());
            double cycle = parseDouble(cycleBox.getValue());
            calculate(page);
            progress.savePlan(target, new RecipeTreeProgress.SavedPlan(amount, minutes, cycle, page.key));
            status = "Plan saved locally";
        } catch (IllegalArgumentException error) {
            status = "Fix the highlighted planning values first";
        }
    }

    private void restorePlan() {
        RecipeTreeProgress.SavedPlan saved = progress.plan(target);
        if (saved == null) return;
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).key.equals(saved.recipeKey())) {
                pageIndex = index;
                break;
            }
        }
    }

    private MachineParallelPlan calculate(RecipePage<?> page) {
        return MachineParallelPlan.calculate(
                parseLong(amountBox.getValue()),
                page.outputPerCycle,
                parseDouble(cycleBox.getValue()),
                parseDouble(minutesBox.getValue()) * 60);
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(error);
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(error);
        }
    }

    private void toggleMachine() {
        currentPage().flatMap(this::selectedMachine).ifPresent(machine -> {
            boolean available = progress.isMachineManuallyAvailable(machine);
            progress.setMachineManuallyAvailable(machine, !available);
            craftableMachineCache.remove(RecipeTreeProgress.itemKey(machine));
            updateButtons();
        });
    }

    private void updateButtons() {
        if (previousButton == null) return;
        previousButton.active = pages.size() > 1;
        nextButton.active = pages.size() > 1;
        progressionButton.setMessage(Component.literal(
                "Progression: " + (progress.isProgressionEnabled() ? "ON" : "OFF")));
        Optional<ItemStack> machine = currentPage().flatMap(this::selectedMachine);
        machineButton.visible = machine.isPresent();
        machine.ifPresent(stack -> machineButton.setMessage(Component.literal(
                progress.isMachineManuallyAvailable(stack) ? "Uncheck machine" : "Check machine")));
    }

    private String defaultCycleText() {
        return currentPage().map(page -> trimNumber(page.defaultCycleSeconds)).orElse("1");
    }

    private static String trimNumber(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        return Double.toString(value);
    }

    private Optional<RecipePage<?>> currentPage() {
        if (pages.isEmpty()) return Optional.empty();
        pageIndex = Mth.clamp(pageIndex, 0, pages.size() - 1);
        return Optional.of(pages.get(pageIndex));
    }

    private Optional<ItemStack> selectedMachine(RecipePage<?> page) {
        return page.machines.stream()
                .filter(this::isMachineAvailable)
                .findFirst()
                .or(() -> page.machines.stream().findFirst());
    }

    private boolean isPageAvailable(RecipePage<?> page) {
        if (!progress.isProgressionEnabled() || page.machines.isEmpty()) return true;
        return page.machines.stream().anyMatch(this::isMachineAvailable);
    }

    private boolean isMachineAvailable(ItemStack machine) {
        if (progress.isMachineManuallyAvailable(machine) || progress.isDiscovered(machine)) return true;
        return craftableMachineCache.computeIfAbsent(
                RecipeTreeProgress.itemKey(machine), ignored -> canCraftMachineFromDiscoveredItems(machine));
    }

    private boolean canCraftMachineFromDiscoveredItems(ItemStack machine) {
        IFocus<ItemStack> focus = runtime.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, machine);
        IFocusGroup focusGroup = runtime.getJeiHelpers().getFocusFactory().createFocusGroup(List.of(focus));
        return runtime.getRecipeManager().createRecipeCategoryLookup()
                .limitFocus(List.of(focus))
                .get()
                .anyMatch(category -> anyDiscoveredRecipe(category, focus, focusGroup));
    }

    private <T> boolean anyDiscoveredRecipe(
            IRecipeCategory<T> category,
            IFocus<ItemStack> focus,
            IFocusGroup focusGroup) {
        return runtime.getRecipeManager().createRecipeLookup(category.getRecipeType())
                .limitFocus(List.of(focus))
                .get()
                .limit(MAX_MACHINE_RECIPES_CHECKED)
                .map(recipe -> runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe, focusGroup))
                .flatMap(Optional::stream)
                .anyMatch(this::allItemInputsDiscovered);
    }

    private boolean allItemInputsDiscovered(IRecipeLayoutDrawable<?> layout) {
        List<IRecipeSlotView> inputs = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT);
        if (inputs.isEmpty()) return false;
        for (IRecipeSlotView input : inputs) {
            List<ItemStack> alternatives = input.getItemStacks().filter(stack -> !stack.isEmpty()).toList();
            if (alternatives.isEmpty() || alternatives.stream().noneMatch(progress::isDiscovered)) return false;
        }
        return true;
    }

    private void collectPages() {
        runtime.getRecipeManager().createRecipeCategoryLookup()
                .limitFocus(List.of(targetFocus))
                .get()
                .forEach(category -> collectCategoryPages(category, targetFocus));
    }

    private <T> void collectCategoryPages(IRecipeCategory<T> category, IFocus<ItemStack> focus) {
        if (pages.size() >= MAX_RECIPE_PAGES) return;
        List<ItemStack> machines = runtime.getRecipeManager()
                .createRecipeCatalystLookup(category.getRecipeType())
                .getItemStack()
                .filter(stack -> !stack.isEmpty())
                .limit(MAX_MACHINE_CATALYSTS)
                .map(ItemStack::copy)
                .toList();
        runtime.getRecipeManager().createRecipeLookup(category.getRecipeType())
                .limitFocus(List.of(focus))
                .get()
                .limit(MAX_RECIPE_PAGES - pages.size())
                .forEach(recipe -> runtime.getRecipeManager()
                        .createRecipeLayoutDrawable(category, recipe, targetFocusGroup)
                        .ifPresent(layout -> pages.add(new RecipePage<>(
                                category,
                                recipe,
                                layout,
                                machines,
                                outputPerCycle(layout),
                                cycleSeconds(category.getRecipeType().getUid(), recipe),
                                recipe instanceof AbstractCookingRecipe,
                                recipeKey(category, recipe)))));
    }

    private long outputPerCycle(IRecipeLayoutDrawable<?> layout) {
        long output = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .map(IRecipeSlotView::getDisplayedItemStack)
                .flatMap(Optional::stream)
                .filter(stack -> ItemStack.isSameItemSameTags(stack, target))
                .mapToLong(ItemStack::getCount)
                .sum();
        return Math.max(1, output);
    }

    private static double cycleSeconds(ResourceLocation categoryId, Object recipe) {
        if (recipe instanceof AbstractCookingRecipe cookingRecipe) {
            return Math.max(0.05, cookingRecipe.getCookingTime() / 20.0);
        }
        String path = categoryId.getPath();
        if (path.contains("blasting") || path.contains("smoking")) return 5;
        if (path.contains("smelting") || path.contains("furnace")) return 10;
        return 1;
    }

    private static <T> String recipeKey(IRecipeCategory<T> category, T recipe) {
        @Nullable ResourceLocation registryName = category.getRegistryName(recipe);
        return category.getRecipeType().getUid() + "|" + (registryName == null ? recipe.toString() : registryName);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record RecipePage<T>(
            IRecipeCategory<T> category,
            T recipe,
            IRecipeLayoutDrawable<T> layout,
            List<ItemStack> machines,
            long outputPerCycle,
            double defaultCycleSeconds,
            boolean measuredCycleSeconds,
            String key) {
    }
}
