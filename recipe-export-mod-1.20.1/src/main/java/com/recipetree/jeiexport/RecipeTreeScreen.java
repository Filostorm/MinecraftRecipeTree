package com.recipetree.jeiexport;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Matrix4f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Lightweight, lazy in-game planner. JEI remains the recipe renderer and source of truth. */
public final class RecipeTreeScreen extends Screen {
    private static final Gson SHARE_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SHARE_FORMAT = "minecraft-recipe-tree";
    private static final int SHARE_VERSION = 1;
    private static final int MAX_SHARE_BYTES = 1_048_576;
    private static final int DETAIL_PANEL_WIDTH = 720;
    private static final int DETAIL_PANEL_HEIGHT = 400;
    private static final int PANEL_MARGIN = 8;
    private static final int JEI_RECIPE_BORDER_PADDING = 4;
    private static final int MAX_AUTOMATIC_FAVORITE_EXPANSIONS = 128;
    private static final int TREE_NODE_GAP = 12;
    private static final int TREE_LEVEL_GAP = 24;
    private static final int INSPECTOR_PANEL_WIDTH = 180;
    private static final int SUMMARY_ROW_HEIGHT = 20;

    private final ItemStack target;
    private final IJeiRuntime runtime;
    private final IFocus<ItemStack> targetFocus;
    private final List<ItemStack> path;
    private final OutputHistory history;
    private final List<RecipePage<?>> pages = new ArrayList<>();
    private final RecipeTreeProgress progress = RecipeTreeProgress.get();
    private final Set<String> loggedAmountFallbackTypes = new HashSet<>();
    private final Set<ResourceLocation> loggedFluidRenderFallbacks = new HashSet<>();
    private final Set<String> loggedSummaryKeyFallbackTypes = new HashSet<>();
    private final Set<String> loggedByproductOutputFallbackRecipes = new HashSet<>();
    private final Set<String> loggedByproductLinkLayoutFailures = new HashSet<>();
    private final Set<String> loggedSupplementalInputFailures = new HashSet<>();

    private List<TreeNode> treeNodes = List.of();
    private List<RecipeBoxHitbox> recipeBoxes = List.of();
    private List<RecipePage<?>> visibleRecipePages = List.of();
    private CompactTreeLayout cachedTreeLayout;
    private boolean treeLayoutDirty = true;
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
    private SummaryPanelBounds summaryPanelArea;
    private SummarySectionBounds materialSummaryArea;
    private SummarySectionBounds byproductSummaryArea;
    private List<SummaryRowHitbox> summaryRows = List.of();
    private List<InspectorTabHitbox> inspectorTabs = List.of();
    private InspectorTab inspectorTab = InspectorTab.RECIPE;
    private PlanNode previewNode;
    private PlanSummary planSummary = PlanSummary.empty();
    private boolean planSummaryDirty = true;
    private int materialSummaryScroll;
    private int byproductSummaryScroll;
    private final Map<String, Integer> byproductCenterIndices = new LinkedHashMap<>();
    private int pageIndex;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int treeViewportTopOffset = 60;
    private int amountLabelX;
    private int amountLabelY;
    private boolean compactMode;
    private String requestedAmount = "1";
    private EditBox amountBox;
    private Button previousButton;
    private Button nextButton;
    private Button modeButton;
    private Button useByproductsButton;
    private Button recipeBookButton;
    private boolean centerTreeRequested;
    private boolean useByproducts;
    private boolean recipeBookMode;
    private int favoriteExpansionAttemptsRemaining = MAX_AUTOMATIC_FAVORITE_EXPANSIONS;
    private String status = "";

    public RecipeTreeScreen(ItemStack target, IJeiRuntime runtime) {
        this(target, runtime, List.of(target.copyWithCount(1)), false, null, new OutputHistory(runtime), true);
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
        this.recipeBookMode = progress.recipeBookMode();
        this.targetFocus = runtime.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, this.target);
        collectPages();
        restorePlan();
        if (!selectRecipe(preferredRecipeKey)) {
            selectRecipe(progress.favoriteRecipe(this.target));
        }
        this.rootNode = new PlanNode(this.target, requestedQuantity(), null, 0);
        currentPage()
                .filter(page -> page.layout().isPresent())
                .ifPresent(rootNode::setRecipe);
        this.previewNode = this.rootNode;
        if (addToHistory) history.push(this);
    }

    private RecipeTreeProgress.RecipeHistoryEntry historyEntry() {
        String recipeKey = currentPage().map(page -> page.key).orElse(null);
        return RecipeTreeProgress.historyEntry(
                target,
                recipeKey,
                requestedQuantity(),
                compactMode);
    }

    private void applyHistoryAmount(long amount) {
        requestedAmount = Long.toString(Math.min(
                RecipeQuantityMath.MAX_REQUESTED_AMOUNT,
                Math.max(1, amount)));
        rootNode.updateQuantity(requestedQuantity());
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
        int secondaryWidth = 502;
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

        addRenderableWidget(Button.builder(Component.translatable("button.jeiexport.save_plan"), button -> savePlan())
                .bounds(secondaryLeft + 134, secondaryY, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Center"), button -> centerTree())
                .bounds(secondaryLeft + 210, secondaryY, 58, 20).build());
        useByproductsButton = addRenderableWidget(Button.builder(
                        Component.empty(), button -> toggleByproducts())
                .bounds(secondaryLeft + 272, secondaryY, 118, 20).build());
        recipeBookButton = addRenderableWidget(Button.builder(
                        Component.empty(), button -> toggleRecipeBook())
                .bounds(secondaryLeft + 394, secondaryY, 108, 20).build());

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
        box.setFilter(text -> text.isEmpty()
                || (text.matches("[0-9]{1,3}")
                && Long.parseLong(text) <= RecipeQuantityMath.MAX_REQUESTED_AMOUNT));
        return addRenderableWidget(box);
    }

    private void changeRequestedAmount(String value) {
        requestedAmount = value;
        status = "";
        try {
            long quantity = parseLong(value);
            if (quantity > 0
                    && quantity <= RecipeQuantityMath.MAX_REQUESTED_AMOUNT
                    && rootNode != null) {
                rootNode.updateQuantity(quantity);
            }
        } catch (IllegalArgumentException ignored) {
            // Keep the last valid tree totals while the player edits an empty or invalid value.
        }
    }

    private long requestedQuantity() {
        try {
            return Math.min(
                    RecipeQuantityMath.MAX_REQUESTED_AMOUNT,
                    Math.max(1, parseLong(requestedAmount)));
        } catch (IllegalArgumentException ignored) {
            return 1;
        }
    }

    @Override
    public void tick() {
        super.tick();
        visibleRecipePages.forEach(page -> page.layout().ifPresent(IRecipeLayoutDrawable::tick));
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
        if (isUndiscovered(target)) {
            graphics.drawString(font, "?", panelLeft + 25, panelTop + 7, 0xff8fc1ff, false);
        }
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
        int availableWidth = Math.max(1, contentRight - treeViewportLeft);
        int summaryWidth = Math.min(INSPECTOR_PANEL_WIDTH, Math.max(1, availableWidth / 3));
        treeViewportRight = Math.max(treeViewportLeft + 1, contentRight - summaryWidth - 6);
        summaryPanelArea = new SummaryPanelBounds(
                treeViewportRight + 6,
                treeViewportTop,
                Math.max(1, contentRight - treeViewportRight - 6),
                Math.max(1, treeViewportBottom - treeViewportTop));
        int viewportWidth = Math.max(1, treeViewportRight - treeViewportLeft);
        PlanSummary summary = currentPlanSummary();

        CompactTreeLayout treeLayout = currentTreeLayout();
        int contentWidth = treeLayout.width;
        List<TreeLayoutNode> layoutNodes = treeLayout.nodes;
        if (centerTreeRequested) {
            int viewportHeight = Math.max(1, treeViewportBottom - treeViewportTop);
            treePanX = (viewportWidth - contentWidth * treeZoom) / 2.0;
            treePanY = (viewportHeight - treeLayout.height * treeZoom) / 2.0;
            treeViewInitialized = true;
            centerTreeRequested = false;
        } else if (!treeViewInitialized) {
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
            if (!treeModelBoundsVisible(
                    Math.min(parentX, childX),
                    Math.min(parentContentBottom, parentBottom),
                    Math.abs(parentX - childX) + 1,
                    Math.max(1, childTop - Math.min(parentContentBottom, parentBottom) + 1))) {
                continue;
            }
            if (parentContentBottom < parentBottom) {
                graphics.fill(parentX, parentContentBottom, parentX + 1, parentBottom + 1, 0xff52624d);
            }
            graphics.fill(parentX, parentBottom, parentX + 1, branchY + 1, 0xff52624d);
            graphics.fill(Math.min(parentX, childX), branchY, Math.max(parentX, childX) + 1,
                    branchY + 1, 0xff52624d);
            graphics.fill(childX, branchY, childX + 1, childTop + 1, 0xff52624d);
        }

        renderByproductLinks(graphics, summary.links, treeLayout, contentWidth);

        for (TreeLayoutNode layoutNode : layoutNodes) {
            if (!treeModelBoundsVisible(
                    layoutNode.left, layoutNode.top, layoutNode.width, layoutNode.height)) {
                continue;
            }
            PlanNode node = layoutNode.node;
            if (!compactMode && node.recipe != null) {
                IRecipeLayoutDrawable<?> recipeLayout = node.recipe.requireLayout();
                recipeLayout.setPosition(layoutNode.left, layoutNode.top);
                recipeLayout.drawRecipe(graphics, (int) modelMouseX, (int) modelMouseY);
                var rect = recipeLayout.getRectWithBorder();
                if (isUndiscovered(node)) {
                    graphics.fill(rect.getX(), rect.getY(),
                            rect.getX() + rect.getWidth(), rect.getY() + rect.getHeight(),
                            0x30070d18);
                    graphics.fill(rect.getX(), rect.getY(),
                            rect.getX() + rect.getWidth(), rect.getY() + 1, 0xff6fa8ff);
                    graphics.drawString(font, "?",
                            rect.getX() + rect.getWidth() - 7, rect.getY() + 2,
                            0xff8fc1ff, false);
                }
                long displayedQuantity = node.displayedQuantity();
                if (displayedQuantity > 1) {
                    String count = formatQuantity(node, displayedQuantity);
                    int recipeHeight = recipeLayout.getRect().getHeight();
                    int countLeft = layoutNode.left + (layoutNode.width - font.width(count)) / 2;
                    graphics.fill(countLeft - 2, layoutNode.top + recipeHeight + 1,
                            countLeft + font.width(count) + 2, layoutNode.top + recipeHeight + 12,
                            0xe0181a1b);
                    graphics.drawCenteredString(font, count,
                            layoutNode.left + layoutNode.width / 2,
                            layoutNode.top + recipeHeight + 2, 0xffffffff);
                }
                boxes.add(canvasRecipeHitbox(
                        node,
                        node.recipe,
                        rect,
                        layoutNode.left,
                        layoutNode.top));
            } else {
                int size = layoutNode.width;
                renderNode(graphics, node, layoutNode.left, layoutNode.top, size,
                        node.displayedQuantity(), (int) modelMouseX, (int) modelMouseY);
                nodes.add(canvasTreeHitbox(node, layoutNode.left, layoutNode.top, size));
            }
        }
        graphics.pose().popPose();
        graphics.disableScissor();

        Optional<TreeNode> hoveredNode = insideTreeViewport(mouseX, mouseY)
                ? nodes.stream().filter(node -> node.contains(mouseX, mouseY)).findFirst()
                : Optional.empty();
        PlanNode hoveredPlanNode = hoveredNode.map(node -> node.node).orElseGet(() -> boxes.stream()
                .filter(box -> box.contains(mouseX, mouseY))
                .map(box -> box.node)
                .findFirst()
                .orElse(null));
        if (hoveredPlanNode != null) {
            previewNode = hoveredPlanNode;
        }
        renderInspectorPanel(graphics, summary, mouseX, mouseY, boxes);

        treeNodes = List.copyOf(nodes);
        recipeBoxes = List.copyOf(boxes);
        List<RecipePage<?>> tickingPages = new ArrayList<>();
        for (RecipeBoxHitbox box : boxes) {
            if (!tickingPages.contains(box.page)) tickingPages.add(box.page);
        }
        visibleRecipePages = List.copyOf(tickingPages);
        renderTreeTooltip(graphics, nodes, mouseX, mouseY);
        boxes.stream()
                .filter(box -> box.contains(mouseX, mouseY))
                .findFirst()
                .flatMap(box -> box.recipeSlotUnderMouse(mouseX, mouseY))
                .ifPresent(slot -> graphics.renderComponentTooltip(font, slot.getTooltip(), mouseX, mouseY));
        renderSummaryTooltip(graphics, mouseX, mouseY);
    }

    private CompactTreeLayout currentTreeLayout() {
        if (treeLayoutDirty || cachedTreeLayout == null) {
            cachedTreeLayout = compactTreeLayout(rootNode);
            treeLayoutDirty = false;
        }
        return cachedTreeLayout;
    }

    private void invalidateTreeLayout() {
        treeLayoutDirty = true;
    }

    private CompactTreeLayout compactTreeLayout(PlanNode root) {
        LayoutDraft draft = compactLayoutDraft(root);
        List<Integer> depthHeights = new ArrayList<>();
        collectDepthHeights(draft, 0, depthHeights);
        List<Integer> depthTops = new ArrayList<>(depthHeights.size());
        int nextTop = 0;
        for (int height : depthHeights) {
            depthTops.add(nextTop);
            nextTop += height + TREE_LEVEL_GAP;
        }

        List<RawTreeLayoutNode> rawNodes = new ArrayList<>();
        flattenLayoutDraft(draft, 0.0, 0, -1, depthTops, rawNodes);
        double minimumLeft = rawNodes.stream().mapToDouble(node -> node.left).min().orElse(0.0);
        double maximumRight = rawNodes.stream()
                .mapToDouble(node -> node.left + node.width)
                .max()
                .orElse(1.0);
        int contentWidth = Math.max(1, (int) Math.ceil(maximumRight - minimumLeft));
        List<TreeLayoutNode> nodes = rawNodes.stream()
                .map(node -> new TreeLayoutNode(
                        node.node,
                        (int) Math.round(node.left - minimumLeft),
                        node.top,
                        node.width,
                        node.height,
                        node.parentIndex))
                .toList();
        Map<PlanNode, TreeLayoutNode> nodesByPlan = new LinkedHashMap<>();
        nodes.forEach(node -> nodesByPlan.put(node.node, node));
        int contentHeight = nodes.stream()
                .mapToInt(node -> node.top + node.height)
                .max()
                .orElse(1);
        return new CompactTreeLayout(nodes, Map.copyOf(nodesByPlan), contentWidth, contentHeight);
    }

    private boolean treeModelBoundsVisible(int left, int top, int width, int height) {
        double visibleLeft = -treePanX / treeZoom;
        double visibleTop = -treePanY / treeZoom;
        double visibleRight = visibleLeft + (treeViewportRight - treeViewportLeft) / treeZoom;
        double visibleBottom = visibleTop + (treeViewportBottom - treeViewportTop) / treeZoom;
        return left + width >= visibleLeft - 2.0
                && left <= visibleRight + 2.0
                && top + height >= visibleTop - 2.0
                && top <= visibleBottom + 2.0;
    }

    private LayoutDraft compactLayoutDraft(PlanNode node) {
        NodeSize size = nodeSize(node);
        List<LayoutDraft> children = node.children.stream()
                .map(this::compactLayoutDraft)
                .toList();
        if (children.isEmpty()) {
            return new LayoutDraft(
                    node,
                    size,
                    children,
                    List.of(-size.width / 2.0),
                    List.of(size.width / 2.0));
        }

        List<Double> combinedMinimums = new ArrayList<>();
        List<Double> combinedMaximums = new ArrayList<>();
        for (LayoutDraft child : children) {
            double offset = 0.0;
            if (!combinedMinimums.isEmpty()) {
                int sharedDepths = Math.min(combinedMaximums.size(), child.minimumContour.size());
                for (int depth = 0; depth < sharedDepths; depth++) {
                    offset = Math.max(
                            offset,
                            combinedMaximums.get(depth) + TREE_NODE_GAP
                                    - child.minimumContour.get(depth));
                }
            }
            child.offsetX = offset;
            mergeContour(combinedMinimums, combinedMaximums, child, offset);
        }

        double childrenCenter = (children.get(0).offsetX
                + children.get(children.size() - 1).offsetX) / 2.0;
        children.forEach(child -> child.offsetX -= childrenCenter);
        for (int depth = 0; depth < combinedMinimums.size(); depth++) {
            combinedMinimums.set(depth, combinedMinimums.get(depth) - childrenCenter);
            combinedMaximums.set(depth, combinedMaximums.get(depth) - childrenCenter);
        }

        List<Double> minimumContour = new ArrayList<>();
        List<Double> maximumContour = new ArrayList<>();
        minimumContour.add(-size.width / 2.0);
        maximumContour.add(size.width / 2.0);
        minimumContour.addAll(combinedMinimums);
        maximumContour.addAll(combinedMaximums);
        return new LayoutDraft(node, size, children, minimumContour, maximumContour);
    }

    private static void mergeContour(
            List<Double> minimums,
            List<Double> maximums,
            LayoutDraft child,
            double offset) {
        for (int depth = 0; depth < child.minimumContour.size(); depth++) {
            double minimum = child.minimumContour.get(depth) + offset;
            double maximum = child.maximumContour.get(depth) + offset;
            if (depth >= minimums.size()) {
                minimums.add(minimum);
                maximums.add(maximum);
            } else {
                minimums.set(depth, Math.min(minimums.get(depth), minimum));
                maximums.set(depth, Math.max(maximums.get(depth), maximum));
            }
        }
    }

    private static void collectDepthHeights(
            LayoutDraft draft,
            int depth,
            List<Integer> heights) {
        while (heights.size() <= depth) heights.add(0);
        heights.set(depth, Math.max(heights.get(depth), draft.size.height));
        draft.children.forEach(child -> collectDepthHeights(child, depth + 1, heights));
    }

    private static void flattenLayoutDraft(
            LayoutDraft draft,
            double centerX,
            int depth,
            int parentIndex,
            List<Integer> depthTops,
            List<RawTreeLayoutNode> result) {
        int nodeIndex = result.size();
        result.add(new RawTreeLayoutNode(
                draft.node,
                centerX - draft.size.width / 2.0,
                depthTops.get(depth),
                draft.size.width,
                draft.size.height,
                parentIndex));
        for (LayoutDraft child : draft.children) {
            flattenLayoutDraft(
                    child,
                    centerX + child.offsetX,
                    depth + 1,
                    nodeIndex,
                    depthTops,
                    result);
        }
    }

    private void renderByproductLinks(
            GuiGraphics graphics,
            List<ByproductLink> links,
            CompactTreeLayout treeLayout,
            int contentWidth) {
        if (links.isEmpty()) return;
        Set<ByproductEdgeKey> renderedEdges = new HashSet<>();
        List<CurveEndpoints> curves = new ArrayList<>();
        for (ByproductLink link : links) {
            ByproductEdgeKey edge = new ByproductEdgeKey(link.source, link.target);
            if (!renderedEdges.add(edge)) continue;
            TreeLayoutNode source = treeLayout.nodesByPlan.get(link.source);
            TreeLayoutNode targetNode = treeLayout.nodesByPlan.get(link.target);
            if (source == null || targetNode == null) {
                String failureKey = System.identityHashCode(link.source)
                        + "->" + System.identityHashCode(link.target);
                if (loggedByproductLinkLayoutFailures.add(failureKey)) {
                    JeiExportMod.LOGGER.warn(
                            "Could not draw byproduct link from {} to {} because a tree layout node is missing",
                            ingredientDisplayName(link.source.ingredient),
                            ingredientDisplayName(link.target.ingredient));
                }
                continue;
            }
            double startX = source.left + source.width / 2.0;
            double startY = source.top + source.height / 2.0;
            double endX = targetNode.left + targetNode.width / 2.0;
            double endY = targetNode.top + targetNode.height / 2.0;
            int curveLeft = (int) Math.floor(Math.min(startX, endX) - 72.0);
            int curveTop = (int) Math.floor(Math.min(startY, endY));
            int curveWidth = (int) Math.ceil(Math.abs(endX - startX) + 145.0);
            int curveHeight = Math.max(1, (int) Math.ceil(Math.abs(endY - startY) + 1.0));
            if (!treeModelBoundsVisible(curveLeft, curveTop, curveWidth, curveHeight)) continue;
            curves.add(new CurveEndpoints(startX, startY, endX, endY));
        }
        if (curves.isEmpty()) return;

        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int alpha = useByproducts ? 255 : 190;
        for (CurveEndpoints curve : curves) {
            double midpointX = (curve.startX + curve.endX) / 2.0;
            double midpointY = (curve.startY + curve.endY) / 2.0;
            double distance = Math.hypot(curve.endX - curve.startX, curve.endY - curve.startY);
            double direction = midpointX < contentWidth / 2.0 ? 1.0 : -1.0;
            double controlX = midpointX + direction * Mth.clamp(distance / 3.0, 24.0, 72.0);
            double controlY = midpointY;
            int steps = Mth.clamp((int) Math.ceil(distance / 8.0), 16, 48);
            double previousX = curve.startX;
            double previousY = curve.startY;
            for (int step = 1; step <= steps; step++) {
                double t = (double) step / steps;
                double inverse = 1.0 - t;
                double nextX = inverse * inverse * curve.startX
                        + 2.0 * inverse * t * controlX
                        + t * t * curve.endX;
                double nextY = inverse * inverse * curve.startY
                        + 2.0 * inverse * t * controlY
                        + t * t * curve.endY;
                appendLineQuad(buffer, pose, previousX, previousY, nextX, nextY,
                        1.5, 66, 165, 245, alpha);
                previousX = nextX;
                previousY = nextY;
            }
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void appendLineQuad(
            BufferBuilder buffer,
            Matrix4f pose,
            double startX,
            double startY,
            double endX,
            double endY,
            double thickness,
            int red,
            int green,
            int blue,
            int alpha) {
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double length = Math.hypot(deltaX, deltaY);
        if (length <= 0.0001) return;
        double normalX = -deltaY / length * thickness / 2.0;
        double normalY = deltaX / length * thickness / 2.0;
        buffer.vertex(pose, (float) (startX + normalX), (float) (startY + normalY), 0)
                .color(red, green, blue, alpha).endVertex();
        buffer.vertex(pose, (float) (startX - normalX), (float) (startY - normalY), 0)
                .color(red, green, blue, alpha).endVertex();
        buffer.vertex(pose, (float) (endX - normalX), (float) (endY - normalY), 0)
                .color(red, green, blue, alpha).endVertex();
        buffer.vertex(pose, (float) (endX + normalX), (float) (endY + normalY), 0)
                .color(red, green, blue, alpha).endVertex();
    }

    private NodeSize nodeSize(PlanNode node) {
        if (!compactMode && node.recipe != null) {
            int labelHeight = node.displayedQuantity() > 1 ? 12 : 0;
            return new NodeSize(
                    node.recipe.category.getWidth(),
                    node.recipe.category.getHeight() + labelHeight);
        }
        return new NodeSize(28, 28 + (node.displayedQuantity() > 1 ? 12 : 0));
    }

    private int nodeContentHeight(PlanNode node) {
        if (!compactMode && node.recipe != null) return node.recipe.category.getHeight();
        return 28;
    }

    private TreeNode canvasTreeHitbox(PlanNode node, int left, int top, int size) {
        int screenLeft = (int) Math.floor(treeViewportLeft + treePanX + left * treeZoom);
        int screenTop = (int) Math.floor(treeViewportTop + treePanY + top * treeZoom);
        int screenSize = Math.max(1, (int) Math.ceil(size * treeZoom));
        return new TreeNode(node, screenLeft, screenTop, screenSize);
    }

    private RecipeBoxHitbox canvasRecipeHitbox(
            PlanNode node,
            RecipePage<?> page,
            net.minecraft.client.renderer.Rect2i rect,
            int layoutLeft,
            int layoutTop) {
        double renderOriginX = treeViewportLeft + treePanX;
        double renderOriginY = treeViewportTop + treePanY;
        int screenLeft = (int) Math.floor(renderOriginX + rect.getX() * treeZoom);
        int screenTop = (int) Math.floor(renderOriginY + rect.getY() * treeZoom);
        int screenWidth = Math.max(1, (int) Math.ceil(rect.getWidth() * treeZoom));
        int screenHeight = Math.max(1, (int) Math.ceil(rect.getHeight() * treeZoom));
        return new RecipeBoxHitbox(node, page, screenLeft, screenTop, screenWidth, screenHeight,
                renderOriginX, renderOriginY, treeZoom, layoutLeft, layoutTop);
    }

    private void renderRecipePreview(
            GuiGraphics graphics,
            SummarySectionBounds bounds,
            PlanNode node,
            int mouseX,
            int mouseY,
            List<RecipeBoxHitbox> boxes) {
        int reservedLeft = bounds.left;
        int reservedTop = bounds.top;
        int reservedWidth = bounds.width;
        int reservedHeight = bounds.height;

        if (node == null) {
            graphics.drawCenteredString(font, "Hover a recipe node",
                    reservedLeft + reservedWidth / 2, reservedTop + reservedHeight / 2 - 4, 0xff8f9b8b);
            return;
        }

        renderIngredient(graphics, node.ingredient, reservedLeft + 6, reservedTop + 4, 16);
        boolean undiscovered = isUndiscovered(node);
        if (undiscovered) {
            graphics.drawString(font, "?", reservedLeft + 18, reservedTop + 3,
                    0xff8fc1ff, false);
        }
        String itemName = font.plainSubstrByWidth(
                ingredientDisplayName(node.ingredient), Math.max(1, reservedWidth - 30));
        graphics.drawString(font, itemName, reservedLeft + 28, reservedTop + 8,
                undiscovered ? 0xff8fc1ff : 0xffffffff, false);
        if (node.recipe == null) {
            Component attackKey = minecraft == null
                    ? Component.literal("Left Button")
                    : minecraft.options.keyAttack.getTranslatedKeyMessage();
            String action = attackKey.getString() + " Select recipe";
            String selectHint = font.plainSubstrByWidth(action, Math.max(1, reservedWidth - 12));
            graphics.drawCenteredString(font, selectHint,
                    reservedLeft + reservedWidth / 2, reservedTop + reservedHeight / 2 - 9, 0xffffffff);
            if (node.hasIngredientOptions()) {
                String optionHint = "Change item " + (node.ingredientOptionIndex + 1)
                        + " / " + node.ingredientOptions.size();
                graphics.drawCenteredString(font, optionHint,
                        reservedLeft + reservedWidth / 2, reservedTop + reservedHeight / 2 + 7,
                        0xffaeb7aa);
            }
            return;
        }
        IRecipeLayoutDrawable<?> recipeLayout = node.recipe.requireLayout();
        recipeLayout.setPosition(0, 0);
        var rect = recipeLayout.getRectWithBorder();
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
        recipeLayout.drawRecipe(graphics, (int) recipeMouseX, (int) recipeMouseY);
        graphics.pose().popPose();

        int screenLeft = (int) Math.floor(renderOriginX + rect.getX() * recipeScale);
        int screenTop = (int) Math.floor(renderOriginY + rect.getY() * recipeScale);
        int screenWidth = Math.max(1, (int) Math.ceil(rect.getWidth() * recipeScale));
        int screenHeight = Math.max(1, (int) Math.ceil(rect.getHeight() * recipeScale));
        boxes.add(new RecipeBoxHitbox(node, node.recipe,
                screenLeft, screenTop, screenWidth, screenHeight,
                renderOriginX, renderOriginY, recipeScale, 0, 0));
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
        ByproductCoverage coverage = currentPlanSummary().coverage.get(node);
        int background = hovered ? 0xff4c5d46 : 0xff293029;
        int border = hovered ? 0xff9fcf7f : 0xff52624d;
        if (coverage != null && coverage.amount > 0) {
            if (!useByproducts) {
                background = hovered ? 0xff405b65 : 0xff263f47;
                border = 0xff62c9df;
            } else if (coverage.amount >= coverage.request) {
                background = hovered ? 0xff3f6b4c : 0xff244832;
                border = 0xff70db8c;
            } else {
                background = hovered ? 0xff6b5b34 : 0xff4b3d20;
                border = 0xffffc857;
            }
        }
        graphics.fill(left, top, left + size, top + size, background);
        graphics.fill(left, top, left + size, top + 1, border);
        renderIngredient(graphics, node.ingredient,
                left + (size - 16) / 2, top + (size - 16) / 2, 16);
        if (isUndiscovered(node)) {
            graphics.fill(left, top, left + size, top + size, 0x70070d18);
            graphics.fill(left, top, left + size, top + 1, 0xff6fa8ff);
            graphics.drawString(font, "?", left + size - 7, top + 2, 0xff8fc1ff, false);
        }
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
        for (IRecipeSlotView slot : page.requireLayout().getRecipeSlotsView()
                .getSlotViews(RecipeIngredientRole.INPUT)) {
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
        for (GroupedIngredient supplemental : supplementalInputs(page)) {
            boolean alreadyExposedByJei = grouped.stream()
                    .anyMatch(existing -> sameIngredient(existing.ingredient, supplemental.ingredient));
            if (!alreadyExposedByJei) grouped.add(supplemental);
        }
        return grouped;
    }

    private List<GroupedIngredient> supplementalInputs(RecipePage<?> page) {
        List<SupplementalRecipeInputs.FluidCost> costs;
        try {
            costs = SupplementalRecipeInputs.fluidCosts(
                    page.category.getRecipeType().getUid(),
                    page.recipe);
        } catch (RuntimeException error) {
            if (loggedSupplementalInputFailures.add(page.key)) {
                JeiExportMod.LOGGER.error(
                        "Could not extract supplemental resource inputs for recipe {}",
                        page.key,
                        error);
            }
            return List.of();
        }
        List<GroupedIngredient> inputs = new ArrayList<>();
        for (SupplementalRecipeInputs.FluidCost cost : costs) {
            if (cost.amount() > Integer.MAX_VALUE) {
                if (loggedSupplementalInputFailures.add(page.key)) {
                    JeiExportMod.LOGGER.error(
                            "Supplemental fluid input {} for recipe {} exceeds FluidStack's integer capacity: {}",
                            cost.fluidId(),
                            page.key,
                            cost.amount());
                }
                continue;
            }
            Optional<ITypedIngredient<FluidStack>> typed = BuiltInRegistries.FLUID
                    .getOptional(cost.fluidId())
                    .flatMap(fluid -> runtime.getIngredientManager().createTypedIngredient(
                            ForgeTypes.FLUID_STACK,
                            new FluidStack(fluid, (int) cost.amount())));
            if (typed.isEmpty()) {
                if (loggedSupplementalInputFailures.add(page.key)) {
                    JeiExportMod.LOGGER.error(
                            "Supplemental fluid {} for recipe {} is unavailable to JEI",
                            cost.fluidId(),
                            page.key);
                }
                continue;
            }
            ITypedIngredient<?> ingredient = typed.get();
            inputs.add(new GroupedIngredient(
                    ingredient,
                    cost.amount(),
                    List.of(ingredient)));
        }
        return inputs;
    }

    private boolean sameIngredientOptions(
            List<ITypedIngredient<?>> first,
            List<ITypedIngredient<?>> second) {
        if (first.size() != second.size()) return false;
        return first.stream().allMatch(stack -> second.stream()
                .anyMatch(other -> sameIngredient(stack, other)));
    }

    private long outputAmount(RecipePage<?> page, ITypedIngredient<?> output) {
        long exact = page.requireLayout().getRecipeSlotsView()
                .getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .map(slot -> slot.getAllIngredients()
                        .filter(ingredient -> sameIngredient(ingredient, output))
                        .findFirst()
                        .map(this::ingredientAmount)
                        .orElse(0L))
                .reduce(0L, RecipeQuantityMath::safeAdd);
        return Math.max(1, exact);
    }

    private void invalidatePlanSummary() {
        planSummaryDirty = true;
    }

    private PlanSummary currentPlanSummary() {
        if (planSummaryDirty) {
            planSummary = calculatePlanSummary();
            planSummaryDirty = false;
        }
        return planSummary;
    }

    private PlanSummary calculatePlanSummary() {
        if (rootNode == null) return PlanSummary.empty();
        List<PlanNode> recipeNodes = new ArrayList<>();
        List<PlanNode> materialNodes = new ArrayList<>();
        collectSummaryNodes(rootNode, recipeNodes, materialNodes);

        LinkedHashMap<String, MutableIngredientSummary> byproducts = new LinkedHashMap<>();
        LinkedHashMap<String, List<MutableByproductSupply>> supplies = new LinkedHashMap<>();
        recipeNodes.forEach(node -> addRecipeByproducts(node, byproducts, supplies));

        Map<PlanNode, ByproductCoverage> coverage = new LinkedHashMap<>();
        List<ByproductLink> byproductLinks = new ArrayList<>();
        LinkedHashMap<String, MutableIngredientSummary> materials = new LinkedHashMap<>();
        for (PlanNode node : materialNodes) {
            String key = summaryIngredientKey(node.ingredient);
            ByproductConsumption consumption = consumeMatchingByproducts(node, supplies);
            long covered = consumption.amount;
            if (covered > 0) {
                coverage.put(node, new ByproductCoverage(covered, node.quantity));
                byproductLinks.addAll(consumption.links);
            }
            addSummaryAmount(
                    materials,
                    key,
                    node.ingredient,
                    node.quantity,
                    useByproducts ? node.quantity - covered : node.quantity,
                    node);
        }

        List<IngredientSummary> materialList = materials.values().stream()
                .map(MutableIngredientSummary::freeze)
                .toList();
        List<IngredientSummary> byproductList = byproducts.entrySet().stream()
                .map(entry -> new IngredientSummary(
                        entry.getValue().ingredient,
                        entry.getValue().gross,
                        useByproducts
                                ? remainingByproducts(supplies.get(entry.getKey()))
                                : entry.getValue().gross,
                        List.copyOf(entry.getValue().nodes)))
                .toList();
        return new PlanSummary(
                materialList,
                byproductList,
                Map.copyOf(coverage),
                List.copyOf(byproductLinks));
    }

    private ByproductConsumption consumeMatchingByproducts(
            PlanNode node,
            Map<String, List<MutableByproductSupply>> supplies) {
        List<ITypedIngredient<?>> accepted = new ArrayList<>();
        accepted.add(node.ingredient);
        node.ingredientOptions.stream()
                .filter(option -> !sameIngredient(option, node.ingredient))
                .forEach(accepted::add);
        long covered = 0;
        List<ByproductLink> links = new ArrayList<>();
        for (ITypedIngredient<?> option : accepted) {
            if (covered >= node.quantity) break;
            String optionKey = summaryIngredientKey(option);
            for (MutableByproductSupply supply : supplies.getOrDefault(optionKey, List.of())) {
                if (covered >= node.quantity) break;
                long used = Math.min(node.quantity - covered, supply.remaining);
                if (used <= 0) continue;
                covered = RecipeQuantityMath.safeAdd(covered, used);
                supply.remaining -= used;
                links.add(new ByproductLink(supply.source, node, used));
            }
        }
        return new ByproductConsumption(covered, List.copyOf(links));
    }

    private static long remainingByproducts(List<MutableByproductSupply> supplies) {
        if (supplies == null) return 0;
        long remaining = 0;
        for (MutableByproductSupply supply : supplies) {
            remaining = RecipeQuantityMath.safeAdd(remaining, supply.remaining);
        }
        return remaining;
    }

    private void collectSummaryNodes(
            PlanNode node,
            List<PlanNode> recipeNodes,
            List<PlanNode> materialNodes) {
        if (node.recipe == null) {
            materialNodes.add(node);
            return;
        }
        recipeNodes.add(node);
        node.children.forEach(child -> collectSummaryNodes(child, recipeNodes, materialNodes));
    }

    private void addRecipeByproducts(
            PlanNode node,
            LinkedHashMap<String, MutableIngredientSummary> totals,
            LinkedHashMap<String, List<MutableByproductSupply>> supplies) {
        long outputPerCraft = node.outputPerCraft;
        long crafts = RecipeQuantityMath.craftsFor(node.quantity, outputPerCraft);
        long producedPrimary = RecipeQuantityMath.producedTotal(outputPerCraft, crafts);
        long primarySurplus = Math.max(0, producedPrimary - node.quantity);
        if (primarySurplus > 0) {
            String key = summaryIngredientKey(node.ingredient);
            addSummaryAmount(
                    totals,
                    key,
                    node.ingredient,
                    primarySurplus,
                    primarySurplus,
                    node);
            addByproductSupply(supplies, key, node, primarySurplus);
        }

        for (IRecipeSlotView slot : node.recipe.requireLayout().getRecipeSlotsView()
                .getSlotViews(RecipeIngredientRole.OUTPUT)) {
            Optional<ITypedIngredient<?>> output = slot.getDisplayedIngredient()
                    .filter(ingredient -> !ItemCatalog.isEmptyIngredient(ingredient));
            if (output.isEmpty()) {
                output = slot.getAllIngredients()
                        .filter(ingredient -> !ItemCatalog.isEmptyIngredient(ingredient))
                        .findFirst();
                if (output.isPresent() && loggedByproductOutputFallbackRecipes.add(node.recipe.key)) {
                    JeiExportMod.LOGGER.warn(
                            "Recipe {} has an output slot without a displayed JEI ingredient; "
                                    + "using the slot's first available ingredient for its byproduct ledger",
                            node.recipe.key);
                }
            }
            if (output.isEmpty() || sameIngredient(output.get(), node.ingredient)) continue;
            long amount = RecipeQuantityMath.producedTotal(ingredientAmount(output.get()), crafts);
            String key = summaryIngredientKey(output.get());
            addSummaryAmount(
                    totals,
                    key,
                    output.get(),
                    amount,
                    amount,
                    node);
            addByproductSupply(supplies, key, node, amount);
        }
    }

    private static void addByproductSupply(
            LinkedHashMap<String, List<MutableByproductSupply>> supplies,
            String key,
            PlanNode source,
            long amount) {
        List<MutableByproductSupply> entries = supplies.computeIfAbsent(
                key,
                ignored -> new ArrayList<>());
        for (MutableByproductSupply entry : entries) {
            if (entry.source == source) {
                entry.remaining = RecipeQuantityMath.safeAdd(entry.remaining, amount);
                return;
            }
        }
        entries.add(new MutableByproductSupply(source, amount));
    }

    private void addSummaryAmount(
            LinkedHashMap<String, MutableIngredientSummary> totals,
            String key,
            ITypedIngredient<?> ingredient,
            long gross,
            long remaining,
            PlanNode sourceNode) {
        MutableIngredientSummary total = totals.computeIfAbsent(
                key,
                ignored -> new MutableIngredientSummary(ingredient));
        total.gross = RecipeQuantityMath.safeAdd(total.gross, gross);
        total.remaining = RecipeQuantityMath.safeAdd(total.remaining, remaining);
        if (!total.nodes.contains(sourceNode)) total.nodes.add(sourceNode);
    }

    private String summaryIngredientKey(ITypedIngredient<?> ingredient) {
        try {
            return ingredientKey(ingredient);
        } catch (RuntimeException error) {
            String type = IngredientKeys.typePrefix(ingredient.getType());
            if (loggedSummaryKeyFallbackTypes.add(type)) {
                JeiExportMod.LOGGER.warn(
                        "Could not obtain a stable JEI key for summary ingredient type {}; "
                                + "using its runtime representation",
                        type,
                        error);
            }
            return type + "|runtime|" + ingredient.getIngredient();
        }
    }

    private String formatIngredientQuantity(ITypedIngredient<?> ingredient, long quantity) {
        if (ingredient.getItemStack().filter(stack -> !stack.isEmpty()).isPresent()) {
            return quantity + "x";
        }
        if (ingredient.getIngredient() instanceof FluidStack) return quantity + " mB";
        return Long.toString(quantity);
    }

    private void renderInspectorPanel(
            GuiGraphics graphics,
            PlanSummary summary,
            int mouseX,
            int mouseY,
            List<RecipeBoxHitbox> boxes) {
        if (summaryPanelArea == null) return;
        int left = summaryPanelArea.left;
        int top = summaryPanelArea.top;
        int right = left + summaryPanelArea.width;
        int bottom = top + summaryPanelArea.height;
        graphics.fill(left, top, right, bottom, 0xc8202421);
        graphics.fill(left, top, right, top + 1, 0xff52624d);
        renderInspectorTabs(graphics, mouseX, mouseY);

        SummarySectionBounds content = new SummarySectionBounds(
                left,
                top + 22,
                summaryPanelArea.width,
                Math.max(1, summaryPanelArea.height - 22));
        materialSummaryArea = null;
        byproductSummaryArea = null;
        summaryRows = List.of();
        if (inspectorTab == InspectorTab.RECIPE) {
            renderRecipePreview(graphics, content, previewNode, mouseX, mouseY, boxes);
            return;
        }

        List<SummaryRowHitbox> rows = new ArrayList<>();
        if (inspectorTab == InspectorTab.MATERIALS) {
            materialSummaryArea = content;
            materialSummaryScroll = renderSummarySection(
                    graphics,
                    content,
                    "Materials (" + summary.materials.size() + ")",
                    summary.materials,
                    SummaryKind.MATERIAL,
                    materialSummaryScroll,
                    mouseX,
                    mouseY,
                    rows);
        } else {
            byproductSummaryArea = content;
            byproductSummaryScroll = renderSummarySection(
                    graphics,
                    content,
                    "Byproducts (" + summary.byproducts.size() + ")",
                    summary.byproducts,
                    SummaryKind.BYPRODUCT,
                    byproductSummaryScroll,
                    mouseX,
                    mouseY,
                    rows);
        }
        summaryRows = List.copyOf(rows);
    }

    private void renderInspectorTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = summaryPanelArea.left;
        int top = summaryPanelArea.top + 2;
        int width = summaryPanelArea.width;
        List<InspectorTabHitbox> hitboxes = new ArrayList<>();
        InspectorTab[] tabs = InspectorTab.values();
        for (int index = 0; index < tabs.length; index++) {
            InspectorTab tab = tabs[index];
            int tabLeft = left + width * index / tabs.length;
            int tabRight = left + width * (index + 1) / tabs.length;
            boolean selected = inspectorTab == tab;
            boolean hovered = mouseX >= tabLeft && mouseX < tabRight
                    && mouseY >= top && mouseY < top + 18;
            graphics.fill(tabLeft, top, tabRight - 1, top + 18,
                    selected ? 0xff405239 : (hovered ? 0xff343b34 : 0xff252925));
            graphics.fill(tabLeft, top + 17, tabRight - 1, top + 18,
                    selected ? 0xff9fcf7f : 0xff394139);
            String label = font.plainSubstrByWidth(tab.label, Math.max(1, tabRight - tabLeft - 4));
            graphics.drawCenteredString(font, label, (tabLeft + tabRight - 1) / 2, top + 5,
                    selected ? 0xffffffff : 0xffaeb7aa);
            hitboxes.add(new InspectorTabHitbox(tab, tabLeft, top, tabRight - tabLeft, 18));
        }
        inspectorTabs = List.copyOf(hitboxes);
    }

    private int renderSummarySection(
            GuiGraphics graphics,
            SummarySectionBounds bounds,
            String title,
            List<IngredientSummary> entries,
            SummaryKind kind,
            int requestedScroll,
            int mouseX,
            int mouseY,
            List<SummaryRowHitbox> rows) {
        int contentTop = bounds.top + 18;
        int visibleRows = Math.max(0, (bounds.height - 20) / SUMMARY_ROW_HEIGHT);
        int maximumScroll = Math.max(0, entries.size() - visibleRows);
        int scroll = Mth.clamp(requestedScroll, 0, maximumScroll);
        int titleColor = kind == SummaryKind.MATERIAL ? 0xffd7e6ce : 0xffffc857;
        String fittedTitle = font.plainSubstrByWidth(title, Math.max(1, bounds.width - 10));
        graphics.drawString(font, fittedTitle, bounds.left + 5, bounds.top + 5, titleColor, false);

        graphics.enableScissor(
                bounds.left,
                contentTop,
                bounds.left + bounds.width,
                bounds.top + bounds.height);
        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int entryIndex = scroll + visibleIndex;
            if (entryIndex >= entries.size()) break;
            IngredientSummary entry = entries.get(entryIndex);
            int rowTop = contentTop + visibleIndex * SUMMARY_ROW_HEIGHT;
            boolean hovered = mouseX >= bounds.left && mouseX < bounds.left + bounds.width
                    && mouseY >= rowTop && mouseY < rowTop + SUMMARY_ROW_HEIGHT;
            if (hovered) {
                graphics.fill(bounds.left + 2, rowTop, bounds.left + bounds.width - 2,
                        rowTop + SUMMARY_ROW_HEIGHT, 0x704c5d46);
            }
            renderIngredient(graphics, entry.ingredient, bounds.left + 4, rowTop + 2, 16);
            boolean undiscovered = isUndiscovered(entry.ingredient);
            if (undiscovered) {
                graphics.drawString(font, "?", bounds.left + 16, rowTop + 1,
                        0xff8fc1ff, false);
            }
            String amount = entry.remaining == 0
                    ? (kind == SummaryKind.MATERIAL ? "covered" : "used")
                    : formatIngredientQuantity(entry.ingredient, entry.remaining);
            int amountColor = entry.remaining < entry.gross
                    ? (kind == SummaryKind.MATERIAL ? 0xff70db8c : 0xffffc857)
                    : 0xffffffff;
            int amountLeft = bounds.left + bounds.width - 5 - font.width(amount);
            int nameWidth = Math.max(1, amountLeft - (bounds.left + 24) - 4);
            String name = font.plainSubstrByWidth(ingredientDisplayName(entry.ingredient), nameWidth);
            graphics.drawString(font, name, bounds.left + 24, rowTop + 6,
                    undiscovered ? 0xff8fc1ff : 0xffd7e6ce, false);
            graphics.drawString(font, amount, amountLeft, rowTop + 6, amountColor, false);
            rows.add(new SummaryRowHitbox(
                    entry,
                    kind,
                    bounds.left,
                    rowTop,
                    bounds.width,
                    SUMMARY_ROW_HEIGHT));
        }
        graphics.disableScissor();
        if (maximumScroll > 0) {
            String position = (scroll + 1) + "-" + Math.min(entries.size(), scroll + visibleRows)
                    + "/" + entries.size();
            graphics.drawString(font, position,
                    bounds.left + bounds.width - 5 - font.width(position),
                    bounds.top + 5,
                    0xff8f9b8b,
                    false);
        }
        return scroll;
    }

    private void renderSummaryTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        summaryRows.stream()
                .filter(row -> row.contains(mouseX, mouseY))
                .findFirst()
                .ifPresent(row -> {
                    List<Component> tooltip = new ArrayList<>(ingredientTooltip(row.entry.ingredient));
                    appendDiscoveryTooltip(tooltip, row.entry.ingredient);
                    String totalLabel = row.kind == SummaryKind.MATERIAL
                            ? "Gross material demand: "
                            : "Total byproduct: ";
                    tooltip.add(Component.literal(totalLabel
                            + formatIngredientQuantity(row.entry.ingredient, row.entry.gross))
                            .withStyle(ChatFormatting.GRAY));
                    if (row.entry.remaining < row.entry.gross) {
                        String remainingLabel = row.kind == SummaryKind.MATERIAL
                                ? "Still required: "
                                : "Remaining byproduct: ";
                        tooltip.add(Component.literal(remainingLabel
                                + formatIngredientQuantity(row.entry.ingredient, row.entry.remaining))
                                .withStyle(row.kind == SummaryKind.MATERIAL
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.GOLD));
                    }
                    String action = row.kind == SummaryKind.MATERIAL
                            ? "Click: select input recipe"
                            : (row.entry.nodes.size() > 1
                                    ? "Click: cycle through source nodes"
                                    : "Click: center source node");
                    tooltip.add(Component.literal(action).withStyle(ChatFormatting.DARK_GRAY));
                    graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                });
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
                .ifPresent(node -> {
                    List<Component> tooltip = new ArrayList<>(ingredientTooltip(node.node.ingredient));
                    appendDiscoveryTooltip(tooltip, node.node.ingredient);
                    ByproductCoverage coverage = currentPlanSummary().coverage.get(node.node);
                    if (coverage != null && coverage.amount > 0) {
                        String qualifier = node.node.hasIngredientOptions() ? "Accepted byproducts" : "Byproducts";
                        String message = qualifier + (useByproducts ? " supply " : " can supply ")
                                + formatIngredientQuantity(node.node.ingredient, coverage.amount)
                                + " of " + formatIngredientQuantity(node.node.ingredient, coverage.request);
                        tooltip.add(Component.literal(message).withStyle(
                                useByproducts && coverage.amount >= coverage.request
                                        ? ChatFormatting.GREEN
                                        : ChatFormatting.GOLD));
                    }
                    graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if ((button != 0 && button != 1 && button != 2) || minecraft == null) return false;
        if (button == 0) {
            Optional<InspectorTabHitbox> selectedTab = inspectorTabs.stream()
                    .filter(tab -> tab.contains(mouseX, mouseY))
                    .findFirst();
            if (selectedTab.isPresent()) {
                inspectorTab = selectedTab.get().tab;
                summaryRows = List.of();
                materialSummaryArea = null;
                byproductSummaryArea = null;
                status = "";
                return true;
            }
            Optional<SummaryRowHitbox> selectedSummary = summaryRows.stream()
                    .filter(row -> row.contains(mouseX, mouseY))
                    .findFirst();
            if (selectedSummary.isPresent()) {
                activateSummaryRow(selectedSummary.get());
                return true;
            }
        }

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
            Optional<IRecipeSlotView> slot = box.recipeSlotUnderMouse(mouseX, mouseY)
                    .map(value -> value);
            if (slot.isPresent()) {
                Optional<ITypedIngredient<?>> selected = slot.get().getDisplayedIngredient()
                        .filter(ingredient -> !ItemCatalog.isEmptyIngredient(ingredient));
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

    private void activateSummaryRow(SummaryRowHitbox row) {
        if (row.entry.nodes.isEmpty()) {
            JeiExportMod.LOGGER.error(
                    "Cannot activate {} summary entry {} because it has no source tree node",
                    row.kind,
                    ingredientDisplayName(row.entry.ingredient));
            status = "Summary entry has no source node";
            return;
        }
        if (row.kind == SummaryKind.MATERIAL) {
            PlanNode materialNode = row.entry.nodes.get(0);
            previewNode = materialNode;
            openInputRecipePicker(materialNode);
            return;
        }

        String key = summaryIngredientKey(row.entry.ingredient);
        int sourceIndex = Math.floorMod(
                byproductCenterIndices.getOrDefault(key, 0),
                row.entry.nodes.size());
        PlanNode sourceNode = row.entry.nodes.get(sourceIndex);
        byproductCenterIndices.put(key, sourceIndex + 1);
        previewNode = sourceNode;
        if (centerTreeOnNode(sourceNode)) {
            status = row.entry.nodes.size() == 1
                    ? "Centered byproduct source"
                    : "Centered byproduct source " + (sourceIndex + 1) + "/" + row.entry.nodes.size();
        }
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
        return mouseX >= treeViewportLeft && mouseX < treeViewportRight
                && mouseY >= treeViewportTop && mouseY < treeViewportBottom;
    }

    private void openInputRecipePicker(PlanNode selectedNode) {
        createInputRecipePicker(selectedNode).ifPresent(minecraft::setScreen);
    }

    Screen initialInputRecipeScreen() {
        String favorite = progress.favoriteRecipe(target);
        if (favorite != null) {
            if (rootNode.recipe != null && favorite.equals(rootNode.recipe.key)) return this;
            JeiExportMod.LOGGER.warn(
                    "Favorite recipe {} for {} is unavailable; opening the recipe chooser",
                    favorite,
                    target.getHoverName().getString());
        }
        return createInputRecipePicker(rootNode).<Screen>map(screen -> screen).orElse(this);
    }

    private Optional<RecipePickerScreen> createInputRecipePicker(PlanNode selectedNode) {
        ITypedIngredient<?> selected = selectedNode.ingredient;
        List<RecipePage<?>> choices = selectedNode == rootNode
                ? List.copyOf(pages)
                : collectPagesFor(selected, RecipeIngredientRole.OUTPUT);
        if (choices.isEmpty() && !selectedNode.hasIngredientOptions()) {
            status = "No input recipes found for " + ingredientDisplayName(selected);
            return Optional.empty();
        }
        return Optional.of(new RecipePickerScreen(
                PickerKind.INPUT_RECIPE,
                selected,
                selectedNode,
                choices.stream().map(page -> new RecipeChoice(selected, page, false)).toList()));
    }

    private void openOutputPicker(PlanNode selectedNode) {
        ITypedIngredient<?> selected = selectedNode.ingredient;
        List<RecipeChoice> choices = collectPagesFor(selected, RecipeIngredientRole.INPUT).stream()
                .map(page -> new RecipeChoice(selected, page, true))
                .toList();
        if (choices.isEmpty()) {
            status = "No recipe outputs found for " + ingredientDisplayName(selected);
            return;
        }
        minecraft.setScreen(new RecipePickerScreen(
                PickerKind.OUTPUT,
                selected,
                selectedNode,
                choices));
    }

    private void navigateHistory(int delta) {
        RecipeTreeScreen destination = history.move(delta);
        if (destination != null && minecraft != null) {
            minecraft.setScreen(destination);
        } else if (history.canMove(delta)) {
            status = "Saved recipe history entry is unavailable in this modpack";
        }
    }

    private void toggleMode() {
        compactMode = !compactMode;
        treeNodes = List.of();
        recipeBoxes = List.of();
        visibleRecipePages = List.of();
        invalidateTreeLayout();
        treeViewInitialized = false;
        treeZoom = 1.0f;
        rebuildWidgets();
    }

    private void centerTree() {
        centerTreeRequested = true;
        status = "Tree centered";
    }

    private boolean centerTreeOnNode(PlanNode targetNode) {
        TreeLayoutNode node = currentTreeLayout().nodesByPlan.get(targetNode);
        if (node == null) {
            JeiExportMod.LOGGER.warn(
                    "Could not center the tree on byproduct source {} because its node is no longer in the plan",
                    ingredientDisplayName(targetNode.ingredient));
            status = "Byproduct source is no longer in the tree";
            return false;
        }
        double nodeCenterX = node.left + node.width / 2.0;
        double nodeCenterY = node.top + node.height / 2.0;
        int viewportWidth = Math.max(1, treeViewportRight - treeViewportLeft);
        int viewportHeight = Math.max(1, treeViewportBottom - treeViewportTop);
        treePanX = viewportWidth / 2.0 - nodeCenterX * treeZoom;
        treePanY = viewportHeight / 2.0 - nodeCenterY * treeZoom;
        treeViewInitialized = true;
        centerTreeRequested = false;
        return true;
    }

    private void toggleByproducts() {
        useByproducts = !useByproducts;
        invalidatePlanSummary();
        updateButtons();
        status = useByproducts
                ? "Byproducts now reduce matching material requirements"
                : "Byproduct allocation disabled";
    }

    private void toggleRecipeBook() {
        recipeBookMode = !recipeBookMode;
        progress.setRecipeBookMode(recipeBookMode);
        updateButtons();
        status = recipeBookMode
                ? "Undiscovered item markers enabled"
                : "Undiscovered item markers disabled";
    }

    private boolean isUndiscovered(PlanNode node) {
        return recipeBookMode && node != null && isUndiscovered(node.stack);
    }

    private boolean isUndiscovered(ItemStack stack) {
        return recipeBookMode && stack != null && !stack.isEmpty() && !progress.hasDiscovered(stack);
    }

    private boolean isUndiscovered(ITypedIngredient<?> ingredient) {
        return recipeBookMode && ingredient != null
                && ingredient.getItemStack()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> !progress.hasDiscovered(stack))
                .orElse(false);
    }

    private void appendDiscoveryTooltip(List<Component> tooltip, ITypedIngredient<?> ingredient) {
        if (!isUndiscovered(ingredient)) return;
        tooltip.add(Component.literal("Undiscovered — not held or seen in an AE2 terminal")
                .withStyle(ChatFormatting.BLUE));
    }

    private void expandFavoriteIngredients(PlanNode node) {
        if (node == null) return;
        if (node.recipe == null) node.expandFavoriteRecipe();
        List.copyOf(node.children).forEach(this::expandFavoriteIngredients);
    }

    private boolean isRecipePageSelected(PlanNode node, RecipePage<?> page) {
        if (node == null) return false;
        if (node.recipe == page) return true;
        return node.children.stream().anyMatch(child -> isRecipePageSelected(child, page));
    }

    private void openJei() {
        runtime.getRecipesGui().show(targetFocus);
    }

    private void openJei(ITypedIngredient<?> ingredient) {
        openJeiTyped(ingredient);
    }

    private <T> void openJeiTyped(ITypedIngredient<T> ingredient) {
        IFocus<T> focus = runtime.getJeiHelpers().getFocusFactory().createFocus(
                RecipeIngredientRole.OUTPUT,
                ingredient);
        runtime.getRecipesGui().show(focus);
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
        selection.addProperty("itemKey", ingredientKey(node.ingredient));
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
            if (amount > 0) {
                long clamped = Math.min(amount, RecipeQuantityMath.MAX_REQUESTED_AMOUNT);
                if (clamped != amount) {
                    JeiExportMod.LOGGER.warn(
                            "Imported requested amount {} was clamped to {}",
                            amount,
                            clamped);
                }
                requestedAmount = Long.toString(clamped);
            }
        }
        rootNode = new PlanNode(target, requestedQuantity(), null, 0);
        invalidateTreeLayout();
        previewNode = rootNode;
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
            if (!ingredientKey(node.ingredient).equals(requiredString(selection, "itemKey", 512))) {
                throw new IllegalArgumentException("A shared ingredient does not match its recipe branch");
            }
            JsonObject source = selection.getAsJsonObject("source");
            if (source == null || !"recipe".equals(requiredString(source, "kind", 32))) {
                skipped++;
                continue;
            }
            String recipeKey = requiredString(source, "recipeKey", 1024);
            RecipePage<?> recipe = collectPagesFor(node.ingredient, RecipeIngredientRole.OUTPUT).stream()
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
        if (delta != 0 && materialSummaryArea != null
                && materialSummaryArea.contains(mouseX, mouseY)) {
            materialSummaryScroll = scrollSummary(
                    materialSummaryScroll,
                    currentPlanSummary().materials.size(),
                    materialSummaryArea,
                    delta);
            return true;
        }
        if (delta != 0 && byproductSummaryArea != null
                && byproductSummaryArea.contains(mouseX, mouseY)) {
            byproductSummaryScroll = scrollSummary(
                    byproductSummaryScroll,
                    currentPlanSummary().byproducts.size(),
                    byproductSummaryArea,
                    delta);
            return true;
        }
        if (amountBox != null && amountBox.isMouseOver(mouseX, mouseY) && delta != 0) {
            try {
                long amount = RecipeQuantityMath.adjustRequestedAmount(parseLong(requestedAmount), delta);
                amountBox.setValue(Long.toString(amount));
                return true;
            } catch (IllegalArgumentException error) {
                JeiExportMod.LOGGER.warn(
                        "[jeiexport] Requested output scroll was ignored because the amount field is invalid: '{}'",
                        requestedAmount,
                        error);
                status = "Enter an output amount from 1 to 999 before scrolling";
                return true;
            }
        }
        if (insideTreeViewport(mouseX, mouseY) && delta != 0) {
            Optional<TreeNode> hoveredNode = treeNodes.stream()
                    .filter(node -> node.contains(mouseX, mouseY))
                    .findFirst();
            if (hoveredNode.isPresent()
                    && hoveredNode.get().node.cycleIngredientOption(delta > 0 ? 1 : -1)) {
                status = "";
                return true;
            }
            double viewportCenterX = (treeViewportLeft + treeViewportRight) / 2.0;
            double viewportCenterY = (treeViewportTop + treeViewportBottom) / 2.0;
            double modelCenterX = toTreeX(viewportCenterX);
            double modelCenterY = toTreeY(viewportCenterY);
            float factor = delta > 0 ? 1.15f : (1.0f / 1.15f);
            treeZoom = Mth.clamp(treeZoom * factor, 0.35f, 2.5f);
            treePanX = viewportCenterX - treeViewportLeft - modelCenterX * treeZoom;
            treePanY = viewportCenterY - treeViewportTop - modelCenterY * treeZoom;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private static int scrollSummary(
            int current,
            int entryCount,
            SummarySectionBounds bounds,
            double delta) {
        int visibleRows = Math.max(0, (bounds.height - 20) / SUMMARY_ROW_HEIGHT);
        int maximum = Math.max(0, entryCount - visibleRows);
        return Mth.clamp(current + (delta > 0 ? -1 : 1), 0, maximum);
    }

    private void savePlan() {
        RecipePage<?> page = currentPage().orElse(null);
        if (page == null) return;
        try {
            long amount = parseLong(requestedAmount);
            if (amount <= 0 || amount > RecipeQuantityMath.MAX_REQUESTED_AMOUNT) {
                throw new IllegalArgumentException("amount");
            }
            progress.savePlan(target, new RecipeTreeProgress.SavedPlan(amount, page.key));
            status = "Plan saved locally";
        } catch (IllegalArgumentException error) {
            status = "Enter an output amount from 1 to 999";
        }
    }

    private void restorePlan() {
        RecipeTreeProgress.SavedPlan saved = progress.plan(target);
        if (saved == null) return;
        long restoredAmount = Math.min(
                RecipeQuantityMath.MAX_REQUESTED_AMOUNT,
                Math.max(1, saved.amount()));
        if (restoredAmount != saved.amount()) {
            JeiExportMod.LOGGER.warn(
                    "Saved requested amount {} for {} was clamped to {}",
                    saved.amount(),
                    target.getHoverName().getString(),
                    restoredAmount);
        }
        requestedAmount = Long.toString(restoredAmount);
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
        if (useByproductsButton != null) {
            useByproductsButton.setMessage(Component.literal(
                    useByproducts ? "Byproducts: ON" : "Use byproducts"));
        }
        if (recipeBookButton != null) {
            recipeBookButton.setMessage(Component.literal(
                    recipeBookMode ? "Recipe book: ON" : "Recipe book"));
        }
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
        return collectPagesFor(typedItem(stack), role);
    }

    private List<RecipePage<?>> collectPagesFor(
            ITypedIngredient<?> ingredient,
            RecipeIngredientRole role) {
        return collectPagesForTyped(ingredient, role);
    }

    private <V> List<RecipePage<?>> collectPagesForTyped(
            ITypedIngredient<V> ingredient,
            RecipeIngredientRole role) {
        IFocus<V> focus = runtime.getJeiHelpers().getFocusFactory().createFocus(role, ingredient);
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
            IFocus<?> focus,
            IFocusGroup focusGroup) {
        runtime.getRecipeManager().createRecipeLookup(category.getRecipeType())
                .limitFocus(List.of(focus))
                .get()
                .forEach(recipe -> found.add(new RecipePage<>(
                        category,
                        recipe,
                        focusGroup,
                        recipeKey(category, recipe))));
    }

    private List<ItemStack> displayedOutputs(RecipePage<?> page) {
        Optional<? extends IRecipeLayoutDrawable<?>> optionalLayout = page.layout();
        if (optionalLayout.isEmpty()) return List.of();
        List<ItemStack> outputs = new ArrayList<>();
        optionalLayout.get().getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT).stream()
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
        T value = ingredient.getIngredient();
        if (value instanceof ItemStack stack && !stack.isEmpty()) {
            return Math.max(1, stack.getCount());
        }
        if (value instanceof FluidStack stack && !stack.isEmpty()) {
            return Math.max(1, stack.getAmount());
        }
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
        try {
            IIngredientHelper<T> helper = runtime.getIngredientManager()
                    .getIngredientHelper(ingredient.getType());
            long helperAmount = helper.getAmount(value);
            if (helperAmount > 0) return helperAmount;
        } catch (RuntimeException error) {
            String type = IngredientKeys.typePrefix(ingredient.getType());
            if (loggedAmountFallbackTypes.add(type)) {
                JeiExportMod.LOGGER.warn(
                        "JEI ingredient helper failed to provide an amount for type {}; defaulting to 1",
                        type,
                        error);
            }
            return 1;
        }
        String type = IngredientKeys.typePrefix(ingredient.getType());
        if (loggedAmountFallbackTypes.add(type)) {
            JeiExportMod.LOGGER.warn(
                    "JEI ingredient type {} has no positive amount; defaulting to 1",
                    type);
        }
        return 1;
    }

    private static String formatQuantity(PlanNode node, long quantity) {
        if (!node.stack.isEmpty()) return quantity + "x";
        if (node.ingredient.getIngredient() instanceof FluidStack) return quantity + " mB";
        return Long.toString(quantity);
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
        if (ingredient.getIngredient() instanceof FluidStack fluidStack
                && !fluidStack.isEmpty()
                && renderFullFluid(graphics, fluidStack, left, top, size)) {
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

    private boolean renderFullFluid(
            GuiGraphics graphics,
            FluidStack fluidStack,
            int left,
            int top,
            int size) {
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluidStack.getFluid());
        try {
            IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluidStack.getFluid());
            ResourceLocation texture = properties.getStillTexture(fluidStack);
            if (texture == null) {
                throw new IllegalStateException("Fluid has no still texture");
            }
            TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
            int tint = properties.getTintColor(fluidStack);
            float alpha = (float) ((tint >>> 24) & 0xFF) / 255.0f;
            float red = (float) ((tint >>> 16) & 0xFF) / 255.0f;
            float green = (float) ((tint >>> 8) & 0xFF) / 255.0f;
            float blue = (float) (tint & 0xFF) / 255.0f;
            RenderSystem.setShaderColor(red, green, blue, alpha);
            graphics.blit(left, top, 0, size, size, sprite);
            return true;
        } catch (RuntimeException error) {
            if (loggedFluidRenderFallbacks.add(fluidId)) {
                JeiExportMod.LOGGER.warn(
                        "Could not render full fluid node for {}; falling back to JEI's ingredient renderer",
                        fluidId,
                        error);
            }
            return false;
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
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
            invalidateTreeLayout();
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
        copy.outputPerCraft = source.outputPerCraft;
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
        private static final int MAX_CACHED_PICKER_LAYOUTS = 64;

        private final PickerKind kind;
        private ITypedIngredient<?> selectedIngredient;
        private final PlanNode selectedNode;
        private List<RecipeChoice> choices = List.of();
        private List<PickerGroup> groups = List.of();
        private final LinkedHashMap<RecipePage<?>, Boolean> layoutLru =
                new LinkedHashMap<>(64, 0.75f, true);
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
        private Button openJeiButton;
        private Button changeItemButton;

        private RecipePickerScreen(
                PickerKind kind,
                ITypedIngredient<?> selectedIngredient,
                PlanNode selectedNode,
                List<RecipeChoice> choices) {
            super(Component.literal(kind == PickerKind.INPUT_RECIPE
                    ? "Choose input recipe"
                    : "Choose output"));
            this.kind = kind;
            this.selectedIngredient = selectedIngredient;
            this.selectedNode = selectedNode;
            replaceChoices(choices);
            this.choices.stream()
                    .map(choice -> choice.page)
                    .filter(RecipePage::hasCachedLayout)
                    .forEach(this::rememberLayout);
            trimLayoutCache();
        }

        private void replaceChoices(List<RecipeChoice> choices) {
            List<RecipeChoice> orderedChoices = new ArrayList<>(choices);
            if (kind == PickerKind.INPUT_RECIPE) {
                orderedChoices.sort((left, right) -> Boolean.compare(isFavorite(right), isFavorite(left)));
            }
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
            int headerButtonRight = pickerLeft + pickerWidth - 10;
            if (kind == PickerKind.INPUT_RECIPE) {
                noRecipeButton = addRenderableWidget(Button.builder(
                                Component.literal("No recipe"), button -> clearRecipeSelection())
                        .bounds(headerButtonRight - 76, pickerTop + 7, 76, 20).build());
                headerButtonRight = noRecipeButton.getX() - 6;
                if (selectedNode.hasIngredientOptions()) {
                    changeItemButton = addRenderableWidget(Button.builder(
                                    Component.empty(), button -> changeIngredientOption())
                            .bounds(headerButtonRight - 104, pickerTop + 7, 104, 20).build());
                    updateChangeItemButton();
                    headerButtonRight = changeItemButton.getX() - 6;
                }
            }
            openJeiButton = addRenderableWidget(Button.builder(
                            Component.translatable("button.jeiexport.open_jei"),
                            button -> openJei(selectedIngredient))
                    .bounds(headerButtonRight - 76, pickerTop + 7, 76, 20).build());
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
                PickerCard card = new PickerCard(
                        choice,
                        choice.page.cardWidth(),
                        choice.page.cardHeight(),
                        -JEI_RECIPE_BORDER_PADDING,
                        -JEI_RECIPE_BORDER_PADDING);
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
            renderIngredient(graphics, selectedIngredient, pickerLeft + 10, pickerTop + 9, 16);
            String prompt = kind == PickerKind.INPUT_RECIPE ? "Input recipe for " : "Output using ";
            String choiceCount = choices.size() + (choices.size() == 1 ? " choice" : " choices");
            int countRight = openJeiButton.getX() - 8;
            int countLeft = countRight - font.width(choiceCount);
            int titleLeft = pickerLeft + 32;
            String title = font.plainSubstrByWidth(
                    prompt + ingredientDisplayName(selectedIngredient),
                    Math.max(1, countLeft - titleLeft - 8));
            graphics.drawString(font, title, pickerLeft + 32, pickerTop + 13, 0xffffffff, false);
            graphics.drawString(font, choiceCount, countLeft, pickerTop + 13, 0xffaeb7aa, false);

            List<ChoiceHitbox> rendered = new ArrayList<>();
            List<GroupHeaderHitbox> renderedHeaders = new ArrayList<>();
            RecipePage<?> hoveredPage = null;
            graphics.enableScissor(recipesLeft, recipesTop, recipesRight, recipesBottom);
            for (PickerPlacement placement : placements) {
                int cardTop = recipesTop + placement.top - (int) Math.round(scrollOffset);
                if (cardTop + placement.height <= recipesTop || cardTop >= recipesBottom) continue;
                RecipeChoice choice = placement.choice;
                int cardLeft = recipesLeft + placement.left;
                int recipeX = cardLeft - placement.borderOffsetX;
                int recipeY = cardTop - placement.borderOffsetY;
                Optional<? extends IRecipeLayoutDrawable<?>> optionalLayout = choice.page.layout();
                if (optionalLayout.isEmpty()) {
                    graphics.fill(cardLeft, cardTop,
                            cardLeft + placement.width, cardTop + placement.height, 0xff4a2525);
                    graphics.drawCenteredString(font, "Layout unavailable",
                            cardLeft + placement.width / 2,
                            cardTop + placement.height / 2 - 4,
                            0xffff8f8f);
                    continue;
                }
                IRecipeLayoutDrawable<?> recipeLayout = optionalLayout.get();
                rememberLayout(choice.page);
                recipeLayout.setPosition(recipeX, recipeY);
                var recipeRect = recipeLayout.getRectWithBorder();
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
                recipeLayout.drawRecipe(graphics, mouseX, mouseY);
                if (isFavorite(choice)) {
                    graphics.drawString(font, "★", recipeRect.getX() + 2, recipeRect.getY() + 2,
                            0xffffd866, true);
                }
                rendered.add(new ChoiceHitbox(choice,
                        recipeRect.getX(), recipeRect.getY(), recipeRect.getWidth(), recipeRect.getHeight()));
                if (hovered) hoveredPage = choice.page;
            }
            trimLayoutCache();
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
            if (hoveredPage != null) {
                hoveredPage.layout().ifPresent(layout -> layout.drawOverlays(graphics, mouseX, mouseY));
            }
        }

        @Override
        public void tick() {
            super.tick();
            for (PickerPlacement placement : placements) {
                if (!isVisible(placement)) continue;
                RecipePage<?> page = placement.choice.page;
                page.layout().ifPresent(layout -> {
                    rememberLayout(page);
                    layout.tick();
                });
            }
            trimLayoutCache();
        }

        private void rememberLayout(RecipePage<?> page) {
            layoutLru.put(page, Boolean.TRUE);
        }

        private void trimLayoutCache() {
            if (layoutLru.size() <= MAX_CACHED_PICKER_LAYOUTS) return;
            var iterator = layoutLru.keySet().iterator();
            while (layoutLru.size() > MAX_CACHED_PICKER_LAYOUTS && iterator.hasNext()) {
                RecipePage<?> page = iterator.next();
                if (isRecipePageSelected(rootNode, page)) continue;
                iterator.remove();
                page.releaseLayout();
            }
        }

        private void releaseUnselectedPickerLayouts() {
            for (RecipePage<?> page : layoutLru.keySet()) {
                if (!isRecipePageSelected(rootNode, page)) page.releaseLayout();
            }
            layoutLru.clear();
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

        private void changeIngredientOption() {
            if (!selectedNode.cycleIngredientOption(1, false)) return;
            releaseUnselectedPickerLayouts();
            selectedIngredient = selectedNode.ingredient;
            List<RecipePage<?>> pagesForIngredient = collectPagesFor(
                    selectedIngredient,
                    RecipeIngredientRole.OUTPUT);
            replaceChoices(pagesForIngredient.stream()
                    .map(page -> new RecipeChoice(selectedIngredient, page, false))
                    .toList());
            scrollOffset = 0;
            hitboxes = List.of();
            groupHeaderHitboxes = List.of();
            rebuildWidgets();
        }

        private void updateChangeItemButton() {
            if (changeItemButton == null) return;
            changeItemButton.setMessage(Component.literal(
                    "Change item " + (selectedNode.ingredientOptionIndex + 1)
                            + "/" + selectedNode.ingredientOptions.size()));
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
                if (selectedNode.stack.isEmpty()) {
                    JeiExportMod.LOGGER.info(
                            "Recipe selection for custom JEI ingredient {} is session-local",
                            ingredientKey(selectedNode.ingredient));
                } else {
                    progress.saveFavoriteRecipe(selectedNode.stack, choice.page.key);
                }
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
            Optional<ITypedIngredient<?>> resolvedOutput = choiceIngredient(choice);
            ItemStack choiceItem = resolvedOutput
                    .map(RecipeTreeScreen.this::ingredientItemStack)
                    .orElse(ItemStack.EMPTY);
            if (choiceItem.isEmpty()) {
                JeiExportMod.LOGGER.error(
                        "Output navigation recipe {} did not expose an item output",
                        choice.page.key);
                status = "Cannot open a non-item recipe output as the tree root";
                minecraft.setScreen(RecipeTreeScreen.this);
                return;
            }
            progress.saveFavoriteRecipe(choiceItem, choice.page.key);
            List<ItemStack> nextPath;
            ItemStack selectedItem = ingredientItemStack(selectedIngredient);
            if (ItemStack.isSameItemSameTags(choiceItem, target)) {
                nextPath = new ArrayList<>(path);
            } else if (path.size() > 1
                    && ItemStack.isSameItemSameTags(selectedItem, target)
                    && ItemStack.isSameItemSameTags(choiceItem, path.get(path.size() - 2))) {
                nextPath = new ArrayList<>(path.subList(0, path.size() - 1));
            } else {
                nextPath = List.of(choiceItem.copyWithCount(1));
            }
            RecipeTreeScreen nextScreen = new RecipeTreeScreen(
                    choiceItem, runtime, nextPath, compactMode, choice.page.key, history, true);
            if (selectedNode == rootNode) {
                nextScreen.attachPreviousRoot(rootNode);
            }
            minecraft.setScreen(nextScreen);
        }

        private void clearRecipeSelection() {
            if (kind != PickerKind.INPUT_RECIPE) return;
            if (!selectedNode.stack.isEmpty()) progress.clearFavoriteRecipe(selectedNode.stack);
            selectedNode.clearRecipe();
            treeNodes = List.of();
            recipeBoxes = List.of();
            treeViewInitialized = false;
            status = "No recipe selected for " + ingredientDisplayName(selectedNode.ingredient);
            minecraft.setScreen(RecipeTreeScreen.this);
        }

        private boolean isFavorite(RecipeChoice choice) {
            ItemStack item = choiceIngredient(choice)
                    .map(RecipeTreeScreen.this::ingredientItemStack)
                    .orElse(ItemStack.EMPTY);
            if (item.isEmpty()) return false;
            String favorite = progress.favoriteRecipe(item);
            return favorite != null && favorite.equals(choice.page.key);
        }

        private Optional<ITypedIngredient<?>> choiceIngredient(RecipeChoice choice) {
            if (!choice.resolveFirstItemOutput) return Optional.of(choice.ingredient);
            return displayedOutputs(choice.page).stream()
                    .findFirst()
                    .map(RecipeTreeScreen.this::typedItem);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(RecipeTreeScreen.this);
        }

        @Override
        public void removed() {
            releaseUnselectedPickerLayouts();
            super.removed();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    @Override
    public void removed() {
        history.persist();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class RecipePage<T> {
        private final IRecipeCategory<T> category;
        private final T recipe;
        private final IFocusGroup focusGroup;
        private final String key;
        private IRecipeLayoutDrawable<T> cachedLayout;
        private boolean layoutAttempted;

        private RecipePage(
                IRecipeCategory<T> category,
                T recipe,
                IFocusGroup focusGroup,
                String key) {
            this.category = category;
            this.recipe = recipe;
            this.focusGroup = focusGroup;
            this.key = key;
        }

        private Optional<IRecipeLayoutDrawable<T>> layout() {
            if (!layoutAttempted) {
                layoutAttempted = true;
                cachedLayout = runtime.getRecipeManager()
                        .createRecipeLayoutDrawable(category, recipe, focusGroup)
                        .orElse(null);
                if (cachedLayout == null) {
                    JeiExportMod.LOGGER.error(
                            "JEI could not create a recipe layout for {}; the recipe card is unavailable",
                            key);
                }
            }
            return Optional.ofNullable(cachedLayout);
        }

        private IRecipeLayoutDrawable<T> requireLayout() {
            return layout().orElseThrow(() -> new IllegalStateException(
                    "JEI recipe layout is unavailable for " + key));
        }

        private void releaseLayout() {
            if (cachedLayout == null) return;
            cachedLayout = null;
            layoutAttempted = false;
        }

        private boolean hasCachedLayout() {
            return cachedLayout != null;
        }

        private int cardWidth() {
            return Math.max(1, category.getWidth() + JEI_RECIPE_BORDER_PADDING * 2);
        }

        private int cardHeight() {
            return Math.max(1, category.getHeight() + JEI_RECIPE_BORDER_PADDING * 2);
        }
    }

    private record GroupedIngredient(
            ITypedIngredient<?> ingredient,
            long quantity,
            List<ITypedIngredient<?>> options) {
    }

    private record RecipeChoice(
            ITypedIngredient<?> ingredient,
            RecipePage<?> page,
            boolean resolveFirstItemOutput) {
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
        private final IJeiRuntime runtime;
        private final RecipeTreeProgress progress = RecipeTreeProgress.get();
        private final List<HistoryEntry> entries = new ArrayList<>();
        private int index = -1;

        private OutputHistory(IJeiRuntime runtime) {
            this.runtime = runtime;
            progress.recipeHistory().stream()
                    .filter(entry -> entry != null
                            && entry.itemKey() != null
                            && !entry.itemKey().isBlank())
                    .map(HistoryEntry::new)
                    .forEach(entries::add);
            if (entries.size() > MAX_ENTRIES) {
                entries.subList(0, entries.size() - MAX_ENTRIES).clear();
            }
            index = entries.size() - 1;
        }

        private void push(RecipeTreeScreen screen) {
            if (index + 1 < entries.size()) {
                entries.subList(index + 1, entries.size()).clear();
            }
            RecipeTreeProgress.RecipeHistoryEntry descriptor = screen.historyEntry();
            if (index >= 0 && entries.get(index).descriptor().equals(descriptor)) {
                entries.set(index, new HistoryEntry(screen));
                persist();
                return;
            }
            entries.add(new HistoryEntry(screen));
            if (entries.size() > MAX_ENTRIES) {
                entries.remove(0);
            }
            index = entries.size() - 1;
            persist();
        }

        private boolean canMove(int delta) {
            int destination = index + delta;
            return destination >= 0 && destination < entries.size();
        }

        private RecipeTreeScreen move(int delta) {
            if (!canMove(delta)) return null;
            persist();
            int destination = index + delta;
            RecipeTreeScreen screen = entries.get(destination).screen(this, runtime);
            if (screen == null) return null;
            index = destination;
            return screen;
        }

        private void persist() {
            progress.replaceRecipeHistory(entries.stream()
                    .map(HistoryEntry::descriptor)
                    .toList());
        }

        private static final class HistoryEntry {
            private RecipeTreeProgress.RecipeHistoryEntry descriptor;
            private RecipeTreeScreen screen;

            private HistoryEntry(RecipeTreeProgress.RecipeHistoryEntry descriptor) {
                this.descriptor = descriptor;
            }

            private HistoryEntry(RecipeTreeScreen screen) {
                this.screen = screen;
                this.descriptor = screen.historyEntry();
            }

            private RecipeTreeProgress.RecipeHistoryEntry descriptor() {
                if (screen != null) descriptor = screen.historyEntry();
                return descriptor;
            }

            private RecipeTreeScreen screen(OutputHistory history, IJeiRuntime runtime) {
                if (screen != null) return screen;
                ResourceLocation itemId = ResourceLocation.tryParse(descriptor.itemKey());
                Optional<ItemStack> target = Optional.ofNullable(itemId)
                        .flatMap(BuiltInRegistries.ITEM::getOptional)
                        .map(ItemStack::new)
                        .filter(stack -> !stack.isEmpty());
                if (target.isEmpty()) {
                    JeiExportMod.LOGGER.warn(
                            "Cannot restore recipe history item {} because it is unavailable",
                            descriptor.itemKey());
                    return null;
                }
                screen = new RecipeTreeScreen(
                        target.get(),
                        runtime,
                        List.of(target.get()),
                        descriptor.compactMode(),
                        descriptor.recipeKey(),
                        history,
                        false);
                screen.applyHistoryAmount(descriptor.amount());
                return screen;
            }
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
        private long outputPerCraft = 1;
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
            this.outputPerCraft = outputAmount(recipe, ingredient);
            if (depth() >= 12) {
                this.children = List.of();
                invalidateTreeLayout();
                invalidatePlanSummary();
                return;
            }
            long crafts = RecipeQuantityMath.craftsFor(quantity, outputPerCraft);
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
            invalidateTreeLayout();
            invalidatePlanSummary();
        }

        private void clearRecipe() {
            recipe = null;
            outputPerCraft = 1;
            children = List.of();
            invalidateTreeLayout();
            invalidatePlanSummary();
        }

        private void expandFavoriteRecipe() {
            if (stack.isEmpty() || recipe != null || depth() >= 12 || repeatsAncestorIngredient()) return;
            String favorite = progress.favoriteRecipe(stack);
            if (favorite == null || favoriteExpansionAttemptsRemaining <= 0) return;
            favoriteExpansionAttemptsRemaining--;
            collectPagesFor(stack, RecipeIngredientRole.OUTPUT).stream()
                    .filter(page -> page.key.equals(favorite))
                    .filter(page -> page.layout().isPresent())
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
            if (recipe != null) {
                long crafts = RecipeQuantityMath.craftsFor(this.quantity, outputPerCraft);
                children.forEach(child -> child.updateQuantity(
                        RecipeQuantityMath.inputTotal(child.quantityPerParentCraft, crafts)));
            }
            invalidateTreeLayout();
            invalidatePlanSummary();
        }

        private long displayedQuantity() {
            if (recipe == null) return quantity;
            long crafts = RecipeQuantityMath.craftsFor(quantity, outputPerCraft);
            return RecipeQuantityMath.producedTotal(outputPerCraft, crafts);
        }

        private boolean hasIngredientOptions() {
            return ingredientOptions.size() > 1;
        }

        private boolean cycleIngredientOption(int direction) {
            return cycleIngredientOption(direction, true);
        }

        private boolean cycleIngredientOption(int direction, boolean expandFavorite) {
            if (!hasIngredientOptions() || direction == 0) return false;
            ingredientOptionIndex = Math.floorMod(
                    ingredientOptionIndex + Integer.signum(direction), ingredientOptions.size());
            ingredient = ingredientOptions.get(ingredientOptionIndex);
            stack = ingredientItemStack(ingredient);
            recipe = null;
            outputPerCraft = 1;
            children = List.of();
            if (expandFavorite) expandFavoriteRecipe();
            invalidateTreeLayout();
            invalidatePlanSummary();
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

        private PlanNode childFor(ITypedIngredient<?> selected) {
            return children.stream()
                    .filter(child -> sameIngredient(child.ingredient, selected)
                            || child.ingredientOptions.stream().anyMatch(option ->
                            sameIngredient(option, selected)))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record IngredientSummary(
            ITypedIngredient<?> ingredient,
            long gross,
            long remaining,
            List<PlanNode> nodes) {
    }

    private static final class MutableIngredientSummary {
        private final ITypedIngredient<?> ingredient;
        private final List<PlanNode> nodes = new ArrayList<>();
        private long gross;
        private long remaining;

        private MutableIngredientSummary(ITypedIngredient<?> ingredient) {
            this.ingredient = ingredient;
        }

        private IngredientSummary freeze() {
            return new IngredientSummary(ingredient, gross, remaining, List.copyOf(nodes));
        }
    }

    private record ByproductCoverage(
            long amount,
            long request) {
    }

    private static final class MutableByproductSupply {
        private final PlanNode source;
        private long remaining;

        private MutableByproductSupply(PlanNode source, long remaining) {
            this.source = source;
            this.remaining = remaining;
        }
    }

    private record ByproductLink(
            PlanNode source,
            PlanNode target,
            long amount) {
    }

    private record ByproductEdgeKey(
            PlanNode source,
            PlanNode target) {
    }

    private record CurveEndpoints(
            double startX,
            double startY,
            double endX,
            double endY) {
    }

    private record ByproductConsumption(
            long amount,
            List<ByproductLink> links) {
    }

    private record PlanSummary(
            List<IngredientSummary> materials,
            List<IngredientSummary> byproducts,
            Map<PlanNode, ByproductCoverage> coverage,
            List<ByproductLink> links) {
        private static PlanSummary empty() {
            return new PlanSummary(List.of(), List.of(), Map.of(), List.of());
        }
    }

    private enum SummaryKind {
        MATERIAL,
        BYPRODUCT
    }

    private enum InspectorTab {
        RECIPE("Recipe"),
        MATERIALS("Materials"),
        BYPRODUCTS("Byproducts");

        private final String label;

        InspectorTab(String label) {
            this.label = label;
        }
    }

    private record InspectorTabHitbox(
            InspectorTab tab,
            int left,
            int top,
            int width,
            int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + width
                    && mouseY >= top && mouseY < top + height;
        }
    }

    private record SummaryPanelBounds(
            int left,
            int top,
            int width,
            int height) {
    }

    private record SummarySectionBounds(
            int left,
            int top,
            int width,
            int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + width
                    && mouseY >= top && mouseY < top + height;
        }
    }

    private record SummaryRowHitbox(
            IngredientSummary entry,
            SummaryKind kind,
            int left,
            int top,
            int width,
            int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + width
                    && mouseY >= top && mouseY < top + height;
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

    private record CompactTreeLayout(
            List<TreeLayoutNode> nodes,
            Map<PlanNode, TreeLayoutNode> nodesByPlan,
            int width,
            int height) {
    }

    private record RawTreeLayoutNode(
            PlanNode node,
            double left,
            int top,
            int width,
            int height,
            int parentIndex) {
    }

    private static final class LayoutDraft {
        private final PlanNode node;
        private final NodeSize size;
        private final List<LayoutDraft> children;
        private final List<Double> minimumContour;
        private final List<Double> maximumContour;
        private double offsetX;

        private LayoutDraft(
                PlanNode node,
                NodeSize size,
                List<LayoutDraft> children,
                List<Double> minimumContour,
                List<Double> maximumContour) {
            this.node = node;
            this.size = size;
            this.children = children;
            this.minimumContour = minimumContour;
            this.maximumContour = maximumContour;
        }
    }

    private record NodeSize(
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
            double renderScale,
            int layoutLeft,
            int layoutTop) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
        }

        Optional<IRecipeSlotDrawable> recipeSlotUnderMouse(double mouseX, double mouseY) {
            IRecipeLayoutDrawable<?> recipeLayout = page.requireLayout();
            recipeLayout.setPosition(layoutLeft, layoutTop);
            return recipeLayout.getRecipeSlotUnderMouse(
                    recipeMouseX(mouseX),
                    recipeMouseY(mouseY));
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
