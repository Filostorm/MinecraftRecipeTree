package com.recipetree.neiexport1710;

import codechicken.nei.guihook.GuiContainerManager;
import com.google.gson.stream.JsonWriter;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ItemCatalog {
    private static final class IconRenderResult {
        final BufferedImage image;
        final boolean adaptedBotaniaCocoon;
        final boolean adaptedBotaniaPrism;
        final boolean adaptedGalacticraftFlag;
        final boolean adaptedWrcbeTriangulator;
        final boolean adaptedModernMarkingsCrossing;
        final boolean adaptedThaumcraftRunedStone;
        final boolean adaptedProjectBlueControlPanel;
        final boolean adaptedBuildCraftPhasedFacade;
        final boolean adaptedDraconicMobSoul;

        IconRenderResult(
                BufferedImage image,
                boolean adaptedBotaniaCocoon,
                boolean adaptedBotaniaPrism,
                boolean adaptedGalacticraftFlag,
                boolean adaptedWrcbeTriangulator,
                boolean adaptedModernMarkingsCrossing,
                boolean adaptedThaumcraftRunedStone,
                boolean adaptedProjectBlueControlPanel,
                boolean adaptedBuildCraftPhasedFacade,
                boolean adaptedDraconicMobSoul) {
            this.image = image;
            this.adaptedBotaniaCocoon = adaptedBotaniaCocoon;
            this.adaptedBotaniaPrism = adaptedBotaniaPrism;
            this.adaptedGalacticraftFlag = adaptedGalacticraftFlag;
            this.adaptedWrcbeTriangulator = adaptedWrcbeTriangulator;
            this.adaptedModernMarkingsCrossing = adaptedModernMarkingsCrossing;
            this.adaptedThaumcraftRunedStone = adaptedThaumcraftRunedStone;
            this.adaptedProjectBlueControlPanel = adaptedProjectBlueControlPanel;
            this.adaptedBuildCraftPhasedFacade = adaptedBuildCraftPhasedFacade;
            this.adaptedDraconicMobSoul = adaptedDraconicMobSoul;
        }
    }

    static final class Entry {
        final StackIdentity identity;
        final String name;
        final String icon;

        Entry(StackIdentity identity, String name, String icon) {
            this.identity = identity;
            this.name = name;
            this.icon = icon;
        }
    }

    private final ExportContext context;
    private final JsonWriter writer;
    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
    private BotaniaCocoonIconRenderer botaniaCocoonIconRenderer;
    private BotaniaPrismIconRenderer botaniaPrismIconRenderer;
    private GalacticraftFlagIconRenderer galacticraftFlagIconRenderer;
    private WrcbeTriangulatorIconRenderer wrcbeTriangulatorIconRenderer;
    private ModernMarkingsCrossingIconRenderer modernMarkingsCrossingIconRenderer;
    private ThaumcraftRunedStoneIconRenderer thaumcraftRunedStoneIconRenderer;
    private ProjectBlueControlPanelIconRenderer projectBlueControlPanelIconRenderer;
    private boolean closed;

    ItemCatalog(ExportContext context) throws IOException {
        this.context = context;
        writer = ExportContext.jsonWriter(context.root.resolve("items.json"));
        writer.beginObject().name("items").beginArray();
    }

    Entry ensure(ItemStack stack) throws IOException {
        final StackIdentity identity;
        try {
            identity = StackIdentity.of(stack);
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY", "could not canonicalize " + stack, error);
        }
        return ensure(identity);
    }

    Entry ensure(StackIdentity identity) throws IOException {
        return ensure(identity, null, null);
    }

    Entry ensure(
            StackIdentity identity,
            GregTechForestryScannedSaplingPreflight.DisplayNameAuthorization
                    forestryScannedSaplingAuthorization,
            GregTechForestryScannedPollenPreflight.DisplayNameAuthorization
                    forestryScannedPollenAuthorization)
            throws IOException {
        ItemStack stack = identity.stack;
        boolean scannedSapling = DisplayNameResolver
                .FORESTRY_SCANNED_SAPLING_CANONICAL_KEY.equals(identity.key);
        boolean scannedPollen = DisplayNameResolver
                .FORESTRY_SCANNED_POLLEN_CANONICAL_KEY.equals(identity.key);
        boolean saplingAuthorized = forestryScannedSaplingAuthorization != null;
        boolean pollenAuthorized = forestryScannedPollenAuthorization != null;
        if (saplingAuthorized && pollenAuthorized) {
            throw new ExportFailure(
                    "ITEM_IDENTITY",
                    "multiple Forestry scanner display-name authorizations for " + identity.key);
        }
        if (saplingAuthorized != scannedSapling) {
            throw new ExportFailure(
                    "ITEM_IDENTITY",
                    DisplayNameResolver.FORESTRY_SCANNED_SAPLING_NAME_CONTRACT
                            + " catalog authorization mismatch for " + identity.key
                            + "; authorized=" + saplingAuthorized);
        }
        if (pollenAuthorized != scannedPollen) {
            throw new ExportFailure(
                    "ITEM_IDENTITY",
                    DisplayNameResolver.FORESTRY_SCANNED_POLLEN_NAME_CONTRACT
                            + " catalog authorization mismatch for " + identity.key
                            + "; authorized=" + pollenAuthorized);
        }
        if (saplingAuthorized) {
            String authorizedName = forestryScannedSaplingAuthorization
                    .claimDisplayName(identity);
            if (!DisplayNameResolver.FORESTRY_SCANNED_SAPLING_NAME
                    .equals(authorizedName)) {
                throw new ExportFailure(
                        "ITEM_IDENTITY",
                        DisplayNameResolver.FORESTRY_SCANNED_SAPLING_NAME_CONTRACT
                                + " authorization returned a drifted display name");
            }
        }
        if (pollenAuthorized) {
            String authorizedName = forestryScannedPollenAuthorization
                    .claimDisplayName(identity);
            if (!DisplayNameResolver.FORESTRY_SCANNED_POLLEN_NAME
                    .equals(authorizedName)) {
                throw new ExportFailure(
                        "ITEM_IDENTITY",
                        DisplayNameResolver.FORESTRY_SCANNED_POLLEN_NAME_CONTRACT
                                + " authorization returned a drifted display name");
            }
        }
        Entry known = entries.get(identity.key);
        if (known != null) {
            if (!known.identity.sameLogicalIdentity(identity)) {
                throw new ExportFailure("ITEM_IDENTITY", "canonical key collision for " + identity.key);
            }
            return known;
        }

        final String name;
        try {
            DisplayNameResolver.Result resolved = DisplayNameResolver.resolve(
                    identity, saplingAuthorized, pollenAuthorized);
            name = resolved.name;
            if (resolved.knowledgeIndependentAspect) {
                context.knowledgeIndependentAspectNames++;
            }
            if (resolved.adaptedForestryScannedSapling) {
                context.adaptedForestryScannedSaplingDisplayNames++;
                GtnhNeiExportMod.LOGGER.warn(
                        "[gtnh-nei-export] Applied exact display-name policy {} to {}",
                        DisplayNameResolver.FORESTRY_SCANNED_SAPLING_NAME_CONTRACT,
                        identity.key);
            }
            if (resolved.adaptedForestryScannedPollen) {
                context.adaptedForestryScannedPollenDisplayNames++;
                GtnhNeiExportMod.LOGGER.warn(
                        "[gtnh-nei-export] Applied exact display-name policy {} to {}",
                        DisplayNameResolver.FORESTRY_SCANNED_POLLEN_NAME_CONTRACT,
                        identity.key);
            }
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY", "display name failed for " + identity.key, error);
        }
        if (name == null || name.trim().isEmpty()) {
            throw new ExportFailure("ITEM_IDENTITY", "blank display name for " + identity.key);
        }

        String relativeIcon = "icons/" + identity.type + "/"
                + Naming.sanitize(identity.namespace()) + "/"
                + Naming.sha256(identity.key) + ".png";
        final IconRenderResult rendered;
        try {
            rendered = renderIcon(stack);
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_ICON_RENDER", identity.key, error);
        }
        BufferedImage image = rendered.image;
        String unusable = RenderedImageValidation.unusableReason(image);
        if (unusable != null) {
            throw new ExportFailure("ITEM_ICON_RENDER", identity.key + ": " + unusable);
        }
        if (rendered.adaptedBotaniaCocoon) {
            context.adaptedBotaniaCocoonItemIcons++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Applied exact item icon render policy {} to {}",
                    BotaniaCocoonIconRenderer.CONTRACT, identity.key);
        }
        if (rendered.adaptedBotaniaPrism) {
            context.adaptedBotaniaPrismItemIcons++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Applied exact item icon render policy {} to {}",
                    BotaniaPrismIconRenderer.CONTRACT, identity.key);
        }
        if (rendered.adaptedGalacticraftFlag) {
            context.adaptedGalacticraftFlagItemIcons++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Applied exact item icon render policy {} to {}",
                    GalacticraftFlagIconRenderer.CONTRACT, identity.key);
        }
        if (rendered.adaptedWrcbeTriangulator) {
            context.adaptedWrcbeTriangulatorItemIcons++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Applied exact item icon render policy {} to {}",
                    WrcbeTriangulatorIconRenderer.CONTRACT, identity.key);
        }
        if (rendered.adaptedModernMarkingsCrossing) {
            context.adaptedModernMarkingsCrossingItemIcons++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Applied exact item icon render policy {} to {}",
                    ModernMarkingsCrossingIconRenderer.CONTRACT, identity.key);
        }
        if (rendered.adaptedThaumcraftRunedStone) {
            context.adaptedThaumcraftRunedStoneItemIcons++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Applied exact item icon render policy {} to {}",
                    ThaumcraftRunedStoneIconRenderer.CONTRACT, identity.key);
        }
        if (rendered.adaptedProjectBlueControlPanel) {
            context.adaptedProjectBlueControlPanelItemIcons++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Applied exact item icon render policy {} to {}",
                    ProjectBlueControlPanelIconRenderer.CONTRACT, identity.key);
        }
        if (rendered.adaptedBuildCraftPhasedFacade) {
            context.adaptedBuildCraftPhasedFacadeItemIcons++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Applied exact item icon render policy {} to {}",
                    BuildCraftPhasedFacadeIconRenderer.CONTRACT, identity.key);
        }
        if (rendered.adaptedDraconicMobSoul) {
            context.adaptedDraconicMobSoulItemIcons++;
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Applied exact item icon render policy {} to {}",
                    DraconicMobSoulIconRenderer.CONTRACT, identity.key);
        }
        Path iconFile = context.root.resolve(relativeIcon);
        context.submitImage(image, iconFile);

        writer.beginObject();
        writer.name("k").value(identity.key);
        writer.name("id").value(identity.registryId);
        writer.name("n").value(name);
        writer.name("m").value(identity.namespace());
        if (identity.isFluid()) {
            writer.name("t").value("fluid");
        }
        writer.name("icon").value(relativeIcon);
        writer.endObject();

        Entry entry = new Entry(identity, name, relativeIcon);
        entries.put(identity.key, entry);
        context.itemIconsRendered++;
        return entry;
    }

    Entry requireExisting(StackIdentity identity) throws ExportFailure {
        Entry known = entries.get(identity.key);
        if (known == null) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "required pre-existing catalog identity is absent: " + identity.key);
        }
        if (!known.identity.sameLogicalIdentity(identity)) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "canonical key collision for required pre-existing identity "
                            + identity.key);
        }
        return known;
    }

    private IconRenderResult renderIcon(ItemStack source) throws Exception {
        final ItemStack stack = source.copy();
        stack.stackSize = 1;
        final boolean adaptedBotaniaCocoon =
                StackIdentity.isPinnedBotaniaCocoonIconTarget(stack);
        final boolean adaptedBotaniaPrism =
                StackIdentity.isPinnedBotaniaPrismIconTarget(stack);
        final boolean adaptedGalacticraftFlag =
                StackIdentity.isPinnedGalacticraftFlagIconTarget(stack);
        final boolean adaptedWrcbeTriangulator =
                StackIdentity.isPinnedWrcbeTriangulatorIconTarget(stack);
        final boolean adaptedModernMarkingsCrossing =
                StackIdentity.isPinnedModernMarkingsCrossingIconTarget(stack);
        final boolean adaptedThaumcraftRunedStone =
                StackIdentity.isPinnedThaumcraftRunedStoneIconTarget(stack);
        final boolean adaptedProjectBlueControlPanel =
                ProjectBlueControlPanelIconRenderer.isPinnedTarget(stack);
        final boolean adaptedBuildCraftPhasedFacade =
                BuildCraftPhasedFacadeIconRenderer.isPinnedTarget(StackIdentity.of(stack));
        final StackIdentity renderIdentity = StackIdentity.of(stack);
        final boolean adaptedDraconicMobSoul =
                DraconicMobSoulIconRenderer.isPinnedTarget(renderIdentity);
        final BotaniaCocoonIconRenderer cocoonRenderer;
        if (adaptedBotaniaCocoon) {
            if (botaniaCocoonIconRenderer == null) {
                botaniaCocoonIconRenderer = BotaniaCocoonIconRenderer.create();
            }
            cocoonRenderer = botaniaCocoonIconRenderer;
        } else {
            cocoonRenderer = null;
        }
        final BotaniaPrismIconRenderer prismRenderer;
        if (adaptedBotaniaPrism || adaptedWrcbeTriangulator) {
            if (botaniaPrismIconRenderer == null) {
                botaniaPrismIconRenderer = adaptedBotaniaPrism
                        ? BotaniaPrismIconRenderer.create(stack)
                        : BotaniaPrismIconRenderer.createPinnedRuntime();
            }
            prismRenderer = botaniaPrismIconRenderer;
        } else {
            prismRenderer = null;
        }
        final GalacticraftFlagIconRenderer flagRenderer;
        if (adaptedGalacticraftFlag) {
            if (galacticraftFlagIconRenderer == null) {
                galacticraftFlagIconRenderer = GalacticraftFlagIconRenderer.create(stack);
            }
            flagRenderer = galacticraftFlagIconRenderer;
        } else {
            flagRenderer = null;
        }
        final WrcbeTriangulatorIconRenderer triangulatorRenderer;
        if (adaptedWrcbeTriangulator) {
            if (wrcbeTriangulatorIconRenderer == null) {
                wrcbeTriangulatorIconRenderer =
                        WrcbeTriangulatorIconRenderer.create(stack);
                prismRenderer.attachWrcbeTriangulator(wrcbeTriangulatorIconRenderer);
            }
            triangulatorRenderer = wrcbeTriangulatorIconRenderer;
        } else {
            triangulatorRenderer = null;
        }
        final ModernMarkingsCrossingIconRenderer crossingRenderer;
        if (adaptedModernMarkingsCrossing) {
            if (modernMarkingsCrossingIconRenderer == null) {
                modernMarkingsCrossingIconRenderer =
                        ModernMarkingsCrossingIconRenderer.create();
            }
            crossingRenderer = modernMarkingsCrossingIconRenderer;
        } else {
            crossingRenderer = null;
        }
        final ThaumcraftRunedStoneIconRenderer runedStoneRenderer;
        if (adaptedThaumcraftRunedStone) {
            if (thaumcraftRunedStoneIconRenderer == null) {
                thaumcraftRunedStoneIconRenderer =
                        ThaumcraftRunedStoneIconRenderer.create();
            }
            runedStoneRenderer = thaumcraftRunedStoneIconRenderer;
        } else {
            runedStoneRenderer = null;
        }
        final ProjectBlueControlPanelIconRenderer projectBlueRenderer;
        if (adaptedProjectBlueControlPanel) {
            if (projectBlueControlPanelIconRenderer == null) {
                projectBlueControlPanelIconRenderer =
                        ProjectBlueControlPanelIconRenderer.create(stack);
            }
            projectBlueRenderer = projectBlueControlPanelIconRenderer;
        } else {
            projectBlueRenderer = null;
        }
        final BuildCraftPhasedFacadeIconRenderer buildCraftFacadeRenderer =
                adaptedBuildCraftPhasedFacade
                        ? BuildCraftPhasedFacadeIconRenderer.create(StackIdentity.of(stack))
                        : null;
        BufferedImage image = context.renderer.renderScaled(
                16, 16, ExportRequest.ICON_SCALE, 0x00000000,
                new OffscreenRenderer.DrawCall() {
            @Override
            public void draw() throws Exception {
                GL11.glPushMatrix();
                try {
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    OffscreenRenderer.DrawCall ownerInventoryDraw =
                            new OffscreenRenderer.DrawCall() {
                        @Override
                        public void draw() {
                            GuiContainerManager.drawItem(0, 0, stack, false, "");
                        }
                    };
                    OffscreenRenderer.DrawCall cocoonCompatibleDraw = ownerInventoryDraw;
                    if (cocoonRenderer != null) {
                        cocoonCompatibleDraw = new OffscreenRenderer.DrawCall() {
                            @Override
                            public void draw() throws Exception {
                                cocoonRenderer.drawExactlyOnce(ownerInventoryDraw);
                            }
                        };
                    }
                    final OffscreenRenderer.DrawCall botaniaCompatibleDraw =
                            cocoonCompatibleDraw;
                    OffscreenRenderer.DrawCall allCompatibleDraw =
                            new OffscreenRenderer.DrawCall() {
                        @Override
                        public void draw() throws Exception {
                            if (prismRenderer == null) {
                                botaniaCompatibleDraw.draw();
                            } else if (triangulatorRenderer != null) {
                                triangulatorRenderer.drawExactlyOnce(
                                        prismRenderer, botaniaCompatibleDraw);
                            } else {
                                prismRenderer.drawExactlyOnce(botaniaCompatibleDraw);
                            }
                        }
                    };
                    final OffscreenRenderer.DrawCall projectBlueCompatibleDraw;
                    if (projectBlueRenderer == null) {
                        projectBlueCompatibleDraw = allCompatibleDraw;
                    } else {
                        projectBlueCompatibleDraw = new OffscreenRenderer.DrawCall() {
                            @Override
                            public void draw() throws Exception {
                                projectBlueRenderer.drawExactlyOnce(allCompatibleDraw);
                            }
                        };
                    }
                    if (adaptedDraconicMobSoul) {
                        DraconicMobSoulIconRenderer.draw(renderIdentity);
                    } else if (buildCraftFacadeRenderer != null) {
                        buildCraftFacadeRenderer.draw();
                    } else if (runedStoneRenderer != null) {
                        runedStoneRenderer.draw(stack);
                    } else if (crossingRenderer != null) {
                        crossingRenderer.draw(stack);
                    } else if (flagRenderer == null) {
                        projectBlueCompatibleDraw.draw();
                    } else {
                        flagRenderer.drawExactlyOnce(projectBlueCompatibleDraw);
                    }
                } finally {
                    GL11.glPopMatrix();
                }
            }
        });
        return new IconRenderResult(
                image, adaptedBotaniaCocoon, adaptedBotaniaPrism,
                adaptedGalacticraftFlag, adaptedWrcbeTriangulator,
                adaptedModernMarkingsCrossing, adaptedThaumcraftRunedStone,
                adaptedProjectBlueControlPanel, adaptedBuildCraftPhasedFacade,
                adaptedDraconicMobSoul);
    }

    BotaniaCocoonIconRenderer requireBotaniaCocoonIconRenderer() {
        if (botaniaCocoonIconRenderer == null) {
            throw new IllegalStateException(
                    "RECIPE_WIDGET_RENDER: Botania cocoon preview reached rendering before its "
                            + "pinned catalog icon initialized the compatibility adapter");
        }
        return botaniaCocoonIconRenderer;
    }

    BotaniaPrismIconRenderer requireBotaniaPrismIconRenderer() {
        if (botaniaPrismIconRenderer == null) {
            throw new IllegalStateException(
                    "RECIPE_WIDGET_RENDER: Botania prism preview reached rendering before its "
                            + "pinned catalog icon initialized the compatibility adapter");
        }
        return botaniaPrismIconRenderer;
    }

    GalacticraftFlagIconRenderer requireGalacticraftFlagIconRenderer() {
        if (galacticraftFlagIconRenderer == null) {
            throw new IllegalStateException(
                    "RECIPE_WIDGET_RENDER: Galacticraft flag preview reached rendering before "
                            + "its pinned catalog icon initialized the compatibility adapter");
        }
        return galacticraftFlagIconRenderer;
    }

    WrcbeTriangulatorIconRenderer requireWrcbeTriangulatorIconRenderer() {
        if (wrcbeTriangulatorIconRenderer == null) {
            throw new IllegalStateException(
                    "RECIPE_WIDGET_RENDER: WR-CBE triangulator preview reached rendering "
                            + "before its pinned catalog icon initialized the owner texture "
                            + "adapter");
        }
        return wrcbeTriangulatorIconRenderer;
    }

    ProjectBlueControlPanelIconRenderer requireProjectBlueControlPanelIconRenderer() {
        if (projectBlueControlPanelIconRenderer == null) {
            throw new IllegalStateException(
                    "RECIPE_WIDGET_RENDER: ProjectBlue malformed-material preview reached "
                            + "rendering before one of its three pinned catalog icons initialized "
                            + "the owner-renderer adapter");
        }
        return projectBlueControlPanelIconRenderer;
    }

    int count() {
        return entries.size();
    }

    void close() throws IOException {
        if (!closed) {
            writer.endArray().endObject();
            writer.close();
            closed = true;
        }
    }
}
