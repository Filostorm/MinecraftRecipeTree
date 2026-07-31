package com.recipetree.reiexport118;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.recipetree.reiexport118.compat.CategoricalIngredientAmountContract;
import com.recipetree.reiexport118.compat.CompactCraftingInputAmounts;
import com.recipetree.reiexport118.compat.LowDragFboViewportCompatibility;
import com.recipetree.reiexport118.compat.LowDragFboViewportContract;
import com.recipetree.reiexport118.compat.IndustrialForegoingScreenCompatibility;
import com.recipetree.reiexport118.compat.IndustrialForegoingScreenContract;
import com.recipetree.reiexport118.compat.JeiRecipeIngredientRoles;
import com.recipetree.reiexport118.compat.Mm2PreviewRenderClock;
import com.recipetree.reiexport118.compat.ReturnedIngredientSlots;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Label;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.display.DisplayCategoryView;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

final class RecipePhase implements ExportJob.PhaseRunner {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int PAD = 4;
    private static final int MAX_TEXTURE = 4096;
    private static final int BACKGROUND_ARGB = 0xFFC6C6C6;

    private final ExportContext context;
    private final ExportPlan plan;
    private final ItemCatalog catalog;
    private final Set<Class<?>> warnedUnknownAmounts = new HashSet<>();
    private final Set<CategoricalIngredientAmountContract.ExactPair> warnedCategoricalAmounts =
            new HashSet<>();
    private final Set<String> auditedJeiRoleCategories = new HashSet<>();
    private final Set<String> auditedReturnedIngredientCategories = new HashSet<>();
    private boolean warnedCompactCraftingAmounts;

    private int categoryIndex = -1;
    private int registeredCategoryIndex = -1;
    private int recipeIndex;
    private int exported;
    private ExportPlan.CategoryPlan currentCategory;
    private String categoryDirectory;
    private JsonWriter recipeWriter;

    RecipePhase(ExportContext context, ExportPlan plan) throws IOException {
        this.context = context;
        this.plan = plan;
        this.catalog = context.catalog();
    }

    @Override
    public boolean step() throws IOException {
        if (currentCategory == null) {
            categoryIndex++;
            if (categoryIndex >= plan.categories().size()) {
                return true;
            }
            beginCategory(plan.categories().get(categoryIndex));
            return false;
        }
        if (recipeIndex >= currentCategory.displays().size()) {
            closeCurrentCategory();
            currentCategory = null;
            return false;
        }
        exportDisplay(currentCategory.displays().get(recipeIndex), recipeIndex);
        recipeIndex++;
        exported++;
        return false;
    }

    private void beginCategory(ExportPlan.CategoryPlan categoryPlan) throws IOException {
        this.currentCategory = categoryPlan;
        this.recipeIndex = 0;
        CategoryRegistry.CategoryConfiguration<?> configuration = categoryPlan.configuration();
        String categoryId = configuration.getCategoryIdentifier().getIdentifier().toString();
        this.categoryDirectory = "recipes/" + context.uniquePath("", Naming.sanitize(categoryId.replace(':', '_')), "")
                .replaceFirst("^/", "");

        DisplayCategory<?> category = configuration.getCategory();
        String title;
        try {
            title = category.getTitle().getString();
        } catch (Throwable throwable) {
            title = categoryId;
            context.warning("Category title lookup " + categoryId + ": " + throwable);
        }

        JsonObject categoryJson = new JsonObject();
        categoryJson.addProperty("id", categoryId);
        categoryJson.addProperty("title", title);
        categoryJson.addProperty("dir", categoryDirectory);
        categoryJson.addProperty("count", categoryPlan.displays().size());
        try {
            categoryJson.addProperty("icon", renderCategoryIcon(category));
        } catch (Throwable throwable) {
            context.failure("Category icon " + categoryId + ": " + throwable);
        }

        JsonArray catalysts = new JsonArray();
        try {
            LinkedHashSet<String> catalystKeys = new LinkedHashSet<>();
            for (EntryIngredient workstation : configuration.getWorkstations()) {
                addIngredientKeys(workstation, catalystKeys);
            }
            catalystKeys.forEach(catalysts::add);
        } catch (Throwable throwable) {
            context.failure("Category catalysts " + categoryId + ": " + throwable);
        }
        categoryJson.add("catalysts", catalysts);
        registeredCategoryIndex = context.registerCategory(categoryJson);

        java.nio.file.Path recipePath = context.root.resolve(categoryDirectory).resolve("recipes.json");
        Files.createDirectories(recipePath.getParent());
        recipeWriter = new JsonWriter(Files.newBufferedWriter(recipePath));
        recipeWriter.beginArray();
    }

    private void exportDisplay(ExportPlan.PlannedDisplay planned, int localIndex) throws IOException {
        Display display = planned.display();
        JsonObject recipeJson = new JsonObject();
        Set<String> inputKeys = new LinkedHashSet<>();
        Set<String> outputKeys = new LinkedHashSet<>();
        try {
            Optional<ResourceLocation> location = display.getDisplayLocation();
            if (location.isPresent()) {
                recipeJson.addProperty("id", location.get().toString());
            }

            Optional<JeiRecipeIngredientRoles.Resolution> jeiRoles =
                    JeiRecipeIngredientRoles.resolve(display);
            List<EntryIngredient> inputEntries = jeiRoles
                    .map(JeiRecipeIngredientRoles.Resolution::materialInputs)
                    .orElseGet(display::getInputEntries);
            List<EntryIngredient> catalystEntries = jeiRoles
                    .map(JeiRecipeIngredientRoles.Resolution::catalysts)
                    .orElseGet(List::of);
            jeiRoles.ifPresent(resolution -> {
                String categoryId = display.getCategoryIdentifier().getIdentifier().toString();
                if (auditedJeiRoleCategories.add(categoryId)) {
                    context.warning(resolution.auditMessage());
                }
            });
            Optional<CompactCraftingInputAmounts.Resolution> compactCraftingAmounts =
                    CompactCraftingInputAmounts.resolve(display, inputEntries);
            if (compactCraftingAmounts.isPresent() && !warnedCompactCraftingAmounts) {
                context.warning(compactCraftingAmounts.get().auditWarning());
                warnedCompactCraftingAmounts = true;
            }

            JsonArray serializedInputs = serializeIngredients(
                    inputEntries,
                    inputKeys,
                    compactCraftingAmounts
                            .map(CompactCraftingInputAmounts.Resolution::amountByIngredientIndex)
                            .orElseGet(Map::of));
            JsonArray serializedOutputs = serializeIngredients(
                    display.getOutputEntries(),
                    outputKeys,
                    Map.of());
            JsonArray serializedCatalysts = serializeIngredients(
                    catalystEntries,
                    inputKeys,
                    Map.of());

            ReturnedIngredientSlots.Resolution returnedIngredients =
                    ReturnedIngredientSlots.extract(serializedInputs, serializedOutputs);
            ReturnedIngredientSlots.appendUnique(
                    serializedCatalysts,
                    returnedIngredients.returnedInputs()
            );
            if (returnedIngredients.returnedSlotCount() > 0) {
                String categoryId = display.getCategoryIdentifier().getIdentifier().toString();
                if (auditedReturnedIngredientCategories.add(categoryId)) {
                    ReiExportMod.LOGGER.info(
                            "[reiexport] RETURNED_INGREDIENTS_CLASSIFIED category={} firstRecipeSlots={}",
                            categoryId,
                            returnedIngredients.returnedSlotCount()
                    );
                }
            }

            inputKeys.clear();
            outputKeys.clear();
            addSerializedIngredientKeys(returnedIngredients.materialInputs(), inputKeys);
            addSerializedIngredientKeys(serializedCatalysts, inputKeys);
            addSerializedIngredientKeys(returnedIngredients.outputs(), outputKeys);

            recipeJson.add("in", returnedIngredients.materialInputs());
            recipeJson.add("out", returnedIngredients.outputs());
            if (!serializedCatalysts.isEmpty()) {
                recipeJson.add("cat", serializedCatalysts);
            }

            RenderedRecipe rendered = renderDisplay(
                    currentCategory.configuration(), display, planned.sourceIndex(), localIndex);
            String imageName = "r" + localIndex + ".png";
            context.saveImage(rendered.image(), categoryDirectory + "/" + imageName, true);
            recipeJson.addProperty("img", imageName);
            recipeJson.addProperty("w", rendered.logicalWidth());
            recipeJson.addProperty("h", rendered.logicalHeight());
        } catch (Throwable throwable) {
            context.failure("Recipe " + currentCategory.configuration().getCategoryIdentifier().getIdentifier()
                    + " #" + planned.sourceIndex() + ": " + throwable);
            recipeJson = new JsonObject();
            recipeJson.addProperty("err", true);
            inputKeys.clear();
            outputKeys.clear();
        }
        GSON.toJson(recipeJson, recipeWriter);
        for (String key : inputKeys) {
            context.indexRecipe(key, false, registeredCategoryIndex, localIndex);
        }
        for (String key : outputKeys) {
            context.indexRecipe(key, true, registeredCategoryIndex, localIndex);
        }
        context.recipeCount++;
    }

    private JsonArray serializeIngredients(
            List<EntryIngredient> ingredients,
            Set<String> relationKeys,
            Map<Integer, Long> amountByIngredientIndex
    ) {
        if (ingredients == null) {
            throw new IllegalStateException("REI returned a null ingredient list");
        }
        JsonArray result = new JsonArray();
        for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++) {
            EntryIngredient ingredient = ingredients.get(ingredientIndex);
            if (ingredient == null) {
                throw new IllegalStateException("REI returned null ingredient at index " + ingredientIndex);
            }
            JsonArray alternatives = new JsonArray();
            for (EntryStack<?> stack : ingredient) {
                if (stack == null || stack.isEmpty()) {
                    context.skippedEmptyEntries++;
                    continue;
                }
                String key = catalog.ensure(stack);
                JsonArray pair = new JsonArray();
                pair.add(key);
                Long overriddenAmount = amountByIngredientIndex.get(ingredientIndex);
                pair.add(overriddenAmount != null ? overriddenAmount : amountOf(stack));
                alternatives.add(pair);
                relationKeys.add(key);
            }
            result.add(alternatives);
        }
        return result;
    }

    private void addIngredientKeys(EntryIngredient ingredient, Set<String> output) {
        if (ingredient == null) {
            throw new IllegalStateException("REI category contains a null workstation ingredient");
        }
        for (EntryStack<?> stack : ingredient) {
            if (stack == null || stack.isEmpty()) {
                context.skippedEmptyEntries++;
            } else {
                output.add(catalog.ensure(stack));
            }
        }
    }

    private void addSerializedIngredientKeys(JsonArray ingredients, Set<String> output) {
        for (var ingredientElement : ingredients) {
            JsonArray ingredient = ingredientElement.getAsJsonArray();
            for (var alternativeElement : ingredient) {
                JsonArray alternative = alternativeElement.getAsJsonArray();
                if (alternative.isEmpty() || !alternative.get(0).isJsonPrimitive()) {
                    throw new IllegalStateException(
                            "Serialized ingredient alternative is missing its canonical key"
                    );
                }
                output.add(alternative.get(0).getAsString());
            }
        }
    }

    private long amountOf(EntryStack<?> stack) {
        Object value = stack.getValue();
        if (value instanceof ItemStack itemStack) {
            return requirePositiveAmount(itemStack.getCount(), value.getClass());
        }
        if (value instanceof FluidStack fluidStack) {
            return requirePositiveAmount(fluidStack.getAmount(), value.getClass());
        }
        if (value instanceof Number number) {
            return requirePositiveAmount(number.longValue(), value.getClass());
        }
        if (value == null) {
            context.failure("Recipe ingredient has a null REI value; exporting -1 and rejecting publication");
            return -1;
        }
        Optional<CategoricalIngredientAmountContract.Resolution> categorical =
                CategoricalIngredientAmountContract.resolve(
                        stack.getType().getId().toString(),
                        value.getClass().getName());
        if (categorical.isPresent()) {
            CategoricalIngredientAmountContract.Resolution resolution = categorical.get();
            if (warnedCategoricalAmounts.add(resolution.pair())) {
                context.warning(resolution.auditWarning());
            }
            return resolution.amount();
        }
        for (String methodName : List.of("getAmount", "getCount")) {
            try {
                Method method = value.getClass().getMethod(methodName);
                Object reflected = method.invoke(value);
                if (reflected instanceof Number number) {
                    return requirePositiveAmount(number.longValue(), value.getClass());
                }
            } catch (NoSuchMethodException ignored) {
                // The next explicit method probe may still match.
            } catch (Throwable throwable) {
                context.warning("Quantity reflection " + value.getClass().getName() + "." + methodName + ": " + throwable);
            }
        }
        if (warnedUnknownAmounts.add(value.getClass())) {
            context.failure("Quantity is unavailable for " + value.getClass().getName()
                    + "; exporting -1 and rejecting publication");
        }
        return -1;
    }

    private long requirePositiveAmount(long amount, Class<?> valueClass) {
        if (amount > 0) {
            return amount;
        }
        context.failure("ZERO_UNCLASSIFIED recipe ingredient amount " + amount + " for "
                + valueClass.getName() + "; preserving the value and rejecting publication");
        return amount;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private RenderedRecipe renderDisplay(CategoryRegistry.CategoryConfiguration configuration, Display display,
                                         int sourceIndex, int localIndex) {
        DisplayCategory category = configuration.getCategory();
        String categoryId = configuration.getCategoryIdentifier().getIdentifier().toString();
        int innerWidth = Math.max(1, category.getDisplayWidth(display));
        int innerHeight = Math.max(1, category.getDisplayHeight());
        int logicalWidth = innerWidth + PAD * 2;
        int logicalHeight = innerHeight + PAD * 2;
        int scale = Math.min(context.request.recipeScale,
                Math.max(1, MAX_TEXTURE / Math.max(logicalWidth, logicalHeight)));
        if (scale != context.request.recipeScale) {
            throw new IllegalStateException("Recipe layout exceeds the requested texture scale: "
                    + logicalWidth + "x" + logicalHeight + " at " + context.request.recipeScale + "x");
        }

        Rectangle bounds = new Rectangle(PAD, PAD, innerWidth, innerHeight);
        DisplayCategoryView view = configuration.getView(display);
        List<Widget> widgets = view.setupDisplay(display, bounds);
        if (widgets == null || widgets.isEmpty()) {
            throw new IllegalStateException("REI category returned no layout widgets");
        }
        rejectJeiCompatibilityErrorPanel(widgets);

        LowDragFboViewportCompatibility.CaptureMode lowDragCaptureMode = null;
        int expectedModularIngredientGroups = 0;
        if (categoryId.startsWith("multiblocked:")) {
            lowDragCaptureMode = "multiblocked:multiblock_info".equals(categoryId)
                    ? LowDragFboViewportCompatibility.CaptureMode.MULTIBLOCK_SCENE
                    : LowDragFboViewportCompatibility.CaptureMode.MODULAR_UI;
            if (lowDragCaptureMode == LowDragFboViewportCompatibility.CaptureMode.MODULAR_UI) {
                expectedModularIngredientGroups =
                        LowDragFboViewportContract.expectedModularIngredientGroups(
                                display.getClass().getName());
            }
        }

        Supplier<? extends AutoCloseable> nativeContextFactory =
                IndustrialForegoingScreenContract.requiresScreen(categoryId)
                        ? () -> IndustrialForegoingScreenCompatibility.beginIfRequired(
                                categoryId, logicalWidth, logicalHeight)
                        : null;
        NativeImage image = context.renderer.capture(
                    logicalWidth * scale,
                    logicalHeight * scale,
                    BACKGROUND_ARGB,
                    lowDragCaptureMode,
                    categoryId + "#" + sourceIndex,
                    localIndex == 0,
                    expectedModularIngredientGroups,
                    nativeContextFactory,
                    pose -> {
            try (Mm2PreviewRenderClock.CaptureScope ignored =
                         Mm2PreviewRenderClock.beginRecipePreview(categoryId, sourceIndex)) {
                pose.pushPose();
                try {
                    pose.scale(scale, scale, 1f);
                    for (Widget widget : widgets) {
                        if (widget == null) {
                            throw new IllegalStateException("REI category returned a null layout widget");
                        }
                        pose.pushPose();
                        try {
                            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                            // setupDisplay has already assigned every widget its native layout bounds.
                            // The five-argument Renderer overload is not a clipping API: REI's
                            // EntryWidget implementation temporarily replaces its own 18x18 bounds
                            // with the supplied rectangle. Passing the category rectangle there makes
                            // every ingredient render across the complete recipe canvas.
                            widget.render(pose, -10_000, -10_000, 0f);
                        } finally {
                            pose.popPose();
                        }
                    }
                } finally {
                    pose.popPose();
                }
            }
                    });
        long minimumNativePixels = RecipeImageValidation.minimumNativePixels(scale);
        long nativePixels = RecipeImageValidation.countPixelsDifferentFrom(
                image,
                BACKGROUND_ARGB,
                localIndex == 0 ? Long.MAX_VALUE : minimumNativePixels
        );
        if (nativePixels < minimumNativePixels) {
            image.close();
            throw new IllegalStateException("Recipe preview contains no validated native layout ink: category="
                    + categoryId + ", sourceIndex=" + sourceIndex + ", logical=" + logicalWidth + "x"
                    + logicalHeight + ", physical=" + (logicalWidth * scale) + "x" + (logicalHeight * scale)
                    + ", scale=" + scale + ", nonBackgroundPixels=" + nativePixels
                    + ", required=" + minimumNativePixels + ", backgroundArgb=0x"
                    + Integer.toHexString(BACKGROUND_ARGB));
        }
        if (localIndex == 0) {
            ReiExportMod.LOGGER.info(
                    "[reiexport] Recipe native-ink gate passed: category={}, sourceIndex={}, logical={}x{}, " +
                            "physical={}x{}, scale={}, nonBackgroundPixels={}",
                    categoryId, sourceIndex, logicalWidth, logicalHeight,
                    logicalWidth * scale, logicalHeight * scale, scale, nativePixels);
        }
        return new RenderedRecipe(image, logicalWidth, logicalHeight);
    }

    private static void rejectJeiCompatibilityErrorPanel(List<Widget> widgets) {
        for (Widget widget : widgets) {
            if (widget instanceof Label label
                    && "Failed to initiate JEI integration setRecipe".equals(label.getMessage().getString())) {
                throw new IllegalStateException(
                        "REI Plugin Compatibilities returned its JEI setRecipe error panel; check the launch log");
            }
        }
    }

    private String renderCategoryIcon(DisplayCategory<?> category) {
        Renderer icon = category.getIcon();
        requireCategoryIcon(icon);
        int logicalSize = 16;
        int scale = 2;
        NativeImage image = context.renderer.capture(logicalSize * scale, logicalSize * scale, pose -> {
            pose.pushPose();
            try {
                pose.scale(scale, scale, 1f);
                icon.render(pose, new Rectangle(0, 0, logicalSize, logicalSize), -10_000, -10_000, 0f);
            } finally {
                pose.popPose();
            }
        });
        String relative = categoryDirectory + "/icon.png";
        context.saveImage(image, relative, true);
        return relative;
    }

    static void requireCategoryIcon(Object icon) {
        if (icon == null) {
            throw new IllegalStateException(
                    "REI DisplayCategory.getIcon() returned null; native category-icon completeness is required"
            );
        }
    }

    private void closeCurrentCategory() throws IOException {
        if (recipeWriter != null) {
            recipeWriter.endArray();
            recipeWriter.close();
            recipeWriter = null;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            closeCurrentCategory();
        } finally {
            IndustrialForegoingScreenCompatibility.requireReleasedAndLog();
        }
    }

    @Override
    public String label() {
        return "recipes";
    }

    @Override
    public int done() {
        return exported;
    }

    @Override
    public int total() {
        return plan.categories().stream().mapToInt(category -> category.displays().size()).sum();
    }

    private record RenderedRecipe(NativeImage image, int logicalWidth, int logicalHeight) {
    }
}
