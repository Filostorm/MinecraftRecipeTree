package com.recipetree.neiexport1710;

import codechicken.nei.recipe.StackInfo;
import com.google.common.base.Optional;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemMinecart;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

final class StackIdentity implements Comparable<StackIdentity> {
    interface FluidResolver {
        Fluid resolve(String normalizedName);
    }

    static final class DecodedFluidDrop {
        final Fluid fluid;
        final int amount;
        final NBTTagCompound tag;

        private DecodedFluidDrop(Fluid fluid, int amount, NBTTagCompound tag) {
            this.fluid = fluid;
            this.amount = amount;
            this.tag = tag;
        }
    }

    static final String AE2FC_FLUID_DROP_CLASS =
            "com.glodblock.github.common.item.ItemFluidDrop";
    static final String AE2FC_FLUID_PACKET_CLASS =
            "com.glodblock.github.common.item.ItemFluidPacket";
    static final String AE2_CABLE_BUS_ITEM_CLASS =
            "appeng.block.AEBaseItemBlock";
    static final String AE2_CABLE_BUS_BLOCK_CLASS =
            "appeng.block.networking.BlockCableBus";
    static final String AE2_CABLE_BUS_TILE_CLASS =
            "appeng.tile.networking.TileCableBus";
    static final String AE2_CABLE_BUS_LAYERED_TILE_CLASS =
            "appeng.parts.layers.LayerIEnergyConnected_TileCableBus";
    static final String[] AE2_CABLE_BUS_LAYERED_TILE_HIERARCHY = {
        AE2_CABLE_BUS_LAYERED_TILE_CLASS,
        "appeng.parts.layers.LayerSidedEnvironment_TileCableBus",
        "appeng.parts.layers.LayerIEnergyHandler_TileCableBus",
        "appeng.parts.layers.LayerIPipeConnection_TileCableBus",
        "appeng.parts.layers.LayerITileStorageMonitorable_TileCableBus",
        "appeng.parts.layers.LayerIFluidHandler_TileCableBus",
        "appeng.parts.layers.LayerISidedInventory_TileCableBus",
        AE2_CABLE_BUS_TILE_CLASS
    };
    static final String AE2_CABLE_BUS_FEATURE_HANDLER_CLASS =
            "appeng.core.features.AECableBusFeatureHandler";
    static final String AE2_CABLE_BUS_ITEM_RENDERER_CLASS =
            "appeng.client.render.ItemRenderer";
    static final String AE2_CABLE_BUS_BLOCK_RENDERER_CLASS =
            "appeng.client.render.blocks.RendererCableBus";
    static final String AE2_MATRIX_FRAME_ITEM_CLASS =
            "appeng.block.AEBaseItemBlock";
    static final String AE2_MATRIX_FRAME_BLOCK_CLASS =
            "appeng.block.spatial.BlockMatrixFrame";
    static final String AE2_MATRIX_FRAME_FEATURE_HANDLER_CLASS =
            "appeng.core.features.AEBlockFeatureHandler";
    static final String AE2_MATRIX_FRAME_ITEM_RENDERER_CLASS =
            "appeng.client.render.ItemRenderer";
    static final String AE2_MATRIX_FRAME_BLOCK_RENDERER_CLASS =
            "appeng.client.render.blocks.RenderNull";
    static final String VANILLA_ITEM_BLOCK_CLASS = "net.minecraft.item.ItemBlock";
    static final String BLOOD_MAGIC_BLOOD_LIGHT_BLOCK_CLASS =
            "WayofTime.alchemicalWizardry.common.block.BlockBloodLightSource";
    static final String BLOOD_MAGIC_SPECTRAL_CONTAINER_BLOCK_CLASS =
            "WayofTime.alchemicalWizardry.common.block.BlockSpectralContainer";
    static final String ARCHITECTURE_CRAFT_CLADDING_ITEM_CLASS =
            "gcewing.architecture.common.item.ItemCladding";
    static final String AVARITIA_MATTER_CLUSTER_ITEM_CLASS =
            "fox.spiteful.avaritia.items.ItemMatterCluster";
    static final String DREAMCRAFT_NOTHING_ITEM_CLASS =
            "eu.usrv.yamcore.items.ItemBase";
    static final String DREAMCRAFT_NH_ITEM_LIST_CLASS =
            "com.dreammaster.item.NHItemList";
    static final String DREAMCRAFT_SIMPLE_ITEM_WRAPPER_CLASS =
            "eu.usrv.yamcore.items.ModSimpleBaseItem";
    static final String DREAMCRAFT_GENERIC_TAB_CLASS =
            "eu.usrv.yamcore.creativetabs.ModCreativeTab";
    static final String DREAMCRAFT_NOTHING_UNLOCALIZED_NAME = "item.Nothing";
    static final String DREAMCRAFT_NOTHING_ICON = "dreamcraft:itemNothing";
    static final String DREAMCRAFT_GENERIC_TAB_LABEL = "tabDreamCraftItems_Generic";
    static final String LITTLE_TILES_ITEM_CLASS =
            "com.creativemd.littletiles.common.items.ItemBlockTiles";
    static final String LITTLE_TILES_BLOCK_CLASS =
            "com.creativemd.littletiles.common.blocks.BlockTile";
    static final String LITTLE_TILES_TILE_ENTITY_CLASS =
            "com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles";
    static final String LITTLE_TILES_RENDERER_CLASS =
            "com.creativemd.littletiles.client.render.SpecialBlockTilesRenderer";
    static final String LITTLE_TILES_DYNAMIC_RENDERER_INTERFACE =
            "com.creativemd.littletiles.client.render.ITilesRenderer";
    static final String LITTLE_TILES_OWNER_CLASS =
            "com.creativemd.littletiles.LittleTiles";
    static final String LITTLE_TILES_CLIENT_CLASS =
            "com.creativemd.littletiles.client.LittleTilesClient";
    static final String LITTLE_TILES_COMPOSITE_ITEM_CLASS =
            "com.creativemd.littletiles.common.items.ItemMultiTiles";
    static final String LITTLE_TILES_TILE_ENTITY_ID = "LittleTilesTileEntity";
    static final String LITTLE_TILES_BLOCK_UNLOCALIZED_NAME = "tile.LTTile";
    static final String MALISIS_DOORS_CUSTOM_DOOR_ITEM_CLASS =
            "net.malisis.doors.door.item.CustomDoorItem";
    static final String MALISIS_DOORS_DOOR_ITEM_CLASS =
            "net.malisis.doors.door.item.DoorItem";
    static final String MALISIS_DOORS_CUSTOM_DOOR_RENDERER_CLASS =
            "net.malisis.doors.door.renderer.CustomDoorRenderer";
    static final String MALISIS_DOORS_DOOR_RENDERER_CLASS =
            "net.malisis.doors.door.renderer.DoorRenderer";
    static final String MALISIS_DOORS_CUSTOM_DOOR_TILE_ENTITY_CLASS =
            "net.malisis.doors.door.tileentity.CustomDoorTileEntity";
    static final String MALISIS_DOORS_CUSTOM_DOOR_UNLOCALIZED_NAME = "item.custom_door";
    static final String MALISIS_DOORS_MIXED_BLOCK_ITEM_CLASS =
            "net.malisis.doors.item.MixedBlockBlockItem";
    static final String MALISIS_DOORS_MIXED_BLOCK_CLASS =
            "net.malisis.doors.block.MixedBlock";
    static final String MALISIS_DOORS_MIXED_BLOCK_RENDERER_CLASS =
            "net.malisis.doors.renderer.MixedBlockRenderer";
    static final String MALISIS_DOORS_MIXED_BLOCK_TILE_ENTITY_CLASS =
            "net.malisis.doors.entity.MixedBlockTileEntity";
    static final String MALISIS_DOORS_BLOCK_MIXER_TILE_ENTITY_CLASS =
            "net.malisis.doors.entity.BlockMixerTileEntity";
    static final String MALISIS_DOORS_MIXED_BLOCK_UNLOCALIZED_NAME = "tile.mixed_block";
    static final String[] MALISIS_DOORS_MIXED_BLOCK_NBT_KEYS = {
        "block1", "block2", "metadata1", "metadata2"
    };
    static final String MODERN_MARKINGS_FLOOR_BLOCK_CLASS =
            "modernmarkings.blocks.MarkingFloor";
    static final String[] MODERN_MARKINGS_CROSSING_REGISTRY_IDS = {
        "modernmarkings:tile.floor_marking_blue_crossing",
        "modernmarkings:tile.floor_marking_green_crossing",
        "modernmarkings:tile.floor_marking_orange_crossing",
        "modernmarkings:tile.floor_marking_red_crossing",
        "modernmarkings:tile.floor_marking_white_crossing",
        "modernmarkings:tile.floor_marking_yellow_crossing"
    };
    static final String THAUMCRAFT_ELDRITCH_ITEM_CLASS =
            "thaumcraft.common.blocks.BlockEldritchItem";
    static final String THAUMCRAFT_ELDRITCH_BLOCK_CLASS =
            "thaumcraft.common.blocks.BlockEldritch";
    static final String THAUMCRAFT_ELDRITCH_TRAP_TILE_CLASS =
            "thaumcraft.common.tiles.TileEldritchTrap";
    static final int PINNED_RAW_ITEM_LIST_COUNT = 56038;
    static final int PINNED_CATALOG_EXCLUSION_COUNT = 46;
    static final int PINNED_RETAINED_ITEM_LIST_COUNT = 55992;
    static final int PINNED_RETAINED_UNIQUE_ITEM_LIST_IDENTITY_COUNT = 55991;
    static final String BOTANIA_BURIED_PETALS_ITEM_CLASS =
            "vazkii.botania.common.item.block.ItemBlockWithMetadataAndName";
    static final String BOTANIA_ITEM_BLOCK_MOD_CLASS =
            "vazkii.botania.common.item.block.ItemBlockMod";
    static final String BOTANIA_BIFROST_BLOCK_CLASS =
            "vazkii.botania.common.block.BlockBifrost";
    static final String BOTANIA_BURIED_PETALS_BLOCK_CLASS =
            "vazkii.botania.common.block.decor.BlockBuriedPetals";
    static final String BOTANIA_CACOPHONIUM_BLOCK_CLASS =
            "vazkii.botania.common.block.BlockCacophonium";
    static final String BOTANIA_COCOON_BLOCK_CLASS =
            "vazkii.botania.common.block.BlockCocoon";
    static final String BOTANIA_COCOON_CANONICAL_KEY =
            "item|Botania:cocoon|meta=0|nbt=-";
    static final String BOTANIA_PRISM_BLOCK_CLASS =
            "vazkii.botania.common.block.mana.BlockPrism";
    static final String BOTANIA_PRISM_CANONICAL_KEY =
            "item|Botania:prism|meta=0|nbt=-";
    static final String GALACTICRAFT_FLAG_ITEM_CLASS =
            "micdoodle8.mods.galacticraft.core.items.ItemFlag";
    static final String GALACTICRAFT_FLAG_CANONICAL_KEY =
            "item|GalacticraftCore:item.flag|meta=0|nbt=-";
    static final String WRCBE_TRIANGULATOR_ITEM_CLASS =
            "codechicken.wirelessredstone.addons.ItemWirelessTriangulator";
    static final String WRCBE_TRIANGULATOR_CANONICAL_KEY =
            "item|WR-CBE|Addons:triangulator|meta=0|nbt=-";
    static final String WRCBE_TRIANGULATOR_WILDCARD_CANONICAL_KEY =
            "item|WR-CBE|Addons:triangulator|meta=32767|nbt=-";
    static final String STEVES_CARTS_MODULAR_CART_ITEM_CLASS =
            "vswe.stevescarts.Items.ItemCarts";
    static final String STEVES_CARTS_MODULAR_CART_RENDERER_CLASS =
            "vswe.stevescarts.Renders.RendererMinecartItem";
    static final String TCONSTRUCT_BATTLESIGN_BLOCK_CLASS =
            "tconstruct.tools.blocks.BattlesignBlock";
    static final String TCONSTRUCT_BATTLESIGN_PUBLIC_ITEM_CLASS =
            "tconstruct.items.tools.BattleSign";
    static final String TCONSTRUCT_BATTLESIGN_BLOCK_RENDERER_CLASS =
            "tconstruct.tools.model.BattlesignRender";
    static final String TCONSTRUCT_BATTLESIGN_PUBLIC_ITEM_RENDERER_CLASS =
            "tconstruct.client.FlexibleToolRenderer";
    static final String TCONSTRUCT_HELD_ITEM_BLOCK_CLASS =
            "tconstruct.tools.blocks.EquipBlock";
    static final String TCONSTRUCT_HELD_ITEM_TILE_CLASS =
            "tconstruct.tools.logic.FrypanLogic";
    static final String TCONSTRUCT_HELD_ITEM_PUBLIC_ITEM_CLASS =
            "tconstruct.items.tools.FryingPan";
    static final String TCONSTRUCT_HELD_ITEM_BLOCK_RENDERER_CLASS =
            "tconstruct.tools.model.FrypanRender";
    static final String TCONSTRUCT_HELD_ITEM_PUBLIC_ITEM_RENDERER_CLASS =
            "tconstruct.client.FlexibleToolRenderer";
    static final String THAUMCRAFT_BLOCK_HOLE_CLASS =
            "thaumcraft.common.blocks.BlockHole";
    static final String THAUMCRAFT_TILE_HOLE_CLASS =
            "thaumcraft.common.tiles.TileHole";
    static final String THAUMCRAFT_PORTABLE_HOLE_FOCUS_CLASS =
            "thaumcraft.common.items.wands.foci.ItemFocusPortableHole";
    static final String THAUMCRAFT_BLOCK_HOLE_BLANK_ICON = "thaumcraft:blank";
    static final String THAUMCRAFT_BLOCK_HOLE_EMPTY_SENTINEL_ICON = "thaumcraft:empty";
    static final String THAUMCRAFT_ELDRITCH_PORTAL_BLOCK_CLASS =
            "thaumcraft.common.blocks.BlockEldritchPortal";
    static final String THAUMCRAFT_ELDRITCH_PORTAL_TILE_CLASS =
            "thaumcraft.common.tiles.TileEldritchPortal";
    static final String THAUMCRAFT_ELDRITCH_OBJECT_ITEM_CLASS =
            "thaumcraft.common.items.ItemEldritchObject";
    static final String THAUMCRAFT_ELDRITCH_PORTAL_BLANK_ICON = "thaumcraft:blank";
    static final String THAUMCRAFT_ELDRITCH_OBJECT_ICON = "thaumcraft:eldritch_object";
    static final String GADOMANCY_ELDRITCH_PORTAL_ITEM_CLASS =
            "makeo.gadomancy.common.items.ItemBlockAdditionalEldritchPortal";
    static final String GADOMANCY_ELDRITCH_PORTAL_BLOCK_CLASS =
            "makeo.gadomancy.common.blocks.BlockAdditionalEldritchPortal";
    static final String GADOMANCY_ELDRITCH_PORTAL_ICON = "gadomancy:eldritch_portal";
    static final String THAUMIC_HORIZONS_LIGHT_BLOCK_CLASS =
            "com.kentington.thaumichorizons.common.blocks.BlockLight";
    static final String THAUMIC_HORIZONS_SOLAR_LIGHT_BLOCK_CLASS =
            "com.kentington.thaumichorizons.common.blocks.BlockLightSolar";
    static final String THAUMIC_HORIZONS_LIGHT_TILE_CLASS =
            "com.kentington.thaumichorizons.common.tiles.TileLight";
    static final String THAUMIC_HORIZONS_ILLUMINATION_FOCUS_ITEM_CLASS =
            "com.kentington.thaumichorizons.common.items.ItemFocusIllumination";
    static final String THAUMIC_HORIZONS_LIGHT_BLANK_ICON = "thaumcraft:blank";
    static final String TWILIGHT_FOREST_EXPERIMENT_115_BLOCK_CLASS =
            "twilightforest.block.BlockTFExperiment115";
    static final String TWILIGHT_FOREST_EXPERIMENT_115_ITEM_BLOCK_CLASS =
            "twilightforest.item.ItemBlockTFMeta";
    static final String TWILIGHT_FOREST_EXPERIMENT_115_PUBLIC_ITEM_CLASS =
            "twilightforest.item.ItemTFFood";
    static final String TWILIGHT_FOREST_EXPERIMENT_115_TILE_CLASS =
            "twilightforest.tileentity.TileEntityTFCake";
    static final String TWILIGHT_FOREST_EXPERIMENT_115_BLOCK_RENDERER_CLASS =
            "twilightforest.client.renderer.blocks.RenderBlockTFCake";
    static final String WITCHING_GADGETS_CUSTOM_AIR_BLOCK_CLASS =
            "witchinggadgets.common.blocks.BlockModifiedAiry";
    static final String WITCHING_GADGETS_TEMP_LIGHT_TILE_CLASS =
            "witchinggadgets.common.blocks.tiles.TileEntityTempLight";
    static final String WITCHING_GADGETS_CUSTOM_AIR_BLANK_ICON = "thaumcraft:blank";
    static final String BOTANIA_ENCHANTER_BLOCK_CLASS =
            "vazkii.botania.common.block.mana.BlockEnchanter";
    static final String BOTANIA_FAKE_AIR_BLOCK_CLASS =
            "vazkii.botania.common.block.BlockFakeAir";
    static final String BOTANIA_MANA_FLAME_BLOCK_CLASS =
            "vazkii.botania.common.block.decor.BlockManaFlame";
    static final String BOTANIA_SOLID_VINE_BLOCK_CLASS =
            "vazkii.botania.common.block.BlockSolidVines";
    static final String BOTANIA_STRUCTURE_LIB_FLOWER_BLOCK_CLASS =
            "vazkii.botania.common.block.tile.TileEnchanter$StructureLibFlower";
    static final String CARPENTERS_BED_BLOCK_CLASS =
            "com.carpentersblocks.block.BlockCarpentersBed";
    static final String CARPENTERS_DOOR_BLOCK_CLASS =
            "com.carpentersblocks.block.BlockCarpentersDoor";
    static final String CARPENTERS_BED_PUBLIC_ITEM_CLASS =
            "com.carpentersblocks.item.ItemCarpentersBed";
    static final String CARPENTERS_DOOR_PUBLIC_ITEM_CLASS =
            "com.carpentersblocks.item.ItemCarpentersDoor";
    static final CatalogExclusion AE2FC_FLUID_DROP_PLACEHOLDER = new CatalogExclusion(
            "ae2fc:fluid_drop",
            AE2FC_FLUID_DROP_CLASS,
            null,
            "ae2fc-fluid-drop-nei-damage-search-placeholder-v1",
            "browser-placeholder", 1, 0x1, false);
    static final CatalogExclusion AE2FC_FLUID_PACKET_PLACEHOLDER = new CatalogExclusion(
            "ae2fc:fluid_packet",
            AE2FC_FLUID_PACKET_CLASS,
            null,
            "ae2fc-fluid-packet-vanilla-subitems-placeholder-v1",
            "browser-placeholder", 1, 0x1, false);
    static final CatalogExclusion AE2_CABLE_BUS_INTERNAL_WORLD_ITEM_BLOCK =
            new CatalogExclusion(
                    "appliedenergistics2:tile.BlockCableBus",
                    AE2_CABLE_BUS_ITEM_CLASS,
                    AE2_CABLE_BUS_BLOCK_CLASS,
                    "ae2-cablebus-internal-multipart-world-host-itemblock-v1",
                    "owner-internal-multipart-world-host", 1, 0x1, true);
    static final CatalogExclusion AE2_MATRIX_FRAME_INTERNAL_WORLD_ITEM_BLOCK =
            new CatalogExclusion(
                    "appliedenergistics2:tile.BlockMatrixFrame",
                    AE2_MATRIX_FRAME_ITEM_CLASS,
                    AE2_MATRIX_FRAME_BLOCK_CLASS,
                    "ae2-matrix-frame-owner-internal-spatial-storage-world-substrate-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion BLOOD_MAGIC_BLOOD_LIGHT_HELPER = new CatalogExclusion(
            "AWWayofTime:bloodLight",
            VANILLA_ITEM_BLOCK_CLASS,
            BLOOD_MAGIC_BLOOD_LIGHT_BLOCK_CLASS,
            "bloodmagic-blood-light-internal-world-helper-itemlist-entry-v1",
            "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion BLOOD_MAGIC_SPECTRAL_CONTAINER_HELPER =
            new CatalogExclusion(
                    "AWWayofTime:spectralContainer",
                    VANILLA_ITEM_BLOCK_CLASS,
                    BLOOD_MAGIC_SPECTRAL_CONTAINER_BLOCK_CLASS,
                    "bloodmagic-spectral-container-internal-world-helper-itemlist-entry-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion ARCHITECTURE_CRAFT_CLADDING_PLACEHOLDER =
            new CatalogExclusion(
                    "ArchitectureCraft:cladding",
                    ARCHITECTURE_CRAFT_CLADDING_ITEM_CLASS,
                    null,
                    "architecturecraft-materialless-cladding-vanilla-subitems-placeholder-v1",
                    "browser-placeholder", 1, 0x1, false);
    static final CatalogExclusion AVARITIA_EMPTY_MATTER_CLUSTER_PLACEHOLDER =
            new CatalogExclusion(
                    "Avaritia:Matter_Cluster",
                    AVARITIA_MATTER_CLUSTER_ITEM_CLASS,
                    null,
                    "avaritia-empty-matter-cluster-vanilla-subitems-placeholder-v1",
                    "browser-placeholder", 1, 0x1, false);
    static final CatalogExclusion DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL =
            new CatalogExclusion(
                    "dreamcraft:item.Nothing",
                    DREAMCRAFT_NOTHING_ITEM_CLASS,
                    null,
                    "dreamcraft-nothing-orphaned-legacy-lootbag-empty-reward-sentinel-v1",
                    "presentation-placeholder", 1, 0x1, true);
    static final CatalogExclusion LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER =
            new CatalogExclusion(
                    "littletiles:BlockLittleTiles",
                    LITTLE_TILES_ITEM_CLASS,
                    LITTLE_TILES_BLOCK_CLASS,
                    "littletiles-unparameterized-microtile-carrier-nei-damage-search-v1",
                    "owner-internal-world-state", 1, 0x1, false);
    static final CatalogExclusion MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER =
            new CatalogExclusion(
                    "malisisdoors:item.custom_door",
                    MALISIS_DOORS_CUSTOM_DOOR_ITEM_CLASS,
                    null,
                    "malisisdoors-unconfigured-custom-door-carrier-nei-getsubitems-v1",
                    "owner-internal-unconfigured-dynamic-item", 1, 0x1, false);
    static final CatalogExclusion MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER =
            new CatalogExclusion(
                    "malisisdoors:mixed_block",
                    MALISIS_DOORS_MIXED_BLOCK_ITEM_CLASS,
                    MALISIS_DOORS_MIXED_BLOCK_CLASS,
                    "malisisdoors-unconfigured-mixed-block-carrier-nei-getsubitems-v1",
                    "owner-internal-unconfigured-dynamic-item", 1, 0x1, false);
    static final CatalogExclusion BOTANIA_BIFROST_WORLD_STATE =
            new CatalogExclusion(
                    "Botania:bifrost",
                    BOTANIA_ITEM_BLOCK_MOD_CLASS,
                    BOTANIA_BIFROST_BLOCK_CLASS,
                    "botania-bifrost-owner-internal-world-state-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion BOTANIA_BURIED_PETALS_WORLD_STATE_VARIANT =
            new CatalogExclusion(
                    "Botania:buriedPetals",
                    BOTANIA_BURIED_PETALS_ITEM_CLASS,
                    BOTANIA_BURIED_PETALS_BLOCK_CLASS,
                    "botania-buried-petals-owner-internal-world-state-itemblock-variants-v1",
                    "owner-internal-world-state", 16, 0xffff, true);
    static final CatalogExclusion BOTANIA_CACOPHONIUM_BLOCK_WORLD_STATE =
            new CatalogExclusion(
                    "Botania:cacophoniumBlock",
                    BOTANIA_ITEM_BLOCK_MOD_CLASS,
                    BOTANIA_CACOPHONIUM_BLOCK_CLASS,
                    "botania-cacophonium-owner-internal-world-state-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion BOTANIA_ENCHANTER_WORLD_STATE =
            new CatalogExclusion(
                    "Botania:enchanter",
                    BOTANIA_ITEM_BLOCK_MOD_CLASS,
                    BOTANIA_ENCHANTER_BLOCK_CLASS,
                    "botania-enchanter-owner-internal-world-state-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion BOTANIA_FAKE_AIR_WORLD_STATE =
            new CatalogExclusion(
                    "Botania:fakeAir",
                    BOTANIA_ITEM_BLOCK_MOD_CLASS,
                    BOTANIA_FAKE_AIR_BLOCK_CLASS,
                    "botania-fake-air-owner-internal-world-state-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion BOTANIA_MANA_FLAME_WORLD_STATE =
            new CatalogExclusion(
                    "Botania:manaFlame",
                    BOTANIA_ITEM_BLOCK_MOD_CLASS,
                    BOTANIA_MANA_FLAME_BLOCK_CLASS,
                    "botania-mana-flame-owner-internal-world-state-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion BOTANIA_SOLID_VINE_WORLD_STATE =
            new CatalogExclusion(
                    "Botania:solidVine",
                    BOTANIA_ITEM_BLOCK_MOD_CLASS,
                    BOTANIA_SOLID_VINE_BLOCK_CLASS,
                    "botania-solid-vine-owner-internal-world-state-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion BOTANIA_STRUCTURE_LIB_ANY_FLOWER_PLACEHOLDER =
            new CatalogExclusion(
                    "Botania:flower_structurelib",
                    BOTANIA_ITEM_BLOCK_MOD_CLASS,
                    BOTANIA_STRUCTURE_LIB_FLOWER_BLOCK_CLASS,
                    "botania-structurelib-any-flower-presentation-placeholder-v1",
                    "presentation-placeholder", 1, 0x1, true);
    static final CatalogExclusion CARPENTERS_BED_INTERNAL_WORLD_ITEM_BLOCK =
            new CatalogExclusion(
                    "CarpentersBlocks:blockCarpentersBed",
                    VANILLA_ITEM_BLOCK_CLASS,
                    CARPENTERS_BED_BLOCK_CLASS,
                    "carpentersblocks-bed-internal-multiblock-world-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion CARPENTERS_DOOR_INTERNAL_WORLD_ITEM_BLOCK =
            new CatalogExclusion(
                    "CarpentersBlocks:blockCarpentersDoor",
                    VANILLA_ITEM_BLOCK_CLASS,
                    CARPENTERS_DOOR_BLOCK_CLASS,
                    "carpentersblocks-door-internal-multiblock-world-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion STEVES_CARTS_UNCONFIGURED_MODULAR_CART_PLACEHOLDER =
            new CatalogExclusion(
                    "StevesCarts:ModularCart",
                    STEVES_CARTS_MODULAR_CART_ITEM_CLASS,
                    null,
                    "stevescarts-unconfigured-modular-cart-global-itemlist-placeholder-v1",
                    "browser-placeholder", 1, 0x1, false);
    static final CatalogExclusion TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK =
            new CatalogExclusion(
                    "TConstruct:BattleSignBlock",
                    VANILLA_ITEM_BLOCK_CLASS,
                    TCONSTRUCT_BATTLESIGN_BLOCK_CLASS,
                    "tconstruct-battlesign-internal-equipped-tool-world-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK =
            new CatalogExclusion(
                    "TConstruct:HeldItemBlock",
                    VANILLA_ITEM_BLOCK_CLASS,
                    TCONSTRUCT_HELD_ITEM_BLOCK_CLASS,
                    "tconstruct-helditemblock-internal-equipped-frypan-world-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion THAUMCRAFT_BLOCK_HOLE_INTERNAL_WORLD_ITEM_BLOCK =
            new CatalogExclusion(
                    "Thaumcraft:blockHole",
                    VANILLA_ITEM_BLOCK_CLASS,
                    THAUMCRAFT_BLOCK_HOLE_CLASS,
                    "thaumcraft-blockhole-internal-portable-hole-world-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_WORLD_ITEM_BLOCK =
            new CatalogExclusion(
                    "Thaumcraft:blockPortalEldritch",
                    VANILLA_ITEM_BLOCK_CLASS,
                    THAUMCRAFT_ELDRITCH_PORTAL_BLOCK_CLASS,
                    "thaumcraft-eldritch-portal-owner-internal-world-state-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK =
            new CatalogExclusion(
                    "ThaumicHorizons:light",
                    VANILLA_ITEM_BLOCK_CLASS,
                    THAUMIC_HORIZONS_LIGHT_BLOCK_CLASS,
                    "thaumichorizons-base-illumination-light-owner-internal-world-state-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK =
            new CatalogExclusion(
                    "ThaumicHorizons:lightSolar",
                    VANILLA_ITEM_BLOCK_CLASS,
                    THAUMIC_HORIZONS_SOLAR_LIGHT_BLOCK_CLASS,
                    "thaumichorizons-solar-illumination-light-owner-internal-world-state-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK =
            new CatalogExclusion(
                    "TwilightForest:tile.TFExperiment115",
                    TWILIGHT_FOREST_EXPERIMENT_115_ITEM_BLOCK_CLASS,
                    TWILIGHT_FOREST_EXPERIMENT_115_BLOCK_CLASS,
                    "twilightforest-experiment115-internal-cake-world-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);
    static final CatalogExclusion WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK =
            new CatalogExclusion(
                    "WitchingGadgets:WG_CustomAir",
                    VANILLA_ITEM_BLOCK_CLASS,
                    WITCHING_GADGETS_CUSTOM_AIR_BLOCK_CLASS,
                    "witchinggadgets-custom-air-owner-internal-temporary-light-world-state-itemblock-v1",
                    "owner-internal-world-state", 1, 0x1, true);

    static final CatalogExclusion[] CATALOG_EXCLUSION_POLICIES = {
            AE2FC_FLUID_DROP_PLACEHOLDER,
            AE2FC_FLUID_PACKET_PLACEHOLDER,
            AE2_CABLE_BUS_INTERNAL_WORLD_ITEM_BLOCK,
            AE2_MATRIX_FRAME_INTERNAL_WORLD_ITEM_BLOCK,
            BLOOD_MAGIC_BLOOD_LIGHT_HELPER,
            BLOOD_MAGIC_SPECTRAL_CONTAINER_HELPER,
            ARCHITECTURE_CRAFT_CLADDING_PLACEHOLDER,
            AVARITIA_EMPTY_MATTER_CLUSTER_PLACEHOLDER,
            DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL,
            LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER,
            MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER,
            MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER,
            BOTANIA_BIFROST_WORLD_STATE,
            BOTANIA_BURIED_PETALS_WORLD_STATE_VARIANT,
            BOTANIA_CACOPHONIUM_BLOCK_WORLD_STATE,
            BOTANIA_ENCHANTER_WORLD_STATE,
            BOTANIA_FAKE_AIR_WORLD_STATE,
            BOTANIA_MANA_FLAME_WORLD_STATE,
            BOTANIA_SOLID_VINE_WORLD_STATE,
            BOTANIA_STRUCTURE_LIB_ANY_FLOWER_PLACEHOLDER,
            CARPENTERS_BED_INTERNAL_WORLD_ITEM_BLOCK,
            CARPENTERS_DOOR_INTERNAL_WORLD_ITEM_BLOCK,
            STEVES_CARTS_UNCONFIGURED_MODULAR_CART_PLACEHOLDER,
            TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK,
            TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK,
            THAUMCRAFT_BLOCK_HOLE_INTERNAL_WORLD_ITEM_BLOCK,
            THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_WORLD_ITEM_BLOCK,
            THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK,
            THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK,
            TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK,
            WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK
    };

    static final class CatalogExclusion {
        final String registryId;
        final String runtimeClass;
        final String blockRuntimeClass;
        final String contract;
        final String semanticBucket;
        final int expectedCount;
        final int expectedMetadataMask;
        final boolean strictIdentity;

        private CatalogExclusion(String registryId, String runtimeClass,
                                 String blockRuntimeClass, String contract,
                                 String semanticBucket, int expectedCount,
                                 int expectedMetadataMask, boolean strictIdentity) {
            this.registryId = registryId;
            this.runtimeClass = runtimeClass;
            this.blockRuntimeClass = blockRuntimeClass;
            this.contract = contract;
            this.semanticBucket = semanticBucket;
            this.expectedCount = expectedCount;
            this.expectedMetadataMask = expectedMetadataMask;
            this.strictIdentity = strictIdentity;
        }
    }

    static final class CatalogExclusionAudit {
        private final java.util.IdentityHashMap<CatalogExclusion, Integer> counts =
                new java.util.IdentityHashMap<CatalogExclusion, Integer>();
        private final java.util.IdentityHashMap<CatalogExclusion, Integer> metadataMasks =
                new java.util.IdentityHashMap<CatalogExclusion, Integer>();

        void record(CatalogExclusion policy, ItemStack stack) {
            if (!isKnownCatalogExclusion(policy)) {
                throw new IllegalArgumentException(
                        "ITEM_IDENTITY: unrecognized catalog exclusion policy");
            }
            if (stack == null || stack.getItem() == null) {
                throw new IllegalArgumentException(
                        "ITEM_IDENTITY: catalog exclusion audit received a null stack");
            }
            int metadata = stack.getItemDamage();
            if (metadata < 0 || metadata > 30) {
                throw new IllegalArgumentException(
                        "ITEM_IDENTITY: catalog exclusion metadata cannot be represented exactly: "
                                + metadata + "; policy=" + policy.contract);
            }
            counts.put(policy, count(policy) + 1);
            metadataMasks.put(policy, metadataMask(policy) | (1 << metadata));
        }

        int count(CatalogExclusion policy) {
            Integer value = counts.get(policy);
            return value == null ? 0 : value;
        }

        int metadataMask(CatalogExclusion policy) {
            Integer value = metadataMasks.get(policy);
            return value == null ? 0 : value;
        }

        void requireExpected() {
            StringBuilder drift = new StringBuilder();
            for (CatalogExclusion policy : CATALOG_EXCLUSION_POLICIES) {
                int observedCount = count(policy);
                int observedMask = metadataMask(policy);
                if (observedCount != policy.expectedCount
                        || observedMask != policy.expectedMetadataMask) {
                    if (drift.length() > 0) {
                        drift.append("; ");
                    }
                    drift.append(policy.registryId)
                            .append(" expected count=").append(policy.expectedCount)
                            .append(" metadataMask=0x")
                            .append(Integer.toHexString(policy.expectedMetadataMask))
                            .append(", got count=").append(observedCount)
                            .append(" metadataMask=0x")
                            .append(Integer.toHexString(observedMask));
                }
            }
            if (drift.length() > 0) {
                throw new IllegalArgumentException(
                        "ITEM_IDENTITY: pinned global NEI ItemList exclusion drift; " + drift);
            }
        }
    }

    static void requireExactGlobalItemListCardinality(
            int rawCount, int excludedCount, int retainedCount,
            int retainedUniqueIdentityCount) {
        if (rawCount != PINNED_RAW_ITEM_LIST_COUNT
                || excludedCount != PINNED_CATALOG_EXCLUSION_COUNT
                || retainedCount != PINNED_RETAINED_ITEM_LIST_COUNT
                || retainedUniqueIdentityCount
                        != PINNED_RETAINED_UNIQUE_ITEM_LIST_IDENTITY_COUNT
                || rawCount - excludedCount != retainedCount) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: pinned global NEI ItemList cardinality drift; expected raw="
                            + PINNED_RAW_ITEM_LIST_COUNT + ", excluded="
                            + PINNED_CATALOG_EXCLUSION_COUNT + ", retained="
                            + PINNED_RETAINED_ITEM_LIST_COUNT + ", retainedUnique="
                            + PINNED_RETAINED_UNIQUE_ITEM_LIST_IDENTITY_COUNT
                            + "; got raw=" + rawCount
                            + ", excluded=" + excludedCount + ", retained=" + retainedCount
                            + ", retainedUnique=" + retainedUniqueIdentityCount);
        }
    }

    private static final class PinnedCatalogItems {
        static final Item FLUID_DROP = GameRegistry.findItem("ae2fc", "fluid_drop");
        static final Item FLUID_PACKET = GameRegistry.findItem("ae2fc", "fluid_packet");
        static final Item AE2_CABLE_BUS_INTERNAL_ITEM =
                GameRegistry.findItem("appliedenergistics2", "tile.BlockCableBus");
        static final Block AE2_CABLE_BUS_BLOCK =
                GameRegistry.findBlock("appliedenergistics2", "tile.BlockCableBus");
        static final Item AE2_MATRIX_FRAME_INTERNAL_ITEM =
                GameRegistry.findItem("appliedenergistics2", "tile.BlockMatrixFrame");
        static final Block AE2_MATRIX_FRAME_BLOCK =
                GameRegistry.findBlock("appliedenergistics2", "tile.BlockMatrixFrame");
        static final Item BLOOD_LIGHT = GameRegistry.findItem("AWWayofTime", "bloodLight");
        static final Block BLOOD_LIGHT_BLOCK =
                GameRegistry.findBlock("AWWayofTime", "bloodLight");
        static final Item SPECTRAL_CONTAINER =
                GameRegistry.findItem("AWWayofTime", "spectralContainer");
        static final Block SPECTRAL_CONTAINER_BLOCK =
                GameRegistry.findBlock("AWWayofTime", "spectralContainer");
        static final Item ARCHITECTURE_CRAFT_CLADDING =
                GameRegistry.findItem("ArchitectureCraft", "cladding");
        static final Item AVARITIA_MATTER_CLUSTER =
                GameRegistry.findItem("Avaritia", "Matter_Cluster");
        static final Item DREAMCRAFT_NOTHING =
                GameRegistry.findItem("dreamcraft", "item.Nothing");
        static final Item LITTLE_TILES_CARRIER =
                GameRegistry.findItem("littletiles", "BlockLittleTiles");
        static final Block LITTLE_TILES_BLOCK =
                GameRegistry.findBlock("littletiles", "BlockLittleTiles");
        static final Item LITTLE_TILES_COMPOSITE =
                GameRegistry.findItem("littletiles", "multiTiles");
        static final Item MALISIS_DOORS_CUSTOM_DOOR =
                GameRegistry.findItem("malisisdoors", "item.custom_door");
        static final Item MALISIS_DOORS_MIXED_BLOCK =
                GameRegistry.findItem("malisisdoors", "mixed_block");
        static final Block MALISIS_DOORS_MIXED_BLOCK_BLOCK =
                GameRegistry.findBlock("malisisdoors", "mixed_block");
        static final Item BOTANIA_BIFROST =
                GameRegistry.findItem("Botania", "bifrost");
        static final Block BOTANIA_BIFROST_BLOCK =
                GameRegistry.findBlock("Botania", "bifrost");
        static final Item BOTANIA_BURIED_PETALS =
                GameRegistry.findItem("Botania", "buriedPetals");
        static final Block BOTANIA_BURIED_PETALS_BLOCK =
                GameRegistry.findBlock("Botania", "buriedPetals");
        static final Item BOTANIA_CACOPHONIUM_BLOCK_ITEM =
                GameRegistry.findItem("Botania", "cacophoniumBlock");
        static final Block BOTANIA_CACOPHONIUM_WORLD_BLOCK =
                GameRegistry.findBlock("Botania", "cacophoniumBlock");
        static final Item BOTANIA_COCOON =
                GameRegistry.findItem("Botania", "cocoon");
        static final Block BOTANIA_COCOON_BLOCK =
                GameRegistry.findBlock("Botania", "cocoon");
        static final Item BOTANIA_PRISM =
                GameRegistry.findItem("Botania", "prism");
        static final Block BOTANIA_PRISM_BLOCK =
                GameRegistry.findBlock("Botania", "prism");
        static final Item GALACTICRAFT_FLAG =
                GameRegistry.findItem("GalacticraftCore", "item.flag");
        static final Item WRCBE_TRIANGULATOR =
                GameRegistry.findItem("WR-CBE|Addons", "triangulator");
        static final Item BOTANIA_ENCHANTER =
                GameRegistry.findItem("Botania", "enchanter");
        static final Block BOTANIA_ENCHANTER_BLOCK =
                GameRegistry.findBlock("Botania", "enchanter");
        static final Item BOTANIA_FAKE_AIR =
                GameRegistry.findItem("Botania", "fakeAir");
        static final Block BOTANIA_FAKE_AIR_BLOCK =
                GameRegistry.findBlock("Botania", "fakeAir");
        static final Item BOTANIA_MANA_FLAME =
                GameRegistry.findItem("Botania", "manaFlame");
        static final Block BOTANIA_MANA_FLAME_BLOCK =
                GameRegistry.findBlock("Botania", "manaFlame");
        static final Item BOTANIA_SOLID_VINE =
                GameRegistry.findItem("Botania", "solidVine");
        static final Block BOTANIA_SOLID_VINE_BLOCK =
                GameRegistry.findBlock("Botania", "solidVine");
        static final Item BOTANIA_STRUCTURE_LIB_ANY_FLOWER =
                GameRegistry.findItem("Botania", "flower_structurelib");
        static final Block BOTANIA_STRUCTURE_LIB_ANY_FLOWER_BLOCK =
                GameRegistry.findBlock("Botania", "flower_structurelib");
        static final Item CARPENTERS_BED_INTERNAL_ITEM =
                GameRegistry.findItem("CarpentersBlocks", "blockCarpentersBed");
        static final Block CARPENTERS_BED_BLOCK =
                GameRegistry.findBlock("CarpentersBlocks", "blockCarpentersBed");
        static final Item CARPENTERS_BED_PUBLIC_ITEM =
                GameRegistry.findItem("CarpentersBlocks", "itemCarpentersBed");
        static final Item CARPENTERS_DOOR_INTERNAL_ITEM =
                GameRegistry.findItem("CarpentersBlocks", "blockCarpentersDoor");
        static final Block CARPENTERS_DOOR_BLOCK =
                GameRegistry.findBlock("CarpentersBlocks", "blockCarpentersDoor");
        static final Item CARPENTERS_DOOR_PUBLIC_ITEM =
                GameRegistry.findItem("CarpentersBlocks", "itemCarpentersDoor");
        static final Item STEVES_CARTS_MODULAR_CART =
                GameRegistry.findItem("StevesCarts", "ModularCart");
        static final Item TCONSTRUCT_BATTLESIGN_INTERNAL_ITEM =
                GameRegistry.findItem("TConstruct", "BattleSignBlock");
        static final Block TCONSTRUCT_BATTLESIGN_BLOCK =
                GameRegistry.findBlock("TConstruct", "BattleSignBlock");
        static final Item TCONSTRUCT_BATTLESIGN_PUBLIC_ITEM =
                GameRegistry.findItem("TConstruct", "battlesign");
        static final Item TCONSTRUCT_HELD_ITEM_INTERNAL_ITEM =
                GameRegistry.findItem("TConstruct", "HeldItemBlock");
        static final Block TCONSTRUCT_HELD_ITEM_BLOCK =
                GameRegistry.findBlock("TConstruct", "HeldItemBlock");
        static final Item TCONSTRUCT_HELD_ITEM_PUBLIC_ITEM =
                GameRegistry.findItem("TConstruct", "frypan");
        static final Item THAUMCRAFT_BLOCK_HOLE_INTERNAL_ITEM =
                GameRegistry.findItem("Thaumcraft", "blockHole");
        static final Block THAUMCRAFT_BLOCK_HOLE_BLOCK =
                GameRegistry.findBlock("Thaumcraft", "blockHole");
        static final Item THAUMCRAFT_PORTABLE_HOLE_FOCUS =
                GameRegistry.findItem("Thaumcraft", "FocusPortableHole");
        static final Item THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_ITEM =
                GameRegistry.findItem("Thaumcraft", "blockPortalEldritch");
        static final Block THAUMCRAFT_ELDRITCH_PORTAL_BLOCK =
                GameRegistry.findBlock("Thaumcraft", "blockPortalEldritch");
        static final Item THAUMCRAFT_ELDRITCH_OBJECT_PUBLIC_ITEM =
                GameRegistry.findItem("Thaumcraft", "ItemEldritchObject");
        static final Item THAUMCRAFT_ELDRITCH_BLOCK_ITEM =
                GameRegistry.findItem("Thaumcraft", "blockEldritch");
        static final Block THAUMCRAFT_ELDRITCH_BLOCK =
                GameRegistry.findBlock("Thaumcraft", "blockEldritch");
        static final Item GADOMANCY_ELDRITCH_PORTAL_ITEM =
                GameRegistry.findItem("gadomancy", "BlockAdditionalEldritchPortal");
        static final Block GADOMANCY_ELDRITCH_PORTAL_BLOCK =
                GameRegistry.findBlock("gadomancy", "BlockAdditionalEldritchPortal");
        static final Item THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_ITEM =
                GameRegistry.findItem("ThaumicHorizons", "light");
        static final Block THAUMIC_HORIZONS_BASE_LIGHT_BLOCK =
                GameRegistry.findBlock("ThaumicHorizons", "light");
        static final Item THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_ITEM =
                GameRegistry.findItem("ThaumicHorizons", "lightSolar");
        static final Block THAUMIC_HORIZONS_SOLAR_LIGHT_BLOCK =
                GameRegistry.findBlock("ThaumicHorizons", "lightSolar");
        static final Item THAUMIC_HORIZONS_ILLUMINATION_FOCUS =
                GameRegistry.findItem("ThaumicHorizons", "focusIllumination");
        static final Item TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_ITEM =
                GameRegistry.findItem("TwilightForest", "tile.TFExperiment115");
        static final Block TWILIGHT_FOREST_EXPERIMENT_115_BLOCK =
                GameRegistry.findBlock("TwilightForest", "tile.TFExperiment115");
        static final Item TWILIGHT_FOREST_EXPERIMENT_115_PUBLIC_ITEM =
                GameRegistry.findItem("TwilightForest", "item.experiment115");
        static final Item WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_ITEM =
                GameRegistry.findItem("WitchingGadgets", "WG_CustomAir");
        static final Block WITCHING_GADGETS_CUSTOM_AIR_BLOCK =
                GameRegistry.findBlock("WitchingGadgets", "WG_CustomAir");
    }

    final ItemStack stack;
    final String type;
    final String registryId;
    final int metadata;
    final String canonicalNbt;
    final String key;
    final int amount;
    final String fluidDisplayName;

    private StackIdentity(ItemStack stack, String type, String registryId, int metadata,
                          String canonicalNbt, String key, int amount, String fluidDisplayName) {
        this.stack = stack;
        this.type = type;
        this.registryId = registryId;
        this.metadata = metadata;
        this.canonicalNbt = canonicalNbt;
        this.key = key;
        this.amount = amount;
        this.fluidDisplayName = fluidDisplayName;
    }

    static StackIdentity of(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            throw new IllegalArgumentException("NEI supplied a null/empty ItemStack identity");
        }
        CatalogExclusion catalogExclusion = catalogOnlyExclusion(stack);
        if (catalogExclusion != null) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: catalog-only exclusion appeared outside the completed global "
                            + "ItemList snapshot; policy=" + catalogExclusion.contract + "; "
                            + describe(stack));
        }
        Item item = stack.getItem();
        if (item == PinnedCatalogItems.FLUID_DROP) {
            requireRuntimeClass(item, AE2FC_FLUID_DROP_CLASS, "ae2fc:fluid_drop");
            FluidStack decoded = decodePinnedAe2fcFluidDropPayload(stack);
            requireSameFluidIdentity(
                    decoded, StackInfo.getFluid(stack), "AE2FC/NEI fluid-drop decoder", stack);
            return fluidProxy(stack, decoded, stack.stackSize);
        }
        FluidStack detectedFluid = StackInfo.getFluid(stack);
        requireKnownProxyDecoded(isPinnedFluidProxy(item), detectedFluid, describe(stack));
        if (detectedFluid != null && !StackInfo.isFluidContainer(stack)) {
            return fluidProxy(stack, detectedFluid, detectedFluid.amount);
        }
        return item(stack);
    }

    private static boolean isPinnedFluidProxy(Item item) {
        if (item == PinnedCatalogItems.FLUID_DROP) {
            requireRuntimeClass(item, AE2FC_FLUID_DROP_CLASS, "ae2fc:fluid_drop");
            return true;
        }
        if (item == PinnedCatalogItems.FLUID_PACKET) {
            requireRuntimeClass(item, AE2FC_FLUID_PACKET_CLASS, "ae2fc:fluid_packet");
            return true;
        }
        for (Class<?> type = item.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if ("gregtech.common.items.ItemFluidDisplay".equals(name)
                    || "gregtech.common.items.GT_FluidDisplayItem".equals(name)) {
                return true;
            }
        }
        return false;
    }

    static void verifyPinnedCatalogRuntime() throws ExportFailure {
        requirePinnedAe2CableBusRegistration();
        requirePinnedAe2MatrixFrameRegistration();
        requirePinnedDreamcraftNothingRegistration();
        requirePinnedLittleTilesCarrierRegistration();
        requirePinnedMalisisDoorsCustomDoorRegistration();
        requirePinnedMalisisDoorsMixedBlockRegistration();
        requirePinnedModernMarkingsCrossingRegistrations();
        requirePinnedThaumcraftRunedStoneRegistration();
        requirePinnedRegistration(
                PinnedCatalogItems.FLUID_DROP, "ae2fc:fluid_drop", AE2FC_FLUID_DROP_CLASS);
        requirePinnedRegistration(
                PinnedCatalogItems.FLUID_PACKET, "ae2fc:fluid_packet", AE2FC_FLUID_PACKET_CLASS);
        requirePinnedBlockItemRegistration(
                PinnedCatalogItems.BLOOD_LIGHT,
                PinnedCatalogItems.BLOOD_LIGHT_BLOCK,
                "AWWayofTime:bloodLight",
                VANILLA_ITEM_BLOCK_CLASS,
                BLOOD_MAGIC_BLOOD_LIGHT_BLOCK_CLASS);
        requirePinnedBlockItemRegistration(
                PinnedCatalogItems.SPECTRAL_CONTAINER,
                PinnedCatalogItems.SPECTRAL_CONTAINER_BLOCK,
                "AWWayofTime:spectralContainer",
                VANILLA_ITEM_BLOCK_CLASS,
                BLOOD_MAGIC_SPECTRAL_CONTAINER_BLOCK_CLASS);
        requirePinnedRegistration(
                PinnedCatalogItems.ARCHITECTURE_CRAFT_CLADDING,
                "ArchitectureCraft:cladding",
                ARCHITECTURE_CRAFT_CLADDING_ITEM_CLASS);
        requirePinnedRegistration(
                PinnedCatalogItems.AVARITIA_MATTER_CLUSTER,
                "Avaritia:Matter_Cluster",
                AVARITIA_MATTER_CLUSTER_ITEM_CLASS);
        requirePinnedBlockItemRegistration(
                PinnedCatalogItems.BOTANIA_BIFROST,
                PinnedCatalogItems.BOTANIA_BIFROST_BLOCK,
                "Botania:bifrost",
                BOTANIA_ITEM_BLOCK_MOD_CLASS,
                BOTANIA_BIFROST_BLOCK_CLASS);
        requirePinnedBlockItemRegistration(
                PinnedCatalogItems.BOTANIA_BURIED_PETALS,
                PinnedCatalogItems.BOTANIA_BURIED_PETALS_BLOCK,
                "Botania:buriedPetals",
                BOTANIA_BURIED_PETALS_ITEM_CLASS,
                BOTANIA_BURIED_PETALS_BLOCK_CLASS);
        requirePinnedBlockItemRegistration(
                PinnedCatalogItems.BOTANIA_CACOPHONIUM_BLOCK_ITEM,
                PinnedCatalogItems.BOTANIA_CACOPHONIUM_WORLD_BLOCK,
                "Botania:cacophoniumBlock",
                BOTANIA_ITEM_BLOCK_MOD_CLASS,
                BOTANIA_CACOPHONIUM_BLOCK_CLASS);
        requirePinnedBlockItemRegistration(
                PinnedCatalogItems.BOTANIA_COCOON,
                PinnedCatalogItems.BOTANIA_COCOON_BLOCK,
                "Botania:cocoon",
                BOTANIA_ITEM_BLOCK_MOD_CLASS,
                BOTANIA_COCOON_BLOCK_CLASS);
        requirePinnedBlockItemRegistration(
                PinnedCatalogItems.BOTANIA_PRISM,
                PinnedCatalogItems.BOTANIA_PRISM_BLOCK,
                "Botania:prism",
                BOTANIA_ITEM_BLOCK_MOD_CLASS,
                BOTANIA_PRISM_BLOCK_CLASS);
        requirePinnedRegistration(
                PinnedCatalogItems.GALACTICRAFT_FLAG,
                "GalacticraftCore:item.flag",
                GALACTICRAFT_FLAG_ITEM_CLASS);
        requirePinnedRegistration(
                PinnedCatalogItems.WRCBE_TRIANGULATOR,
                "WR-CBE|Addons:triangulator",
                WRCBE_TRIANGULATOR_ITEM_CLASS);
        requirePinnedBlockItemRegistration(
                PinnedCatalogItems.BOTANIA_ENCHANTER,
                PinnedCatalogItems.BOTANIA_ENCHANTER_BLOCK,
                "Botania:enchanter",
                BOTANIA_ITEM_BLOCK_MOD_CLASS,
                BOTANIA_ENCHANTER_BLOCK_CLASS);
        requirePinnedBlockItemRegistration(
                PinnedCatalogItems.BOTANIA_FAKE_AIR,
                PinnedCatalogItems.BOTANIA_FAKE_AIR_BLOCK,
                "Botania:fakeAir",
                BOTANIA_ITEM_BLOCK_MOD_CLASS,
                BOTANIA_FAKE_AIR_BLOCK_CLASS);
        requirePinnedBlockItemRegistration(
                PinnedCatalogItems.BOTANIA_MANA_FLAME,
                PinnedCatalogItems.BOTANIA_MANA_FLAME_BLOCK,
                "Botania:manaFlame",
                BOTANIA_ITEM_BLOCK_MOD_CLASS,
                BOTANIA_MANA_FLAME_BLOCK_CLASS);
        requirePinnedBlockItemRegistration(
                PinnedCatalogItems.BOTANIA_SOLID_VINE,
                PinnedCatalogItems.BOTANIA_SOLID_VINE_BLOCK,
                "Botania:solidVine",
                BOTANIA_ITEM_BLOCK_MOD_CLASS,
                BOTANIA_SOLID_VINE_BLOCK_CLASS);
        requirePinnedBlockItemRegistration(
                PinnedCatalogItems.BOTANIA_STRUCTURE_LIB_ANY_FLOWER,
                PinnedCatalogItems.BOTANIA_STRUCTURE_LIB_ANY_FLOWER_BLOCK,
                "Botania:flower_structurelib",
                BOTANIA_ITEM_BLOCK_MOD_CLASS,
                BOTANIA_STRUCTURE_LIB_FLOWER_BLOCK_CLASS);
        requirePinnedCarpentersMultipartRegistration(
                PinnedCatalogItems.CARPENTERS_BED_INTERNAL_ITEM,
                PinnedCatalogItems.CARPENTERS_BED_BLOCK,
                "CarpentersBlocks:blockCarpentersBed",
                CARPENTERS_BED_BLOCK_CLASS,
                PinnedCatalogItems.CARPENTERS_BED_PUBLIC_ITEM,
                "CarpentersBlocks:itemCarpentersBed",
                CARPENTERS_BED_PUBLIC_ITEM_CLASS,
                CARPENTERS_BED_INTERNAL_WORLD_ITEM_BLOCK);
        requirePinnedCarpentersMultipartRegistration(
                PinnedCatalogItems.CARPENTERS_DOOR_INTERNAL_ITEM,
                PinnedCatalogItems.CARPENTERS_DOOR_BLOCK,
                "CarpentersBlocks:blockCarpentersDoor",
                CARPENTERS_DOOR_BLOCK_CLASS,
                PinnedCatalogItems.CARPENTERS_DOOR_PUBLIC_ITEM,
                "CarpentersBlocks:itemCarpentersDoor",
                CARPENTERS_DOOR_PUBLIC_ITEM_CLASS,
                CARPENTERS_DOOR_INTERNAL_WORLD_ITEM_BLOCK);
        requirePinnedStevesCartsModularCartRegistration();
        requirePinnedTConstructBattlesignRegistration();
        requirePinnedTConstructHeldItemRegistration();
        requirePinnedThaumcraftBlockHoleRegistration();
        requirePinnedThaumcraftEldritchPortalRegistration();
        requirePinnedGadomancyEldritchPortalRegistration();
        requirePinnedThaumicHorizonsIlluminationLightRegistration();
        requirePinnedTwilightForestExperiment115Registration();
        requirePinnedWitchingGadgetsCustomAirRegistration();
    }

    @SuppressWarnings("unchecked")
    private static void requirePinnedDreamcraftNothingRegistration() throws ExportFailure {
        Item item = PinnedCatalogItems.DREAMCRAFT_NOTHING;
        String registryId = DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL.registryId;
        requirePinnedRegistration(item, registryId, DREAMCRAFT_NOTHING_ITEM_CLASS);
        try {
            requireExactRegistration(item, registryId);
            Block registeredBlock = GameRegistry.findBlock("dreamcraft", "item.Nothing");
            Block aliasedBlock = Block.getBlockFromItem(item);
            if (item instanceof ItemBlock || registeredBlock != null
                    || (aliasedBlock != null && aliasedBlock != Blocks.air)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: legacy loot-bag sentinel "
                        + registryId + " unexpectedly aliases a block; registered="
                        + describeRuntimeValue(registeredBlock) + ", mapped="
                        + describeRuntimeValue(aliasedBlock));
            }

            Class<?> itemListClass = Class.forName(DREAMCRAFT_NH_ITEM_LIST_CLASS);
            if (!itemListClass.isEnum()) {
                throw new IllegalArgumentException("ITEM_IDENTITY: "
                        + DREAMCRAFT_NH_ITEM_LIST_CLASS + " is no longer an enum");
            }
            Field nothingField = itemListClass.getField("Nothing");
            int nothingModifiers = nothingField.getModifiers();
            if (nothingField.getDeclaringClass() != itemListClass
                    || nothingField.getType() != itemListClass
                    || !Modifier.isPublic(nothingModifiers)
                    || !Modifier.isStatic(nothingModifiers)
                    || !Modifier.isFinal(nothingModifiers)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: NHItemList.Nothing public "
                        + "enum field linkage drifted");
            }
            Object nothing = nothingField.get(null);
            if (!(nothing instanceof Enum)
                    || nothing.getClass() != itemListClass
                    || !"Nothing".equals(((Enum<?>) nothing).name())) {
                throw new IllegalArgumentException("ITEM_IDENTITY: NHItemList.Nothing did not "
                        + "resolve to its exact enum singleton");
            }

            Field wrapperField = itemListClass.getField("Item");
            int wrapperModifiers = wrapperField.getModifiers();
            if (wrapperField.getDeclaringClass() != itemListClass
                    || !DREAMCRAFT_SIMPLE_ITEM_WRAPPER_CLASS.equals(
                            wrapperField.getType().getName())
                    || !Modifier.isPublic(wrapperModifiers)
                    || Modifier.isStatic(wrapperModifiers)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: NHItemList.Item public YAM "
                        + "wrapper field linkage drifted");
            }
            Object wrapper = wrapperField.get(nothing);
            requireExactRuntimeClass(
                    wrapper, DREAMCRAFT_SIMPLE_ITEM_WRAPPER_CLASS,
                    "NHItemList.Nothing Item wrapper");
            Object constructedItem = invokeRequiredNoArg(
                    wrapper, "getConstructedItem", "NHItemList.Nothing constructed item");
            if (constructedItem != item) {
                throw new IllegalArgumentException("ITEM_IDENTITY: NHItemList.Nothing wrapper "
                        + "does not own the exact registered " + registryId + " item");
            }
            if (!"Nothing".equals(invokeRequiredNoArg(
                    wrapper, "getUnlocItemName", "NHItemList.Nothing wrapper name"))
                    || !DREAMCRAFT_GENERIC_TAB_LABEL.equals(invokeRequiredNoArg(
                            wrapper, "getCreativeTabName",
                            "NHItemList.Nothing wrapper creative tab"))) {
                throw new IllegalArgumentException("ITEM_IDENTITY: NHItemList.Nothing YAM "
                        + "wrapper name/tab ownership drifted");
            }

            Object recipeBehavior = invokeRequiredNoArg(
                    item, "getItemRecipeBehavior", registryId + " recipe behavior");
            if (!(recipeBehavior instanceof Enum)
                    || !"Consume".equals(((Enum<?>) recipeBehavior).name())) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " recipe behavior drifted; expected Consume, got "
                        + String.valueOf(recipeBehavior));
            }
            if (!DREAMCRAFT_NOTHING_UNLOCALIZED_NAME.equals(item.getUnlocalizedName())
                    || item.getHasSubtypes()
                    || item.getMaxDamage() != 0
                    || item.getItemStackLimit() != 64
                    || item.hasContainerItem()
                    || item.requiresMultipleRenderPasses()
                    || item.getRenderPasses(0) != 1) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " default item traits drifted; unlocalized="
                        + item.getUnlocalizedName() + ", subtypes=" + item.getHasSubtypes()
                        + ", maxDamage=" + item.getMaxDamage() + ", stackLimit="
                        + item.getItemStackLimit() + ", container=" + item.hasContainerItem()
                        + ", multiplePasses=" + item.requiresMultipleRenderPasses()
                        + ", renderPasses=" + item.getRenderPasses(0));
            }

            Object creativeTab = item.getCreativeTab();
            requireExactRuntimeClass(
                    creativeTab, DREAMCRAFT_GENERIC_TAB_CLASS, registryId + " creative tab");
            if (!DREAMCRAFT_GENERIC_TAB_LABEL.equals(invokeRequiredNoArg(
                    creativeTab, "getTabName", registryId + " YAM creative tab name"))
                    || !DREAMCRAFT_GENERIC_TAB_LABEL.equals(item.getCreativeTab().getTabLabel())) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " creative-tab label drifted");
            }

            IIcon icon = item.getIconFromDamage(0);
            String iconName = icon == null ? "<null>" : icon.getIconName();
            if (!DREAMCRAFT_NOTHING_ICON.equals(iconName)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                        + " expected icon " + DREAMCRAFT_NOTHING_ICON + ", got " + iconName);
            }
            InputStream iconResource = itemListClass.getResourceAsStream(
                    "/assets/dreamcraft/textures/items/itemNothing.png");
            if (iconResource == null) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                        + " owner texture resource is absent");
            }
            try {
                requireExactTransparentDreamcraftNothingIcon(ImageIO.read(iconResource));
            } finally {
                iconResource.close();
            }

            ItemStack bare = new ItemStack(item, 1, 0);
            if (MinecraftForgeClient.getItemRenderer(
                    bare, IItemRenderer.ItemRenderType.INVENTORY) != null) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                        + " unexpectedly acquired a Forge inventory renderer");
            }
            java.util.ArrayList<ItemStack> variants = new java.util.ArrayList<ItemStack>();
            item.getSubItems(item, item.getCreativeTab(), variants);
            if (variants.size() != 1
                    || variants.get(0).getItem() != item
                    || !isBareCatalogEntryShape(variants.get(0))) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " expected exactly one bare metadata-0 creative subitem; got "
                        + variants.size());
            }
            if (catalogOnlyExclusion(bare)
                    != DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " no longer maps to its exact catalog policy");
            }
            requireStrictCatalogShapeRejected(new ItemStack(item, 2, 0), registryId);
            requireStrictCatalogShapeRejected(new ItemStack(item, 1, 1), registryId);
            requireStrictCatalogShapeRejected(new ItemStack(item, 1, 32767), registryId);
            ItemStack tagged = bare.copy();
            tagged.setTagCompound(new NBTTagCompound());
            requireStrictCatalogShapeRejected(tagged, registryId);
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned Dreamcraft Nothing owner-registration reflection drift", error);
        } catch (IOException error) {
            throw new ExportFailure("ITEM_ICON_RENDER",
                    "pinned Dreamcraft Nothing owner texture could not be audited", error);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned Dreamcraft Nothing legacy loot-bag sentinel registration drift",
                    error);
        }
    }

    static void requireExactTransparentDreamcraftNothingIcon(BufferedImage image) {
        if (image == null || image.getWidth() != 16 || image.getHeight() != 16) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: dreamcraft:itemNothing owner "
                    + "texture must decode as an exact 16x16 image");
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    throw new IllegalArgumentException("ITEM_ICON_RENDER: "
                            + "dreamcraft:itemNothing owner texture is no longer fully transparent; "
                            + "nonzero alpha at " + x + "," + y);
                }
            }
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private static void requirePinnedLittleTilesCarrierRegistration() throws ExportFailure {
        Item item = PinnedCatalogItems.LITTLE_TILES_CARRIER;
        Block block = PinnedCatalogItems.LITTLE_TILES_BLOCK;
        Item compositeItem = PinnedCatalogItems.LITTLE_TILES_COMPOSITE;
        String registryId = LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER.registryId;
        requirePinnedBlockItemRegistration(
                item, block, registryId, LITTLE_TILES_ITEM_CLASS, LITTLE_TILES_BLOCK_CLASS);
        requirePinnedRegistration(
                compositeItem, "littletiles:multiTiles", LITTLE_TILES_COMPOSITE_ITEM_CLASS);
        try {
            requireExactRegistration(item, registryId);
            requireExactRegistration(block, registryId);
            requireExactRegistration(compositeItem, "littletiles:multiTiles");
            if (item == compositeItem
                    || !LITTLE_TILES_BLOCK_UNLOCALIZED_NAME.equals(block.getUnlocalizedName())) {
                throw new IllegalArgumentException("ITEM_IDENTITY: LittleTiles carrier/public "
                        + "composite boundary drifted; blockUnlocalized="
                        + block.getUnlocalizedName());
            }

            Class<?> ownerClass = Class.forName(LITTLE_TILES_OWNER_CLASS);
            Field blockField = ownerClass.getField("blockTile");
            int blockFieldModifiers = blockField.getModifiers();
            if (!LITTLE_TILES_BLOCK_CLASS.equals(blockField.getType().getName())
                    || !Modifier.isPublic(blockFieldModifiers)
                    || !Modifier.isStatic(blockFieldModifiers)
                    || blockField.get(null) != block) {
                throw new IllegalArgumentException("ITEM_IDENTITY: LittleTiles.blockTile owner "
                        + "registration linkage drifted");
            }
            Field proxyField = ownerClass.getField("proxy");
            Object proxy = proxyField.get(null);
            requireExactRuntimeClass(proxy, LITTLE_TILES_CLIENT_CLASS, "LittleTiles client proxy");

            Class<?> clientClass = Class.forName(LITTLE_TILES_CLIENT_CLASS);
            Field rendererField = clientClass.getField("renderer");
            int rendererFieldModifiers = rendererField.getModifiers();
            if (!LITTLE_TILES_RENDERER_CLASS.equals(rendererField.getType().getName())
                    || !Modifier.isPublic(rendererFieldModifiers)
                    || !Modifier.isStatic(rendererFieldModifiers)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: LittleTilesClient.renderer "
                        + "owner field linkage drifted");
            }
            Object ownerRenderer = rendererField.get(null);
            requireExactRuntimeClass(
                    ownerRenderer, LITTLE_TILES_RENDERER_CLASS, "LittleTiles owner renderer");
            Field modelIdField = clientClass.getField("modelID");
            int modelIdModifiers = modelIdField.getModifiers();
            if (modelIdField.getType() != Integer.TYPE
                    || !Modifier.isPublic(modelIdModifiers)
                    || !Modifier.isStatic(modelIdModifiers)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: LittleTilesClient.modelID "
                        + "owner field linkage drifted");
            }
            int modelId = modelIdField.getInt(null);
            if (modelId != block.getRenderType()) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: LittleTiles model ID "
                        + "drifted; owner=" + modelId + ", block=" + block.getRenderType());
            }

            Class<?> dynamicRendererInterface =
                    Class.forName(LITTLE_TILES_DYNAMIC_RENDERER_INTERFACE);
            if (!dynamicRendererInterface.isInterface()
                    || !dynamicRendererInterface.isAssignableFrom(item.getClass())
                    || !dynamicRendererInterface.isAssignableFrom(compositeItem.getClass())) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: LittleTiles carrier/public "
                        + "composite lost the exact dynamic item-renderer interface");
            }

            Class<?> tileEntityClass = Class.forName(LITTLE_TILES_TILE_ENTITY_CLASS);
            if (!TileEntity.class.isAssignableFrom(tileEntityClass)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: LittleTiles tile class is not "
                        + "a TileEntity: " + tileEntityClass.getName());
            }
            TileEntity tileEntity = block.createTileEntity(null, 0);
            requireExactRuntimeClass(
                    tileEntity, LITTLE_TILES_TILE_ENTITY_CLASS, registryId + " tile entity");
            NBTTagCompound tileRegistration = new NBTTagCompound();
            tileEntity.writeToNBT(tileRegistration);
            TileEntity roundTrippedTile = TileEntity.createAndLoadEntity(tileRegistration);
            if (!LITTLE_TILES_TILE_ENTITY_ID.equals(tileRegistration.getString("id"))
                    || roundTrippedTile == null
                    || roundTrippedTile.getClass() != tileEntityClass) {
                throw new IllegalArgumentException("ITEM_IDENTITY: LittleTiles tile-entity "
                        + "bidirectional registration drifted for "
                        + LITTLE_TILES_TILE_ENTITY_ID);
            }

            TileEntitySpecialRenderer tileRenderer =
                    TileEntityRendererDispatcher.instance.getSpecialRendererByClass(
                            (Class<? extends TileEntity>) tileEntityClass);
            requireExactRuntimeClass(
                    tileRenderer, LITTLE_TILES_RENDERER_CLASS, registryId + " TESR");
            ISimpleBlockRenderingHandler blockRenderer = pinnedBlockRenderer(
                    block.getRenderType(), LITTLE_TILES_RENDERER_CLASS);

            Field itemRenderersField = MinecraftForgeClient.class.getDeclaredField(
                    "customItemRenderers");
            itemRenderersField.setAccessible(true);
            Object itemRenderersValue = itemRenderersField.get(null);
            Object registeredItemRenderer = itemRenderersValue instanceof Map
                    ? ((Map<?, ?>) itemRenderersValue).get(item) : null;
            requireExactRuntimeClass(
                    registeredItemRenderer, LITTLE_TILES_RENDERER_CLASS,
                    registryId + " registered item renderer");
            requirePinnedLittleTilesRendererTopology(
                    ownerRenderer, blockRenderer, tileRenderer, registeredItemRenderer,
                    modelId, blockRenderer.getRenderId());
            IItemRenderer itemRenderer = (IItemRenderer) registeredItemRenderer;
            ItemStack bare = new ItemStack(item, 1, 0);
            if (itemRenderer.handleRenderType(bare, IItemRenderer.ItemRenderType.INVENTORY)
                    || MinecraftForgeClient.getItemRenderer(
                            bare, IItemRenderer.ItemRenderType.INVENTORY) != null) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: unparameterized LittleTiles "
                        + "carrier unexpectedly became an inventory render target");
            }

            java.util.ArrayList<ItemStack> itemVariants =
                    new java.util.ArrayList<ItemStack>();
            item.getSubItems(item, item.getCreativeTab(), itemVariants);
            java.util.ArrayList<ItemStack> blockVariants =
                    new java.util.ArrayList<ItemStack>();
            block.getSubBlocks(item, block.getCreativeTabToDisplayOn(), blockVariants);
            if (!itemVariants.isEmpty() || !blockVariants.isEmpty()) {
                throw new IllegalArgumentException("ITEM_IDENTITY: unparameterized LittleTiles "
                        + "carrier unexpectedly exposed creative subitems; item="
                        + itemVariants.size() + ", block=" + blockVariants.size());
            }
            if (catalogOnlyExclusion(bare)
                    != LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER) {
                throw new IllegalArgumentException("ITEM_IDENTITY: unparameterized LittleTiles "
                        + "carrier no longer maps to its exact ItemList policy");
            }

            ItemStack tagged = bare.copy();
            tagged.setTagCompound(new NBTTagCompound());
            if (!itemRenderer.handleRenderType(
                    tagged, IItemRenderer.ItemRenderType.INVENTORY)
                    || MinecraftForgeClient.getItemRenderer(
                            tagged, IItemRenderer.ItemRenderType.INVENTORY) != itemRenderer
                    || catalogOnlyExclusion(tagged) != null
                    || catalogOnlyExclusion(new ItemStack(item, 2, 0)) != null
                    || catalogOnlyExclusion(new ItemStack(item, 1, 1)) != null
                    || catalogOnlyExclusion(new ItemStack(item, 1, 32767)) != null
                    || catalogOnlyExclusion(new ItemStack(compositeItem, 1, 0)) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: LittleTiles policy captured a "
                        + "parameterized/non-bare carrier or distinct composite item");
            }
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned LittleTiles carrier owner/renderer reflection drift", error);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned LittleTiles unparameterized microtile carrier registration drift",
                    error);
        }
    }

    static void requirePinnedLittleTilesRendererTopology(
            Object ownerRenderer, Object blockRenderer, Object tileRenderer,
            Object registeredItemRenderer, int modelId, int blockRenderId) {
        if (blockRenderer != ownerRenderer) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: LittleTiles raw block renderer "
                    + "is not the static owner renderer");
        }
        if (tileRenderer == ownerRenderer || tileRenderer == blockRenderer) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: LittleTiles TESR unexpectedly "
                    + "aliases the distinct static owner/block renderer");
        }
        if (registeredItemRenderer != ownerRenderer) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: LittleTiles carrier item "
                    + "renderer is not the static owner renderer");
        }
        if (blockRenderId != modelId) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: LittleTiles raw block renderer "
                    + "ID drifted; owner=" + modelId + ", renderer=" + blockRenderId);
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private static void requirePinnedMalisisDoorsCustomDoorRegistration()
            throws ExportFailure {
        Item item = PinnedCatalogItems.MALISIS_DOORS_CUSTOM_DOOR;
        String registryId = MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER.registryId;
        requirePinnedRegistration(item, registryId, MALISIS_DOORS_CUSTOM_DOOR_ITEM_CLASS);
        try {
            requireExactRegistration(item, registryId);
            Class<?> itemClass = item.getClass();
            Class<?> doorItemClass = itemClass.getSuperclass();
            Class<?> vanillaDoorItemClass = doorItemClass == null
                    ? null : doorItemClass.getSuperclass();
            if (doorItemClass == null
                    || !MALISIS_DOORS_DOOR_ITEM_CLASS.equals(doorItemClass.getName())
                    || vanillaDoorItemClass == null
                    || !"net.minecraft.item.ItemDoor".equals(vanillaDoorItemClass.getName())) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " exact CustomDoorItem -> DoorItem -> ItemDoor hierarchy drifted");
            }
            if (!MALISIS_DOORS_CUSTOM_DOOR_UNLOCALIZED_NAME.equals(
                    item.getUnlocalizedName())) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " unlocalized name drifted; got " + item.getUnlocalizedName());
            }
            if (item.getCreativeTab() != null || item.getItemStackLimit() != 16) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " carrier envelope drifted; creativeTab=" + item.getCreativeTab()
                        + ", maxStackSize=" + item.getItemStackLimit());
            }
            if (item.getHasSubtypes() || item.getMaxDamage() != 0) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " unexpectedly acquired metadata/durability variants");
            }

            int getSubItemsSignatures = 0;
            Method inheritedGetSubItems = null;
            for (Method method : itemClass.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getReturnType() == Void.TYPE
                        && parameters.length == 3
                        && parameters[0] == Item.class
                        && parameters[1] == CreativeTabs.class
                        && parameters[2] == List.class) {
                    getSubItemsSignatures++;
                    inheritedGetSubItems = method;
                }
            }
            if (getSubItemsSignatures != 1 || inheritedGetSubItems == null
                    || inheritedGetSubItems.getDeclaringClass() != Item.class) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " must inherit exactly one vanilla Item.getSubItems declaration; "
                        + "signatures=" + getSubItemsSignatures + ", declaring="
                        + (inheritedGetSubItems == null ? "<none>"
                        : inheritedGetSubItems.getDeclaringClass().getName()));
            }
            java.util.ArrayList<ItemStack> permutations =
                    new java.util.ArrayList<ItemStack>();
            item.getSubItems(item, null, permutations);
            ItemStack bare = new ItemStack(item, 1, 0);
            if (permutations.size() != 1
                    || permutations.get(0).getItem() != item
                    || !isBareCatalogEntryShape(permutations.get(0))
                    || !isBareCatalogEntryShape(bare)
                    || bare.getMaxStackSize() != 16
                    || catalogOnlyExclusion(bare)
                    != MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " no longer exposes exactly one vanilla bare meta-0 permutation");
            }

            int registerIconSignatures = 0;
            for (Method method : itemClass.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getReturnType() == Void.TYPE
                        && parameters.length == 1
                        && parameters[0] == IIconRegister.class) {
                    registerIconSignatures++;
                }
            }
            if (registerIconSignatures != 1) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                        + " exact no-sprite owner registerIcons declaration drifted; signatures="
                        + registerIconSignatures);
            }

            Class<?> rendererClass = Class.forName(
                    MALISIS_DOORS_CUSTOM_DOOR_RENDERER_CLASS);
            if (rendererClass.getSuperclass() == null
                    || !MALISIS_DOORS_DOOR_RENDERER_CLASS.equals(
                    rendererClass.getSuperclass().getName())) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                        + " renderer hierarchy drifted");
            }
            Method renderMethod = rendererClass.getMethod("render");
            if (renderMethod.getReturnType() != Void.TYPE
                    || renderMethod.getParameterTypes().length != 0
                    || renderMethod.getDeclaringClass() != rendererClass) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                        + " exact CustomDoorRenderer.render declaration drifted");
            }

            Field itemRenderersField = MinecraftForgeClient.class.getDeclaredField(
                    "customItemRenderers");
            itemRenderersField.setAccessible(true);
            Object itemRenderersValue = itemRenderersField.get(null);
            Object registeredItemRenderer = itemRenderersValue instanceof Map
                    ? ((Map<?, ?>) itemRenderersValue).get(item) : null;
            requireExactRuntimeClass(
                    registeredItemRenderer, MALISIS_DOORS_CUSTOM_DOOR_RENDERER_CLASS,
                    registryId + " registered item renderer");
            IItemRenderer itemRenderer = (IItemRenderer) registeredItemRenderer;

            Class<?> tileEntityClass = Class.forName(
                    MALISIS_DOORS_CUSTOM_DOOR_TILE_ENTITY_CLASS);
            if (!TileEntity.class.isAssignableFrom(tileEntityClass)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                        + " owner tile class is not a TileEntity");
            }
            TileEntitySpecialRenderer tileRenderer =
                    TileEntityRendererDispatcher.instance.getSpecialRendererByClass(
                            (Class<? extends TileEntity>) tileEntityClass);
            requireExactRuntimeClass(
                    tileRenderer, MALISIS_DOORS_CUSTOM_DOOR_RENDERER_CLASS,
                    registryId + " TESR");

            ItemStack tagged = bare.copy();
            tagged.setTagCompound(new NBTTagCompound());
            boolean handlesBare = itemRenderer.handleRenderType(
                    bare, IItemRenderer.ItemRenderType.INVENTORY)
                    && MinecraftForgeClient.getItemRenderer(
                    bare, IItemRenderer.ItemRenderType.INVENTORY) == itemRenderer;
            boolean handlesTagged = itemRenderer.handleRenderType(
                    tagged, IItemRenderer.ItemRenderType.INVENTORY)
                    && MinecraftForgeClient.getItemRenderer(
                    tagged, IItemRenderer.ItemRenderType.INVENTORY) == itemRenderer;
            requirePinnedMalisisDoorsRendererTopology(
                    registeredItemRenderer, tileRenderer, handlesBare, handlesTagged,
                    item.getIconFromDamage(0), item.getIconIndex(bare),
                    renderMethod.getDeclaringClass(), rendererClass);

            if (catalogOnlyExclusion(tagged) != null
                    || catalogOnlyExclusion(new ItemStack(item, 2, 0)) != null
                    || catalogOnlyExclusion(new ItemStack(item, 1, 1)) != null
                    || catalogOnlyExclusion(new ItemStack(item, 1, 32767)) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " policy captured a configured/NBT-bearing or non-bare variant");
            }
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned MalisisDoors custom-door owner/renderer reflection drift", error);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned MalisisDoors unconfigured custom-door carrier registration drift",
                    error);
        }
    }

    static void requirePinnedMalisisDoorsRendererTopology(
            Object registeredItemRenderer, Object tileRenderer,
            boolean handlesBare, boolean handlesTagged,
            IIcon damageIcon, IIcon stackIcon,
            Class<?> renderDeclaringClass, Class<?> expectedRendererClass) {
        if (registeredItemRenderer == null || registeredItemRenderer != tileRenderer) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: MalisisDoors custom-door item "
                    + "renderer and TESR must be the same exact owner instance");
        }
        if (!handlesBare || !handlesTagged) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: MalisisDoors custom-door exact "
                    + "renderer must own both bare and configured inventory stacks");
        }
        if (damageIcon != null || stackIcon != null) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: MalisisDoors custom-door owner "
                    + "unexpectedly acquired a fallback sprite");
        }
        if (expectedRendererClass == null || renderDeclaringClass != expectedRendererClass
                || registeredItemRenderer.getClass() != expectedRendererClass) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: MalisisDoors custom-door "
                    + "intentional bare-transparency renderer declaration drifted");
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private static void requirePinnedMalisisDoorsMixedBlockRegistration()
            throws ExportFailure {
        Item item = PinnedCatalogItems.MALISIS_DOORS_MIXED_BLOCK;
        Block block = PinnedCatalogItems.MALISIS_DOORS_MIXED_BLOCK_BLOCK;
        String registryId = MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER.registryId;
        requirePinnedBlockItemRegistration(
                item, block, registryId,
                MALISIS_DOORS_MIXED_BLOCK_ITEM_CLASS,
                MALISIS_DOORS_MIXED_BLOCK_CLASS);
        try {
            requireExactRegistration(item, registryId);
            requireExactRegistration(block, registryId);
            Class<?> itemClass = item.getClass();
            Class<?> blockClass = block.getClass();
            if (itemClass.getSuperclass() != ItemBlock.class) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " exact MixedBlockBlockItem -> ItemBlock hierarchy drifted");
            }
            if (!MALISIS_DOORS_MIXED_BLOCK_UNLOCALIZED_NAME.equals(
                    block.getUnlocalizedName())
                    || !MALISIS_DOORS_MIXED_BLOCK_UNLOCALIZED_NAME.equals(
                    item.getUnlocalizedName())) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " unlocalized-name topology drifted; item="
                        + item.getUnlocalizedName() + ", block=" + block.getUnlocalizedName());
            }
            if (item.getCreativeTab() != null
                    || block.getCreativeTabToDisplayOn() != null
                    || item.getHasSubtypes() || item.getMaxDamage() != 0) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " unconfigured carrier envelope drifted; itemTab="
                        + item.getCreativeTab() + ", blockTab="
                        + block.getCreativeTabToDisplayOn() + ", hasSubtypes="
                        + item.getHasSubtypes() + ", maxDamage=" + item.getMaxDamage());
            }

            requireUniquePublicVoidMethodBySignature(
                    itemClass, ItemBlock.class, registryId + " item permutation producer",
                    Item.class, CreativeTabs.class, List.class);
            requireUniquePublicVoidMethodBySignature(
                    blockClass, Block.class, registryId + " block permutation producer",
                    Item.class, CreativeTabs.class, List.class);
            java.util.ArrayList<ItemStack> permutations =
                    new java.util.ArrayList<ItemStack>();
            item.getSubItems(item, null, permutations);
            ItemStack bare = new ItemStack(item, 1, 0);
            if (permutations.size() != 1
                    || permutations.get(0).getItem() != item
                    || !isBareCatalogEntryShape(permutations.get(0))
                    || !isBareCatalogEntryShape(bare)
                    || catalogOnlyExclusion(bare)
                    != MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " no longer exposes exactly one inherited bare meta-0 permutation");
            }

            Class<?> tileClass = Class.forName(
                    MALISIS_DOORS_MIXED_BLOCK_TILE_ENTITY_CLASS);
            if (!TileEntity.class.isAssignableFrom(tileClass)
                    || !block.hasTileEntity(0)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " mixed-block tile topology drifted");
            }
            Object createdTile = block.createTileEntity(null, 0);
            requireExactRuntimeClass(
                    createdTile, MALISIS_DOORS_MIXED_BLOCK_TILE_ENTITY_CLASS,
                    registryId + " created tile entity");

            Method fromItemStacks = itemClass.getDeclaredMethod(
                    "fromItemStacks", ItemStack.class, ItemStack.class);
            Method fromTileEntity = itemClass.getDeclaredMethod(
                    "fromTileEntity", tileClass);
            requireExactMixedBlockFactoryMethod(
                    fromItemStacks, itemClass, "fromItemStacks", 2, registryId);
            requireExactMixedBlockFactoryMethod(
                    fromTileEntity, itemClass, "fromTileEntity", 1, registryId);

            ItemStack first = new ItemStack(Blocks.stone, 1, 0);
            ItemStack second = new ItemStack(Blocks.dirt, 1, 0);
            ItemStack mixedFromItems = (ItemStack) fromItemStacks.invoke(
                    null, first, second);
            requireExactMalisisDoorsMixedBlockProducerNbt(
                    mixedFromItems, item, Blocks.stone, Blocks.dirt,
                    ((ItemBlock) first.getItem()).getMetadata(first.getItemDamage()),
                    ((ItemBlock) second.getItem()).getMetadata(second.getItemDamage()),
                    registryId + " fromItemStacks");

            Object tile = tileClass.getConstructor().newInstance();
            tileClass.getField("block1").set(tile, Blocks.stone);
            tileClass.getField("block2").set(tile, Blocks.dirt);
            tileClass.getField("metadata1").setInt(tile, 3);
            tileClass.getField("metadata2").setInt(tile, 5);
            ItemStack mixedFromTile = (ItemStack) fromTileEntity.invoke(null, tile);
            requireExactMalisisDoorsMixedBlockProducerNbt(
                    mixedFromTile, item, Blocks.stone, Blocks.dirt, 3, 5,
                    registryId + " fromTileEntity");

            Class<?> mixerTileClass = Class.forName(
                    MALISIS_DOORS_BLOCK_MIXER_TILE_ENTITY_CLASS);
            if (!TileEntity.class.isAssignableFrom(mixerTileClass)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " Block Mixer producer topology drifted");
            }
            requireUniqueDeclaredPublicVoidMethodBySignature(
                    mixerTileClass, registryId + " Block Mixer tick producer");

            Class<?> rendererClass = Class.forName(
                    MALISIS_DOORS_MIXED_BLOCK_RENDERER_CLASS);
            Field itemRenderersField = MinecraftForgeClient.class.getDeclaredField(
                    "customItemRenderers");
            itemRenderersField.setAccessible(true);
            Object itemRenderersValue = itemRenderersField.get(null);
            Object registeredItemRenderer = itemRenderersValue instanceof Map
                    ? ((Map<?, ?>) itemRenderersValue).get(item) : null;
            requireExactRuntimeClass(
                    registeredItemRenderer, MALISIS_DOORS_MIXED_BLOCK_RENDERER_CLASS,
                    registryId + " registered item renderer");
            ISimpleBlockRenderingHandler blockRenderer = pinnedBlockRenderer(
                    block.getRenderType(), MALISIS_DOORS_MIXED_BLOCK_RENDERER_CLASS);
            IItemRenderer itemRenderer = (IItemRenderer) registeredItemRenderer;
            boolean handlesBare = itemRenderer.handleRenderType(
                    bare, IItemRenderer.ItemRenderType.INVENTORY)
                    && MinecraftForgeClient.getItemRenderer(
                    bare, IItemRenderer.ItemRenderType.INVENTORY) == itemRenderer;
            boolean handlesConfigured = itemRenderer.handleRenderType(
                    mixedFromItems, IItemRenderer.ItemRenderType.INVENTORY)
                    && MinecraftForgeClient.getItemRenderer(
                    mixedFromItems, IItemRenderer.ItemRenderType.INVENTORY) == itemRenderer;

            Method setup = rendererClass.getDeclaredMethod("setup");
            setup.setAccessible(true);
            Class<?> rendererBaseClass = rendererClass.getSuperclass();
            Field renderTypeField = rendererBaseClass.getDeclaredField("renderType");
            Field itemStackField = rendererBaseClass.getDeclaredField("itemStack");
            renderTypeField.setAccessible(true);
            itemStackField.setAccessible(true);
            Field mixedBlock1Field = rendererClass.getDeclaredField("block1");
            Field mixedBlock2Field = rendererClass.getDeclaredField("block2");
            Field mixedMetadata1Field = rendererClass.getDeclaredField("metadata1");
            Field mixedMetadata2Field = rendererClass.getDeclaredField("metadata2");
            Field mixedBlockMetadataField = rendererClass.getDeclaredField(
                    "mixedBlockMetadata");
            Field mixedTileEntityField = rendererClass.getDeclaredField("tileEntity");
            mixedBlock1Field.setAccessible(true);
            mixedBlock2Field.setAccessible(true);
            mixedMetadata1Field.setAccessible(true);
            mixedMetadata2Field.setAccessible(true);
            mixedBlockMetadataField.setAccessible(true);
            mixedTileEntityField.setAccessible(true);
            Object originalRenderType = renderTypeField.get(registeredItemRenderer);
            Object originalItemStack = itemStackField.get(registeredItemRenderer);
            Object originalMixedBlock1 = mixedBlock1Field.get(registeredItemRenderer);
            Object originalMixedBlock2 = mixedBlock2Field.get(registeredItemRenderer);
            int originalMixedMetadata1 = mixedMetadata1Field.getInt(registeredItemRenderer);
            int originalMixedMetadata2 = mixedMetadata2Field.getInt(registeredItemRenderer);
            int originalMixedBlockMetadata =
                    mixedBlockMetadataField.getInt(registeredItemRenderer);
            Object originalMixedTileEntity = mixedTileEntityField.get(registeredItemRenderer);
            Object inventoryRenderType = Class.forName(
                    "net.malisis.core.renderer.RenderType")
                    .getField("ITEM_INVENTORY").get(null);
            boolean bareSetup;
            boolean configuredSetup;
            try {
                renderTypeField.set(registeredItemRenderer, inventoryRenderType);
                itemStackField.set(registeredItemRenderer, bare);
                bareSetup = Boolean.TRUE.equals(setup.invoke(registeredItemRenderer));
                itemStackField.set(registeredItemRenderer, mixedFromItems);
                configuredSetup = Boolean.TRUE.equals(setup.invoke(registeredItemRenderer));
            } finally {
                mixedTileEntityField.set(registeredItemRenderer, originalMixedTileEntity);
                mixedBlockMetadataField.setInt(
                        registeredItemRenderer, originalMixedBlockMetadata);
                mixedMetadata2Field.setInt(
                        registeredItemRenderer, originalMixedMetadata2);
                mixedMetadata1Field.setInt(
                        registeredItemRenderer, originalMixedMetadata1);
                mixedBlock2Field.set(registeredItemRenderer, originalMixedBlock2);
                mixedBlock1Field.set(registeredItemRenderer, originalMixedBlock1);
                itemStackField.set(registeredItemRenderer, originalItemStack);
                renderTypeField.set(registeredItemRenderer, originalRenderType);
            }
            Method renderMethod = rendererClass.getMethod("render");
            requirePinnedMalisisDoorsMixedBlockRendererTopology(
                    registeredItemRenderer, blockRenderer,
                    handlesBare, handlesConfigured, bareSetup, configuredSetup,
                    block.getRenderType(), blockRenderer.getRenderId(),
                    renderMethod.getDeclaringClass(), rendererClass);

            ItemStack emptyTag = bare.copy();
            emptyTag.setTagCompound(new NBTTagCompound());
            ItemStack partialTag = bare.copy();
            NBTTagCompound partial = new NBTTagCompound();
            partial.setInteger("block1", Block.getIdFromBlock(Blocks.stone));
            partialTag.setTagCompound(partial);
            if (catalogOnlyExclusion(mixedFromItems) != null
                    || catalogOnlyExclusion(mixedFromTile) != null
                    || catalogOnlyExclusion(emptyTag) != null
                    || catalogOnlyExclusion(partialTag) != null
                    || catalogOnlyExclusion(new ItemStack(item, 2, 0)) != null
                    || catalogOnlyExclusion(new ItemStack(item, 1, 1)) != null
                    || catalogOnlyExclusion(new ItemStack(item, 1, 32767)) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " policy captured a configured, malformed-NBT, or non-bare variant");
            }
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned MalisisDoors mixed-block registry/renderer/producer reflection drift",
                    error);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned MalisisDoors unconfigured mixed-block carrier topology drift",
                    error);
        }
    }

    /**
     * Resolves a public inherited method by its JVM signature and exact declaring owner.
     *
     * <p>Legacy Forge reobfuscates vanilla method names (for example MCP
     * {@code getSubItems} becomes SRG {@code func_150895_a}) but does not rewrite reflection
     * string literals. Signature/owner resolution is therefore the deterministic topology pin:
     * it accepts exactly one method and fails closed on an override, overload collision, or
     * owner drift without maintaining environment-dependent method-name fallbacks.</p>
     */
    static Method requireUniquePublicVoidMethodBySignature(
            Class<?> receiverClass, Class<?> expectedDeclaringClass, String description,
            Class<?>... parameterTypes) {
        Method match = null;
        int matches = 0;
        for (Method method : receiverClass.getMethods()) {
            if (method.getReturnType() == Void.TYPE
                    && Arrays.equals(method.getParameterTypes(), parameterTypes)) {
                matches++;
                match = method;
            }
        }
        if (matches != 1 || match == null
                || match.getDeclaringClass() != expectedDeclaringClass) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + description
                    + " must resolve exactly one public void JVM signature declared by "
                    + expectedDeclaringClass.getName() + "; matches=" + matches
                    + ", declaring=" + (match == null
                    ? "<none>" : match.getDeclaringClass().getName()));
        }
        return match;
    }

    /** Resolves one public method declared by an exact owner without pinning an MCP/SRG name. */
    static Method requireUniqueDeclaredPublicVoidMethodBySignature(
            Class<?> ownerClass, String description, Class<?>... parameterTypes) {
        Method match = null;
        int matches = 0;
        for (Method method : ownerClass.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())
                    && method.getReturnType() == Void.TYPE
                    && Arrays.equals(method.getParameterTypes(), parameterTypes)) {
                matches++;
                match = method;
            }
        }
        if (matches != 1 || match == null) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + description
                    + " must declare exactly one public void JVM signature; matches="
                    + matches);
        }
        return match;
    }

    private static void requireExactMixedBlockFactoryMethod(
            Method method, Class<?> owner, String name, int parameterCount,
            String registryId) {
        int modifiers = method.getModifiers();
        if (method.getDeclaringClass() != owner
                || !name.equals(method.getName())
                || method.getReturnType() != ItemStack.class
                || method.getParameterTypes().length != parameterCount
                || !Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                    + " exact " + name + " producer declaration drifted");
        }
    }

    @SuppressWarnings("unchecked")
    static void requireExactMalisisDoorsMixedBlockProducerNbt(
            ItemStack stack, Item expectedItem,
            Block expectedBlock1, Block expectedBlock2,
            int expectedMetadata1, int expectedMetadata2,
            String boundary) {
        if (stack == null || stack.getItem() != expectedItem
                || stack.stackSize != 1 || stack.getItemDamage() != 0
                || stack.getTagCompound() == null) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + boundary
                    + " did not produce an exact amount-1/meta-0 tagged mixed-block stack");
        }
        NBTTagCompound tag = stack.getTagCompound();
        Set<String> observedKeys = (Set<String>) tag.func_150296_c();
        TreeSet<String> expectedKeys = new TreeSet<String>();
        for (String key : MALISIS_DOORS_MIXED_BLOCK_NBT_KEYS) {
            expectedKeys.add(key);
            if (!tag.hasKey(key, 3)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + boundary
                        + " producer key " + key + " is absent or is not an NBT int");
            }
        }
        if (!expectedKeys.equals(new TreeSet<String>(observedKeys))) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + boundary
                    + " producer NBT keyset drifted; expected=" + expectedKeys
                    + ", got=" + new TreeSet<String>(observedKeys));
        }
        if (tag.getInteger("block1") != Block.getIdFromBlock(expectedBlock1)
                || tag.getInteger("block2") != Block.getIdFromBlock(expectedBlock2)
                || tag.getInteger("metadata1") != expectedMetadata1
                || tag.getInteger("metadata2") != expectedMetadata2) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + boundary
                    + " producer NBT values drifted");
        }
    }

    static void requirePinnedMalisisDoorsMixedBlockRendererTopology(
            Object registeredItemRenderer, Object blockRenderer,
            boolean handlesBare, boolean handlesConfigured,
            boolean bareSetup, boolean configuredSetup,
            int blockRenderId, int rendererRenderId,
            Class<?> renderDeclaringClass, Class<?> expectedRendererClass) {
        if (registeredItemRenderer == null || registeredItemRenderer != blockRenderer) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: MalisisDoors mixed-block item "
                    + "and block renderers must be the same exact owner instance");
        }
        if (!handlesBare || !handlesConfigured) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: MalisisDoors mixed-block exact "
                    + "renderer must own both bare and configured inventory stacks");
        }
        if (bareSetup || !configuredSetup) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: MalisisDoors mixed-block "
                    + "renderer must reject the unconfigured bare carrier and accept its "
                    + "four-key configured NBT envelope");
        }
        if (blockRenderId != rendererRenderId) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: MalisisDoors mixed-block "
                    + "block render ID drifted; block=" + blockRenderId
                    + ", renderer=" + rendererRenderId);
        }
        if (expectedRendererClass == null
                || registeredItemRenderer.getClass() != expectedRendererClass
                || renderDeclaringClass != expectedRendererClass) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: MalisisDoors mixed-block "
                    + "renderer class/declaration drifted");
        }
    }

    @SuppressWarnings("deprecation")
    private static void requirePinnedAe2CableBusRegistration() throws ExportFailure {
        Item item = PinnedCatalogItems.AE2_CABLE_BUS_INTERNAL_ITEM;
        Block block = PinnedCatalogItems.AE2_CABLE_BUS_BLOCK;
        String registryId = AE2_CABLE_BUS_INTERNAL_WORLD_ITEM_BLOCK.registryId;
        requirePinnedBlockItemRegistration(
                item, block, registryId, AE2_CABLE_BUS_ITEM_CLASS, AE2_CABLE_BUS_BLOCK_CLASS);
        try {
            requireExactRegistration(item, registryId);
            requireExactRegistration(block, registryId);

            Object handler = invokeRequiredNoArg(block, "handler", registryId + " feature handler");
            requireExactRuntimeClass(
                    handler, AE2_CABLE_BUS_FEATURE_HANDLER_CLASS,
                    registryId + " feature handler");
            Object available = invokeRequiredNoArg(
                    handler, "isFeatureAvailable", registryId + " feature availability");
            if (!Boolean.TRUE.equals(available)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner feature registration "
                        + registryId + " is not enabled");
            }
            Object definition = invokeRequiredNoArg(
                    handler, "getDefinition", registryId + " owner definition");
            requireOptionalIdentity(
                    invokeRequiredNoArg(definition, "maybeItem", registryId + " definition item"),
                    item, registryId + " definition item");
            requireOptionalIdentity(
                    invokeRequiredNoArg(definition, "maybeBlock", registryId + " definition block"),
                    block, registryId + " definition block");
            Object tileDefinition = requirePresentOptionalValue(
                    invokeRequiredNoArg(
                            definition, "maybeEntity", registryId + " definition tile class"),
                    registryId + " definition tile class");
            Class<?> layeredTileClass = requirePinnedLayeredTileClass(
                    tileDefinition,
                    AE2_CABLE_BUS_LAYERED_TILE_HIERARCHY,
                    registryId + " definition tile class");
            Object blockTileClass = invokeRequiredNoArg(
                    block, "getTileEntityClass", registryId + " block tile class");
            if (blockTileClass != layeredTileClass) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " block tile class is not the exact definition-owned layered class; got "
                        + describeRuntimeValue(blockTileClass));
            }
            Object noTesrTileClass = invokeRequiredNoArg(
                    block, "getNoTesrTile", registryId + " no-TESR tile class");
            if (noTesrTileClass != layeredTileClass) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " no-TESR tile class is not the exact definition-owned layered class; got "
                        + describeRuntimeValue(noTesrTileClass));
            }
            Object itemBlockClass = invokeRequiredNoArg(
                    block, "getItemBlockClass", registryId + " ItemBlock owner class");
            if (!(itemBlockClass instanceof Class)
                    || !AE2_CABLE_BUS_ITEM_CLASS.equals(((Class<?>) itemBlockClass).getName())) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " owner ItemBlock class drifted; expected " + AE2_CABLE_BUS_ITEM_CLASS
                        + ", got " + describeRuntimeValue(itemBlockClass));
            }

            if (item.getCreativeTab() == null
                    || item.getCreativeTab() != block.getCreativeTabToDisplayOn()) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner registration "
                        + registryId + " lost its shared non-null item/block creative tab");
            }
            java.util.ArrayList<ItemStack> itemVariants =
                    new java.util.ArrayList<ItemStack>();
            item.getSubItems(item, item.getCreativeTab(), itemVariants);
            if (!itemVariants.isEmpty()) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner-internal multipart host "
                        + registryId + " unexpectedly exposed direct creative subitems; count="
                        + itemVariants.size());
            }
            java.util.ArrayList<ItemStack> blockVariants =
                    new java.util.ArrayList<ItemStack>();
            block.getSubBlocks(item, block.getCreativeTabToDisplayOn(), blockVariants);
            if (!blockVariants.isEmpty()) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner-internal multipart host "
                        + registryId + " unexpectedly exposed direct block subitems; count="
                        + blockVariants.size());
            }
            Item dropped = block.getItemDropped(0, new Random(0L), 0);
            boolean opaque = block.isOpaqueCube();
            boolean normalRender = block.renderAsNormalBlock();
            if (dropped != null || opaque || normalRender) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner-internal multipart host "
                        + registryId + " world semantics drifted; dropped="
                        + dropped + ", opaque=" + opaque
                        + ", normalRender=" + normalRender);
            }
            Object tile = block.createTileEntity(null, 0);
            String tileClass = tile == null ? "<null>" : tile.getClass().getName();
            if (!block.hasTileEntity(0) || !(tile instanceof TileEntity)
                    || tile.getClass() != layeredTileClass) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner-internal multipart host "
                        + registryId + " tile semantics drifted; expected exact definition-owned "
                        + AE2_CABLE_BUS_LAYERED_TILE_CLASS + ", got " + tileClass);
            }

            ItemStack bare = new ItemStack(item, 1, 0);
            IItemRenderer itemRenderer = MinecraftForgeClient.getItemRenderer(
                    bare, IItemRenderer.ItemRenderType.INVENTORY);
            String itemRendererClass = itemRenderer == null ? "<null>"
                    : itemRenderer.getClass().getName();
            if (!AE2_CABLE_BUS_ITEM_RENDERER_CLASS.equals(itemRendererClass)
                    || !itemRenderer.handleRenderType(
                            bare, IItemRenderer.ItemRenderType.INVENTORY)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                        + " inventory renderer ownership drifted; expected "
                        + AE2_CABLE_BUS_ITEM_RENDERER_CLASS + ", got " + itemRendererClass);
            }
            Object renderInfo = invokeRequiredNoArg(
                    block, "getRendererInstance", registryId + " block render information");
            Object blockRenderer = invokeRequiredNoArg(
                    renderInfo, "getRendererInstance", registryId + " block renderer");
            requireExactRuntimeClass(
                    blockRenderer, AE2_CABLE_BUS_BLOCK_RENDERER_CLASS,
                    registryId + " block renderer");

            if (catalogOnlyExclusion(bare) != AE2_CABLE_BUS_INTERNAL_WORLD_ITEM_BLOCK) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner-internal multipart host "
                        + registryId + " no longer maps to its exact ItemList policy");
            }
            requireStrictCatalogShapeRejected(new ItemStack(item, 2, 0), registryId);
            requireStrictCatalogShapeRejected(new ItemStack(item, 1, 1), registryId);
            ItemStack tagged = bare.copy();
            tagged.setTagCompound(new NBTTagCompound());
            requireStrictCatalogShapeRejected(tagged, registryId);
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned AE2 CableBus owner-registration reflection drift", error);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned AE2 CableBus owner-internal multipart registration drift", error);
        }
    }

    @SuppressWarnings("deprecation")
    private static void requirePinnedAe2MatrixFrameRegistration() throws ExportFailure {
        Item item = PinnedCatalogItems.AE2_MATRIX_FRAME_INTERNAL_ITEM;
        Block block = PinnedCatalogItems.AE2_MATRIX_FRAME_BLOCK;
        String registryId = AE2_MATRIX_FRAME_INTERNAL_WORLD_ITEM_BLOCK.registryId;
        requirePinnedBlockItemRegistration(
                item, block, registryId,
                AE2_MATRIX_FRAME_ITEM_CLASS, AE2_MATRIX_FRAME_BLOCK_CLASS);
        try {
            requireExactRegistration(item, registryId);
            requireExactRegistration(block, registryId);

            Object handler = invokeRequiredNoArg(block, "handler", registryId + " feature handler");
            requireExactRuntimeClass(
                    handler, AE2_MATRIX_FRAME_FEATURE_HANDLER_CLASS,
                    registryId + " feature handler");
            Object available = invokeRequiredNoArg(
                    handler, "isFeatureAvailable", registryId + " feature availability");
            if (!Boolean.TRUE.equals(available)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner feature registration "
                        + registryId + " is not enabled");
            }
            Object definition = invokeRequiredNoArg(
                    handler, "getDefinition", registryId + " owner definition");
            requireOptionalIdentity(
                    invokeRequiredNoArg(definition, "maybeItem", registryId + " definition item"),
                    item, registryId + " definition item");
            requireOptionalIdentity(
                    invokeRequiredNoArg(definition, "maybeBlock", registryId + " definition block"),
                    block, registryId + " definition block");
            Object itemBlockClass = invokeRequiredNoArg(
                    block, "getItemBlockClass", registryId + " ItemBlock owner class");
            if (!(itemBlockClass instanceof Class)
                    || !AE2_MATRIX_FRAME_ITEM_CLASS.equals(
                            ((Class<?>) itemBlockClass).getName())) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " owner ItemBlock class drifted; expected "
                        + AE2_MATRIX_FRAME_ITEM_CLASS + ", got "
                        + describeRuntimeValue(itemBlockClass));
            }

            if (item.getCreativeTab() == null
                    || item.getCreativeTab() != block.getCreativeTabToDisplayOn()) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner registration "
                        + registryId + " lost its shared non-null item/block creative tab");
            }
            java.util.ArrayList<ItemStack> itemVariants =
                    new java.util.ArrayList<ItemStack>();
            item.getSubItems(item, item.getCreativeTab(), itemVariants);
            java.util.ArrayList<ItemStack> blockVariants =
                    new java.util.ArrayList<ItemStack>();
            block.getSubBlocks(item, block.getCreativeTabToDisplayOn(), blockVariants);
            if (!itemVariants.isEmpty() || !blockVariants.isEmpty()) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner-internal spatial "
                        + "substrate " + registryId + " unexpectedly exposed creative subitems; "
                        + "item=" + itemVariants.size() + ", block=" + blockVariants.size());
            }

            Item dropped = block.getItemDropped(0, new Random(0L), 0);
            int droppedMetadata = block.damageDropped(0);
            boolean opaque = block.isOpaqueCube();
            boolean normalRender = block.renderAsNormalBlock();
            float hardness = block.getBlockHardness(null, 0, 0, 0);
            boolean placeable = block.canPlaceBlockAt(null, 0, 0, 0);
            boolean entityDestroyable = block.canEntityDestroy(
                    (IBlockAccess) null, 0, 0, 0, null);
            if (dropped != item || droppedMetadata != 0
                    || opaque || normalRender || hardness != -1.0F
                    || placeable || entityDestroyable) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner-internal spatial "
                        + "substrate " + registryId + " world semantics drifted; dropped="
                        + dropped + ", droppedMetadata=" + droppedMetadata
                        + ", opaque=" + opaque + ", normalRender=" + normalRender
                        + ", hardness=" + hardness + ", placeable=" + placeable
                        + ", entityDestroyable=" + entityDestroyable);
            }
            if (block.hasTileEntity(0) || block.createTileEntity(null, 0) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner-internal spatial "
                        + "substrate " + registryId + " unexpectedly acquired a tile entity");
            }

            ItemStack bare = new ItemStack(item, 1, 0);
            IItemRenderer itemRenderer = MinecraftForgeClient.getItemRenderer(
                    bare, IItemRenderer.ItemRenderType.INVENTORY);
            String itemRendererClass = itemRenderer == null ? "<null>"
                    : itemRenderer.getClass().getName();
            if (!AE2_MATRIX_FRAME_ITEM_RENDERER_CLASS.equals(itemRendererClass)
                    || !itemRenderer.handleRenderType(
                            bare, IItemRenderer.ItemRenderType.INVENTORY)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                        + " inventory renderer ownership drifted; expected "
                        + AE2_MATRIX_FRAME_ITEM_RENDERER_CLASS + ", got " + itemRendererClass);
            }
            Object renderInfo = invokeRequiredNoArg(
                    block, "getRendererInstance", registryId + " block render information");
            Object blockRenderer = invokeRequiredNoArg(
                    renderInfo, "getRendererInstance", registryId + " block renderer");
            requireExactRuntimeClass(
                    blockRenderer, AE2_MATRIX_FRAME_BLOCK_RENDERER_CLASS,
                    registryId + " block renderer");

            if (catalogOnlyExclusion(bare) != AE2_MATRIX_FRAME_INTERNAL_WORLD_ITEM_BLOCK) {
                throw new IllegalArgumentException("ITEM_IDENTITY: owner-internal spatial "
                        + "substrate " + registryId
                        + " no longer maps to its exact ItemList policy");
            }
            requireStrictCatalogShapeRejected(new ItemStack(item, 2, 0), registryId);
            requireStrictCatalogShapeRejected(new ItemStack(item, 1, 1), registryId);
            ItemStack tagged = bare.copy();
            tagged.setTagCompound(new NBTTagCompound());
            requireStrictCatalogShapeRejected(tagged, registryId);
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned AE2 Matrix Frame owner-registration reflection drift", error);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned AE2 Matrix Frame internal spatial-substrate registration drift",
                    error);
        }
    }

    private static Object invokeRequiredNoArg(
            Object owner, String methodName, String boundary)
            throws ReflectiveOperationException {
        if (owner == null) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: " + boundary + " owner is null");
        }
        return owner.getClass().getMethod(methodName).invoke(owner);
    }

    private static void requireOptionalIdentity(
            Object optional, Object expected, String boundary) {
        Object observed = requirePresentOptionalValue(optional, boundary);
        if (observed != expected) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + boundary
                    + " is not the exact registered owner object; got "
                    + describeRuntimeValue(observed));
        }
    }

    static Object requirePresentOptionalValue(Object optional, String boundary) {
        if (!(optional instanceof Optional<?>)) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + boundary
                    + " is not a Guava Optional; got " + describeRuntimeValue(optional));
        }
        Optional<?> guavaOptional = (Optional<?>) optional;
        if (!guavaOptional.isPresent()) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + boundary + " is absent");
        }
        return guavaOptional.get();
    }

    static Class<?> requirePinnedLayeredTileClass(
            Object value, String[] expectedHierarchy, String boundary) {
        if (!(value instanceof Class<?>)) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + boundary
                    + " is not a Class; got " + describeRuntimeValue(value));
        }
        if (expectedHierarchy == null || expectedHierarchy.length < 2) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + boundary
                    + " layered hierarchy policy is incomplete");
        }
        Class<?> observed = (Class<?>) value;
        Class<?> cursor = observed;
        for (int index = 0; index < expectedHierarchy.length; index++) {
            String observedName = cursor == null ? "<null>" : cursor.getName();
            if (!expectedHierarchy[index].equals(observedName)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + boundary
                        + " layered hierarchy drifted at depth " + index
                        + "; expected " + expectedHierarchy[index]
                        + ", got " + observedName);
            }
            cursor = cursor.getSuperclass();
        }
        if (!TileEntity.class.isAssignableFrom(observed)) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + boundary
                    + " exact layered hierarchy is not a TileEntity");
        }
        return observed;
    }

    private static void requireExactRuntimeClass(
            Object value, String expectedClass, String boundary) {
        String observedClass = value == null ? "<null>" : value.getClass().getName();
        if (!expectedClass.equals(observedClass)) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + boundary
                    + " runtime class mismatch; expected " + expectedClass
                    + ", got " + observedClass);
        }
    }

    private static String describeRuntimeValue(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof Class) {
            return ((Class<?>) value).getName();
        }
        return value.getClass().getName();
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private static void requirePinnedWitchingGadgetsCustomAirRegistration()
            throws ExportFailure {
        Item item = PinnedCatalogItems.WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_ITEM;
        Block block = PinnedCatalogItems.WITCHING_GADGETS_CUSTOM_AIR_BLOCK;
        String registryId = WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK.registryId;
        requirePinnedBlockItemRegistration(
                item, block, registryId,
                VANILLA_ITEM_BLOCK_CLASS, WITCHING_GADGETS_CUSTOM_AIR_BLOCK_CLASS);
        try {
            requireExactRegistration(item, registryId);
            requireExactRegistration(block, registryId);
            if (block.getCreativeTabToDisplayOn() == null || item.getCreativeTab() == null
                    || block.getCreativeTabToDisplayOn() != item.getCreativeTab()) {
                throw new IllegalArgumentException("ITEM_IDENTITY: temporary-light block "
                        + registryId + " lost its pinned creative-enumeration leak");
            }
            java.util.ArrayList<ItemStack> variants = new java.util.ArrayList<ItemStack>();
            item.getSubItems(item, item.getCreativeTab(), variants);
            if (variants.size() != 1
                    || !isBareCatalogEntryShape(variants.get(0))
                    || variants.get(0).getItem() != item) {
                throw new IllegalArgumentException("ITEM_IDENTITY: temporary-light block "
                        + registryId + " expected exactly one leaked metadata-0/no-NBT subitem");
            }
            Item dropped = block.getItemDropped(0, new Random(0L), 0);
            boolean opaque = block.isOpaqueCube();
            boolean normalRender = block.renderAsNormalBlock();
            int renderType = block.getRenderType();
            int lightValue = requireContextFreeWitchingGadgetsLightValue(block, registryId);
            if (dropped != null || opaque || normalRender || renderType != -1
                    || lightValue != 14) {
                throw new IllegalArgumentException("ITEM_IDENTITY: temporary-light block "
                        + registryId + " world-state semantics drifted; dropped=" + dropped
                        + ", opaque=" + opaque + ", normalRender=" + normalRender
                        + ", renderType=" + renderType + ", lightValue=" + lightValue);
            }
            IIcon icon = block.getIcon(0, 0);
            String iconName = icon == null ? "<null>" : icon.getIconName();
            Object tile = block.createTileEntity(null, 0);
            String tileClass = tile == null ? "<null>" : tile.getClass().getName();
            if (!WITCHING_GADGETS_CUSTOM_AIR_BLANK_ICON.equals(iconName)
                    || !block.hasTileEntity(0) || !(tile instanceof TileEntity)
                    || !WITCHING_GADGETS_TEMP_LIGHT_TILE_CLASS.equals(tileClass)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: temporary-light block "
                        + registryId + " blank-icon/tile semantics drifted; icon=" + iconName
                        + ", tile=" + tileClass);
            }
            ItemStack bare = new ItemStack(item, 1, 0);
            if (MinecraftForgeClient.getItemRenderer(
                    bare, IItemRenderer.ItemRenderType.INVENTORY) != null) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: temporary-light block "
                        + registryId + " unexpectedly acquired a Forge inventory renderer");
            }
            if (catalogOnlyExclusion(bare)
                    != WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK) {
                throw new IllegalArgumentException("ITEM_IDENTITY: temporary-light block "
                        + registryId + " no longer maps to its exact ItemList policy");
            }
            requireStrictCatalogShapeRejected(new ItemStack(item, 2, 0), registryId);
            requireStrictCatalogShapeRejected(new ItemStack(item, 1, 1), registryId);
            ItemStack tagged = bare.copy();
            tagged.setTagCompound(new NBTTagCompound());
            requireStrictCatalogShapeRejected(tagged, registryId);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned Witching Gadgets temporary-light registration drift", error);
        }
    }

    private static int requireContextFreeWitchingGadgetsLightValue(
            Block block, final String registryId) {
        IBlockAccess rejectingAccess = (IBlockAccess) Proxy.newProxyInstance(
                IBlockAccess.class.getClassLoader(),
                new Class<?>[]{IBlockAccess.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] arguments) {
                        throw new IllegalStateException("ITEM_IDENTITY: temporary-light block "
                                + registryId + " light query unexpectedly accessed IBlockAccess."
                                + method.getName());
                    }
                });
        return block.getLightValue(rejectingAccess, 0, 0, 0);
    }

    private static void requirePinnedStevesCartsModularCartRegistration()
            throws ExportFailure {
        Item item = PinnedCatalogItems.STEVES_CARTS_MODULAR_CART;
        String registryId = STEVES_CARTS_UNCONFIGURED_MODULAR_CART_PLACEHOLDER.registryId;
        requirePinnedRegistration(item, registryId, STEVES_CARTS_MODULAR_CART_ITEM_CLASS);
        try {
            requireExactRegistration(item, registryId);
            if (!(item instanceof ItemMinecart)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " no longer extends net.minecraft.item.ItemMinecart");
            }
            if (!item.getHasSubtypes() || item.getMaxDamage() != 0
                    || item.getCreativeTab() != null || item.getItemStackLimit() != 1) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " presentation traits drifted; expected subtypes=true, maxDamage=0, "
                        + "creativeTab=null, stackLimit=1");
            }
            ItemStack bare = new ItemStack(item, 1, 0);
            if (catalogOnlyExclusion(bare)
                    != STEVES_CARTS_UNCONFIGURED_MODULAR_CART_PLACEHOLDER) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " bare stack no longer maps to its exact ItemList policy");
            }
            ItemStack configured = bare.copy();
            configured.setTagCompound(new NBTTagCompound());
            if (catalogOnlyExclusion(configured) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " NBT-bearing public cart was captured by the ItemList policy");
            }
            IItemRenderer renderer = MinecraftForgeClient.getItemRenderer(
                    bare, IItemRenderer.ItemRenderType.INVENTORY);
            String rendererClass = renderer == null ? "<null>"
                    : renderer.getClass().getName();
            if (!STEVES_CARTS_MODULAR_CART_RENDERER_CLASS.equals(rendererClass)
                    || !renderer.handleRenderType(
                            bare, IItemRenderer.ItemRenderType.INVENTORY)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                        + " inventory renderer ownership drifted; expected "
                        + STEVES_CARTS_MODULAR_CART_RENDERER_CLASS + ", got " + rendererClass);
            }
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned Steve's Carts ModularCart registration drift", error);
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private static void requirePinnedTConstructBattlesignRegistration()
            throws ExportFailure {
        Item internalItem = PinnedCatalogItems.TCONSTRUCT_BATTLESIGN_INTERNAL_ITEM;
        Block block = PinnedCatalogItems.TCONSTRUCT_BATTLESIGN_BLOCK;
        Item publicItem = PinnedCatalogItems.TCONSTRUCT_BATTLESIGN_PUBLIC_ITEM;
        String internalRegistryId = TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK.registryId;
        String publicRegistryId = "TConstruct:battlesign";
        requirePinnedBlockItemRegistration(
                internalItem, block, internalRegistryId,
                VANILLA_ITEM_BLOCK_CLASS, TCONSTRUCT_BATTLESIGN_BLOCK_CLASS);
        requirePinnedRegistration(
                publicItem, publicRegistryId, TCONSTRUCT_BATTLESIGN_PUBLIC_ITEM_CLASS);
        try {
            requireExactRegistration(internalItem, internalRegistryId);
            requireExactRegistration(block, internalRegistryId);
            requireExactRegistration(publicItem, publicRegistryId);
            if (internalItem == publicItem || Block.getBlockFromItem(publicItem) == block) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal battlesign block "
                        + internalRegistryId + " aliases public tool " + publicRegistryId);
            }
            if (block.getCreativeTabToDisplayOn() != null || internalItem.getCreativeTab() != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal battlesign block "
                        + internalRegistryId + " unexpectedly has a creative tab");
            }
            if (block.getItemDropped(0, new Random(0L), 0) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal battlesign block "
                        + internalRegistryId + " unexpectedly drops its ItemBlock identity");
            }
            if (!block.hasTileEntity(0)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal battlesign block "
                        + internalRegistryId + " no longer owns tile-backed equipment state");
            }
            int renderType = block.getRenderType();
            ISimpleBlockRenderingHandler blockRenderer =
                    pinnedBlockRenderer(renderType, TCONSTRUCT_BATTLESIGN_BLOCK_RENDERER_CLASS);
            if (blockRenderer.getRenderId() != renderType
                    || !blockRenderer.shouldRender3DInInventory(renderType)
                    || !RenderingRegistry.instance().renderItemAsFull3DBlock(renderType)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + internalRegistryId
                        + " custom inventory-renderer ownership drifted");
            }
            ItemStack internalBare = new ItemStack(internalItem, 1, 0);
            if (MinecraftForgeClient.getItemRenderer(
                    internalBare, IItemRenderer.ItemRenderType.INVENTORY) != null) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + internalRegistryId
                        + " unexpectedly acquired a Forge item renderer");
            }
            if (catalogOnlyExclusion(internalBare)
                    != TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + internalRegistryId
                        + " bare stack no longer maps to its exact ItemList policy");
            }
            ItemStack taggedInternal = internalBare.copy();
            taggedInternal.setTagCompound(new NBTTagCompound());
            try {
                catalogOnlyExclusion(taggedInternal);
                throw new IllegalArgumentException("ITEM_IDENTITY: " + internalRegistryId
                        + " strict policy accepted an NBT-bearing internal ItemBlock");
            } catch (IllegalArgumentException expected) {
                if (expected.getMessage() == null
                        || !expected.getMessage().contains("unmodeled stack shape")) {
                    throw expected;
                }
            }
            if (publicItem.getCreativeTab() == null || publicItem.getItemStackLimit() != 1
                    || publicItem.getMaxDamage() != 100) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public battlesign tool "
                        + publicRegistryId + " presentation traits drifted; expected a creative "
                        + "tool with stackLimit=1 and maxDamage=100");
            }
            java.util.ArrayList<ItemStack> publicVariants =
                    new java.util.ArrayList<ItemStack>();
            publicItem.getSubItems(publicItem, publicItem.getCreativeTab(), publicVariants);
            if (publicVariants.isEmpty()) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public battlesign tool "
                        + publicRegistryId + " exposed no configured creative variants");
            }
            for (ItemStack variant : publicVariants) {
                if (variant == null || variant.getItem() != publicItem
                        || !variant.hasTagCompound()
                        || !variant.getTagCompound().hasKey("InfiTool", 10)
                        || catalogOnlyExclusion(variant) != null) {
                    throw new IllegalArgumentException("ITEM_IDENTITY: public battlesign tool "
                            + publicRegistryId
                            + " exposed an unconfigured or excluded creative variant");
                }
            }
            IItemRenderer publicRenderer = MinecraftForgeClient.getItemRenderer(
                    publicVariants.get(0), IItemRenderer.ItemRenderType.INVENTORY);
            String publicRendererClass = publicRenderer == null ? "<null>"
                    : publicRenderer.getClass().getName();
            if (!TCONSTRUCT_BATTLESIGN_PUBLIC_ITEM_RENDERER_CLASS.equals(publicRendererClass)
                    || !publicRenderer.handleRenderType(
                            publicVariants.get(0), IItemRenderer.ItemRenderType.INVENTORY)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: public battlesign tool "
                        + publicRegistryId + " renderer ownership drifted; expected "
                        + TCONSTRUCT_BATTLESIGN_PUBLIC_ITEM_RENDERER_CLASS + ", got "
                        + publicRendererClass);
            }
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned TConstruct battlesign renderer registry drift", error);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned TConstruct battlesign internal/public registration drift", error);
        }
    }

    @SuppressWarnings("deprecation")
    private static void requirePinnedTConstructHeldItemRegistration()
            throws ExportFailure {
        Item internalItem = PinnedCatalogItems.TCONSTRUCT_HELD_ITEM_INTERNAL_ITEM;
        Block block = PinnedCatalogItems.TCONSTRUCT_HELD_ITEM_BLOCK;
        Item publicItem = PinnedCatalogItems.TCONSTRUCT_HELD_ITEM_PUBLIC_ITEM;
        String internalRegistryId = TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK.registryId;
        String publicRegistryId = "TConstruct:frypan";
        requirePinnedBlockItemRegistration(
                internalItem, block, internalRegistryId,
                VANILLA_ITEM_BLOCK_CLASS, TCONSTRUCT_HELD_ITEM_BLOCK_CLASS);
        requirePinnedRegistration(
                publicItem, publicRegistryId, TCONSTRUCT_HELD_ITEM_PUBLIC_ITEM_CLASS);
        try {
            requireExactRegistration(internalItem, internalRegistryId);
            requireExactRegistration(block, internalRegistryId);
            requireExactRegistration(publicItem, publicRegistryId);
            if (internalItem == publicItem || Block.getBlockFromItem(publicItem) == block) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal frying-pan block "
                        + internalRegistryId + " aliases public tool " + publicRegistryId);
            }
            if (block.getCreativeTabToDisplayOn() != null || internalItem.getCreativeTab() != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal frying-pan block "
                        + internalRegistryId + " unexpectedly has a creative tab");
            }
            if (block.getItemDropped(0, new Random(0L), 0) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal frying-pan block "
                        + internalRegistryId + " unexpectedly drops its ItemBlock identity");
            }
            if (!block.hasTileEntity(0)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal frying-pan block "
                        + internalRegistryId + " no longer owns tile-backed equipment state");
            }
            Object tile = block.createTileEntity(null, 0);
            String tileClass = tile == null ? "<null>" : tile.getClass().getName();
            if (!TCONSTRUCT_HELD_ITEM_TILE_CLASS.equals(tileClass)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal frying-pan block "
                        + internalRegistryId + " expected tile "
                        + TCONSTRUCT_HELD_ITEM_TILE_CLASS + ", got " + tileClass);
            }
            int renderType = block.getRenderType();
            ISimpleBlockRenderingHandler blockRenderer =
                    pinnedBlockRenderer(renderType, TCONSTRUCT_HELD_ITEM_BLOCK_RENDERER_CLASS);
            if (blockRenderer.getRenderId() != renderType
                    || !blockRenderer.shouldRender3DInInventory(renderType)
                    || !RenderingRegistry.instance().renderItemAsFull3DBlock(renderType)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + internalRegistryId
                        + " custom inventory-renderer ownership drifted");
            }
            ItemStack internalBare = new ItemStack(internalItem, 1, 0);
            if (MinecraftForgeClient.getItemRenderer(
                    internalBare, IItemRenderer.ItemRenderType.INVENTORY) != null) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + internalRegistryId
                        + " unexpectedly acquired a Forge item renderer");
            }
            if (catalogOnlyExclusion(internalBare)
                    != TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + internalRegistryId
                        + " bare stack no longer maps to its exact ItemList policy");
            }
            ItemStack taggedInternal = internalBare.copy();
            taggedInternal.setTagCompound(new NBTTagCompound());
            try {
                catalogOnlyExclusion(taggedInternal);
                throw new IllegalArgumentException("ITEM_IDENTITY: " + internalRegistryId
                        + " strict policy accepted an NBT-bearing internal ItemBlock");
            } catch (IllegalArgumentException expected) {
                if (expected.getMessage() == null
                        || !expected.getMessage().contains("unmodeled stack shape")) {
                    throw expected;
                }
            }
            if (publicItem.getCreativeTab() == null || publicItem.getItemStackLimit() != 1
                    || publicItem.getMaxDamage() != 100) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public frying-pan tool "
                        + publicRegistryId + " presentation traits drifted; expected a creative "
                        + "tool with stackLimit=1 and maxDamage=100");
            }
            java.util.ArrayList<ItemStack> publicVariants =
                    new java.util.ArrayList<ItemStack>();
            publicItem.getSubItems(publicItem, publicItem.getCreativeTab(), publicVariants);
            if (publicVariants.isEmpty()) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public frying-pan tool "
                        + publicRegistryId + " exposed no configured creative variants");
            }
            for (ItemStack variant : publicVariants) {
                if (variant == null || variant.getItem() != publicItem
                        || !variant.hasTagCompound()
                        || !variant.getTagCompound().hasKey("InfiTool", 10)
                        || catalogOnlyExclusion(variant) != null) {
                    throw new IllegalArgumentException("ITEM_IDENTITY: public frying-pan tool "
                            + publicRegistryId
                            + " exposed an unconfigured or excluded creative variant");
                }
            }
            IItemRenderer publicRenderer = MinecraftForgeClient.getItemRenderer(
                    publicVariants.get(0), IItemRenderer.ItemRenderType.INVENTORY);
            String publicRendererClass = publicRenderer == null ? "<null>"
                    : publicRenderer.getClass().getName();
            if (!TCONSTRUCT_HELD_ITEM_PUBLIC_ITEM_RENDERER_CLASS.equals(publicRendererClass)
                    || !publicRenderer.handleRenderType(
                            publicVariants.get(0), IItemRenderer.ItemRenderType.INVENTORY)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: public frying-pan tool "
                        + publicRegistryId + " renderer ownership drifted; expected "
                        + TCONSTRUCT_HELD_ITEM_PUBLIC_ITEM_RENDERER_CLASS + ", got "
                        + publicRendererClass);
            }
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned TConstruct frying-pan renderer registry drift", error);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned TConstruct frying-pan internal/public registration drift", error);
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private static void requirePinnedThaumcraftBlockHoleRegistration()
            throws ExportFailure {
        Item internalItem = PinnedCatalogItems.THAUMCRAFT_BLOCK_HOLE_INTERNAL_ITEM;
        Block block = PinnedCatalogItems.THAUMCRAFT_BLOCK_HOLE_BLOCK;
        Item publicFocus = PinnedCatalogItems.THAUMCRAFT_PORTABLE_HOLE_FOCUS;
        String internalRegistryId = THAUMCRAFT_BLOCK_HOLE_INTERNAL_WORLD_ITEM_BLOCK.registryId;
        String publicRegistryId = "Thaumcraft:FocusPortableHole";
        requirePinnedBlockItemRegistration(
                internalItem, block, internalRegistryId,
                VANILLA_ITEM_BLOCK_CLASS, THAUMCRAFT_BLOCK_HOLE_CLASS);
        requirePinnedRegistration(
                publicFocus, publicRegistryId, THAUMCRAFT_PORTABLE_HOLE_FOCUS_CLASS);
        try {
            requireExactRegistration(internalItem, internalRegistryId);
            requireExactRegistration(block, internalRegistryId);
            requireExactRegistration(publicFocus, publicRegistryId);
            if (internalItem == publicFocus || Block.getBlockFromItem(publicFocus) == block) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal portable-hole block "
                        + internalRegistryId + " aliases public focus " + publicRegistryId);
            }
            if (block.getCreativeTabToDisplayOn() != null
                    || internalItem.getCreativeTab() != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal portable-hole block "
                        + internalRegistryId + " unexpectedly has a creative tab");
            }
            if (block.getItemDropped(0, new Random(0L), 0) != null
                    || block.getPickBlock(null, null, 0, 0, 0) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal portable-hole block "
                        + internalRegistryId + " unexpectedly exposes a dropped/picked item");
            }
            java.util.ArrayList<ItemStack> subBlocks =
                    new java.util.ArrayList<ItemStack>();
            block.getSubBlocks(internalItem, null, subBlocks);
            if (!subBlocks.isEmpty()) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal portable-hole block "
                        + internalRegistryId + " unexpectedly exposes creative sub-blocks");
            }
            if (!block.hasTileEntity(0) || block.createTileEntity(null, 0) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal portable-hole block "
                        + internalRegistryId + " ordinary tile-creation semantics drifted");
            }
            Class<?> tileClass = Class.forName(
                    THAUMCRAFT_TILE_HOLE_CLASS, false, StackIdentity.class.getClassLoader());
            if (!TileEntity.class.isAssignableFrom(tileClass)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: "
                        + THAUMCRAFT_TILE_HOLE_CLASS
                        + " no longer extends net.minecraft.tileentity.TileEntity");
            }
            if (block.isOpaqueCube() || block.renderAsNormalBlock()) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal portable-hole block "
                        + internalRegistryId + " opaque/normal rendering semantics drifted");
            }
            IIcon blankIcon = block.getIcon(0, 0);
            IIcon emptySentinelIcon = block.getIcon(0, 15);
            String blankIconName = blankIcon == null ? "<null>" : blankIcon.getIconName();
            String emptySentinelIconName = emptySentinelIcon == null
                    ? "<null>" : emptySentinelIcon.getIconName();
            if (!THAUMCRAFT_BLOCK_HOLE_BLANK_ICON.equals(blankIconName)
                    || !THAUMCRAFT_BLOCK_HOLE_EMPTY_SENTINEL_ICON.equals(
                            emptySentinelIconName)
                    || blankIconName.equals(emptySentinelIconName)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + internalRegistryId
                        + " metadata icon semantics drifted; expected metadata 0="
                        + THAUMCRAFT_BLOCK_HOLE_BLANK_ICON + " and metadata 15="
                        + THAUMCRAFT_BLOCK_HOLE_EMPTY_SENTINEL_ICON + ", got metadata 0="
                        + blankIconName + " and metadata 15=" + emptySentinelIconName);
            }
            ItemStack internalBare = new ItemStack(internalItem, 1, 0);
            if (MinecraftForgeClient.getItemRenderer(
                    internalBare, IItemRenderer.ItemRenderType.INVENTORY) != null) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + internalRegistryId
                        + " unexpectedly acquired a Forge inventory item renderer");
            }
            if (catalogOnlyExclusion(internalBare)
                    != THAUMCRAFT_BLOCK_HOLE_INTERNAL_WORLD_ITEM_BLOCK) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + internalRegistryId
                        + " bare metadata-0 stack no longer maps to its exact ItemList policy");
            }
            ItemStack diagramSentinel = new ItemStack(internalItem, 1, 15);
            if (catalogOnlyExclusion(diagramSentinel) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + internalRegistryId
                        + " metadata-15 compound-diagram sentinel was captured by the "
                        + "catalog-only exclusion");
            }
            ItemStack taggedInternal = internalBare.copy();
            taggedInternal.setTagCompound(new NBTTagCompound());
            try {
                catalogOnlyExclusion(taggedInternal);
                throw new IllegalArgumentException("ITEM_IDENTITY: " + internalRegistryId
                        + " strict policy accepted an NBT-bearing internal ItemBlock");
            } catch (IllegalArgumentException expected) {
                if (expected.getMessage() == null
                        || !expected.getMessage().contains("unmodeled stack shape")) {
                    throw expected;
                }
            }
            if (publicFocus.getCreativeTab() == null
                    || publicFocus.getIconFromDamage(0) == null
                    || catalogOnlyExclusion(new ItemStack(publicFocus, 1, 0)) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public portable-hole focus "
                        + publicRegistryId
                        + " lost its creative/icon presentation or was excluded");
            }
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned Thaumcraft portable-hole tile class drift", error);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned Thaumcraft portable-hole internal/public registration drift", error);
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private static void requirePinnedThaumcraftEldritchPortalRegistration()
            throws ExportFailure {
        Item internalItem = PinnedCatalogItems.THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_ITEM;
        Block block = PinnedCatalogItems.THAUMCRAFT_ELDRITCH_PORTAL_BLOCK;
        Item publicItem = PinnedCatalogItems.THAUMCRAFT_ELDRITCH_OBJECT_PUBLIC_ITEM;
        String internalRegistryId =
                THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_WORLD_ITEM_BLOCK.registryId;
        String publicRegistryId = "Thaumcraft:ItemEldritchObject";
        requirePinnedBlockItemRegistration(
                internalItem, block, internalRegistryId,
                VANILLA_ITEM_BLOCK_CLASS, THAUMCRAFT_ELDRITCH_PORTAL_BLOCK_CLASS);
        requirePinnedRegistration(
                publicItem, publicRegistryId, THAUMCRAFT_ELDRITCH_OBJECT_ITEM_CLASS);
        try {
            requireExactRegistration(internalItem, internalRegistryId);
            requireExactRegistration(block, internalRegistryId);
            requireExactRegistration(publicItem, publicRegistryId);
            if (internalItem == publicItem || Block.getBlockFromItem(publicItem) == block) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal Eldritch Portal block "
                        + internalRegistryId + " aliases public item " + publicRegistryId);
            }
            if (block.getCreativeTabToDisplayOn() != null
                    || internalItem.getCreativeTab() != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal Eldritch Portal block "
                        + internalRegistryId + " unexpectedly has a creative tab");
            }
            if (block.getItemDropped(0, new Random(0L), 0) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal Eldritch Portal block "
                        + internalRegistryId + " unexpectedly has a normal item drop");
            }
            if (block.isOpaqueCube() || block.renderAsNormalBlock()
                    || block.getRenderType() != -1) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: internal Eldritch Portal "
                        + internalRegistryId
                        + " expected nonopaque, nonnormal, renderType=-1 semantics");
            }
            IIcon blankIcon = block.getIcon(0, 0);
            String blankIconName = blankIcon == null ? "<null>" : blankIcon.getIconName();
            if (!THAUMCRAFT_ELDRITCH_PORTAL_BLANK_ICON.equals(blankIconName)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + internalRegistryId
                        + " expected blank icon " + THAUMCRAFT_ELDRITCH_PORTAL_BLANK_ICON
                        + ", got " + blankIconName);
            }
            Object tile = block.createTileEntity(null, 0);
            String tileClass = tile == null ? "<null>" : tile.getClass().getName();
            if (!block.hasTileEntity(0) || !(tile instanceof TileEntity)
                    || !THAUMCRAFT_ELDRITCH_PORTAL_TILE_CLASS.equals(tileClass)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal Eldritch Portal block "
                        + internalRegistryId + " expected tile "
                        + THAUMCRAFT_ELDRITCH_PORTAL_TILE_CLASS + ", got " + tileClass);
            }
            ItemStack internalBare = new ItemStack(internalItem, 1, 0);
            if (MinecraftForgeClient.getItemRenderer(
                    internalBare, IItemRenderer.ItemRenderType.INVENTORY) != null) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + internalRegistryId
                        + " unexpectedly acquired a Forge inventory item renderer");
            }
            if (matchThaumcraftEldritchPortalCatalogPolicy(internalBare, internalItem)
                    != THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_WORLD_ITEM_BLOCK
                    || catalogOnlyExclusion(internalBare)
                    != THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_WORLD_ITEM_BLOCK) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + internalRegistryId
                        + " bare stack no longer maps to its exact ItemList policy");
            }
            ItemStack taggedInternal = internalBare.copy();
            taggedInternal.setTagCompound(new NBTTagCompound());
            try {
                matchThaumcraftEldritchPortalCatalogPolicy(taggedInternal, internalItem);
                throw new IllegalArgumentException("ITEM_IDENTITY: " + internalRegistryId
                        + " strict policy accepted an NBT-bearing internal ItemBlock");
            } catch (IllegalArgumentException expected) {
                if (expected.getMessage() == null
                        || !expected.getMessage().contains("unmodeled stack shape")) {
                    throw expected;
                }
            }
            IIcon publicIcon = publicItem.getIconFromDamage(0);
            String publicIconName = publicIcon == null ? "<null>" : publicIcon.getIconName();
            if (publicItem.getCreativeTab() == null || !publicItem.getHasSubtypes()
                    || publicItem.getMaxDamage() != 0 || publicItem.getItemStackLimit() != 1
                    || !THAUMCRAFT_ELDRITCH_OBJECT_ICON.equals(publicIconName)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public Eldritch object "
                        + publicRegistryId + " visible metadata-0 semantics drifted");
            }
            java.util.ArrayList<ItemStack> publicVariants =
                    new java.util.ArrayList<ItemStack>();
            publicItem.getSubItems(publicItem, publicItem.getCreativeTab(), publicVariants);
            boolean foundPublicMetadataZero = false;
            for (ItemStack variant : publicVariants) {
                if (isExactThaumcraftEldritchObjectRetainedItemListEntry(
                        variant, publicItem)) {
                    foundPublicMetadataZero = true;
                    break;
                }
            }
            ItemStack publicBare = new ItemStack(publicItem, 1, 0);
            if (!foundPublicMetadataZero
                    || !isExactThaumcraftEldritchObjectRetainedItemListEntry(
                            publicBare, publicItem)
                    || matchThaumcraftEldritchPortalCatalogPolicy(publicBare, internalItem) != null
                    || catalogOnlyExclusion(publicBare) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public Eldritch object "
                        + publicRegistryId + " metadata 0 was hidden or captured by the internal "
                        + "portal exclusion");
            }
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned Thaumcraft Eldritch Portal internal/public registration drift", error);
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private static void requirePinnedGadomancyEldritchPortalRegistration()
            throws ExportFailure {
        Item item = PinnedCatalogItems.GADOMANCY_ELDRITCH_PORTAL_ITEM;
        Block block = PinnedCatalogItems.GADOMANCY_ELDRITCH_PORTAL_BLOCK;
        Item coreInternalItem = PinnedCatalogItems.THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_ITEM;
        String registryId = "gadomancy:BlockAdditionalEldritchPortal";
        requirePinnedBlockItemRegistration(
                item, block, registryId,
                GADOMANCY_ELDRITCH_PORTAL_ITEM_CLASS,
                GADOMANCY_ELDRITCH_PORTAL_BLOCK_CLASS);
        try {
            requireExactRegistration(item, registryId);
            requireExactRegistration(block, registryId);
            if (item == coreInternalItem
                    || block == PinnedCatalogItems.THAUMCRAFT_ELDRITCH_PORTAL_BLOCK) {
                throw new IllegalArgumentException("ITEM_IDENTITY: Gadomancy portal placer "
                        + registryId + " aliases the core Thaumcraft portal identity");
            }
            if (item.getCreativeTab() == null || block.getCreativeTabToDisplayOn() == null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: Gadomancy portal placer "
                        + registryId + " is no longer creative-visible");
            }
            IIcon icon = item.getIconFromDamage(0);
            String iconName = icon == null ? "<null>" : icon.getIconName();
            if (!GADOMANCY_ELDRITCH_PORTAL_ICON.equals(iconName)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: Gadomancy portal placer "
                        + registryId + " expected icon " + GADOMANCY_ELDRITCH_PORTAL_ICON
                        + ", got " + iconName);
            }
            java.util.ArrayList<ItemStack> variants = new java.util.ArrayList<ItemStack>();
            item.getSubItems(item, item.getCreativeTab(), variants);
            if (variants.size() != 1
                    || !isExactGadomancyEldritchPortalRetainedItemListEntry(
                            variants.get(0), item)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: Gadomancy portal placer "
                        + registryId
                        + " expected one exact bare metadata-0/no-NBT creative subitem");
            }
            ItemStack bare = new ItemStack(item, 1, 0);
            if (!isExactGadomancyEldritchPortalRetainedItemListEntry(bare, item)
                    || matchThaumcraftEldritchPortalCatalogPolicy(bare, coreInternalItem) != null
                    || catalogOnlyExclusion(bare) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: Gadomancy portal placer "
                        + registryId + " was captured by the core portal exclusion");
            }
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned Gadomancy Eldritch Portal placer registration drift", error);
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    private static void requirePinnedThaumicHorizonsIlluminationLightRegistration()
            throws ExportFailure {
        Item baseItem = PinnedCatalogItems.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_ITEM;
        Block baseBlock = PinnedCatalogItems.THAUMIC_HORIZONS_BASE_LIGHT_BLOCK;
        Item solarItem = PinnedCatalogItems.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_ITEM;
        Block solarBlock = PinnedCatalogItems.THAUMIC_HORIZONS_SOLAR_LIGHT_BLOCK;
        Item publicFocus = PinnedCatalogItems.THAUMIC_HORIZONS_ILLUMINATION_FOCUS;
        String baseRegistryId =
                THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK.registryId;
        String solarRegistryId =
                THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK.registryId;
        String publicRegistryId = "ThaumicHorizons:focusIllumination";
        requirePinnedBlockItemRegistration(
                baseItem, baseBlock, baseRegistryId,
                VANILLA_ITEM_BLOCK_CLASS, THAUMIC_HORIZONS_LIGHT_BLOCK_CLASS);
        requirePinnedBlockItemRegistration(
                solarItem, solarBlock, solarRegistryId,
                VANILLA_ITEM_BLOCK_CLASS, THAUMIC_HORIZONS_SOLAR_LIGHT_BLOCK_CLASS);
        requirePinnedRegistration(
                publicFocus, publicRegistryId,
                THAUMIC_HORIZONS_ILLUMINATION_FOCUS_ITEM_CLASS);
        try {
            requireExactRegistration(baseItem, baseRegistryId);
            requireExactRegistration(baseBlock, baseRegistryId);
            requireExactRegistration(solarItem, solarRegistryId);
            requireExactRegistration(solarBlock, solarRegistryId);
            requireExactRegistration(publicFocus, publicRegistryId);
            if (baseItem == solarItem || baseBlock == solarBlock
                    || baseItem == publicFocus || solarItem == publicFocus
                    || Block.getBlockFromItem(publicFocus) == baseBlock
                    || Block.getBlockFromItem(publicFocus) == solarBlock) {
                throw new IllegalArgumentException(
                        "ITEM_IDENTITY: Thaumic Horizons illumination world-state blocks alias "
                                + "one another or the public focus");
            }
            requirePinnedThaumicHorizonsLightBlockSemantics(
                    baseItem, baseBlock, baseRegistryId, THAUMIC_HORIZONS_LIGHT_BLOCK_CLASS);
            requirePinnedThaumicHorizonsLightBlockSemantics(
                    solarItem, solarBlock, solarRegistryId,
                    THAUMIC_HORIZONS_SOLAR_LIGHT_BLOCK_CLASS);

            ItemStack baseBare = new ItemStack(baseItem, 1, 0);
            if (catalogOnlyExclusion(baseBare)
                    != THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + baseRegistryId
                        + " bare stack no longer maps to its exact ItemList policy");
            }
            ItemStack solarBare = new ItemStack(solarItem, 1, 0);
            if (catalogOnlyExclusion(solarBare)
                    != THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + solarRegistryId
                        + " bare stack no longer maps to its exact ItemList policy");
            }
            ItemStack taggedSolar = solarBare.copy();
            taggedSolar.setTagCompound(new NBTTagCompound());
            try {
                catalogOnlyExclusion(taggedSolar);
                throw new IllegalArgumentException("ITEM_IDENTITY: " + solarRegistryId
                        + " strict policy accepted an NBT-bearing internal ItemBlock");
            } catch (IllegalArgumentException expected) {
                if (expected.getMessage() == null
                        || !expected.getMessage().contains("unmodeled stack shape")) {
                    throw expected;
                }
            }
            ItemStack taggedBase = baseBare.copy();
            taggedBase.setTagCompound(new NBTTagCompound());
            try {
                catalogOnlyExclusion(taggedBase);
                throw new IllegalArgumentException("ITEM_IDENTITY: " + baseRegistryId
                        + " strict policy accepted an NBT-bearing internal ItemBlock");
            } catch (IllegalArgumentException expected) {
                if (expected.getMessage() == null
                        || !expected.getMessage().contains("unmodeled stack shape")) {
                    throw expected;
                }
            }

            if (publicFocus.getCreativeTab() == null || !publicFocus.getHasSubtypes()
                    || publicFocus.getMaxDamage() != 0
                    || publicFocus.getItemStackLimit() != 1) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public illumination focus "
                        + publicRegistryId + " presentation traits drifted");
            }
            java.util.ArrayList<ItemStack> focusVariants =
                    new java.util.ArrayList<ItemStack>();
            publicFocus.getSubItems(publicFocus, publicFocus.getCreativeTab(), focusVariants);
            int metadataMask = 0;
            for (ItemStack variant : focusVariants) {
                if (!isExactThaumicHorizonsIlluminationFocusRetainedItemListEntry(
                        variant, publicFocus)) {
                    throw new IllegalArgumentException("ITEM_IDENTITY: public illumination focus "
                            + publicRegistryId + " exposed an unrelated creative variant");
                }
                if (publicFocus.getIconFromDamage(variant.getItemDamage()) == null
                        || catalogOnlyExclusion(variant) != null) {
                    throw new IllegalArgumentException("ITEM_IDENTITY: public illumination focus "
                            + publicRegistryId + " variant lost its icon or was excluded; metadata="
                            + variant.getItemDamage());
                }
                metadataMask |= 1 << variant.getItemDamage();
            }
            if (focusVariants.size() != 16 || metadataMask != 0xffff) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public illumination focus "
                        + publicRegistryId + " expected 16 metadata variants with mask 0xffff, got "
                        + focusVariants.size() + " and 0x" + Integer.toHexString(metadataMask));
            }
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned Thaumic Horizons illumination light/focus registration drift", error);
        }
    }

    private static void requirePinnedThaumicHorizonsLightBlockSemantics(
            Item item, Block block, String registryId, String expectedBlockClass) {
        if (!expectedBlockClass.equals(block.getClass().getName())
                || block.getCreativeTabToDisplayOn() != null || item.getCreativeTab() != null
                || block.getItemDropped(0, new Random(0L), 0) != null
                || block.isOpaqueCube() || block.renderAsNormalBlock()
                || block.getRenderType() != -1
                || block.getCollisionBoundingBoxFromPool(null, 0, 0, 0) != null) {
            throw new IllegalArgumentException("ITEM_IDENTITY: owner-internal illumination block "
                    + registryId + " world-state semantics drifted");
        }
        IIcon icon = block.getIcon(0, 0);
        String iconName = icon == null ? "<null>" : icon.getIconName();
        Object tile = block.createTileEntity(null, 0);
        String tileClass = tile == null ? "<null>" : tile.getClass().getName();
        if (!THAUMIC_HORIZONS_LIGHT_BLANK_ICON.equals(iconName)
                || !block.hasTileEntity(0) || !(tile instanceof TileEntity)
                || !THAUMIC_HORIZONS_LIGHT_TILE_CLASS.equals(tileClass)) {
            throw new IllegalArgumentException("ITEM_IDENTITY: owner-internal illumination block "
                    + registryId + " blank-icon/tile semantics drifted; icon=" + iconName
                    + ", tile=" + tileClass);
        }
        ItemStack bare = new ItemStack(item, 1, 0);
        if (MinecraftForgeClient.getItemRenderer(
                bare, IItemRenderer.ItemRenderType.INVENTORY) != null) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: owner-internal illumination block "
                    + registryId + " unexpectedly acquired a Forge inventory item renderer");
        }
    }

    @SuppressWarnings("deprecation")
    private static void requirePinnedTwilightForestExperiment115Registration()
            throws ExportFailure {
        Item internalItem = PinnedCatalogItems.TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_ITEM;
        Block block = PinnedCatalogItems.TWILIGHT_FOREST_EXPERIMENT_115_BLOCK;
        Item publicItem = PinnedCatalogItems.TWILIGHT_FOREST_EXPERIMENT_115_PUBLIC_ITEM;
        String internalRegistryId =
                TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK.registryId;
        String publicRegistryId = "TwilightForest:item.experiment115";
        requirePinnedBlockItemRegistration(
                internalItem, block, internalRegistryId,
                TWILIGHT_FOREST_EXPERIMENT_115_ITEM_BLOCK_CLASS,
                TWILIGHT_FOREST_EXPERIMENT_115_BLOCK_CLASS);
        requirePinnedRegistration(
                publicItem, publicRegistryId,
                TWILIGHT_FOREST_EXPERIMENT_115_PUBLIC_ITEM_CLASS);
        try {
            requireExactRegistration(internalItem, internalRegistryId);
            requireExactRegistration(block, internalRegistryId);
            requireExactRegistration(publicItem, publicRegistryId);
            if (internalItem == publicItem || Block.getBlockFromItem(publicItem) == block) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal Experiment 115 block "
                        + internalRegistryId + " aliases public food " + publicRegistryId);
            }
            if (block.getCreativeTabToDisplayOn() != null
                    || internalItem.getCreativeTab() != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal Experiment 115 block "
                        + internalRegistryId + " unexpectedly has a creative tab");
            }
            if (publicItem.getCreativeTab() == null
                    || publicItem.getIconFromDamage(0) == null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public Experiment 115 food "
                        + publicRegistryId + " lacks its pinned creative/icon presentation");
            }
            if (block.getItemDropped(0, new Random(0L), 0) != null
                    || block.getItem(null, 0, 0, 0) != publicItem) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal Experiment 115 block "
                        + internalRegistryId + " no longer has a null normal drop and public-food "
                        + "pick identity " + publicRegistryId);
            }
            if (block.isOpaqueCube() || block.renderAsNormalBlock()) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: internal Experiment 115 "
                        + "block " + internalRegistryId
                        + " expected nonopaque, nonnormal world-state rendering");
            }
            Object tile = block.createTileEntity(null, 0);
            String tileClass = tile == null ? "<null>" : tile.getClass().getName();
            if (!block.hasTileEntity(0) || !(tile instanceof TileEntity)
                    || !TWILIGHT_FOREST_EXPERIMENT_115_TILE_CLASS.equals(tileClass)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal Experiment 115 block "
                        + internalRegistryId + " expected tile "
                        + TWILIGHT_FOREST_EXPERIMENT_115_TILE_CLASS + ", got " + tileClass);
            }
            int renderType = block.getRenderType();
            ISimpleBlockRenderingHandler blockRenderer = pinnedBlockRenderer(
                    renderType, TWILIGHT_FOREST_EXPERIMENT_115_BLOCK_RENDERER_CLASS);
            if (blockRenderer.getRenderId() != renderType
                    || !blockRenderer.shouldRender3DInInventory(renderType)
                    || !RenderingRegistry.instance().renderItemAsFull3DBlock(renderType)) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + internalRegistryId
                        + " custom inventory-renderer ownership drifted");
            }
            ItemStack internalBare = new ItemStack(internalItem, 1, 0);
            if (MinecraftForgeClient.getItemRenderer(
                    internalBare, IItemRenderer.ItemRenderType.INVENTORY) != null) {
                throw new IllegalArgumentException("ITEM_ICON_RENDER: " + internalRegistryId
                        + " unexpectedly acquired a Forge inventory item renderer");
            }
            if (catalogOnlyExclusion(internalBare)
                    != TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + internalRegistryId
                        + " bare stack no longer maps to its exact ItemList policy");
            }
            ItemStack taggedInternal = internalBare.copy();
            taggedInternal.setTagCompound(new NBTTagCompound());
            requireStrictCatalogShapeRejected(
                    taggedInternal, internalRegistryId);
            requireStrictCatalogShapeRejected(
                    new ItemStack(internalItem, 2, 0), internalRegistryId);
            requireStrictCatalogShapeRejected(
                    new ItemStack(internalItem, 1, 1), internalRegistryId);
            ItemStack publicBare = new ItemStack(publicItem, 1, 0);
            if (!isExactTwilightForestExperiment115PublicItemListEntry(
                    publicBare, publicItem)
                    || catalogOnlyExclusion(publicBare) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public Experiment 115 food "
                        + publicRegistryId + " was captured by the internal block exclusion");
            }
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned Twilight Forest Experiment 115 renderer registry drift", error);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned Twilight Forest Experiment 115 internal/public registration drift",
                    error);
        }
    }

    private static void requireStrictCatalogShapeRejected(
            ItemStack stack, String registryId) {
        try {
            catalogOnlyExclusion(stack);
            throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                    + " strict policy accepted an unmodeled internal ItemBlock shape");
        } catch (IllegalArgumentException expected) {
            if (expected.getMessage() == null
                    || !expected.getMessage().contains("unmodeled stack shape")) {
                throw expected;
            }
        }
    }

    @SuppressWarnings("deprecation")
    static ISimpleBlockRenderingHandler pinnedBlockRenderer(
            int renderType, String expectedClass) throws ReflectiveOperationException {
        Field field = RenderingRegistry.class.getDeclaredField("blockRenderers");
        field.setAccessible(true);
        Object registry = field.get(RenderingRegistry.instance());
        if (!(registry instanceof Map)) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: Forge block renderer registry "
                    + "is not a Map");
        }
        Object renderer = ((Map<?, ?>) registry).get(Integer.valueOf(renderType));
        String observedClass = renderer == null ? "<null>" : renderer.getClass().getName();
        if (!(renderer instanceof ISimpleBlockRenderingHandler)
                || !expectedClass.equals(observedClass)) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: block render type " + renderType
                    + " expected handler " + expectedClass + ", got " + observedClass);
        }
        return (ISimpleBlockRenderingHandler) renderer;
    }

    private static void requirePinnedRegistration(Item item, String registryId,
                                                  String expectedClass) throws ExportFailure {
        if (item == null) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned runtime did not register required item " + registryId);
        }
        try {
            requireRuntimeClass(item, expectedClass, registryId);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY", "pinned item registration drift: "
                    + registryId, error);
        }
    }

    private static void requirePinnedModernMarkingsCrossingRegistrations()
            throws ExportFailure {
        Set<Item> distinctItems = new java.util.HashSet<Item>();
        Set<Block> distinctBlocks = new java.util.HashSet<Block>();
        try {
            for (String registryId : MODERN_MARKINGS_CROSSING_REGISTRY_IDS) {
                int separator = registryId.indexOf(':');
                String modId = registryId.substring(0, separator);
                String name = registryId.substring(separator + 1);
                Item item = GameRegistry.findItem(modId, name);
                Block block = GameRegistry.findBlock(modId, name);
                if (item == null || block == null
                        || !distinctItems.add(item) || !distinctBlocks.add(block)) {
                    throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                            + " is absent or aliases another pinned crossing registration");
                }
                if (!isPinnedModernMarkingsCrossingIconTarget(
                        new ItemStack(item, 1, 0))) {
                    throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                            + " did not satisfy its exact crossing target predicate");
                }
                int renderType = block.getRenderType();
                ISimpleBlockRenderingHandler renderer = pinnedBlockRenderer(
                        renderType, ModernMarkingsCrossingIconRenderer.FLOOR_RENDERER_CLASS);
                if (renderer.getRenderId() != renderType
                        || !renderer.shouldRender3DInInventory(renderType)
                        || !RenderingRegistry.instance().renderItemAsFull3DBlock(renderType)) {
                    throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                            + " owner 3-D inventory renderer topology drifted");
                }
            }
            if (distinctItems.size()
                    != ModernMarkingsCrossingIconRenderer.EXPECTED_ITEM_ICONS
                    || distinctBlocks.size()
                    != ModernMarkingsCrossingIconRenderer.EXPECTED_ITEM_ICONS) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: ModernMarkings crossing registration cardinality drifted");
            }
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("ITEM_ICON_RENDER",
                    "pinned ModernMarkings crossing renderer registry drift", error);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_ICON_RENDER",
                    "pinned ModernMarkings crossing registration drift", error);
        }
    }

    @SuppressWarnings("deprecation")
    private static void requirePinnedThaumcraftRunedStoneRegistration()
            throws ExportFailure {
        Item item = PinnedCatalogItems.THAUMCRAFT_ELDRITCH_BLOCK_ITEM;
        Block block = PinnedCatalogItems.THAUMCRAFT_ELDRITCH_BLOCK;
        requirePinnedBlockItemRegistration(
                item, block, ThaumcraftRunedStoneIconRenderer.REGISTRY_ID,
                THAUMCRAFT_ELDRITCH_ITEM_CLASS, THAUMCRAFT_ELDRITCH_BLOCK_CLASS);
        try {
            requireExactRegistration(item, ThaumcraftRunedStoneIconRenderer.REGISTRY_ID);
            requireExactRegistration(block, ThaumcraftRunedStoneIconRenderer.REGISTRY_ID);
            ItemStack target = new ItemStack(
                    item, 1, ThaumcraftRunedStoneIconRenderer.METADATA);
            if (!isPinnedThaumcraftRunedStoneIconTarget(target)) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Thaumcraft Runed Stone did not satisfy its exact "
                                + "target predicate");
            }
            if (!"tile.blockEldritch.10".equals(item.getUnlocalizedName(target))
                    || item.getHasSubtypes() != true || item.getMaxDamage() != 0
                    || block.getItemIconName() != null
                    || target.getItemSpriteNumber() != 0) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Thaumcraft Runed Stone item metadata/atlas semantics "
                                + "drifted");
            }
            java.util.ArrayList<ItemStack> creative = new java.util.ArrayList<ItemStack>();
            item.getSubItems(item, item.getCreativeTab(), creative);
            if (creative.size() != 1 || creative.get(0) == null
                    || creative.get(0).getItem() != item
                    || creative.get(0).stackSize != 1
                    || creative.get(0).getItemDamage() != 4
                    || creative.get(0).hasTagCompound()) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Thaumcraft Eldritch owner creative metadata set "
                                + "drifted from the exact metadata-4 singleton");
            }
            if (block.isOpaqueCube() || block.renderAsNormalBlock()
                    || block.getItemDropped(
                            ThaumcraftRunedStoneIconRenderer.METADATA,
                            new Random(0L), 0) != null
                    || block.damageDropped(ThaumcraftRunedStoneIconRenderer.METADATA)
                            != ThaumcraftRunedStoneIconRenderer.METADATA
                    || !block.hasTileEntity(ThaumcraftRunedStoneIconRenderer.METADATA)) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Thaumcraft Runed Stone world-block semantics drifted");
            }
            Object tile = block.createTileEntity(
                    null, ThaumcraftRunedStoneIconRenderer.METADATA);
            if (!(tile instanceof TileEntity)
                    || !THAUMCRAFT_ELDRITCH_TRAP_TILE_CLASS.equals(
                            tile.getClass().getName())) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Thaumcraft Runed Stone tile semantics drifted; got "
                                + (tile == null ? "<null>" : tile.getClass().getName()));
            }
            IIcon icon = block.getIcon(0, ThaumcraftRunedStoneIconRenderer.METADATA);
            if (icon == null
                    || !ThaumcraftRunedStoneIconRenderer.ICON_NAME.equals(icon.getIconName())) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Thaumcraft Runed Stone owner metadata icon drifted; got "
                                + (icon == null ? "<null>" : icon.getIconName()));
            }
            for (int side = 1; side < 6; side++) {
                if (block.getIcon(side, ThaumcraftRunedStoneIconRenderer.METADATA) != icon) {
                    throw new IllegalArgumentException(
                            "ITEM_ICON_RENDER: Thaumcraft Runed Stone owner metadata icon varies "
                                    + "by inventory side");
                }
            }
            int renderType = block.getRenderType();
            ISimpleBlockRenderingHandler renderer = pinnedBlockRenderer(
                    renderType, ThaumcraftRunedStoneIconRenderer.BLOCK_RENDERER_CLASS);
            if (renderer.getRenderId() != renderType
                    || !renderer.shouldRender3DInInventory(renderType)
                    || !RenderingRegistry.instance().renderItemAsFull3DBlock(renderType)) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Thaumcraft Runed Stone owner 3-D inventory renderer "
                                + "topology drifted");
            }
            if (MinecraftForgeClient.getItemRenderer(
                    target, IItemRenderer.ItemRenderType.INVENTORY) != null) {
                throw new IllegalArgumentException(
                        "ITEM_ICON_RENDER: Thaumcraft Runed Stone unexpectedly acquired a Forge "
                                + "inventory item renderer");
            }
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure(
                    "ITEM_ICON_RENDER",
                    "pinned Thaumcraft Runed Stone renderer registry drift", error);
        } catch (IllegalArgumentException error) {
            throw new ExportFailure(
                    "ITEM_ICON_RENDER",
                    "pinned Thaumcraft Runed Stone registration drift", error);
        }
    }

    private static void requireRuntimeClass(Item item, String expectedClass, String registryId) {
        String observed = item == null ? "<null>" : item.getClass().getName();
        if (!expectedClass.equals(observed)) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                    + " runtime class mismatch; expected " + expectedClass + ", got " + observed);
        }
    }

    private static void requirePinnedBlockItemRegistration(
            Item item, Block block, String registryId,
            String expectedItemClass, String expectedBlockClass)
            throws ExportFailure {
        try {
            requireRuntimeClass(item, expectedItemClass, registryId);
            String observedBlockClass = block == null ? "<null>" : block.getClass().getName();
            if (!expectedBlockClass.equals(observedBlockClass)) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " block runtime class mismatch; expected " + expectedBlockClass
                        + ", got " + observedBlockClass);
            }
            if (Item.getItemFromBlock(block) != item || Block.getBlockFromItem(item) != block) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + registryId
                        + " item/block registration is not bijective");
            }
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned block-item registration drift: " + registryId, error);
        }
    }

    @SuppressWarnings("deprecation")
    private static void requirePinnedCarpentersMultipartRegistration(
            Item internalItem, Block block, String internalRegistryId,
            String expectedBlockClass, Item publicItem, String publicRegistryId,
            String expectedPublicItemClass, CatalogExclusion exclusion)
            throws ExportFailure {
        requirePinnedBlockItemRegistration(
                internalItem, block, internalRegistryId,
                VANILLA_ITEM_BLOCK_CLASS, expectedBlockClass);
        requirePinnedRegistration(publicItem, publicRegistryId, expectedPublicItemClass);
        try {
            requireExactRegistration(internalItem, internalRegistryId);
            requireExactRegistration(block, internalRegistryId);
            requireExactRegistration(publicItem, publicRegistryId);
            if (internalItem == publicItem) {
                throw new IllegalArgumentException("ITEM_IDENTITY: " + internalRegistryId
                        + " internal ItemBlock aliases public item " + publicRegistryId);
            }
            if (Block.getBlockFromItem(publicItem) == block) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public item "
                        + publicRegistryId + " aliases internal block " + internalRegistryId);
            }
            if (block.getCreativeTabToDisplayOn() != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal multipart block "
                        + internalRegistryId + " unexpectedly has a creative tab");
            }
            if (publicItem.getCreativeTab() == null || publicItem.getIconFromDamage(0) == null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public item "
                        + publicRegistryId + " lacks its pinned creative-tab/icon presentation");
            }
            if (block.getItemDropped(0, new Random(0L), 0) != publicItem
                    || block.getItem(null, 0, 0, 0) != publicItem) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal multipart block "
                        + internalRegistryId + " does not drop/pick public item "
                        + publicRegistryId);
            }
            if (RenderingRegistry.instance().renderItemAsFull3DBlock(block.getRenderType())) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal multipart block "
                        + internalRegistryId
                        + " unexpectedly has a 3D inventory renderer");
            }
            ItemStack internalBare = new ItemStack(internalItem, 1, 0);
            if (!isBareCatalogEntryShape(internalBare)
                    || catalogOnlyExclusion(internalBare) != exclusion) {
                throw new IllegalArgumentException("ITEM_IDENTITY: internal multipart block "
                        + internalRegistryId + " failed its exact bare ItemList preflight");
            }
            ItemStack publicBare = new ItemStack(publicItem, 1, 0);
            if (!isBareCatalogEntryShape(publicBare)
                    || catalogOnlyExclusion(publicBare) != null) {
                throw new IllegalArgumentException("ITEM_IDENTITY: public item "
                        + publicRegistryId + " was captured by a catalog exclusion policy");
            }
        } catch (IllegalArgumentException error) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "pinned Carpenter's Blocks multipart registration drift: "
                            + internalRegistryId + " / " + publicRegistryId,
                    error);
        }
    }

    private static void requireExactRegistration(Item item, String expectedRegistryId) {
        GameRegistry.UniqueIdentifier identifier = GameRegistry.findUniqueIdentifierFor(item);
        String observed = identifier == null ? "<unregistered>"
                : identifier.modId + ":" + identifier.name;
        if (!expectedRegistryId.equals(observed)) {
            throw new IllegalArgumentException("ITEM_IDENTITY: item registration mismatch; expected "
                    + expectedRegistryId + ", got " + observed);
        }
    }

    private static void requireExactRegistration(Block block, String expectedRegistryId) {
        GameRegistry.UniqueIdentifier identifier = GameRegistry.findUniqueIdentifierFor(block);
        String observed = identifier == null ? "<unregistered>"
                : identifier.modId + ":" + identifier.name;
        if (!expectedRegistryId.equals(observed)) {
            throw new IllegalArgumentException("ITEM_IDENTITY: block registration mismatch; expected "
                    + expectedRegistryId + ", got " + observed);
        }
    }

    static CatalogExclusion catalogOnlyExclusion(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        Item item = stack.getItem();
        CatalogExclusion ae2CableBus = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.AE2_CABLE_BUS_INTERNAL_ITEM,
                AE2_CABLE_BUS_INTERNAL_WORLD_ITEM_BLOCK);
        if (ae2CableBus != null) {
            return ae2CableBus;
        }
        CatalogExclusion ae2MatrixFrame = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.AE2_MATRIX_FRAME_INTERNAL_ITEM,
                AE2_MATRIX_FRAME_INTERNAL_WORLD_ITEM_BLOCK);
        if (ae2MatrixFrame != null) {
            return ae2MatrixFrame;
        }
        if (item == PinnedCatalogItems.FLUID_DROP) {
            return matchCatalogPolicy(
                    stack, AE2FC_FLUID_DROP_PLACEHOLDER, isBareCatalogEntryShape(stack));
        }
        if (item == PinnedCatalogItems.FLUID_PACKET) {
            return matchCatalogPolicy(
                    stack, AE2FC_FLUID_PACKET_PLACEHOLDER, isBareCatalogEntryShape(stack));
        }
        if (item == PinnedCatalogItems.BLOOD_LIGHT) {
            return matchCatalogPolicy(
                    stack, BLOOD_MAGIC_BLOOD_LIGHT_HELPER, isBareCatalogEntryShape(stack));
        }
        if (item == PinnedCatalogItems.SPECTRAL_CONTAINER) {
            return matchCatalogPolicy(
                    stack, BLOOD_MAGIC_SPECTRAL_CONTAINER_HELPER,
                    isBareCatalogEntryShape(stack));
        }
        if (item == PinnedCatalogItems.ARCHITECTURE_CRAFT_CLADDING) {
            return matchCatalogPolicy(
                    stack, ARCHITECTURE_CRAFT_CLADDING_PLACEHOLDER,
                    isBareCatalogEntryShape(stack));
        }
        if (item == PinnedCatalogItems.AVARITIA_MATTER_CLUSTER) {
            return matchCatalogPolicy(
                    stack, AVARITIA_EMPTY_MATTER_CLUSTER_PLACEHOLDER,
                    isBareCatalogEntryShape(stack));
        }
        CatalogExclusion dreamcraftNothing = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.DREAMCRAFT_NOTHING,
                DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL);
        if (dreamcraftNothing != null) {
            return dreamcraftNothing;
        }
        CatalogExclusion littleTilesCarrier = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.LITTLE_TILES_CARRIER,
                LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER);
        if (littleTilesCarrier != null) {
            return littleTilesCarrier;
        }
        CatalogExclusion malisisDoorsCustomDoor = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.MALISIS_DOORS_CUSTOM_DOOR,
                MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER);
        if (malisisDoorsCustomDoor != null) {
            return malisisDoorsCustomDoor;
        }
        CatalogExclusion malisisDoorsMixedBlock = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.MALISIS_DOORS_MIXED_BLOCK,
                MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER);
        if (malisisDoorsMixedBlock != null) {
            return malisisDoorsMixedBlock;
        }
        if (item == PinnedCatalogItems.STEVES_CARTS_MODULAR_CART) {
            return matchCatalogPolicy(
                    stack, STEVES_CARTS_UNCONFIGURED_MODULAR_CART_PLACEHOLDER,
                    isBareCatalogEntryShape(stack));
        }
        CatalogExclusion tconstructBattlesign = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.TCONSTRUCT_BATTLESIGN_INTERNAL_ITEM,
                TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK);
        if (tconstructBattlesign != null) {
            return tconstructBattlesign;
        }
        CatalogExclusion tconstructHeldItem = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.TCONSTRUCT_HELD_ITEM_INTERNAL_ITEM,
                TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK);
        if (tconstructHeldItem != null) {
            return tconstructHeldItem;
        }
        CatalogExclusion thaumcraftBlockHole = matchThaumcraftBlockHoleCatalogPolicy(
                stack, PinnedCatalogItems.THAUMCRAFT_BLOCK_HOLE_INTERNAL_ITEM);
        if (thaumcraftBlockHole != null) {
            return thaumcraftBlockHole;
        }
        CatalogExclusion thaumcraftEldritchPortal =
                matchThaumcraftEldritchPortalCatalogPolicy(
                        stack, PinnedCatalogItems.THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_ITEM);
        if (thaumcraftEldritchPortal != null) {
            return thaumcraftEldritchPortal;
        }
        CatalogExclusion thaumicHorizonsBaseLight = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_ITEM,
                THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK);
        if (thaumicHorizonsBaseLight != null) {
            return thaumicHorizonsBaseLight;
        }
        CatalogExclusion thaumicHorizonsSolarLight = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_ITEM,
                THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK);
        if (thaumicHorizonsSolarLight != null) {
            return thaumicHorizonsSolarLight;
        }
        CatalogExclusion twilightForestExperiment115 = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_ITEM,
                TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK);
        if (twilightForestExperiment115 != null) {
            return twilightForestExperiment115;
        }
        CatalogExclusion witchingGadgetsCustomAir = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_ITEM,
                WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK);
        if (witchingGadgetsCustomAir != null) {
            return witchingGadgetsCustomAir;
        }
        if (item == PinnedCatalogItems.BOTANIA_BIFROST) {
            return matchCatalogPolicy(
                    stack, BOTANIA_BIFROST_WORLD_STATE, isBareCatalogEntryShape(stack));
        }
        if (item == PinnedCatalogItems.BOTANIA_BURIED_PETALS) {
            return matchCatalogPolicy(
                    stack, BOTANIA_BURIED_PETALS_WORLD_STATE_VARIANT,
                    isBareCatalogEntryShapeWithMetadataRange(stack, 0, 15));
        }
        if (item == PinnedCatalogItems.BOTANIA_CACOPHONIUM_BLOCK_ITEM) {
            return matchCatalogPolicy(
                    stack, BOTANIA_CACOPHONIUM_BLOCK_WORLD_STATE,
                    isBareCatalogEntryShape(stack));
        }
        if (item == PinnedCatalogItems.BOTANIA_ENCHANTER) {
            return matchCatalogPolicy(
                    stack, BOTANIA_ENCHANTER_WORLD_STATE, isBareCatalogEntryShape(stack));
        }
        if (item == PinnedCatalogItems.BOTANIA_FAKE_AIR) {
            return matchCatalogPolicy(
                    stack, BOTANIA_FAKE_AIR_WORLD_STATE, isBareCatalogEntryShape(stack));
        }
        if (item == PinnedCatalogItems.BOTANIA_MANA_FLAME) {
            return matchCatalogPolicy(
                    stack, BOTANIA_MANA_FLAME_WORLD_STATE, isBareCatalogEntryShape(stack));
        }
        if (item == PinnedCatalogItems.BOTANIA_SOLID_VINE) {
            return matchCatalogPolicy(
                    stack, BOTANIA_SOLID_VINE_WORLD_STATE, isBareCatalogEntryShape(stack));
        }
        if (item == PinnedCatalogItems.BOTANIA_STRUCTURE_LIB_ANY_FLOWER) {
            return matchCatalogPolicy(
                    stack, BOTANIA_STRUCTURE_LIB_ANY_FLOWER_PLACEHOLDER,
                    isBareCatalogEntryShape(stack));
        }
        CatalogExclusion carpentersBed = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.CARPENTERS_BED_INTERNAL_ITEM,
                CARPENTERS_BED_INTERNAL_WORLD_ITEM_BLOCK);
        if (carpentersBed != null) {
            return carpentersBed;
        }
        CatalogExclusion carpentersDoor = matchExactCatalogPolicy(
                stack, PinnedCatalogItems.CARPENTERS_DOOR_INTERNAL_ITEM,
                CARPENTERS_DOOR_INTERNAL_WORLD_ITEM_BLOCK);
        if (carpentersDoor != null) {
            return carpentersDoor;
        }
        return null;
    }

    static CatalogExclusion matchExactCatalogPolicy(
            ItemStack stack, Item expectedItem, CatalogExclusion policy) {
        if (stack == null || stack.getItem() == null || stack.getItem() != expectedItem) {
            return null;
        }
        return matchCatalogPolicy(stack, policy, isBareCatalogEntryShape(stack));
    }

    static boolean isExactMalisisDoorsUnconfiguredCustomDoorCarrier(ItemStack stack) {
        return isExactMalisisDoorsUnconfiguredCustomDoorCarrier(
                stack, PinnedCatalogItems.MALISIS_DOORS_CUSTOM_DOOR);
    }

    static boolean isExactMalisisDoorsUnconfiguredCustomDoorCarrier(
            ItemStack stack, Item expectedItem) {
        if (stack == null || stack.getItem() == null || stack.getItem() != expectedItem) {
            return false;
        }
        if (expectedItem == PinnedCatalogItems.MALISIS_DOORS_CUSTOM_DOOR) {
            requireRuntimeClass(
                    expectedItem, MALISIS_DOORS_CUSTOM_DOOR_ITEM_CLASS,
                    MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER.registryId);
        }
        return isBareCatalogEntryShape(stack);
    }

    static void requireNoMalisisDoorsUnconfiguredCustomDoorGraphReferences(
            int recipeReferences, int questReferences) {
        if (recipeReferences != 0 || questReferences != 0) {
            throw new IllegalArgumentException("ITEM_IDENTITY: exact bare "
                    + "malisisdoors:item.custom_door must have zero post-discovery graph "
                    + "references; recipes=" + recipeReferences + ", quests="
                    + questReferences);
        }
    }

    static boolean isExactMalisisDoorsUnconfiguredMixedBlockCarrier(ItemStack stack) {
        return isExactMalisisDoorsUnconfiguredMixedBlockCarrier(
                stack, PinnedCatalogItems.MALISIS_DOORS_MIXED_BLOCK);
    }

    static boolean isExactMalisisDoorsUnconfiguredMixedBlockCarrier(
            ItemStack stack, Item expectedItem) {
        if (stack == null || stack.getItem() == null || stack.getItem() != expectedItem) {
            return false;
        }
        if (expectedItem == PinnedCatalogItems.MALISIS_DOORS_MIXED_BLOCK) {
            requireRuntimeClass(
                    expectedItem, MALISIS_DOORS_MIXED_BLOCK_ITEM_CLASS,
                    MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER.registryId);
        }
        return isBareCatalogEntryShape(stack);
    }

    static void requireNoMalisisDoorsUnconfiguredMixedBlockGraphReferences(
            int recipeReferences, int questReferences) {
        if (recipeReferences != 0 || questReferences != 0) {
            throw new IllegalArgumentException("ITEM_IDENTITY: exact bare "
                    + "malisisdoors:mixed_block must have zero post-discovery graph "
                    + "references; recipes=" + recipeReferences + ", quests="
                    + questReferences);
        }
    }

    static CatalogExclusion matchThaumcraftBlockHoleCatalogPolicy(
            ItemStack stack, Item expectedItem) {
        if (stack == null || stack.getItem() == null || stack.getItem() != expectedItem) {
            return null;
        }
        requireRuntimeClass(
                stack.getItem(), VANILLA_ITEM_BLOCK_CLASS, "Thaumcraft:blockHole");
        boolean exactEnvelope = stack.stackSize == 1 && stack.getTagCompound() == null;
        if (exactEnvelope && stack.getItemDamage() == 0) {
            return THAUMCRAFT_BLOCK_HOLE_INTERNAL_WORLD_ITEM_BLOCK;
        }
        if (exactEnvelope && stack.getItemDamage() == 15) {
            return null;
        }
        throw new IllegalArgumentException(
                "ITEM_IDENTITY: pinned Thaumcraft:blockHole has an unmodeled stack shape; "
                        + "expected amount=1, metadata=0 catalog exclusion or metadata=15 "
                        + "compound-diagram sentinel, and no NBT; " + describe(stack));
    }

    static CatalogExclusion matchThaumcraftEldritchPortalCatalogPolicy(
            ItemStack stack, Item expectedItem) {
        return matchExactCatalogPolicy(
                stack, expectedItem, THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_WORLD_ITEM_BLOCK);
    }

    static boolean isExactThaumicHorizonsBaseLightItemListEntry(ItemStack stack) {
        return isExactThaumicHorizonsBaseLightItemListEntry(
                stack, PinnedCatalogItems.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_ITEM);
    }

    static boolean isExactThaumicHorizonsBaseLightItemListEntry(
            ItemStack stack, Item expectedItem) {
        if (stack == null || stack.getItem() == null || stack.getItem() != expectedItem) {
            return false;
        }
        if (isExactBareRetainedItemListEntry(stack, expectedItem)) {
            return true;
        }
        throw new IllegalArgumentException(
                "ITEM_IDENTITY: owner-internal ThaumicHorizons:light has an unmodeled "
                        + "ItemList stack shape; expected amount=1, metadata=0, and no NBT; "
                        + describe(stack));
    }

    static boolean isExactThaumicHorizonsIlluminationFocusRetainedItemListEntry(
            ItemStack stack) {
        if (!isExactThaumicHorizonsIlluminationFocusRetainedItemListEntry(
                stack, PinnedCatalogItems.THAUMIC_HORIZONS_ILLUMINATION_FOCUS)) {
            return false;
        }
        requireRuntimeClass(
                stack.getItem(), THAUMIC_HORIZONS_ILLUMINATION_FOCUS_ITEM_CLASS,
                "ThaumicHorizons:focusIllumination");
        return true;
    }

    static boolean isExactThaumicHorizonsIlluminationFocusRetainedItemListEntry(
            ItemStack stack, Item expectedItem) {
        if (stack == null || stack.getItem() == null || stack.getItem() != expectedItem) {
            return false;
        }
        if (stack.stackSize == 1 && stack.getItemDamage() >= 0
                && stack.getItemDamage() <= 15 && stack.getTagCompound() == null) {
            return true;
        }
        throw new IllegalArgumentException(
                "ITEM_IDENTITY: retained ThaumicHorizons:focusIllumination has an unmodeled "
                        + "ItemList stack shape; expected amount=1, metadata=0..15, and no NBT; "
                        + describe(stack));
    }

    static void requireExactThaumicHorizonsIlluminationItemListCounts(
            int baseLightEntries, int illuminationFocusVariants, int illuminationFocusMetadataMask) {
        if (baseLightEntries != 1 || illuminationFocusVariants != 16
                || illuminationFocusMetadataMask != 0xffff) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: Thaumic Horizons illumination ItemList cardinality drift; "
                            + "expected excluded base light=1 and public focus variants=16 "
                            + "with metadataMask=0xffff, got baseLight=" + baseLightEntries
                            + ", focusVariants=" + illuminationFocusVariants + ", metadataMask=0x"
                            + Integer.toHexString(illuminationFocusMetadataMask));
        }
    }

    static boolean isExactTwilightForestExperiment115PublicItemListEntry(
            ItemStack stack) {
        if (!isExactTwilightForestExperiment115PublicItemListEntry(
                stack, PinnedCatalogItems.TWILIGHT_FOREST_EXPERIMENT_115_PUBLIC_ITEM)) {
            return false;
        }
        requireRuntimeClass(
                stack.getItem(), TWILIGHT_FOREST_EXPERIMENT_115_PUBLIC_ITEM_CLASS,
                "TwilightForest:item.experiment115");
        return true;
    }

    static boolean isExactTwilightForestExperiment115PublicItemListEntry(
            ItemStack stack, Item expectedItem) {
        if (stack == null || stack.getItem() == null || stack.getItem() != expectedItem) {
            return false;
        }
        if (isExactBareRetainedItemListEntry(stack, expectedItem)) {
            return true;
        }
        throw new IllegalArgumentException(
                "ITEM_IDENTITY: retained TwilightForest:item.experiment115 has an unmodeled "
                        + "ItemList stack shape; expected amount=1, metadata=0, and no NBT; "
                        + describe(stack));
    }

    static void requireExactTwilightForestExperiment115ItemListCounts(
            int internalBlockEntries, int publicFoodEntries) {
        if (internalBlockEntries != 1 || publicFoodEntries != 1) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: Twilight Forest Experiment 115 ItemList cardinality drift; "
                            + "expected excluded internal block=1 and retained public food=1, got "
                            + "internalBlock=" + internalBlockEntries
                            + ", publicFood=" + publicFoodEntries);
        }
    }

    static boolean isExactThaumcraftEldritchObjectRetainedItemListEntry(ItemStack stack) {
        if (!isExactThaumcraftEldritchObjectRetainedItemListEntry(
                stack, PinnedCatalogItems.THAUMCRAFT_ELDRITCH_OBJECT_PUBLIC_ITEM)) {
            return false;
        }
        requireRuntimeClass(
                stack.getItem(), THAUMCRAFT_ELDRITCH_OBJECT_ITEM_CLASS,
                "Thaumcraft:ItemEldritchObject");
        return true;
    }

    static boolean isExactThaumcraftEldritchObjectRetainedItemListEntry(
            ItemStack stack, Item expectedItem) {
        if (stack == null || stack.getItem() == null || stack.getItem() != expectedItem) {
            return false;
        }
        boolean exactEnvelope = stack.stackSize == 1 && stack.getTagCompound() == null;
        if (exactEnvelope && stack.getItemDamage() == 0) {
            return true;
        }
        if (exactEnvelope && stack.getItemDamage() >= 1 && stack.getItemDamage() <= 4) {
            return false;
        }
        throw new IllegalArgumentException(
                "ITEM_IDENTITY: retained Thaumcraft:ItemEldritchObject has an unmodeled "
                        + "ItemList stack shape; expected amount=1, metadata=0..4, and no NBT; "
                        + describe(stack));
    }

    static boolean isExactGadomancyEldritchPortalRetainedItemListEntry(ItemStack stack) {
        if (!isExactGadomancyEldritchPortalRetainedItemListEntry(
                stack, PinnedCatalogItems.GADOMANCY_ELDRITCH_PORTAL_ITEM)) {
            return false;
        }
        requireRuntimeClass(
                stack.getItem(), GADOMANCY_ELDRITCH_PORTAL_ITEM_CLASS,
                "gadomancy:BlockAdditionalEldritchPortal");
        return true;
    }

    static boolean isExactGadomancyEldritchPortalRetainedItemListEntry(
            ItemStack stack, Item expectedItem) {
        if (stack == null || stack.getItem() == null || stack.getItem() != expectedItem) {
            return false;
        }
        if (isExactBareRetainedItemListEntry(stack, expectedItem)) {
            return true;
        }
        throw new IllegalArgumentException(
                "ITEM_IDENTITY: retained gadomancy:BlockAdditionalEldritchPortal has an "
                        + "unmodeled ItemList stack shape; expected amount=1, metadata=0, and "
                        + "no NBT; " + describe(stack));
    }

    static void requireExactRetainedEldritchItemListCounts(
            int thaumcraftEldritchObjectMetadataZero,
            int gadomancyEldritchPortalPlacerMetadataZero) {
        if (thaumcraftEldritchObjectMetadataZero != 1
                || gadomancyEldritchPortalPlacerMetadataZero != 1) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: retained Eldritch ItemList cardinality drift; expected "
                            + "Thaumcraft:ItemEldritchObject metadata 0=1 and "
                            + "gadomancy:BlockAdditionalEldritchPortal metadata 0=1, got "
                            + "Thaumcraft=" + thaumcraftEldritchObjectMetadataZero
                            + " and Gadomancy=" + gadomancyEldritchPortalPlacerMetadataZero);
        }
    }

    private static boolean isExactBareRetainedItemListEntry(
            ItemStack stack, Item expectedItem) {
        return stack != null && stack.getItem() != null && stack.getItem() == expectedItem
                && stack.stackSize == 1 && stack.getItemDamage() == 0
                && stack.getTagCompound() == null;
    }

    static boolean isPinnedBotaniaCocoonIconTarget(ItemStack stack) {
        if (stack == null || stack.getItem() == null
                || stack.getItem() != PinnedCatalogItems.BOTANIA_COCOON) {
            return false;
        }
        requireRuntimeClass(
                stack.getItem(), BOTANIA_ITEM_BLOCK_MOD_CLASS, "Botania:cocoon");
        if (!isBareCatalogEntryShape(stack)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: pinned Botania cocoon has an unmodeled icon stack shape; "
                            + describe(stack));
        }
        return true;
    }

    static boolean isPinnedBotaniaPrismIconTarget(ItemStack stack) {
        if (stack == null || stack.getItem() == null
                || stack.getItem() != PinnedCatalogItems.BOTANIA_PRISM) {
            return false;
        }
        requireRuntimeClass(
                stack.getItem(), BOTANIA_ITEM_BLOCK_MOD_CLASS, "Botania:prism");
        if (!isBareCatalogEntryShape(stack)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: pinned Botania prism has an unmodeled icon stack shape; "
                            + describe(stack));
        }
        return true;
    }

    static boolean isPinnedGalacticraftFlagIconTarget(ItemStack stack) {
        if (stack == null || stack.getItem() == null
                || stack.getItem() != PinnedCatalogItems.GALACTICRAFT_FLAG) {
            return false;
        }
        requireRuntimeClass(
                stack.getItem(), GALACTICRAFT_FLAG_ITEM_CLASS,
                "GalacticraftCore:item.flag");
        if (!isBareCatalogEntryShape(stack)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: pinned Galacticraft flag catalog identity has an "
                            + "unmodeled stack shape; " + describe(stack));
        }
        return true;
    }

    static boolean isPinnedWrcbeTriangulatorIconTarget(ItemStack stack) {
        if (stack == null || stack.getItem() == null
                || stack.getItem() != PinnedCatalogItems.WRCBE_TRIANGULATOR) {
            return false;
        }
        requireRuntimeClass(
                stack.getItem(), WRCBE_TRIANGULATOR_ITEM_CLASS,
                "WR-CBE|Addons:triangulator");
        if (!isBareCatalogEntryShape(stack)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: pinned WR-CBE triangulator catalog identity has an "
                            + "unmodeled stack shape; " + describe(stack));
        }
        return true;
    }

    static boolean isPinnedModernMarkingsCrossingIconTarget(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        GameRegistry.UniqueIdentifier identifier =
                GameRegistry.findUniqueIdentifierFor(stack.getItem());
        if (identifier == null) {
            return false;
        }
        String registryId = identifier.modId + ":" + identifier.name;
        if (!isPinnedModernMarkingsCrossingRegistryId(registryId)) {
            return false;
        }
        if (stack.stackSize != 1 || stack.getItemDamage() != 0
                || stack.hasTagCompound()) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                    + " exact crossing adapter requires count=1, metadata=0, and no NBT; got "
                    + describe(stack));
        }
        Item item = stack.getItem();
        String itemClass = item.getClass().getName();
        if (item.getClass() != ItemBlock.class) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                    + " expected exact vanilla ItemBlock, got " + itemClass);
        }
        Block block = GameRegistry.findBlock(identifier.modId, identifier.name);
        if (block == null || GameRegistry.findItem(identifier.modId, identifier.name) != item
                || Item.getItemFromBlock(block) != item
                || Block.getBlockFromItem(item) != block) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                    + " item/block registry identity drifted");
        }
        String blockClass = block.getClass().getName();
        if (!MODERN_MARKINGS_FLOOR_BLOCK_CLASS.equals(blockClass)) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                    + " expected block " + MODERN_MARKINGS_FLOOR_BLOCK_CLASS
                    + ", got " + blockClass);
        }
        GameRegistry.UniqueIdentifier blockIdentifier =
                GameRegistry.findUniqueIdentifierFor(block);
        String blockRegistryId = blockIdentifier == null ? "<unregistered>"
                : blockIdentifier.modId + ":" + blockIdentifier.name;
        if (!registryId.equals(blockRegistryId)) {
            throw new IllegalArgumentException("ITEM_ICON_RENDER: " + registryId
                    + " block registry identity drifted to " + blockRegistryId);
        }
        return true;
    }

    static boolean isPinnedModernMarkingsCrossingRegistryId(String registryId) {
        if (registryId == null) {
            return false;
        }
        for (String expected : MODERN_MARKINGS_CROSSING_REGISTRY_IDS) {
            if (expected.equals(registryId)) {
                return true;
            }
        }
        return false;
    }

    static boolean isPinnedThaumcraftRunedStoneIconTarget(ItemStack stack) {
        if (stack == null || stack.getItem() == null
                || stack.getItem() != PinnedCatalogItems.THAUMCRAFT_ELDRITCH_BLOCK_ITEM) {
            return false;
        }
        if (stack.getItemDamage() != ThaumcraftRunedStoneIconRenderer.METADATA) {
            return false;
        }
        if (stack.stackSize != 1 || stack.hasTagCompound()) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone exact adapter requires count=1 "
                            + "and no NBT; got " + describe(stack));
        }
        Item item = stack.getItem();
        requireRuntimeClass(
                item, THAUMCRAFT_ELDRITCH_ITEM_CLASS,
                ThaumcraftRunedStoneIconRenderer.REGISTRY_ID);
        Block block = PinnedCatalogItems.THAUMCRAFT_ELDRITCH_BLOCK;
        if (block == null || GameRegistry.findItem("Thaumcraft", "blockEldritch") != item
                || GameRegistry.findBlock("Thaumcraft", "blockEldritch") != block
                || Item.getItemFromBlock(block) != item
                || Block.getBlockFromItem(item) != block
                || !THAUMCRAFT_ELDRITCH_BLOCK_CLASS.equals(block.getClass().getName())) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Thaumcraft Runed Stone item/block registry identity "
                            + "drifted");
        }
        return true;
    }

    static boolean isPinnedWrcbeTriangulatorRenderTarget(ItemStack stack) {
        if (stack == null || stack.getItem() == null
                || stack.getItem() != PinnedCatalogItems.WRCBE_TRIANGULATOR) {
            return false;
        }
        requireRuntimeClass(
                stack.getItem(), WRCBE_TRIANGULATOR_ITEM_CLASS,
                "WR-CBE|Addons:triangulator");
        int metadata = stack.getItemDamage();
        if (stack.stackSize != 1 || stack.getTagCompound() != null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: pinned WR-CBE triangulator render target has an "
                            + "unmodeled stack shape; " + describe(stack));
        }
        // ItemWirelessTriangulator clamps NEI's wildcard 32767 to the same deterministic owner
        // slot used by metadata zero. Frequencies 1..5000 are player/world-dependent and are not
        // valid deterministic exporter render targets.
        if (metadata != 0 && metadata != 32767) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: pinned WR-CBE triangulator render target has "
                            + "player/world-dependent or unmodeled metadata; " + describe(stack));
        }
        return true;
    }

    static boolean isPinnedGalacticraftFlagRenderTarget(ItemStack stack) {
        if (stack == null || stack.getItem() == null
                || stack.getItem() != PinnedCatalogItems.GALACTICRAFT_FLAG) {
            return false;
        }
        requireRuntimeClass(
                stack.getItem(), GALACTICRAFT_FLAG_ITEM_CLASS,
                "GalacticraftCore:item.flag");
        if (stack.stackSize != 1
                || stack.getItemDamage() < 0
                || stack.getItemDamage() > 16
                || stack.getTagCompound() != null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: pinned Galacticraft flag renderer received an "
                            + "unmodeled stack shape; expected amount=1, metadata=0..16, "
                            + "and no NBT; " + describe(stack));
        }
        return true;
    }

    private static CatalogExclusion matchCatalogPolicy(
            ItemStack stack, CatalogExclusion policy, boolean exactShape) {
        requireRuntimeClass(stack.getItem(), policy.runtimeClass, policy.registryId);
        if (exactShape) {
            return policy;
        }
        if (policy.strictIdentity) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: pinned catalog-only identity has an unmodeled stack shape; "
                            + "policy=" + policy.contract + "; " + describe(stack));
        }
        return null;
    }

    private static boolean isKnownCatalogExclusion(CatalogExclusion candidate) {
        for (CatalogExclusion policy : CATALOG_EXCLUSION_POLICIES) {
            if (candidate == policy) {
                return true;
            }
        }
        return false;
    }

    static boolean isBareCatalogEntryShape(ItemStack stack) {
        return stack != null && stack.getItem() != null
                && stack.stackSize == 1
                && stack.getItemDamage() == 0
                && stack.getTagCompound() == null;
    }

    static boolean isBareCatalogEntryShapeWithMetadataRange(
            ItemStack stack, int minimumMetadata, int maximumMetadata) {
        return stack != null && stack.getItem() != null
                && stack.stackSize == 1
                && stack.getItemDamage() >= minimumMetadata
                && stack.getItemDamage() <= maximumMetadata
                && stack.getTagCompound() == null;
    }

    static FluidStack decodePinnedAe2fcFluidDropPayload(ItemStack stack) {
        DecodedFluidDrop payload = parsePinnedAe2fcFluidDropPayload(
                stack, new FluidResolver() {
            @Override
            public Fluid resolve(String normalizedName) {
                return FluidRegistry.getFluid(normalizedName);
            }
        });
        FluidStack decoded = new FluidStack(payload.fluid, payload.amount);
        decoded.tag = payload.tag;
        return decoded;
    }

    @SuppressWarnings("unchecked")
    static DecodedFluidDrop parsePinnedAe2fcFluidDropPayload(
            ItemStack stack, FluidResolver fluidResolver) {
        if (stack == null || stack.getItem() == null) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: AE2FC fluid drop is a null/empty ItemStack");
        }
        if (fluidResolver == null) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: AE2FC fluid-drop decoder requires a fluid resolver");
        }
        if (stack.getItemDamage() != 0) {
            throw invalidAe2fcDrop(stack, "metadata must be 0");
        }
        if (stack.stackSize <= 0) {
            throw invalidAe2fcDrop(stack,
                    "stackSize must encode a positive total millibucket amount");
        }
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            throw invalidAe2fcDrop(stack, "required root NBT is absent");
        }

        Set<String> keys = (Set<String>) root.func_150296_c();
        for (String key : new TreeSet<String>(keys)) {
            if (!"Fluid".equals(key) && !"FluidTag".equals(key)
                    && !"DisplayOnly".equals(key)) {
                throw invalidAe2fcDrop(stack, "unsupported root NBT key " + key);
            }
        }
        if (!root.hasKey("Fluid")) {
            throw invalidAe2fcDrop(stack, "required string Fluid field is absent");
        }
        if (!root.hasKey("Fluid", 8)) {
            throw invalidAe2fcDrop(stack, "Fluid must use NBT string type 8");
        }
        String encodedName = root.getString("Fluid");
        if (encodedName.trim().isEmpty()) {
            throw invalidAe2fcDrop(stack, "Fluid must be non-blank");
        }
        Fluid fluid = fluidResolver.resolve(encodedName.toLowerCase(Locale.ROOT));
        if (fluid == null) {
            throw invalidAe2fcDrop(stack,
                    "Fluid does not resolve in the pinned Forge registry: " + encodedName);
        }
        if (root.hasKey("FluidTag") && !root.hasKey("FluidTag", 10)) {
            throw invalidAe2fcDrop(stack, "FluidTag must use NBT compound type 10");
        }
        if (root.hasKey("DisplayOnly")) {
            if (!root.hasKey("DisplayOnly", 1)) {
                throw invalidAe2fcDrop(stack, "DisplayOnly must use NBT byte type 1");
            }
            if (root.getByte("DisplayOnly") != 1) {
                throw invalidAe2fcDrop(stack,
                        "DisplayOnly must be absent or the exact presentation value 1b");
            }
        }

        NBTTagCompound fluidTag = root.hasKey("FluidTag", 10)
                ? root.getCompoundTag("FluidTag") : null;
        return new DecodedFluidDrop(fluid, stack.stackSize, fluidTag);
    }

    private static IllegalArgumentException invalidAe2fcDrop(ItemStack stack, String reason) {
        return new IllegalArgumentException("ITEM_IDENTITY: invalid pinned AE2FC fluid drop: "
                + reason + "; " + describe(stack));
    }

    static void requireSameFluidIdentity(FluidStack expected, FluidStack observed,
                                         String decoder, ItemStack source) {
        if (expected == null || expected.getFluid() == null
                || observed == null || observed.getFluid() == null
                || expected.getFluid() != observed.getFluid()
                || !sameCanonicalTag(expected.tag, observed.tag)) {
            throw new IllegalArgumentException("ITEM_IDENTITY: " + decoder
                    + " disagrees with the pinned direct decoder; " + describe(source));
        }
    }

    private static boolean sameCanonicalTag(NBTTagCompound left, NBTTagCompound right) {
        if (left == null || right == null) {
            return left == right;
        }
        return NbtCanonicalizer.canonical(left).equals(NbtCanonicalizer.canonical(right));
    }

    static String describe(ItemStack stack) {
        if (stack == null) {
            return "stack=<null>";
        }
        Item item = stack.getItem();
        String registeredName = item == null ? null
                : Item.itemRegistry.getNameForObject(item);
        String registryId = registeredName == null ? "<unregistered>" : registeredName;
        String runtimeClass = item == null ? "<null>" : item.getClass().getName();
        NBTTagCompound tag = stack.getTagCompound();
        String nbt = tag == null ? "absent"
                : "sha256:" + Naming.sha256(NbtCanonicalizer.canonical(tag));
        return "registryId=" + registryId + ", runtimeClass=" + runtimeClass
                + ", stackSize=" + stack.stackSize + ", metadata=" + stack.getItemDamage()
                + ", nbt=" + nbt;
    }

    static void requireKnownProxyDecoded(boolean knownProxy, FluidStack decoded, String description) {
        if (knownProxy && decoded == null) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: known fluid display proxy could not be decoded: " + description);
        }
    }

    private static StackIdentity item(ItemStack stack) {
        GameRegistry.UniqueIdentifier identifier = requireForgeRegistryIdentifier(stack);
        String registryId = identifier.modId + ":" + identifier.name;
        int metadata = stack.getItemDamage();
        NBTTagCompound tag = stack.getTagCompound();
        String canonicalNbt = tag == null ? null : NbtCanonicalizer.canonical(tag);
        String nbtDigest = canonicalNbt == null ? "-" : Naming.sha256(canonicalNbt);
        String key = "item|" + registryId + "|meta=" + metadata + "|nbt=" + nbtDigest;
        return new StackIdentity(
                stack, "item", registryId, metadata, canonicalNbt, key, stack.stackSize, null);
    }

    /**
     * Checks the namespaced item registry before invoking Forge's legacy helper. Forge 1.7.10's
     * {@code UniqueIdentifier} constructor dereferences the registry name and throws an opaque
     * {@link NullPointerException} for unregistered items.
     */
    static GameRegistry.UniqueIdentifier requireForgeRegistryIdentifier(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: item registry lookup received a null/empty stack; "
                            + describe(stack));
        }
        String registeredName = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registeredName == null || registeredName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: item is absent from the namespaced item registry; "
                            + describe(stack));
        }
        GameRegistry.UniqueIdentifier identifier =
                GameRegistry.findUniqueIdentifierFor(stack.getItem());
        if (identifier == null || identifier.modId == null || identifier.name == null
                || identifier.modId.trim().isEmpty() || identifier.name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: item has no complete Forge registry identifier; "
                            + describe(stack));
        }
        return identifier;
    }

    static StackIdentity fluidProxy(ItemStack stack, FluidStack detectedFluid, int amount) {
        if (stack == null || stack.getItem() == null) {
            throw new IllegalArgumentException("NEI supplied a null/empty fluid display stack");
        }
        if (detectedFluid == null || detectedFluid.getFluid() == null) {
            throw new IllegalArgumentException("NEI supplied an invalid fluid display identity");
        }
        return fluidProxy(
                stack,
                FluidRegistry.getFluidName(detectedFluid),
                FluidRegistry.getDefaultFluidName(detectedFluid.getFluid()),
                detectedFluid.tag,
                detectedFluid.getLocalizedName(),
                amount);
    }

    static StackIdentity fluidProxy(ItemStack stack, String fluidName, String registryId,
                                    NBTTagCompound tag, String displayName, int amount) {
        if (stack == null || stack.getItem() == null) {
            throw new IllegalArgumentException("NEI supplied a null/empty fluid display stack");
        }
        if (fluidName == null || fluidName.trim().isEmpty()
                || registryId == null || registryId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "ITEM_IDENTITY: fluid display has no registered Forge fluid identity");
        }
        String canonicalNbt = tag == null ? null : NbtCanonicalizer.canonical(tag);
        String key = "fluid|fluid:" + fluidName;
        if (canonicalNbt != null) {
            key += "|nbt=" + Naming.sha256(canonicalNbt);
        }
        return new StackIdentity(
                stack, "fluid", registryId, 0, canonicalNbt, key, amount, displayName);
    }

    String namespace() {
        int colon = registryId.indexOf(':');
        return colon <= 0 ? "unknown" : registryId.substring(0, colon);
    }

    boolean isFluid() {
        return "fluid".equals(type);
    }

    boolean sameLogicalIdentity(StackIdentity other) {
        if (other == null || metadata != other.metadata || !type.equals(other.type)
                || !registryId.equals(other.registryId)) {
            return false;
        }
        return canonicalNbt == null
                ? other.canonicalNbt == null : canonicalNbt.equals(other.canonicalNbt);
    }

    @Override
    public int compareTo(StackIdentity other) {
        int byKey = key.compareTo(other.key);
        if (byKey != 0) {
            return byKey;
        }
        String left = canonicalNbt == null ? "" : canonicalNbt;
        String right = other.canonicalNbt == null ? "" : other.canonicalNbt;
        return left.compareTo(right);
    }
}
