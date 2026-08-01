package com.recipetree.jeiexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Lightweight, lazy in-game planner. JEI remains the recipe renderer and source of truth. */
public final class RecipeTreeScreen extends Screen {
    private static final Gson SHARE_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SHARE_FORMAT = "minecraft-recipe-tree";
    private static final int SHARE_VERSION = 1;
    private static final int MAX_SHARE_BYTES = 1_048_576;
    private static final int DETAIL_PANEL_WIDTH = 720;
    private static final int DETAIL_PANEL_HEIGHT = 400;
    private static final int PANEL_MARGIN = 8;
    private static final int MAX_RECIPE_PAGES = 64;
    private static final int MAX_AUTOMATIC_FAVORITE_EXPANSIONS = 128;

    private final ItemStack target;
    private final IJeiRuntime runtime;
    private final IFocus<ItemStack> targetFocus;
    private final List<ItemStack> path;
    private final OutputHistory history;
    private final List<RecipePage<?>> pages = new ArrayList<>();
    private final RecipeTreeProgress progress = RecipeTreeProgress.get();

    private List<TreeNode> treeNodes = List.of();
    private List<RecipeBoxHitbox> recipeBoxes = List.of();
    private PlanNode rootNode;
    private float treeZoom = 1.0f;
    private double treePanX;
    private double treePanY;
    private boolean treeViewInitialized;
    private boolean treePanning;
    private int treeViewportLeft;
    private int treeViewportTop;
    private int treeViewportRight;
    private int treeViewportBottom;
    private CompactPreviewBounds compactPreviewArea;
    private int pageIndex;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int treeViewportTopOffset = 60;
    private int amountLabelX;
    private int amountLabelY;
    private boolean compactMode;
    private String requestedAmount = "64";
    private EditBox amountBox;
    private Button previousButton;
    private Button nextButton;
    private Button modeButton;
    private int favoriteExpansionAttemptsRemaining = MAX_AUTOMATIC_FAVORITE_EXPANSIONS;
    private String status = "";

    public RecipeTreeScreen(ItemStack target, IJeiRuntime runtime) {
        this(target, runtime, List.of(target.copyWithCount(1)), false, null, new OutputHistory(), true);
    }

    private RecipeTreeScreen(
            ItemStack target,
            IJeiRuntime runtime,
            List<ItemStack> path,
            boolean compactMode,
            String preferredRecipeKey,
            OutputHistory history,
            boolean addToHistory) {
        super(Component.translatable("screen.jeiexport.recipe_tree"));
        this.target = target.copyWithCount(1);
        this.runtime = runtime;
        this.path = path.stream().map(stack -> stack.copyWithCount(1)).toList();
        this.history = history;
        this.compactMode = compactMode;
        this.targetFocus = runtime.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, this.target);
        collectPages();
        restorePlan();
        if (!selectRecipe(preferredRecipeKey)) {
            selectRecipe(progress.favoriteRecipe(this.target));
        }
        this.rootNode = new PlanNode(this.target, requestedQuantity(), null, 0);
        currentPage().ifPresent(rootNode::setRecipe);
        if (addToHistory) history.push(this);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(DETAIL_PANEL_WIDTH, Math.max(1, width - PANEL_MARGIN * 2));
        panelHeight = Math.min(DETAIL_PANEL_HEIGHT, Math.max(1, height - PANEL_MARGIN * 2));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        int left = panelLeft + 12;
        int firstRowY = panelTop + 32;
        int modeLeft = panelLeft + panelWidth - 90;
        int rightControlsLeft = modeLeft - 98;
        boolean stackRightControls = left + 132 + 8 > rightControlsLeft;
        int rightControlsRow = stackRightControls ? 1 : 0;
        int secondaryWidth = compactMode ? 130 : 206;
        int inlineSecondaryRight = left + 136 + secondaryWidth;
        int secondaryRow = stackRightControls
                ? 2
                : (inlineSecondaryRight + 8 > rightControlsLeft ? 1 : 0);
        int maximumToolbarRow = Math.max(rightControlsRow, secondaryRow);
        int nextTreeViewportTopOffset = 60 + maximumToolbarRow * 24;
        if (treeViewportTopOffset != nextTreeViewportTopOffset) treeViewInitialized = false;
        treeViewportTopOffset = nextTreeViewportTopOffset;

        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> navigateHistory(-1))
                .bounds(left, firstRowY, 22, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> navigateHistory(1))
                .bounds(left + 26, firstRowY, 22, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("button.jeiexport.open_jei"), button -> openJei())
                .bounds(left + 54, firstRowY, 78, 20).build());

        int secondaryLeft = secondaryRow == 0 ? left + 136 : left;
        int secondaryY = firstRowY + secondaryRow * 24;
        addRenderableWidget(Button.builder(Component.literal("Share"), button -> shareTree())
                .bounds(secondaryLeft, secondaryY, 54, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Import file"), button -> importTree())
                .bounds(secondaryLeft + 58, secondaryY, 72, 20).build());

        if (!compactMode) {
            addRenderableWidget(Button.builder(Component.translatable("button.jeiexport.save_plan"), button -> savePlan())
                    .bounds(secondaryLeft + 134, secondaryY, 72, 20).build());
        }

        int rightControlsY = firstRowY + rightControlsRow * 24;
        amountLabelX = modeLeft - 98;
        amountLabelY = rightControlsY + 6;
        amountBox = numericBox(modeLeft - 72, rightControlsY, 62, "Requested output", requestedAmount);
        amountBox.setResponder(this::changeRequestedAmount);
        modeButton = addRenderableWidget(Button.builder(Component.empty(), button -> toggleMode())
                .bounds(modeLeft, rightControlsY, 78, 20).build());
        updateButtons();
    }

    private EditBox numericBox(int x, int y, int boxWidth, String narration, String value) {
        EditBox box = new EditBox(font, x, y, boxWidth, 20, Component.literal(narration));
        box.setValue(value);
        box.setFilter(text -> text.isEmpty() || text.matches("[0-9]{0,9}"));
        return addRenderableWidget(box);
    }

    private void changeRequestedAmount(String value) {
        requestedAmount = value;
        status = "";
        try {
            long quantity = parseLong(value);
            if (quantity > 0 && rootNode != null) rootNode.updateQuantity(quantity);
        } catch (IllegalArgumentException ignored) {
            // Keep the last valid tree totals while the player edits an empty or invalid value.
        }
    }

    private long requestedQuantity() {
        try {
            return Math.max(1, parseLong(requestedAmount));
        } catch (IllegalArgumentException ignored) {
            return 1;
        }
    }

    @Override
    public void tick() {
        super.tick();
        tickPlanNode(rootNode);
    }

    private void tickPlanNode(PlanNode node) {
        if (node == null) return;
        if (node.recipe != null) node.recipe.layout.tick();
        node.children.forEach(this::tickPlanNode);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xf0181a1b);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, 0xff69a847);

        renderTree(graphics, mouseX, mouseY);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.renderItem(target, panelLeft + 12, panelTop + 9);
        String targetName = font.plainSubstrByWidth(
                target.getHoverName().getString(), Math.max(40, panelWidth / 2 - 54));
        graphics.drawString(font, targetName, panelLeft + 34, panelTop + 13, 0xffffffff, false);

        Optional<RecipePage<?>> current = currentPage();
        String pageText = pages.isEmpty() ? "No JEI recipe" : (pageIndex + 1) + " / " + pages.size();
        int pageX = panelLeft + (panelWidth - font.width(pageText)) / 2;
        graphics.drawString(font, pageText, pageX, panelTop + 13, 0xffaeb7aa, false);
        current.ifPresent(page -> {
            Component title = page.category.getTitle();
            int maxTitleWidth = Math.min(compactMode ? 112 : 150, Math.max(40, panelWidth / 3));
            String categoryTitle = font.plainSubstrByWidth(title.getString(), maxTitleWidth);
            graphics.drawString(font, categoryTitle,
                    panelLeft + panelWidth - 12 - font.width(categoryTitle), panelTop + 13, 0xffd7e6ce, false);
        });

        graphics.drawString(font, "AMT", amountLabelX, amountLabelY, 0xff8f9b8b, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (!status.isEmpty()) {
            graphics.fill(panelLeft + 1, panelTop + panelHeight - 22,
                    panelLeft + panelWidth - 1, panelTop + panelHeight - 1, 0xf0181a1b);
            graphics.drawCenteredString(font, status, width / 2, panelTop + panelHeight - 14, 0xff9fcf7f);
        }
        graphics.pose().popPose();
    }

    private void renderTree(GuiGraphics graphics, int mouseX, int mouseY) {
        treeViewportLeft = panelLeft + 8;
        int contentRight = panelLeft + panelWidth - 8;
        treeViewportBottom = panelTop + panelHeight - 8;
        treeViewportTop = Math.min(panelTop + treeViewportTopOffset, treeViewportBottom - 1);
        compactPreviewArea = compactMode ? compactPreviewBounds(contentRight) : null;
        treeViewportRight = contentRight;
        int viewportWidth = Math.max(1, treeViewportRight - treeViewportLeft);

        Map<PlanNode, Integer> subtreeWidths = new IdentityHashMap<>();
        int contentWidth = measureSubtree(rootNode, subtreeWidths);
        List<TreeLayoutNode> layoutNodes = new ArrayList<>();
        layoutSubtree(rootNode, 0, 0, -1, subtreeWidths, layoutNodes);
        if (!treeViewInitialized) {
            treePanX = Math.max(8, (viewportWidth - contentWidth) / 2.0);
            treePanY = 8;
            treeViewInitialized = true;
        }

        double modelMouseX = toTreeX(mouseX);
        double modelMouseY = toTreeY(mouseY);
        List<TreeNode> nodes = new ArrayList<>();
        List<RecipeBoxHitbox> boxes = new ArrayList<>();
        graphics.enableScissor(treeViewportLeft, treeViewportTop, treeViewportRight, treeViewportBottom);
        graphics.pose().pushPose();
        graphics.pose().translate(treeViewportLeft + treePanX, treeViewportTop + treePanY, 0);
        graphics.pose().scale(treeZoom, treeZoom, 1.0f);

        for (TreeLayoutNode layoutNode : layoutNodes) {
            if (layoutNode.parentIndex < 0) continue;
            TreeLayoutNode parent = layoutNodes.get(layoutNode.parentIndex);
            int parentX = parent.left + parent.width / 2;
            int parentBottom = parent.top + parent.height;
            int parentContentBottom = parent.top + nodeContentHeight(parent.node);
            int childX = layoutNode.left + layoutNode.width / 2;
            int childTop = layoutNode.top;
            int branchY = parentBottom + Math.max(8, (childTop - parentBottom) / 2);
            if (parentContentBottom < parentBottom) {
                graphics.fill(parentX, parentContentBottom, parentX + 1, parentBottom + 1, 0xff52624d);
            }
            graphics.fill(parentX, parentBottom, parentX + 1, branchY + 1, 0xff52624d);
            graphics.fill(Math.min(parentX, childX), branchY, Math.max(parentX, childX) + 1,
                    branchY + 1, 0xff52624d);
            graphics.fill(childX, branchY, childX + 1, childTop + 1, 0xff52624d);
        }

        for (TreeLayoutNode layoutNode : layoutNodes) {
            PlanNode node = layoutNode.node;
            if (!compactMode && node.recipe != null) {
                node.recipe.layout.setPosition(layoutNode.left, layoutNode.top);
                node.recipe.layout.drawRecipe(graphics, (int) modelMouseX, (int) modelMouseY);
                if (node.quantity > 1) {
                    String count = node.quantity + "x";
                    int recipeHeight = node.recipe.layout.getRect().getHeight();
                    int countLeft = layoutNode.left + (layoutNode.width - font.width(count)) / 2;
                    graphics.fill(countLeft - 2, layoutNode.top + recipeHeight + 1,
                            countLeft + font.width(count) + 2, layoutNode.top + recipeHeight + 12,
                            0xe0181a1b);
                    graphics.drawCenteredString(font, count,
                            layoutNode.left + layoutNode.width / 2,
                            layoutNode.top + recipeHeight + 2, 0xffffffff);
                }
                var rect = node.recipe.layout.getRectWithBorder();
                boxes.add(canvasRecipeHitbox(node, node.recipe, rect));
            } else {
                int size = layoutNode.width;
                renderNode(graphics, node, layoutNode.left, layoutNode.top, size,
                        node.quantity, (int) modelMouseX, (int) modelMouseY);
                nodes.add(canvasTreeHitbox(node, layoutNode.left, layoutNode.top, size));
            }
        }
        graphics.pose().popPose();
        graphics.disableScissor();

        Optional<TreeNode> hoveredNode = insideTreeViewport(mouseX, mouseY)
                ? nodes.stream().filter(node -> node.contains(mouseX, mouseY)).findFirst()
                : Optional.empty();
        if (compactPreviewArea != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            renderReservedPreview(graphics, compactPreviewArea, hoveredNode, mouseX, mouseY, boxes);
            graphics.pose().popPose();
        }

        treeNodes = List.copyOf(nodes);
        recipeBoxes = List.copyOf(boxes);
        renderTreeTooltip(graphics, nodes, mouseX, mouseY);
        boxes.stream()
                .filter(box -> box.contains(mouseX, mouseY))
                .findFirst()
                .flatMap(box -> box.page.layout.getRecipeSlotUnderMouse(
                        box.recipeMouseX(mouseX),
                        box.recipeMouseY(mouseY)))
                .ifPresent(slot -> graphics.renderComponentTooltip(font, slot.getTooltip(), mouseX, mouseY));
    }

    private int measureSubtree(PlanNode node, Map<PlanNode, Integer> widths) {
        NodeSize size = nodeSize(node);
        int childrenWidth = 0;
        for (PlanNode child : node.children) {
            if (childrenWidth > 0) childrenWidth += 24;
            childrenWidth += measureSubtree(child, widths);
        }
        int width = Math.max(size.width, childrenWidth);
        widths.put(node, width);
        return width;
    }

    private void layoutSubtree(
            PlanNode node,
            int subtreeLeft,
            int top,
            int parentIndex,
            Map<PlanNode, Integer> widths,
            List<TreeLayoutNode> layouts) {
        NodeSize size = nodeSize(node);
        int subtreeWidth = widths.get(node);
        int nodeLeft = subtreeLeft + (subtreeWidth - size.width) / 2;
        int nodeIndex = layouts.size();
        layouts.add(new TreeLayoutNode(node, nodeLeft, top, size.width, size.height, parentIndex));
        if (node.children.isEmpty()) return;

        int childrenWidth = node.children.stream().mapToInt(widths::get).sum()
                + Math.max(0, node.children.size() - 1) * 24;
        int childLeft = subtreeLeft + (subtreeWidth - childrenWidth) / 2;
        int childTop = top + size.height + 48;
        for (PlanNode child : node.children) {
            layoutSubtree(child, childLeft, childTop, nodeIndex, widths, layouts);
            childLeft += widths.get(child) + 24;
        }
    }

    private NodeSize nodeSize(PlanNode node) {
        if (!compactMode && node.recipe != null) {
            int labelHeight = node.quantity > 1 ? 12 : 0;
            return new NodeSize(
                    node.recipe.layout.getRect().getWidth(),
                    node.recipe.layout.getRect().getHeight() + labelHeight);
        }
        return new NodeSize(28, 28 + (node.quantity > 1 ? 12 : 0));
    }

    private int nodeContentHeight(PlanNode node) {
        if (!compactMode && node.recipe != null) return node.recipe.layout.getRect().getHeight();
        return 28;
    }

    private TreeNode canvasTreeHitbox(PlanNode node, int left, int top, int size) {
        int screenLeft = (int) Math.floor(treeViewportLeft + treePanX + left * treeZoom);
        int screenTop = (int) Math.floor(treeViewportTop + treePanY + top * treeZoom);
        int screenSize = Math.max(1, (int) Math.ceil(size * treeZoom));
        return new TreeNode(node, screenLeft, screenTop, screenSize);
    }

    private RecipeBoxHitbox canvasRecipeHitbox(PlanNode node, RecipePage<?> page, net.minecraft.client.renderer.Rect2i rect) {
        double renderOriginX = treeViewportLeft + treePanX;
        double renderOriginY = treeViewportTop + treePanY;
        int screenLeft = (int) Math.floor(renderOriginX + rect.getX() * treeZoom);
        int screenTop = (int) Math.floor(renderOriginY + rect.getY() * treeZoom);
        int screenWidth = Math.max(1, (int) Math.ceil(rect.getWidth() * treeZoom));
        int screenHeight = Math.max(1, (int) Math.ceil(rect.getHeight() * treeZoom));
        return new RecipeBoxHitbox(node, page, screenLeft, screenTop, screenWidth, screenHeight,
                renderOriginX, renderOriginY, treeZoom);
    }

    private CompactPreviewBounds compactPreviewBounds(int contentRight) {
        int contentWidth = Math.max(1, contentRight - treeViewportLeft);
        int contentHeight = Math.max(1, treeViewportBottom - treeViewportTop);
        int preferredSide = Mth.clamp(panelHeight * 2 / 5, 96, 160);
        int previewSide = Math.max(1, Math.min(preferredSide, Math.min(contentWidth, contentHeight)));
        return new CompactPreviewBounds(
                contentRight - previewSide,
                treeViewportTop,
                previewSide,
                previewSide);
    }

    private void renderReservedPreview(
            GuiGraphics graphics,
            CompactPreviewBounds bounds,
            Optional<TreeNode> hoveredNode,
            int mouseX,
            int mouseY,
            List<RecipeBoxHitbox> boxes) {
        int reservedLeft = bounds.left;
        int reservedTop = bounds.top;
        int reservedWidth = bounds.width;
        int reservedHeight = bounds.height;
        graphics.fill(reservedLeft, reservedTop, reservedLeft + reservedWidth, reservedTop + reservedHeight,
                0x78181a1b);

        if (hoveredNode.isEmpty()) {
            graphics.drawCenteredString(font, "Hover a recipe node",
                    reservedLeft + reservedWidth / 2, reservedTop + reservedHeight / 2 - 4, 0xff8f9b8b);
            return;
        }

        PlanNode node = hoveredNode.get().node;
        renderIngredient(graphics, node.ingredient, reservedLeft + 6, reservedTop + 4, 16);
        String itemName = font.plainSubstrByWidth(
                ingredientDisplayName(node.ingredient), Math.max(1, reservedWidth - 30));
        graphics.drawString(font, itemName, reservedLeft + 28, reservedTop + 8, 0xffffffff, false);
        if (node.recipe == null) {
            String action;
            if (node.stack.isEmpty()) {
                action = "Fluid / chemical input";
            } else {
                Component attackKey = minecraft == null
                        ? Component.literal("Left Button")
                        : minecraft.options.keyAttack.getTranslatedKeyMessage();
                action = attackKey.getString() + "  Select recipe";
            }
            String selectHint = font.plainSubstrByWidth(action, Math.max(1, reservedWidth - 12));
            graphics.drawCenteredString(font, selectHint,
                    reservedLeft + reservedWidth / 2, reservedTop + reservedHeight / 2 - 9, 0xffffffff);
            if (node.hasIngredientOptions()) {
                String optionHint = "Scroll  " + (node.ingredientOptionIndex + 1)
                        + " / " + node.ingredientOptions.size();
                graphics.drawCenteredString(font, optionHint,
                        reservedLeft + reservedWidth / 2, reservedTop + reservedHeight / 2 + 7,
                        0xffaeb7aa);
            }
            return;
        }
        node.recipe.layout.setPosition(0, 0);
        var rect = node.recipe.layout.getRectWithBorder();
        int availableWidth = Math.max(1, reservedWidth - 8);
        int availableHeight = Math.max(1, reservedHeight - 28);
        double recipeScale = Math.min(1.0, Math.min(
                (double) availableWidth / Math.max(1, rect.getWidth()),
                (double) availableHeight / Math.max(1, rect.getHeight())));
        recipeScale = Math.max(0.1, recipeScale);
        double fittedLeft = reservedLeft + 4 + (availableWidth - rect.getWidth() * recipeScale) / 2.0;
        double fittedTop = reservedTop + 24 + (availableHeight - rect.getHeight() * recipeScale) / 2.0;
        double renderOriginX = fittedLeft - rect.getX() * recipeScale;
        double renderOriginY = fittedTop - rect.getY() * recipeScale;
        double recipeMouseX = (mouseX - renderOriginX) / recipeScale;
        double recipeMouseY = (mouseY - renderOriginY) / recipeScale;

        graphics.pose().pushPose();
        graphics.pose().translate(renderOriginX, renderOriginY, 0);
        graphics.pose().scale((float) recipeScale, (float) recipeScale, 1.0f);
        node.recipe.layout.drawRecipe(graphics, (int) recipeMouseX, (int) recipeMouseY);
        graphics.pose().popPose();

        int screenLeft = (int) Math.floor(renderOriginX + rect.getX() * recipeScale);
        int screenTop = (int) Math.floor(renderOriginY + rect.getY() * recipeScale);
        int screenWidth = Math.max(1, (int) Math.ceil(rect.getWidth() * recipeScale));
        int screenHeight = Math.max(1, (int) Math.ceil(rect.getHeight() * recipeScale));
        boxes.add(new RecipeBoxHitbox(node, node.recipe,
                screenLeft, screenTop, screenWidth, screenHeight,
                renderOriginX, renderOriginY, recipeScale));
    }

    private double toTreeX(double screenX) {
        return (screenX - treeViewportLeft - treePanX) / treeZoom;
    }

    private double toTreeY(double screenY) {
        return (screenY - treeViewportTop - treePanY) / treeZoom;
    }

    private void renderNode(
            GuiGraphics graphics,
            PlanNode node,
            int left,
            int top,
            int size,
            long quantity,
            int mouseX,
            int mouseY) {
        boolean hovered = contains(left, top, size, mouseX, mouseY);
        graphics.fill(left, top, left + size, top + size, hovered ? 0xff4c5d46 : 0xff293029);
        graphics.fill(left, top, left + size, top + 1, hovered ? 0xff9fcf7f : 0xff52624d);
        renderIngredient(graphics, node.ingredient,
                left + (size - 16) / 2, top + (size - 16) / 2, 16);
        if (quantity > 1) {
            String count = node.stack.isEmpty() ? Long.toString(quantity) : quantity + "x";
            int countLeft = left + (size - font.width(count)) / 2;
            graphics.fill(countLeft - 2, top + size + 1,
                    countLeft + font.width(count) + 2, top + size + 12, 0xe0181a1b);
            graphics.drawCenteredString(font, count, left + size / 2, top + size + 2,
                    0xffffffff);
        }
    }

    private List<GroupedIngredient> groupedInputs(RecipePage<?> page) {
        List<GroupedIngredient> grouped = new ArrayList<>();
        for (IRecipeSlotView slot : page.layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT)) {
            List<ITypedIngredient<?>> options = slot.getAllIngredients()
                    .filter(ingredient -> !ItemCatalog.isEmptyIngredient(ingredient))
                    .collect(ArrayList::new, (ingredients, ingredient) -> {
                        if (ingredients.stream().noneMatch(existing ->
                                sameIngredient(existing, ingredient))) {
                            ingredients.add(ingredient);
                        }
                    }, ArrayList::addAll);
            Optional<ITypedIngredient<?>> displayed = slot.getDisplayedIngredient()
                    .filter(ingredient -> !ItemCatalog.isEmptyIngredient(ingredient));
            if (displayed.isEmpty() && !options.isEmpty()) displayed = Optional.of(options.get(0));
            if (displayed.isEmpty()) continue;

            ITypedIngredient<?> ingredient = displayed.get();
            if (options.stream().noneMatch(option -> sameIngredient(option, ingredient))) {
                options.add(0, ingredient);
            }
            long quantity = ingredientAmount(ingredient);
            boolean merged = false;
            for (int index = 0; index < grouped.size(); index++) {
                GroupedIngredient existing = grouped.get(index);
                if (sameIngredient(existing.ingredient, ingredient)
                        && sameIngredientOptions(existing.options, options)) {
                    grouped.set(index, new GroupedIngredient(
                            existing.ingredient, existing.quantity + quantity, existing.options));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                grouped.add(new GroupedIngredient(ingredient, quantity, List.copyOf(options)));
            }
        }
        return grouped;
    }

    private boolean sameIngredientOptions(
            List<ITypedIngredient<?>> first,
            List<ITypedIngredient<?>> second) {
        if (first.size() != second.size()) return false;
        return first.stream().allMatch(stack -> second.stream()
                .anyMatch(other -> sameIngredient(stack, other)));
    }

    private static long outputAmount(RecipePage<?> page, ItemStack output) {
        long exact = page.layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .map(slot -> slot.getItemStacks()
                        .filter(stack -> ItemStack.isSameItemSameTags(stack, output))
                        .findFirst()
                        .map(stack -> (long) Math.max(1, stack.getCount()))
                        .orElse(0L))
                .reduce(0L, RecipeQuantityMath::safeAdd);
        if (exact > 0) return exact;
        long sameItem = page.layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .map(slot -> slot.getItemStacks()
                        .filter(stack -> ItemStack.isSameItem(stack, output))
                        .findFirst()
                        .map(stack -> (long) Math.max(1, stack.getCount()))
                        .orElse(0L))
                .reduce(0L, RecipeQuantityMath::safeAdd);
        return Math.max(1, sameItem);
    }

    private void renderTreeTooltip(
            GuiGraphics graphics,
            List<TreeNode> nodes,
            int mouseX,
            int mouseY) {
        if (!insideTreeViewport(mouseX, mouseY)) return;
        nodes.stream()
                .filter(node -> node.contains(mouseX, mouseY))
                .findFirst()
                .filter(node -> !compactMode || node.node.recipe == null)
                .ifPresent(node -> graphics.renderComponentTooltip(
                        font, ingredientTooltip(node.node.ingredient), mouseX, mouseY));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if ((button != 0 && button != 1 && button != 2) || minecraft == null) return false;

        Optional<TreeNode> selectedNode = insideTreeViewport(mouseX, mouseY)
                ? treeNodes.stream().filter(node -> node.contains(mouseX, mouseY)).findFirst()
                : Optional.empty();
        if (selectedNode.isPresent()) {
            TreeNode node = selectedNode.get();
            if (button == 0) {
                openInputRecipePicker(node.node);
            } else if (button == 1) {
                openOutputPicker(node.node);
            } else {
                treePanning = true;
            }
            return true;
        }

        Optional<RecipeBoxHitbox> selectedBox = recipeBoxes.stream()
                .filter(box -> box.contains(mouseX, mouseY))
                .findFirst();
        if (selectedBox.isPresent() && button != 2) {
            RecipeBoxHitbox box = selectedBox.get();
            double slotMouseX = box.recipeMouseX(mouseX);
            double slotMouseY = box.recipeMouseY(mouseY);
            Optional<IRecipeSlotView> slot = box.page.layout
                    .getRecipeSlotUnderMouse(slotMouseX, slotMouseY)
                    .map(value -> value);
            if (slot.isPresent()) {
                Optional<ItemStack> selected = slot.get().getDisplayedItemStack()
                        .filter(stack -> !stack.isEmpty());
                if (selected.isPresent()) {
                    if (button == 0) {
                        PlanNode planNode = slot.get().getRole() == RecipeIngredientRole.OUTPUT
                                ? box.node
                                : box.node.childFor(selected.get());
                        if (planNode != null) openInputRecipePicker(planNode);
                    } else {
                        PlanNode planNode = slot.get().getRole() == RecipeIngredientRole.OUTPUT
                                ? box.node
                                : box.node.childFor(selected.get());
                        if (planNode != null) openOutputPicker(planNode);
                    }
                    return true;
                }
            }
        }

        if ((button == 0 || button == 2) && insideTreeViewport(mouseX, mouseY)) {
            treePanning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        if (treePanning && (button == 0 || button == 2)) {
            treePanX += dragX;
            treePanY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (treePanning) {
            treePanning = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean insideTreeViewport(double mouseX, double mouseY) {
        boolean insideTree = mouseX >= treeViewportLeft && mouseX < treeViewportRight
                && mouseY >= treeViewportTop && mouseY < treeViewportBottom;
        return insideTree && !insideCompactPreview(mouseX, mouseY);
    }

    private boolean insideCompactPreview(double mouseX, double mouseY) {
        if (compactPreviewArea == null) return false;
        return mouseX >= compactPreviewArea.left
                && mouseX < compactPreviewArea.left + compactPreviewArea.width
                && mouseY >= compactPreviewArea.top
                && mouseY < compactPreviewArea.top + compactPreviewArea.height;
    }

    private void openInputRecipePicker(PlanNode selectedNode) {
        ItemStack selected = selectedNode.stack;
        if (selected.isEmpty()) {
            status = ingredientDisplayName(selectedNode.ingredient)
                    + " is a JEI fluid or chemical input";
            return;
        }
        List<RecipePage<?>> choices = collectPagesFor(selected, RecipeIngredientRole.OUTPUT);
        if (choices.isEmpty()) {
            status = "No input recipes found for " + selected.getHoverName().getString();
            return;
        }
        minecraft.setScreen(new RecipePickerScreen(
                PickerKind.INPUT_RECIPE,
                selected.copyWithCount(1),
                selectedNode,
                choices.stream().map(page -> new RecipeChoice(selected.copyWithCount(1), page)).toList()));
    }

    private void openOutputPicker(PlanNode selectedNode) {
        ItemStack selected = selectedNode.stack;
        if (selected.isEmpty()) {
            status = ingredientDisplayName(selectedNode.ingredient)
                    + " is a JEI fluid or chemical input";
            return;
        }
        List<RecipeChoice> choices = new ArrayList<>();
        for (RecipePage<?> page : collectPagesFor(selected, RecipeIngredientRole.INPUT)) {
            List<ItemStack> outputs = displayedOutputs(page);
            if (!outputs.isEmpty()) {
                choices.add(new RecipeChoice(outputs.get(0).copyWithCount(1), page));
            }
        }
        if (choices.isEmpty()) {
            status = "No recipe outputs found for " + selected.getHoverName().getString();
            return;
        }
        minecraft.setScreen(new RecipePickerScreen(
                PickerKind.OUTPUT,
                selected.copyWithCount(1),
                selectedNode,
                choices));
    }

    private void navigateHistory(int delta) {
        RecipeTreeScreen destination = history.move(delta);
        if (destination != null && minecraft != null) minecraft.setScreen(destination);
    }

    private void toggleMode() {
        compactMode = !compactMode;
        treeNodes = List.of();
        recipeBoxes = List.of();
        treeViewInitialized = false;
        treeZoom = 1.0f;
        rebuildWidgets();
    }

    private void expandFavoriteIngredients(PlanNode node) {
        if (node == null) return;
        if (node.recipe == null) node.expandFavoriteRecipe();
        List.copyOf(node.children).forEach(this::expandFavoriteIngredients);
    }

    private void openJei() {
        runtime.getRecipesGui().show(targetFocus);
    }

    private String portableItemKey(ItemStack stack) {
        var helper = runtime.getIngredientManager().getIngredientHelper(VanillaTypes.ITEM_STACK);
        String uniqueId = helper.getUniqueId(stack, UidContext.Ingredient);
        if (uniqueId == null || uniqueId.isBlank()) {
            throw new IllegalStateException("JEI returned no portable identity for "
                    + stack.getHoverName().getString());
        }
        return "item|" + uniqueId;
    }

    private void appendSharedSelection(JsonArray selections, PlanNode node, List<Integer> path) {
        if (node.recipe == null) return;
        JsonObject selection = new JsonObject();
        JsonArray jsonPath = new JsonArray();
        path.forEach(jsonPath::add);
        selection.add("path", jsonPath);
        selection.addProperty("itemKey", portableItemKey(node.stack));
        JsonObject source = new JsonObject();
        source.addProperty("kind", "recipe");
        source.addProperty("recipeKey", node.recipe.key);
        selection.add("source", source);
        selections.add(selection);
        for (int index = 0; index < node.children.size(); index++) {
            List<Integer> childPath = new ArrayList<>(path);
            childPath.add(index);
            appendSharedSelection(selections, node.children.get(index), childPath);
        }
    }

    private String sharedTreeJson() {
        JsonObject share = new JsonObject();
        share.addProperty("format", SHARE_FORMAT);
        share.addProperty("version", SHARE_VERSION);
        share.addProperty("createdAt", Instant.now().toString());
        JsonObject pack = new JsonObject();
        pack.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().getName());
        pack.addProperty("name", "In-game JEI");
        share.add("pack", pack);
        share.addProperty("rootKey", portableItemKey(target));
        share.addProperty("direction", "inputs");
        JsonObject productionPlan = new JsonObject();
        long requested;
        try {
            requested = Math.max(1, parseLong(requestedAmount));
        } catch (IllegalArgumentException error) {
            requested = 1;
        }
        productionPlan.addProperty("amount", requested);
        productionPlan.addProperty("windowSeconds", 1);
        share.add("productionPlan", productionPlan);
        JsonArray selections = new JsonArray();
        appendSharedSelection(selections, rootNode, List.of());
        share.add("selections", selections);
        return SHARE_GSON.toJson(share);
    }

    private void shareTree() {
        if (minecraft == null) return;
        try {
            String json = sharedTreeJson();
            minecraft.keyboardHandler.setClipboard(json);
            Path directory = RecipeTreeShareFiles.directory(FMLPaths.CONFIGDIR.get());
            Files.createDirectories(directory);
            Path output = directory.resolve("last-tree.mrtree.json");
            Files.writeString(output, json, StandardCharsets.UTF_8);
            status = "Tree copied; also saved to config/recipe-tree-shares/last-tree.mrtree.json";
        } catch (Exception error) {
            JeiExportMod.LOGGER.error("Could not share the current recipe tree", error);
            status = "Tree share failed: " + error.getMessage();
        }
    }

    private ItemStack itemForPortableKey(String key) {
        var manager = runtime.getIngredientManager();
        for (ItemStack candidate : manager.getAllIngredients(VanillaTypes.ITEM_STACK)) {
            try {
                if (portableItemKey(candidate).equals(key)) return candidate.copyWithCount(1);
            } catch (RuntimeException ignored) {
                // One broken subtype must not prevent importing all other JEI items.
            }
        }
        return ItemStack.EMPTY;
    }

    private static String requiredString(JsonObject object, String key, int maximum) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        String text = value.getAsString();
        if (text.isBlank() || text.length() > maximum) {
            throw new IllegalArgumentException("Invalid " + key);
        }
        return text;
    }

    private PlanNode nodeAtImportedPath(List<Integer> path) {
        PlanNode node = rootNode;
        for (int segment : path) {
            if (segment < 0 || segment >= node.children.size()) {
                throw new IllegalArgumentException("A shared branch does not match its parent recipe");
            }
            node = node.children.get(segment);
        }
        return node;
    }

    private void applySharedTree(JsonObject share) {
        JsonObject plan = share.has("productionPlan") && share.get("productionPlan").isJsonObject()
                ? share.getAsJsonObject("productionPlan") : null;
        if (plan != null && plan.has("amount")) {
            long amount = plan.get("amount").getAsLong();
            if (amount > 0 && amount <= 1_000_000_000L) requestedAmount = Long.toString(amount);
        }
        rootNode = new PlanNode(target, requestedQuantity(), null, 0);
        JsonArray selections = share.getAsJsonArray("selections");
        if (selections == null || selections.size() > 2048) {
            throw new IllegalArgumentException("Invalid shared selection count");
        }
        int skipped = 0;
        for (JsonElement element : selections) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid shared selection");
            JsonObject selection = element.getAsJsonObject();
            JsonArray jsonPath = selection.getAsJsonArray("path");
            if (jsonPath == null || jsonPath.size() > 12) {
                throw new IllegalArgumentException("Shared tree exceeds the in-game depth limit");
            }
            List<Integer> path = new ArrayList<>();
            jsonPath.forEach(segment -> path.add(segment.getAsInt()));
            PlanNode node = nodeAtImportedPath(path);
            if (!portableItemKey(node.stack).equals(requiredString(selection, "itemKey", 512))) {
                throw new IllegalArgumentException("A shared item does not match its recipe branch");
            }
            JsonObject source = selection.getAsJsonObject("source");
            if (source == null || !"recipe".equals(requiredString(source, "kind", 32))) {
                skipped++;
                continue;
            }
            String recipeKey = requiredString(source, "recipeKey", 1024);
            RecipePage<?> recipe = collectPagesFor(node.stack, RecipeIngredientRole.OUTPUT).stream()
                    .filter(page -> page.key.equals(recipeKey))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Recipe " + recipeKey + " is not available in this modpack"));
            node.setRecipe(recipe);
            if (path.isEmpty()) selectRecipe(recipeKey);
        }
        treeNodes = List.of();
        recipeBoxes = List.of();
        treeViewInitialized = false;
        treeZoom = 1.0f;
        status = skipped == 0 ? "Shared tree imported" : "Shared tree imported; " + skipped
                + " non-recipe source(s) left collapsed";
    }

    private void importTree() {
        if (minecraft == null) return;
        try {
            Optional<Path> importFile = RecipeTreeShareFiles.newest(FMLPaths.CONFIGDIR.get());
            String raw = importFile.isPresent()
                    ? Files.readString(importFile.get(), StandardCharsets.UTF_8)
                    : minecraft.keyboardHandler.getClipboard();
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException(
                        "No .mrtree.json file is in config/recipe-tree-shares and the clipboard is empty");
            }
            if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_SHARE_BYTES) {
                throw new IllegalArgumentException("Shared tree exceeds the 1 MiB limit");
            }
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Shared tree must be JSON");
            JsonObject share = parsed.getAsJsonObject();
            if (!SHARE_FORMAT.equals(requiredString(share, "format", 64))
                    || !share.has("version") || share.get("version").getAsInt() != SHARE_VERSION) {
                throw new IllegalArgumentException("Unsupported recipe tree share version");
            }
            if (!"inputs".equals(requiredString(share, "direction", 16))) {
                throw new IllegalArgumentException("The in-game viewer currently imports ingredient trees only");
            }
            JsonObject pack = share.getAsJsonObject("pack");
            String shareMinecraft = pack == null ? "" : requiredString(pack, "minecraftVersion", 40);
            String currentMinecraft = SharedConstants.getCurrentVersion().getName();
            if (!currentMinecraft.equals(shareMinecraft)) {
                throw new IllegalArgumentException("Tree is for Minecraft " + shareMinecraft
                        + ", not " + currentMinecraft);
            }
            ItemStack importedTarget = itemForPortableKey(requiredString(share, "rootKey", 512));
            if (importedTarget.isEmpty()) {
                throw new IllegalArgumentException("Starting item is unavailable in this modpack");
            }
            RecipeTreeScreen imported = new RecipeTreeScreen(
                    importedTarget, runtime, List.of(importedTarget), compactMode,
                    null, history, true);
            imported.applySharedTree(share);
            imported.status += importFile
                    .map(path -> " from " + path.getFileName())
                    .orElse(" from clipboard");
            minecraft.setScreen(imported);
        } catch (Exception error) {
            JeiExportMod.LOGGER.error("Could not import the recipe tree", error);
            status = "Import failed: " + error.getMessage();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (insideTreeViewport(mouseX, mouseY) && delta != 0) {
            Optional<TreeNode> hoveredNode = treeNodes.stream()
                    .filter(node -> node.contains(mouseX, mouseY))
                    .findFirst();
            if (hoveredNode.isPresent()
                    && hoveredNode.get().node.cycleIngredientOption(delta > 0 ? 1 : -1)) {
                treeViewInitialized = false;
                status = "";
                return true;
            }
            double modelX = toTreeX(mouseX);
            double modelY = toTreeY(mouseY);
            float factor = delta > 0 ? 1.15f : (1.0f / 1.15f);
            treeZoom = Mth.clamp(treeZoom * factor, 0.35f, 2.5f);
            treePanX = mouseX - treeViewportLeft - modelX * treeZoom;
            treePanY = mouseY - treeViewportTop - modelY * treeZoom;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void savePlan() {
        RecipePage<?> page = currentPage().orElse(null);
        if (page == null) return;
        try {
            long amount = parseLong(requestedAmount);
            if (amount <= 0) throw new IllegalArgumentException("amount");
            progress.savePlan(target, new RecipeTreeProgress.SavedPlan(amount, page.key));
            status = "Plan saved locally";
        } catch (IllegalArgumentException error) {
            status = "Enter a positive output amount";
        }
    }

    private void restorePlan() {
        RecipeTreeProgress.SavedPlan saved = progress.plan(target);
        if (saved == null) return;
        requestedAmount = Long.toString(saved.amount());
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).key.equals(saved.recipeKey())) {
                pageIndex = index;
                break;
            }
        }
    }

    private boolean selectRecipe(String recipeKey) {
        if (recipeKey == null) return false;
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).key.equals(recipeKey)) {
                pageIndex = index;
                return true;
            }
        }
        return false;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(error);
        }
    }

    private void updateButtons() {
        if (previousButton == null) return;
        previousButton.active = history.canMove(-1);
        nextButton.active = history.canMove(1);
        modeButton.setMessage(Component.literal(compactMode ? "Details" : "Compact"));
    }

    private Optional<RecipePage<?>> currentPage() {
        if (pages.isEmpty()) return Optional.empty();
        pageIndex = Mth.clamp(pageIndex, 0, pages.size() - 1);
        return Optional.of(pages.get(pageIndex));
    }

    private void collectPages() {
        pages.addAll(collectPagesFor(target, RecipeIngredientRole.OUTPUT));
    }

    private List<RecipePage<?>> collectPagesFor(ItemStack stack, RecipeIngredientRole role) {
        IFocus<ItemStack> focus = runtime.getJeiHelpers().getFocusFactory().createFocus(
                role, VanillaTypes.ITEM_STACK, stack.copyWithCount(1));
        IFocusGroup focusGroup = runtime.getJeiHelpers().getFocusFactory().createFocusGroup(List.of(focus));
        List<RecipePage<?>> found = new ArrayList<>();
        runtime.getRecipeManager().createRecipeCategoryLookup()
                .limitFocus(List.of(focus))
                .get()
                .filter(category -> !RecipeExporter.isMetaCategory(category.getRecipeType().getUid()))
                .forEach(category -> collectCategoryPages(found, category, focus, focusGroup));
        return found;
    }

    private <T> void collectCategoryPages(
            List<RecipePage<?>> found,
            IRecipeCategory<T> category,
            IFocus<ItemStack> focus,
            IFocusGroup focusGroup) {
        if (found.size() >= MAX_RECIPE_PAGES) return;
        runtime.getRecipeManager().createRecipeLookup(category.getRecipeType())
                .limitFocus(List.of(focus))
                .get()
                .limit(MAX_RECIPE_PAGES - found.size())
                .forEach(recipe -> runtime.getRecipeManager()
                        .createRecipeLayoutDrawable(category, recipe, focusGroup)
                        .ifPresent(layout -> found.add(new RecipePage<>(
                                category,
                                recipe,
                                layout,
                                recipeKey(category, recipe)))));
    }

    private static List<ItemStack> displayedOutputs(RecipePage<?> page) {
        List<ItemStack> outputs = new ArrayList<>();
        page.layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .map(IRecipeSlotView::getDisplayedItemStack)
                .flatMap(Optional::stream)
                .filter(stack -> !stack.isEmpty())
                .forEach(stack -> {
                    boolean duplicate = outputs.stream()
                            .anyMatch(existing -> ItemStack.isSameItemSameTags(existing, stack));
                    if (!duplicate) outputs.add(stack.copyWithCount(1));
                });
        return outputs;
    }

    private ITypedIngredient<?> typedItem(ItemStack stack) {
        return runtime.getIngredientManager()
                .createTypedIngredient(VanillaTypes.ITEM_STACK, stack.copyWithCount(1))
                .orElseThrow(() -> new IllegalArgumentException(
                        "JEI could not create an item ingredient for " + stack));
    }

    private ItemStack ingredientItemStack(ITypedIngredient<?> ingredient) {
        return ingredient.getItemStack()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .orElse(ItemStack.EMPTY);
    }

    private String ingredientKey(ITypedIngredient<?> ingredient) {
        return ingredientKeyTyped(ingredient);
    }

    private <T> String ingredientKeyTyped(ITypedIngredient<T> ingredient) {
        IIngredientHelper<T> helper = runtime.getIngredientManager()
                .getIngredientHelper(ingredient.getType());
        String uniqueId = helper.getUniqueId(ingredient.getIngredient(), UidContext.Ingredient);
        return IngredientKeys.typePrefix(ingredient.getType()) + "|" + uniqueId;
    }

    private boolean sameIngredient(ITypedIngredient<?> first, ITypedIngredient<?> second) {
        if (first.getType() != second.getType()) return false;
        try {
            return ingredientKey(first).equals(ingredientKey(second));
        } catch (RuntimeException error) {
            return first.getIngredient().equals(second.getIngredient());
        }
    }

    private long ingredientAmount(ITypedIngredient<?> ingredient) {
        return ingredientAmountTyped(ingredient);
    }

    private <T> long ingredientAmountTyped(ITypedIngredient<T> ingredient) {
        try {
            IIngredientHelper<T> helper = runtime.getIngredientManager()
                    .getIngredientHelper(ingredient.getType());
            long helperAmount = helper.getAmount(ingredient.getIngredient());
            if (helperAmount > 0) return helperAmount;
        } catch (RuntimeException error) {
            // Optional custom ingredient helpers may leave JEI's default -1 amount in place.
        }
        Object value = ingredient.getIngredient();
        for (String methodName : List.of("getAmount", "getCount")) {
            try {
                Object reflected = value.getClass().getMethod(methodName).invoke(value);
                if (reflected instanceof Number number && number.longValue() > 0) {
                    return number.longValue();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next conventional amount accessor.
            }
        }
        return 1;
    }

    private String ingredientDisplayName(ITypedIngredient<?> ingredient) {
        return ingredientDisplayNameTyped(ingredient);
    }

    private <T> String ingredientDisplayNameTyped(ITypedIngredient<T> ingredient) {
        try {
            return runtime.getIngredientManager().getIngredientHelper(ingredient.getType())
                    .getDisplayName(ingredient.getIngredient());
        } catch (RuntimeException error) {
            return ingredient.getIngredient().toString();
        }
    }

    private void renderIngredient(
            GuiGraphics graphics,
            ITypedIngredient<?> ingredient,
            int left,
            int top,
            int size) {
        renderIngredientTyped(graphics, ingredient, left, top, size);
    }

    private <T> void renderIngredientTyped(
            GuiGraphics graphics,
            ITypedIngredient<T> ingredient,
            int left,
            int top,
            int size) {
        Optional<ItemStack> itemStack = ingredient.getItemStack().filter(stack -> !stack.isEmpty());
        if (itemStack.isPresent()) {
            graphics.renderItem(itemStack.get(), left + (size - 16) / 2, top + (size - 16) / 2);
            return;
        }
        IIngredientRenderer<T> renderer = runtime.getIngredientManager()
                .getIngredientRenderer(ingredient.getType());
        int rendererWidth = Math.max(1, renderer.getWidth());
        int rendererHeight = Math.max(1, renderer.getHeight());
        float scale = Math.min(1.0f, Math.min(
                (float) size / rendererWidth,
                (float) size / rendererHeight));
        float renderLeft = left + (size - rendererWidth * scale) / 2.0f;
        float renderTop = top + (size - rendererHeight * scale) / 2.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(renderLeft, renderTop, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        renderer.render(graphics, ingredient.getIngredient());
        graphics.pose().popPose();
    }

    private List<Component> ingredientTooltip(ITypedIngredient<?> ingredient) {
        return ingredientTooltipTyped(ingredient);
    }

    private <T> List<Component> ingredientTooltipTyped(ITypedIngredient<T> ingredient) {
        try {
            return runtime.getIngredientManager().getIngredientRenderer(ingredient.getType())
                    .getTooltip(ingredient.getIngredient(), TooltipFlag.NORMAL);
        } catch (RuntimeException error) {
            return List.of(Component.literal(ingredientDisplayNameTyped(ingredient)));
        }
    }

    private static <T> String recipeKey(IRecipeCategory<T> category, T recipe) {
        var registryName = category.getRegistryName(recipe);
        return category.getRecipeType().getUid() + "|" + (registryName == null ? recipe.toString() : registryName);
    }

    private static boolean contains(int left, int top, int size, double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + size && mouseY >= top && mouseY < top + size;
    }

    private void attachPreviousRoot(PlanNode previousRoot) {
        List<PlanNode> children = new ArrayList<>(rootNode.children);
        for (int index = 0; index < children.size(); index++) {
            PlanNode expectedInput = children.get(index);
            if (!ItemStack.isSameItemSameTags(expectedInput.stack, previousRoot.stack)) continue;
            children.set(index, copyPlan(previousRoot, rootNode, expectedInput.quantity,
                    expectedInput.quantityPerParentCraft));
            rootNode.children = List.copyOf(children);
            treeViewInitialized = false;
            return;
        }
    }

    private PlanNode copyPlan(
            PlanNode source,
            PlanNode parent,
            long quantity,
            long quantityPerParentCraft) {
        PlanNode copy = new PlanNode(
                source.ingredient, quantity, parent, quantityPerParentCraft, source.ingredientOptions);
        copy.recipe = source.recipe;
        copy.children = source.children.stream()
                .map(child -> copyPlan(child, copy, child.quantity, child.quantityPerParentCraft))
                .toList();
        copy.updateQuantity(quantity);
        return copy;
    }

    private final class RecipePickerScreen extends Screen {
        private static final int CARD_GAP = 6;
        private static final int GROUP_GAP = 8;
        private static final int GROUP_HEADER_HEIGHT = 16;
        private static final int SCROLL_STEP = 36;

        private final PickerKind kind;
        private final ItemStack selectedItem;
        private final PlanNode selectedNode;
        private final List<RecipeChoice> choices;
        private final List<PickerGroup> groups;
        private List<ChoiceHitbox> hitboxes = List.of();
        private List<GroupHeaderHitbox> groupHeaderHitboxes = List.of();
        private int pickerLeft;
        private int pickerTop;
        private int pickerWidth;
        private int pickerHeight;
        private int recipesLeft;
        private int recipesTop;
        private int recipesRight;
        private int recipesBottom;
        private int contentHeight;
        private double scrollOffset;
        private List<PickerPlacement> placements = List.of();
        private List<PickerGroupHeader> groupHeaders = List.of();
        private Button noRecipeButton;

        private RecipePickerScreen(
                PickerKind kind,
                ItemStack selectedItem,
                PlanNode selectedNode,
                List<RecipeChoice> choices) {
            super(Component.literal(kind == PickerKind.INPUT_RECIPE
                    ? "Choose input recipe"
                    : "Choose output"));
            this.kind = kind;
            this.selectedItem = selectedItem;
            this.selectedNode = selectedNode;
            List<RecipeChoice> orderedChoices = new ArrayList<>(choices);
            orderedChoices.sort((left, right) -> Boolean.compare(isFavorite(right), isFavorite(left)));
            this.choices = List.copyOf(orderedChoices);
            this.groups = groupChoices(this.choices);
        }

        private List<PickerGroup> groupChoices(List<RecipeChoice> orderedChoices) {
            Map<String, List<RecipeChoice>> grouped = new LinkedHashMap<>();
            Map<String, Component> titles = new LinkedHashMap<>();
            for (RecipeChoice choice : orderedChoices) {
                String key = choice.page.category.getRecipeType().getUid().toString();
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(choice);
                titles.putIfAbsent(key, choice.page.category.getTitle());
            }
            return grouped.entrySet().stream()
                    .map(entry -> new PickerGroup(
                            entry.getKey(),
                            titles.get(entry.getKey()),
                            List.copyOf(entry.getValue())))
                    .toList();
        }

        @Override
        protected void init() {
            pickerWidth = Math.min(720, Math.max(1, width - 4));
            pickerHeight = Math.min(400, Math.max(1, height - 4));
            pickerLeft = (width - pickerWidth) / 2;
            pickerTop = (height - pickerHeight) / 2;
            recipesLeft = pickerLeft + 8;
            recipesTop = pickerTop + 34;
            recipesRight = pickerLeft + pickerWidth - 12;
            recipesBottom = pickerTop + pickerHeight - 30;
            layoutChoices();
            scrollOffset = Mth.clamp(scrollOffset, 0, maximumScroll());
            int bottom = pickerTop + pickerHeight - 26;
            addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                    .bounds(pickerLeft + pickerWidth - 68, bottom, 58, 20).build());
            if (kind == PickerKind.INPUT_RECIPE) {
                noRecipeButton = addRenderableWidget(Button.builder(
                                Component.literal("No recipe"), button -> clearRecipeSelection())
                        .bounds(pickerLeft + pickerWidth - 86, pickerTop + 7, 76, 20).build());
            }
        }

        private void layoutChoices() {
            int availableWidth = Math.max(1, recipesRight - recipesLeft);
            List<PickerPlacement> laidOut = new ArrayList<>();
            List<PickerGroupHeader> laidOutHeaders = new ArrayList<>();
            int rowTop = 0;
            for (PickerGroup group : groups) {
                laidOutHeaders.add(new PickerGroupHeader(group, rowTop, GROUP_HEADER_HEIGHT));
                rowTop += GROUP_HEADER_HEIGHT + CARD_GAP;
                if (!progress.isRecipeTypeCollapsed(group.key)) {
                    rowTop = layoutPickerGroup(laidOut, group, rowTop, availableWidth);
                }
                rowTop += GROUP_GAP;
            }
            placements = List.copyOf(laidOut);
            groupHeaders = List.copyOf(laidOutHeaders);
            contentHeight = Math.max(0, rowTop - GROUP_GAP);
        }

        private int layoutPickerGroup(
                List<PickerPlacement> laidOut,
                PickerGroup group,
                int rowTop,
                int availableWidth) {
            List<PickerCard> row = new ArrayList<>();
            int rowWidth = 0;
            for (RecipeChoice choice : group.choices) {
                choice.page.layout.setPosition(0, 0);
                var recipeRect = choice.page.layout.getRect();
                var borderRect = choice.page.layout.getRectWithBorder();
                PickerCard card = new PickerCard(
                        choice,
                        borderRect.getWidth(),
                        borderRect.getHeight(),
                        borderRect.getX() - recipeRect.getX(),
                        borderRect.getY() - recipeRect.getY());
                int nextWidth = row.isEmpty() ? card.width : rowWidth + CARD_GAP + card.width;
                if (!row.isEmpty() && nextWidth > availableWidth) {
                    rowTop = appendPickerRow(laidOut, row, rowWidth, rowTop, availableWidth);
                    row = new ArrayList<>();
                    rowWidth = 0;
                }
                if (!row.isEmpty()) rowWidth += CARD_GAP;
                row.add(card);
                rowWidth += card.width;
            }
            if (!row.isEmpty()) rowTop = appendPickerRow(laidOut, row, rowWidth, rowTop, availableWidth);
            return rowTop;
        }

        private int appendPickerRow(
                List<PickerPlacement> result,
                List<PickerCard> row,
                int rowWidth,
                int rowTop,
                int availableWidth) {
            int rowHeight = row.stream().mapToInt(card -> card.height).max().orElse(1);
            int left = Math.max(0, (availableWidth - rowWidth) / 2);
            for (PickerCard card : row) {
                result.add(new PickerPlacement(
                        card.choice,
                        left,
                        rowTop + (rowHeight - card.height) / 2,
                        card.width,
                        card.height,
                        card.borderOffsetX,
                        card.borderOffsetY));
                left += card.width + CARD_GAP;
            }
            return rowTop + rowHeight + CARD_GAP;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            graphics.fill(pickerLeft, pickerTop, pickerLeft + pickerWidth, pickerTop + pickerHeight, 0xf0181a1b);
            graphics.fill(pickerLeft, pickerTop, pickerLeft + pickerWidth, pickerTop + 2, 0xff69a847);
            graphics.renderItem(selectedItem, pickerLeft + 10, pickerTop + 9);
            String prompt = kind == PickerKind.INPUT_RECIPE ? "Input recipe for " : "Output using ";
            String choiceCount = choices.size() + (choices.size() == 1 ? " choice" : " choices");
            int countRight = noRecipeButton == null
                    ? pickerLeft + pickerWidth - 10
                    : noRecipeButton.getX() - 8;
            int countLeft = countRight - font.width(choiceCount);
            int titleLeft = pickerLeft + 32;
            String title = font.plainSubstrByWidth(
                    prompt + selectedItem.getHoverName().getString(),
                    Math.max(1, countLeft - titleLeft - 8));
            graphics.drawString(font, title, pickerLeft + 32, pickerTop + 13, 0xffffffff, false);
            graphics.drawString(font, choiceCount, countLeft, pickerTop + 13, 0xffaeb7aa, false);

            List<ChoiceHitbox> rendered = new ArrayList<>();
            List<GroupHeaderHitbox> renderedHeaders = new ArrayList<>();
            Optional<List<Component>> hoveredTooltip = Optional.empty();
            graphics.enableScissor(recipesLeft, recipesTop, recipesRight, recipesBottom);
            for (PickerPlacement placement : placements) {
                int cardTop = recipesTop + placement.top - (int) Math.round(scrollOffset);
                if (cardTop + placement.height <= recipesTop || cardTop >= recipesBottom) continue;
                RecipeChoice choice = placement.choice;
                int cardLeft = recipesLeft + placement.left;
                int recipeX = cardLeft - placement.borderOffsetX;
                int recipeY = cardTop - placement.borderOffsetY;
                choice.page.layout.setPosition(recipeX, recipeY);
                var recipeRect = choice.page.layout.getRectWithBorder();
                boolean hovered = insideRecipesViewport(mouseX, mouseY)
                        && mouseX >= recipeRect.getX()
                        && mouseX < recipeRect.getX() + recipeRect.getWidth()
                        && mouseY >= recipeRect.getY()
                        && mouseY < recipeRect.getY() + recipeRect.getHeight();
                if (hovered) {
                    graphics.fill(recipeRect.getX() - 2, recipeRect.getY() - 2,
                            recipeRect.getX() + recipeRect.getWidth() + 2,
                            recipeRect.getY() + recipeRect.getHeight() + 2, 0xff69a847);
                }
                choice.page.layout.drawRecipe(graphics, mouseX, mouseY);
                choice.page.layout.drawOverlays(graphics, mouseX, mouseY);
                if (isFavorite(choice)) {
                    graphics.drawString(font, "★", recipeRect.getX() + 2, recipeRect.getY() + 2,
                            0xffffd866, true);
                }
                rendered.add(new ChoiceHitbox(choice,
                        recipeRect.getX(), recipeRect.getY(), recipeRect.getWidth(), recipeRect.getHeight()));
                if (hovered) {
                    hoveredTooltip = choice.page.layout.getRecipeSlotUnderMouse(mouseX, mouseY)
                            .map(slot -> slot.getTooltip());
                }
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            for (PickerGroupHeader header : groupHeaders) {
                int headerTop = recipesTop + header.top - (int) Math.round(scrollOffset);
                if (headerTop + header.height <= recipesTop || headerTop >= recipesBottom) continue;
                boolean hovered = insideRecipesViewport(mouseX, mouseY)
                        && mouseY >= headerTop && mouseY < headerTop + header.height;
                int color = hovered ? 0xff3c4b38 : 0xff293029;
                graphics.fill(recipesLeft, headerTop, recipesRight, headerTop + header.height, color);
                graphics.fill(recipesLeft, headerTop, recipesRight, headerTop + 1,
                        hovered ? 0xff9fcf7f : 0xff52624d);
                boolean collapsed = progress.isRecipeTypeCollapsed(header.group.key);
                graphics.drawString(font, collapsed ? ">" : "v",
                        recipesLeft + 4, headerTop + 4, 0xffd7e6ce, false);
                String count = Integer.toString(header.group.choices.size());
                String groupTitle = font.plainSubstrByWidth(
                        header.group.title.getString(),
                        Math.max(1, recipesRight - recipesLeft - font.width(count) - 26));
                graphics.drawString(font, groupTitle, recipesLeft + 14, headerTop + 4, 0xffffffff, false);
                graphics.drawString(font, count, recipesRight - font.width(count) - 4,
                        headerTop + 4, 0xffaeb7aa, false);
                renderedHeaders.add(new GroupHeaderHitbox(
                        header.group, recipesLeft, headerTop,
                        Math.max(1, recipesRight - recipesLeft), header.height));
            }
            graphics.pose().popPose();
            graphics.disableScissor();
            hitboxes = List.copyOf(rendered);
            groupHeaderHitboxes = List.copyOf(renderedHeaders);

            String scrollStatus = maximumScroll() > 0 ? "Scroll to browse all recipes" : "All recipes shown";
            graphics.drawString(font, scrollStatus, pickerLeft + 10,
                    pickerTop + pickerHeight - 20, 0xffaeb7aa, false);
            renderScrollBar(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            hoveredTooltip.ifPresent(tooltip ->
                    graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY));
        }

        @Override
        public void tick() {
            super.tick();
            placements.stream()
                    .filter(this::isVisible)
                    .forEach(placement -> placement.choice.page.layout.tick());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
            if (button != 0 || !insideRecipesViewport(mouseX, mouseY)) return false;
            Optional<GroupHeaderHitbox> selectedHeader = groupHeaderHitboxes.stream()
                    .filter(hitbox -> hitbox.contains(mouseX, mouseY))
                    .findFirst();
            if (selectedHeader.isPresent()) {
                toggleRecipeGroup(selectedHeader.get().group);
                return true;
            }
            Optional<ChoiceHitbox> selected = hitboxes.stream()
                    .filter(hitbox -> hitbox.contains(mouseX, mouseY))
                    .findFirst();
            selected.ifPresent(hitbox -> applyChoice(hitbox.choice));
            return selected.isPresent();
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (delta == 0) return false;
            scrollOffset = Mth.clamp(scrollOffset - delta * SCROLL_STEP, 0, maximumScroll());
            hitboxes = List.of();
            groupHeaderHitboxes = List.of();
            return true;
        }

        private void toggleRecipeGroup(PickerGroup group) {
            boolean collapsed = !progress.isRecipeTypeCollapsed(group.key);
            progress.setRecipeTypeCollapsed(group.key, collapsed);
            layoutChoices();
            scrollOffset = Mth.clamp(scrollOffset, 0, maximumScroll());
            hitboxes = List.of();
            groupHeaderHitboxes = List.of();
        }

        private boolean isVisible(PickerPlacement placement) {
            double cardTop = recipesTop + placement.top - scrollOffset;
            return cardTop + placement.height > recipesTop && cardTop < recipesBottom;
        }

        private boolean insideRecipesViewport(double mouseX, double mouseY) {
            return mouseX >= recipesLeft && mouseX < recipesRight
                    && mouseY >= recipesTop && mouseY < recipesBottom;
        }

        private double maximumScroll() {
            return Math.max(0, contentHeight - Math.max(1, recipesBottom - recipesTop));
        }

        private void renderScrollBar(GuiGraphics graphics) {
            double maximum = maximumScroll();
            if (maximum <= 0) return;
            int trackTop = recipesTop;
            int trackHeight = Math.max(1, recipesBottom - recipesTop);
            int trackLeft = pickerLeft + pickerWidth - 8;
            graphics.fill(trackLeft, trackTop, trackLeft + 2, recipesBottom, 0xff394139);
            int thumbHeight = Math.max(12, (int) Math.round(
                    (double) trackHeight * trackHeight / Math.max(trackHeight, contentHeight)));
            int travel = Math.max(0, trackHeight - thumbHeight);
            int thumbTop = trackTop + (int) Math.round(travel * scrollOffset / maximum);
            graphics.fill(trackLeft, thumbTop, trackLeft + 2, thumbTop + thumbHeight, 0xff9fcf7f);
        }

        private void applyChoice(RecipeChoice choice) {
            if (kind == PickerKind.INPUT_RECIPE) {
                progress.saveFavoriteRecipe(selectedNode.stack, choice.page.key);
                favoriteExpansionAttemptsRemaining = MAX_AUTOMATIC_FAVORITE_EXPANSIONS;
                selectedNode.setRecipe(choice.page);
                expandFavoriteIngredients(rootNode);
                if (selectedNode == rootNode) selectRecipe(choice.page.key);
                treeNodes = List.of();
                recipeBoxes = List.of();
                status = "Recipe added to tree";
                minecraft.setScreen(RecipeTreeScreen.this);
                return;
            }
            progress.saveFavoriteRecipe(choice.item, choice.page.key);
            List<ItemStack> nextPath;
            if (ItemStack.isSameItemSameTags(choice.item, target)) {
                nextPath = new ArrayList<>(path);
            } else if (path.size() > 1
                    && ItemStack.isSameItemSameTags(selectedItem, target)
                    && ItemStack.isSameItemSameTags(choice.item, path.get(path.size() - 2))) {
                nextPath = new ArrayList<>(path.subList(0, path.size() - 1));
            } else {
                nextPath = List.of(choice.item.copyWithCount(1));
            }
            RecipeTreeScreen nextScreen = new RecipeTreeScreen(
                    choice.item, runtime, nextPath, compactMode, choice.page.key, history, true);
            if (selectedNode == rootNode) {
                nextScreen.attachPreviousRoot(rootNode);
            }
            minecraft.setScreen(nextScreen);
        }

        private void clearRecipeSelection() {
            if (kind != PickerKind.INPUT_RECIPE) return;
            progress.clearFavoriteRecipe(selectedNode.stack);
            selectedNode.clearRecipe();
            treeNodes = List.of();
            recipeBoxes = List.of();
            treeViewInitialized = false;
            status = "No recipe selected for " + selectedNode.stack.getHoverName().getString();
            minecraft.setScreen(RecipeTreeScreen.this);
        }

        private boolean isFavorite(RecipeChoice choice) {
            String favorite = progress.favoriteRecipe(choice.item);
            return favorite != null && favorite.equals(choice.page.key);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(RecipeTreeScreen.this);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record RecipePage<T>(
            IRecipeCategory<T> category,
            T recipe,
            IRecipeLayoutDrawable<T> layout,
            String key) {
    }

    private record GroupedIngredient(
            ITypedIngredient<?> ingredient,
            long quantity,
            List<ITypedIngredient<?>> options) {
    }

    private record RecipeChoice(ItemStack item, RecipePage<?> page) {
    }

    private record PickerGroup(
            String key,
            Component title,
            List<RecipeChoice> choices) {
    }

    private record PickerGroupHeader(
            PickerGroup group,
            int top,
            int height) {
    }

    private record PickerCard(
            RecipeChoice choice,
            int width,
            int height,
            int borderOffsetX,
            int borderOffsetY) {
    }

    private record PickerPlacement(
            RecipeChoice choice,
            int left,
            int top,
            int width,
            int height,
            int borderOffsetX,
            int borderOffsetY) {
    }

    private enum PickerKind {
        INPUT_RECIPE,
        OUTPUT
    }

    private static final class OutputHistory {
        private static final int MAX_ENTRIES = 32;
        private final List<RecipeTreeScreen> entries = new ArrayList<>();
        private int index = -1;

        private void push(RecipeTreeScreen screen) {
            if (index + 1 < entries.size()) {
                entries.subList(index + 1, entries.size()).clear();
            }
            entries.add(screen);
            if (entries.size() > MAX_ENTRIES) {
                entries.remove(0);
            }
            index = entries.size() - 1;
        }

        private boolean canMove(int delta) {
            int destination = index + delta;
            return destination >= 0 && destination < entries.size();
        }

        private RecipeTreeScreen move(int delta) {
            if (!canMove(delta)) return null;
            index += delta;
            return entries.get(index);
        }
    }

    private final class PlanNode {
        private ITypedIngredient<?> ingredient;
        private ItemStack stack;
        private final PlanNode parent;
        private final long quantityPerParentCraft;
        private final List<ITypedIngredient<?>> ingredientOptions;
        private int ingredientOptionIndex;
        private long quantity;
        private RecipePage<?> recipe;
        private List<PlanNode> children = List.of();

        private PlanNode(
                ItemStack stack,
                long quantity,
                PlanNode parent,
                long quantityPerParentCraft) {
            this(typedItem(stack), quantity, parent, quantityPerParentCraft, List.of());
        }

        private PlanNode(
                ITypedIngredient<?> ingredient,
                long quantity,
                PlanNode parent,
                long quantityPerParentCraft,
                List<ITypedIngredient<?>> ingredientOptions) {
            List<ITypedIngredient<?>> normalizedOptions = new ArrayList<>();
            ingredientOptions.stream()
                    .filter(option -> !ItemCatalog.isEmptyIngredient(option))
                    .forEach(option -> {
                        if (normalizedOptions.stream().noneMatch(existing ->
                                sameIngredient(existing, option))) {
                            normalizedOptions.add(option);
                        }
                    });
            if (normalizedOptions.stream().noneMatch(option ->
                    sameIngredient(option, ingredient))) {
                normalizedOptions.add(0, ingredient);
            }
            this.ingredientOptions = List.copyOf(normalizedOptions);
            this.ingredientOptionIndex = 0;
            for (int index = 0; index < this.ingredientOptions.size(); index++) {
                if (sameIngredient(this.ingredientOptions.get(index), ingredient)) {
                    this.ingredientOptionIndex = index;
                    break;
                }
            }
            this.ingredient = this.ingredientOptions.get(this.ingredientOptionIndex);
            this.stack = ingredientItemStack(this.ingredient);
            this.quantity = Math.max(1, quantity);
            this.parent = parent;
            this.quantityPerParentCraft = Math.max(0, quantityPerParentCraft);
        }

        private void setRecipe(RecipePage<?> recipe) {
            this.recipe = recipe;
            if (depth() >= 12) {
                this.children = List.of();
                return;
            }
            long crafts = RecipeQuantityMath.craftsFor(quantity, outputAmount(recipe, stack));
            this.children = groupedInputs(recipe).stream()
                    .limit(32)
                    .map(input -> new PlanNode(
                            input.ingredient,
                            RecipeQuantityMath.inputTotal(input.quantity, crafts),
                            this,
                            input.quantity,
                            input.options))
                    .toList();
            this.children.forEach(PlanNode::expandFavoriteRecipe);
        }

        private void clearRecipe() {
            recipe = null;
            children = List.of();
        }

        private void expandFavoriteRecipe() {
            if (stack.isEmpty() || recipe != null || depth() >= 12 || repeatsAncestorIngredient()) return;
            String favorite = progress.favoriteRecipe(stack);
            if (favorite == null || favoriteExpansionAttemptsRemaining <= 0) return;
            favoriteExpansionAttemptsRemaining--;
            collectPagesFor(stack, RecipeIngredientRole.OUTPUT).stream()
                    .filter(page -> page.key.equals(favorite))
                    .findFirst()
                    .ifPresent(this::setRecipe);
        }

        private boolean repeatsAncestorIngredient() {
            PlanNode cursor = parent;
            while (cursor != null) {
                if (sameIngredient(ingredient, cursor.ingredient)) return true;
                cursor = cursor.parent;
            }
            return false;
        }

        private void updateQuantity(long quantity) {
            this.quantity = Math.max(1, quantity);
            if (recipe == null) return;
            long crafts = RecipeQuantityMath.craftsFor(this.quantity, outputAmount(recipe, stack));
            children.forEach(child -> child.updateQuantity(
                    RecipeQuantityMath.inputTotal(child.quantityPerParentCraft, crafts)));
        }

        private boolean hasIngredientOptions() {
            return ingredientOptions.size() > 1;
        }

        private boolean cycleIngredientOption(int direction) {
            if (!hasIngredientOptions() || direction == 0) return false;
            ingredientOptionIndex = Math.floorMod(
                    ingredientOptionIndex + Integer.signum(direction), ingredientOptions.size());
            ingredient = ingredientOptions.get(ingredientOptionIndex);
            stack = ingredientItemStack(ingredient);
            recipe = null;
            children = List.of();
            expandFavoriteRecipe();
            return true;
        }

        private int depth() {
            int depth = 0;
            PlanNode cursor = parent;
            while (cursor != null) {
                depth++;
                cursor = cursor.parent;
            }
            return depth;
        }

        private PlanNode childFor(ItemStack selected) {
            return children.stream()
                    .filter(child -> ItemStack.isSameItemSameTags(child.stack, selected)
                            || child.ingredientOptions.stream().anyMatch(option ->
                            option.getItemStack().filter(stack ->
                                    ItemStack.isSameItemSameTags(stack, selected)).isPresent()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record TreeNode(
            PlanNode node,
            int left,
            int top,
            int size) {
        boolean contains(double mouseX, double mouseY) {
            return RecipeTreeScreen.contains(left, top, size, mouseX, mouseY);
        }
    }

    private record TreeLayoutNode(
            PlanNode node,
            int left,
            int top,
            int width,
            int height,
            int parentIndex) {
    }

    private record NodeSize(
            int width,
            int height) {
    }

    private record CompactPreviewBounds(
            int left,
            int top,
            int width,
            int height) {
    }

    private record RecipeBoxHitbox(
            PlanNode node,
            RecipePage<?> page,
            int left,
            int top,
            int width,
            int height,
            double renderOriginX,
            double renderOriginY,
            double renderScale) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
        }

        double recipeMouseX(double mouseX) {
            return (mouseX - renderOriginX) / renderScale;
        }

        double recipeMouseY(double mouseY) {
            return (mouseY - renderOriginY) / renderScale;
        }
    }

    private record ChoiceHitbox(RecipeChoice choice, int left, int top, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
        }
    }

    private record GroupHeaderHitbox(PickerGroup group, int left, int top, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
        }
    }
}
