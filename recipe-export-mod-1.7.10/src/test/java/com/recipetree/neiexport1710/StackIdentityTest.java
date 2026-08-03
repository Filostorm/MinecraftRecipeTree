package com.recipetree.neiexport1710;

import com.google.common.base.Optional;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import org.junit.Test;
import twilightforest.item.ItemBlockTFMeta;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StackIdentityTest {
    private static class FixtureTileBase extends TileEntity {}

    private static class FixtureMiddleTile extends FixtureTileBase {}

    private static final class FixtureLayeredTile extends FixtureMiddleTile {}

    private static class ObfuscatedPermutationBase {
        public void func_150895_a(
                Item item, net.minecraft.creativetab.CreativeTabs tab,
                java.util.List values) {}
    }

    private static final class ObfuscatedPermutationChild
            extends ObfuscatedPermutationBase {}

    private static final class OverridingPermutationChild
            extends ObfuscatedPermutationBase {
        @Override
        public void func_150895_a(
                Item item, net.minecraft.creativetab.CreativeTabs tab,
                java.util.List values) {}
    }

    private static final class ObfuscatedTickOwner {
        public void func_145845_h() {}
    }

    private static final class AmbiguousObfuscatedTickOwner {
        public void func_145845_h() {}
        public void anotherPublicVoidMethod() {}
    }

    private static final net.minecraftforge.fluids.Fluid TEST_WATER =
            new net.minecraftforge.fluids.Fluid("recipe_tree_test_water");
    private static final StackIdentity.FluidResolver TEST_FLUID_RESOLVER =
            new StackIdentity.FluidResolver() {
                @Override
                public net.minecraftforge.fluids.Fluid resolve(String normalizedName) {
                    return "water".equals(normalizedName) ? TEST_WATER : null;
                }
            };

    @Test
    public void guavaPresentValueIsReadThroughItsPublicOptionalContract() {
        Object expected = new Object();
        assertSame(expected, StackIdentity.requirePresentOptionalValue(
                Optional.of(expected), "fixture owner definition"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void absentGuavaValueFailsClosed() {
        StackIdentity.requirePresentOptionalValue(
                Optional.absent(), "fixture owner definition");
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonGuavaOptionalShapeFailsClosed() {
        StackIdentity.requirePresentOptionalValue(
                java.util.Optional.of(new Object()), "fixture owner definition");
    }

    @Test
    public void exactDefinitionOwnedLayeredTileSubclassIsAccepted() throws Exception {
        assertSame(FixtureLayeredTile.class, StackIdentity.requirePinnedLayeredTileClass(
                FixtureLayeredTile.class,
                new String[] {
                    FixtureLayeredTile.class.getName(),
                    FixtureMiddleTile.class.getName(),
                    FixtureTileBase.class.getName()
                },
                "fixture layered tile"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unlayeredDefinitionTileFailsClosed() throws Exception {
        StackIdentity.requirePinnedLayeredTileClass(
                FixtureTileBase.class,
                new String[] {
                    FixtureTileBase.class.getName(),
                    FixtureTileBase.class.getName()
                },
                "fixture layered tile");
    }

    @Test(expected = IllegalArgumentException.class)
    public void unexpectedLayeredTileNameFailsClosed() throws Exception {
        StackIdentity.requirePinnedLayeredTileClass(
                FixtureLayeredTile.class,
                new String[] {
                    "fixture.UnexpectedLayeredTile",
                    FixtureTileBase.class.getName()
                },
                "fixture layered tile");
    }

    @Test(expected = IllegalArgumentException.class)
    public void reorderedLayeredTileHierarchyFailsClosed() throws Exception {
        StackIdentity.requirePinnedLayeredTileClass(
                FixtureLayeredTile.class,
                new String[] {
                    FixtureLayeredTile.class.getName(),
                    FixtureTileBase.class.getName(),
                    FixtureMiddleTile.class.getName()
                },
                "fixture layered tile");
    }

    @Test(expected = IllegalArgumentException.class)
    public void knownFluidProxyCannotSilentlyFallBackToItemIdentity() {
        StackIdentity.requireKnownProxyDecoded(true, null, "fixture.FluidProxy");
    }

    @Test
    public void fluidAmountIsSlotDataAndNotPartOfCatalogIdentity() {
        ItemStack display144 = new ItemStack(new Item(), 1);
        ItemStack display1000 = new ItemStack(new Item(), 1);

        StackIdentity first = StackIdentity.fluidProxy(
                display144, "water", "minecraft:water", null, "Water", 144);
        StackIdentity second = StackIdentity.fluidProxy(
                display1000, "water", "minecraft:water", null, "Water", 1000);

        assertEquals("fluid|fluid:water", first.key);
        assertEquals(first.key, second.key);
        assertTrue(first.sameLogicalIdentity(second));
        assertEquals(144, first.amount);
        assertEquals(1000, second.amount);
        assertEquals("minecraft:water", first.registryId);
        assertEquals("minecraft", first.namespace());
        assertTrue(first.isFluid());
    }

    @Test
    public void fluidTagRemainsPartOfLogicalIdentity() {
        ItemStack display = new ItemStack(new Item(), 1);
        NBTTagCompound cold = new NBTTagCompound();
        cold.setString("state", "cold");
        NBTTagCompound hot = new NBTTagCompound();
        hot.setString("state", "hot");

        StackIdentity first = StackIdentity.fluidProxy(
                display, "water", "minecraft:water", cold, "Water", 1000);
        StackIdentity second = StackIdentity.fluidProxy(
                display, "water", "minecraft:water", hot, "Water", 1000);

        assertNotEquals(first.key, second.key);
    }

    @Test
    public void canonicalEquivalentFluidTagsShareIdentity() {
        ItemStack display = new ItemStack(new Item(), 1);
        NBTTagCompound firstTag = new NBTTagCompound();
        firstTag.setString("phase", "liquid");
        firstTag.setInteger("temperature", 300);
        NBTTagCompound reorderedTag = new NBTTagCompound();
        reorderedTag.setInteger("temperature", 300);
        reorderedTag.setString("phase", "liquid");

        StackIdentity first = StackIdentity.fluidProxy(
                display, "water", "minecraft:water", firstTag, "Water", 144);
        StackIdentity reordered = StackIdentity.fluidProxy(
                display, "water", "minecraft:water", reorderedTag, "Water", 1000);

        assertEquals(first.key, reordered.key);
        assertTrue(first.sameLogicalIdentity(reordered));
    }

    @Test
    public void pinnedAe2fcDropDecoderPreservesTotalAmountAndFluidTag() {
        ItemStack drop = ae2fcDrop(144);
        NBTTagCompound fluidTag = new NBTTagCompound();
        fluidTag.setInteger("temperature", 300);
        fluidTag.setString("phase", "liquid");
        NBTTagCompound root = drop.getTagCompound();
        root.setString("Fluid", "WATER");
        root.setTag("FluidTag", fluidTag);
        root.setBoolean("DisplayOnly", true);

        StackIdentity.DecodedFluidDrop decoded =
                StackIdentity.parsePinnedAe2fcFluidDropPayload(drop, TEST_FLUID_RESOLVER);

        assertSame(TEST_WATER, decoded.fluid);
        assertEquals(144, decoded.amount);
        assertEquals(NbtCanonicalizer.canonical(fluidTag),
                NbtCanonicalizer.canonical(decoded.tag));
    }

    @Test
    public void pinnedAe2fcDropDecoderRejectsEveryUnmodeledShape() {
        ItemStack missingRoot = new ItemStack(new Item(), 1, 0);
        assertInvalidDrop(missingRoot, "root NBT is absent");

        ItemStack missingFluid = ae2fcDrop(1);
        assertInvalidDrop(missingFluid, "Fluid field is absent");

        ItemStack wrongFluidType = ae2fcDrop(1);
        wrongFluidType.getTagCompound().setInteger("Fluid", 1);
        assertInvalidDrop(wrongFluidType, "NBT string type 8");

        ItemStack unknownFluid = ae2fcDrop(1);
        unknownFluid.getTagCompound().setString("Fluid", "recipe_tree_missing_fluid");
        assertInvalidDrop(unknownFluid, "does not resolve");

        ItemStack wrongFluidTagType = validWaterDrop(1);
        wrongFluidTagType.getTagCompound().setString("FluidTag", "not-a-compound");
        assertInvalidDrop(wrongFluidTagType, "NBT compound type 10");

        ItemStack falseDisplayFlag = validWaterDrop(1);
        falseDisplayFlag.getTagCompound().setBoolean("DisplayOnly", false);
        assertInvalidDrop(falseDisplayFlag, "exact presentation value 1b");

        ItemStack unknownRootKey = validWaterDrop(1);
        unknownRootKey.getTagCompound().setString("FutureSemanticField", "unreviewed");
        assertInvalidDrop(unknownRootKey, "unsupported root NBT key FutureSemanticField");

        ItemStack wrongMetadata = validWaterDrop(1);
        wrongMetadata.setItemDamage(1);
        assertInvalidDrop(wrongMetadata, "metadata must be 0");

        ItemStack nonPositiveAmount = validWaterDrop(1);
        nonPositiveAmount.stackSize = 0;
        assertInvalidDrop(nonPositiveAmount, "positive total millibucket amount");
    }

    @Test
    public void catalogExclusionShapeIsExactAndCardinalityIsPerPolicy() {
        assertEquals("ArchitectureCraft:cladding",
                StackIdentity.ARCHITECTURE_CRAFT_CLADDING_PLACEHOLDER.registryId);
        assertEquals("gcewing.architecture.common.item.ItemCladding",
                StackIdentity.ARCHITECTURE_CRAFT_CLADDING_PLACEHOLDER.runtimeClass);
        assertEquals("architecturecraft-materialless-cladding-vanilla-subitems-placeholder-v1",
                StackIdentity.ARCHITECTURE_CRAFT_CLADDING_PLACEHOLDER.contract);
        assertEquals("Avaritia:Matter_Cluster",
                StackIdentity.AVARITIA_EMPTY_MATTER_CLUSTER_PLACEHOLDER.registryId);
        assertEquals("fox.spiteful.avaritia.items.ItemMatterCluster",
                StackIdentity.AVARITIA_EMPTY_MATTER_CLUSTER_PLACEHOLDER.runtimeClass);
        assertEquals("avaritia-empty-matter-cluster-vanilla-subitems-placeholder-v1",
                StackIdentity.AVARITIA_EMPTY_MATTER_CLUSTER_PLACEHOLDER.contract);
        assertCatalogPolicy(
                StackIdentity.AE2_CABLE_BUS_INTERNAL_WORLD_ITEM_BLOCK,
                "appliedenergistics2:tile.BlockCableBus",
                StackIdentity.AE2_CABLE_BUS_ITEM_CLASS,
                StackIdentity.AE2_CABLE_BUS_BLOCK_CLASS,
                "ae2-cablebus-internal-multipart-world-host-itemblock-v1",
                "owner-internal-multipart-world-host", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.AE2_MATRIX_FRAME_INTERNAL_WORLD_ITEM_BLOCK,
                "appliedenergistics2:tile.BlockMatrixFrame",
                StackIdentity.AE2_MATRIX_FRAME_ITEM_CLASS,
                StackIdentity.AE2_MATRIX_FRAME_BLOCK_CLASS,
                "ae2-matrix-frame-owner-internal-spatial-storage-world-substrate-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL,
                "dreamcraft:item.Nothing", StackIdentity.DREAMCRAFT_NOTHING_ITEM_CLASS,
                null,
                "dreamcraft-nothing-orphaned-legacy-lootbag-empty-reward-sentinel-v1",
                "presentation-placeholder", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER,
                "littletiles:BlockLittleTiles", StackIdentity.LITTLE_TILES_ITEM_CLASS,
                StackIdentity.LITTLE_TILES_BLOCK_CLASS,
                "littletiles-unparameterized-microtile-carrier-nei-damage-search-v1",
                "owner-internal-world-state", 1, 0x1, false);
        assertCatalogPolicy(
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER,
                "malisisdoors:item.custom_door",
                StackIdentity.MALISIS_DOORS_CUSTOM_DOOR_ITEM_CLASS,
                null,
                "malisisdoors-unconfigured-custom-door-carrier-nei-getsubitems-v1",
                "owner-internal-unconfigured-dynamic-item", 1, 0x1, false);
        assertCatalogPolicy(
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER,
                "malisisdoors:mixed_block",
                StackIdentity.MALISIS_DOORS_MIXED_BLOCK_ITEM_CLASS,
                StackIdentity.MALISIS_DOORS_MIXED_BLOCK_CLASS,
                "malisisdoors-unconfigured-mixed-block-carrier-nei-getsubitems-v1",
                "owner-internal-unconfigured-dynamic-item", 1, 0x1, false);
        assertCatalogPolicy(
                StackIdentity.BOTANIA_BIFROST_WORLD_STATE,
                "Botania:bifrost", StackIdentity.BOTANIA_ITEM_BLOCK_MOD_CLASS,
                StackIdentity.BOTANIA_BIFROST_BLOCK_CLASS,
                "botania-bifrost-owner-internal-world-state-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.BOTANIA_BURIED_PETALS_WORLD_STATE_VARIANT,
                "Botania:buriedPetals", StackIdentity.BOTANIA_BURIED_PETALS_ITEM_CLASS,
                StackIdentity.BOTANIA_BURIED_PETALS_BLOCK_CLASS,
                "botania-buried-petals-owner-internal-world-state-itemblock-variants-v1",
                "owner-internal-world-state", 16, 0xffff, true);
        assertCatalogPolicy(
                StackIdentity.BOTANIA_CACOPHONIUM_BLOCK_WORLD_STATE,
                "Botania:cacophoniumBlock", StackIdentity.BOTANIA_ITEM_BLOCK_MOD_CLASS,
                StackIdentity.BOTANIA_CACOPHONIUM_BLOCK_CLASS,
                "botania-cacophonium-owner-internal-world-state-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.BOTANIA_ENCHANTER_WORLD_STATE,
                "Botania:enchanter", StackIdentity.BOTANIA_ITEM_BLOCK_MOD_CLASS,
                StackIdentity.BOTANIA_ENCHANTER_BLOCK_CLASS,
                "botania-enchanter-owner-internal-world-state-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.BOTANIA_FAKE_AIR_WORLD_STATE,
                "Botania:fakeAir", StackIdentity.BOTANIA_ITEM_BLOCK_MOD_CLASS,
                StackIdentity.BOTANIA_FAKE_AIR_BLOCK_CLASS,
                "botania-fake-air-owner-internal-world-state-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.BOTANIA_MANA_FLAME_WORLD_STATE,
                "Botania:manaFlame", StackIdentity.BOTANIA_ITEM_BLOCK_MOD_CLASS,
                StackIdentity.BOTANIA_MANA_FLAME_BLOCK_CLASS,
                "botania-mana-flame-owner-internal-world-state-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.BOTANIA_SOLID_VINE_WORLD_STATE,
                "Botania:solidVine", StackIdentity.BOTANIA_ITEM_BLOCK_MOD_CLASS,
                StackIdentity.BOTANIA_SOLID_VINE_BLOCK_CLASS,
                "botania-solid-vine-owner-internal-world-state-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.BOTANIA_STRUCTURE_LIB_ANY_FLOWER_PLACEHOLDER,
                "Botania:flower_structurelib", StackIdentity.BOTANIA_ITEM_BLOCK_MOD_CLASS,
                StackIdentity.BOTANIA_STRUCTURE_LIB_FLOWER_BLOCK_CLASS,
                "botania-structurelib-any-flower-presentation-placeholder-v1",
                "presentation-placeholder", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.CARPENTERS_BED_INTERNAL_WORLD_ITEM_BLOCK,
                "CarpentersBlocks:blockCarpentersBed", StackIdentity.VANILLA_ITEM_BLOCK_CLASS,
                StackIdentity.CARPENTERS_BED_BLOCK_CLASS,
                "carpentersblocks-bed-internal-multiblock-world-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.CARPENTERS_DOOR_INTERNAL_WORLD_ITEM_BLOCK,
                "CarpentersBlocks:blockCarpentersDoor", StackIdentity.VANILLA_ITEM_BLOCK_CLASS,
                StackIdentity.CARPENTERS_DOOR_BLOCK_CLASS,
                "carpentersblocks-door-internal-multiblock-world-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.STEVES_CARTS_UNCONFIGURED_MODULAR_CART_PLACEHOLDER,
                "StevesCarts:ModularCart", StackIdentity.STEVES_CARTS_MODULAR_CART_ITEM_CLASS,
                null,
                "stevescarts-unconfigured-modular-cart-global-itemlist-placeholder-v1",
                "browser-placeholder", 1, 0x1, false);
        assertCatalogPolicy(
                StackIdentity.TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK,
                "TConstruct:BattleSignBlock", StackIdentity.VANILLA_ITEM_BLOCK_CLASS,
                StackIdentity.TCONSTRUCT_BATTLESIGN_BLOCK_CLASS,
                "tconstruct-battlesign-internal-equipped-tool-world-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK,
                "TConstruct:HeldItemBlock", StackIdentity.VANILLA_ITEM_BLOCK_CLASS,
                StackIdentity.TCONSTRUCT_HELD_ITEM_BLOCK_CLASS,
                "tconstruct-helditemblock-internal-equipped-frypan-world-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.THAUMCRAFT_BLOCK_HOLE_INTERNAL_WORLD_ITEM_BLOCK,
                "Thaumcraft:blockHole", StackIdentity.VANILLA_ITEM_BLOCK_CLASS,
                StackIdentity.THAUMCRAFT_BLOCK_HOLE_CLASS,
                "thaumcraft-blockhole-internal-portable-hole-world-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_WORLD_ITEM_BLOCK,
                "Thaumcraft:blockPortalEldritch", StackIdentity.VANILLA_ITEM_BLOCK_CLASS,
                StackIdentity.THAUMCRAFT_ELDRITCH_PORTAL_BLOCK_CLASS,
                "thaumcraft-eldritch-portal-owner-internal-world-state-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK,
                "ThaumicHorizons:light", StackIdentity.VANILLA_ITEM_BLOCK_CLASS,
                StackIdentity.THAUMIC_HORIZONS_LIGHT_BLOCK_CLASS,
                "thaumichorizons-base-illumination-light-owner-internal-world-state-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK,
                "ThaumicHorizons:lightSolar", StackIdentity.VANILLA_ITEM_BLOCK_CLASS,
                StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_BLOCK_CLASS,
                "thaumichorizons-solar-illumination-light-owner-internal-world-state-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK,
                "TwilightForest:tile.TFExperiment115",
                StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_ITEM_BLOCK_CLASS,
                StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_BLOCK_CLASS,
                "twilightforest-experiment115-internal-cake-world-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertCatalogPolicy(
                StackIdentity.WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK,
                "WitchingGadgets:WG_CustomAir", StackIdentity.VANILLA_ITEM_BLOCK_CLASS,
                "witchinggadgets.common.blocks.BlockModifiedAiry",
                "witchinggadgets-custom-air-owner-internal-temporary-light-world-state-itemblock-v1",
                "owner-internal-world-state", 1, 0x1, true);
        assertEquals("com.carpentersblocks.item.ItemCarpentersBed",
                StackIdentity.CARPENTERS_BED_PUBLIC_ITEM_CLASS);
        assertEquals("com.carpentersblocks.item.ItemCarpentersDoor",
                StackIdentity.CARPENTERS_DOOR_PUBLIC_ITEM_CLASS);
        assertEquals(31, StackIdentity.CATALOG_EXCLUSION_POLICIES.length);
        assertEquals("com.dreammaster.item.NHItemList",
                StackIdentity.DREAMCRAFT_NH_ITEM_LIST_CLASS);
        assertEquals("eu.usrv.yamcore.items.ModSimpleBaseItem",
                StackIdentity.DREAMCRAFT_SIMPLE_ITEM_WRAPPER_CLASS);
        assertEquals("eu.usrv.yamcore.creativetabs.ModCreativeTab",
                StackIdentity.DREAMCRAFT_GENERIC_TAB_CLASS);
        assertEquals("item.Nothing", StackIdentity.DREAMCRAFT_NOTHING_UNLOCALIZED_NAME);
        assertEquals("dreamcraft:itemNothing", StackIdentity.DREAMCRAFT_NOTHING_ICON);
        assertEquals("tabDreamCraftItems_Generic", StackIdentity.DREAMCRAFT_GENERIC_TAB_LABEL);
        assertEquals(56038, StackIdentity.PINNED_RAW_ITEM_LIST_COUNT);
        assertEquals(46, StackIdentity.PINNED_CATALOG_EXCLUSION_COUNT);
        assertEquals(55992, StackIdentity.PINNED_RETAINED_ITEM_LIST_COUNT);
        assertEquals(55991,
                StackIdentity.PINNED_RETAINED_UNIQUE_ITEM_LIST_IDENTITY_COUNT);
        assertEquals("net.malisis.doors.door.item.CustomDoorItem",
                StackIdentity.MALISIS_DOORS_CUSTOM_DOOR_ITEM_CLASS);
        assertEquals("net.malisis.doors.door.item.DoorItem",
                StackIdentity.MALISIS_DOORS_DOOR_ITEM_CLASS);
        assertEquals("net.malisis.doors.door.renderer.CustomDoorRenderer",
                StackIdentity.MALISIS_DOORS_CUSTOM_DOOR_RENDERER_CLASS);
        assertEquals("net.malisis.doors.door.renderer.DoorRenderer",
                StackIdentity.MALISIS_DOORS_DOOR_RENDERER_CLASS);
        assertEquals("net.malisis.doors.door.tileentity.CustomDoorTileEntity",
                StackIdentity.MALISIS_DOORS_CUSTOM_DOOR_TILE_ENTITY_CLASS);
        assertEquals("item.custom_door",
                StackIdentity.MALISIS_DOORS_CUSTOM_DOOR_UNLOCALIZED_NAME);
        assertEquals("net.malisis.doors.item.MixedBlockBlockItem",
                StackIdentity.MALISIS_DOORS_MIXED_BLOCK_ITEM_CLASS);
        assertEquals("net.malisis.doors.block.MixedBlock",
                StackIdentity.MALISIS_DOORS_MIXED_BLOCK_CLASS);
        assertEquals("net.malisis.doors.renderer.MixedBlockRenderer",
                StackIdentity.MALISIS_DOORS_MIXED_BLOCK_RENDERER_CLASS);
        assertEquals("net.malisis.doors.entity.MixedBlockTileEntity",
                StackIdentity.MALISIS_DOORS_MIXED_BLOCK_TILE_ENTITY_CLASS);
        assertEquals("net.malisis.doors.entity.BlockMixerTileEntity",
                StackIdentity.MALISIS_DOORS_BLOCK_MIXER_TILE_ENTITY_CLASS);
        assertEquals("tile.mixed_block",
                StackIdentity.MALISIS_DOORS_MIXED_BLOCK_UNLOCALIZED_NAME);
        assertEquals(4, StackIdentity.MALISIS_DOORS_MIXED_BLOCK_NBT_KEYS.length);
        assertEquals("com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles",
                StackIdentity.LITTLE_TILES_TILE_ENTITY_CLASS);
        assertEquals("com.creativemd.littletiles.client.render.SpecialBlockTilesRenderer",
                StackIdentity.LITTLE_TILES_RENDERER_CLASS);
        assertEquals("com.creativemd.littletiles.client.render.ITilesRenderer",
                StackIdentity.LITTLE_TILES_DYNAMIC_RENDERER_INTERFACE);
        assertEquals("LittleTilesTileEntity", StackIdentity.LITTLE_TILES_TILE_ENTITY_ID);
        assertEquals("appeng.tile.networking.TileCableBus",
                StackIdentity.AE2_CABLE_BUS_TILE_CLASS);
        assertEquals("appeng.core.features.AECableBusFeatureHandler",
                StackIdentity.AE2_CABLE_BUS_FEATURE_HANDLER_CLASS);
        assertEquals("appeng.client.render.ItemRenderer",
                StackIdentity.AE2_CABLE_BUS_ITEM_RENDERER_CLASS);
        assertEquals("appeng.client.render.blocks.RendererCableBus",
                StackIdentity.AE2_CABLE_BUS_BLOCK_RENDERER_CLASS);
        assertEquals("appeng.block.AEBaseItemBlock",
                StackIdentity.AE2_MATRIX_FRAME_ITEM_CLASS);
        assertEquals("appeng.block.spatial.BlockMatrixFrame",
                StackIdentity.AE2_MATRIX_FRAME_BLOCK_CLASS);
        assertEquals("appeng.core.features.AEBlockFeatureHandler",
                StackIdentity.AE2_MATRIX_FRAME_FEATURE_HANDLER_CLASS);
        assertEquals("appeng.client.render.ItemRenderer",
                StackIdentity.AE2_MATRIX_FRAME_ITEM_RENDERER_CLASS);
        assertEquals("appeng.client.render.blocks.RenderNull",
                StackIdentity.AE2_MATRIX_FRAME_BLOCK_RENDERER_CLASS);
        assertEquals("vazkii.botania.common.block.BlockCocoon",
                StackIdentity.BOTANIA_COCOON_BLOCK_CLASS);
        assertEquals("micdoodle8.mods.galacticraft.core.items.ItemFlag",
                StackIdentity.GALACTICRAFT_FLAG_ITEM_CLASS);
        assertEquals("item|GalacticraftCore:item.flag|meta=0|nbt=-",
                StackIdentity.GALACTICRAFT_FLAG_CANONICAL_KEY);
        assertEquals("codechicken.wirelessredstone.addons.ItemWirelessTriangulator",
                StackIdentity.WRCBE_TRIANGULATOR_ITEM_CLASS);
        assertEquals("item|WR-CBE|Addons:triangulator|meta=0|nbt=-",
                StackIdentity.WRCBE_TRIANGULATOR_CANONICAL_KEY);
        assertEquals("item|WR-CBE|Addons:triangulator|meta=32767|nbt=-",
                StackIdentity.WRCBE_TRIANGULATOR_WILDCARD_CANONICAL_KEY);
        assertEquals("vswe.stevescarts.Renders.RendererMinecartItem",
                StackIdentity.STEVES_CARTS_MODULAR_CART_RENDERER_CLASS);
        assertEquals("tconstruct.items.tools.BattleSign",
                StackIdentity.TCONSTRUCT_BATTLESIGN_PUBLIC_ITEM_CLASS);
        assertEquals("tconstruct.tools.model.BattlesignRender",
                StackIdentity.TCONSTRUCT_BATTLESIGN_BLOCK_RENDERER_CLASS);
        assertEquals("tconstruct.client.FlexibleToolRenderer",
                StackIdentity.TCONSTRUCT_BATTLESIGN_PUBLIC_ITEM_RENDERER_CLASS);
        assertEquals("tconstruct.tools.logic.FrypanLogic",
                StackIdentity.TCONSTRUCT_HELD_ITEM_TILE_CLASS);
        assertEquals("tconstruct.items.tools.FryingPan",
                StackIdentity.TCONSTRUCT_HELD_ITEM_PUBLIC_ITEM_CLASS);
        assertEquals("tconstruct.tools.model.FrypanRender",
                StackIdentity.TCONSTRUCT_HELD_ITEM_BLOCK_RENDERER_CLASS);
        assertEquals("tconstruct.client.FlexibleToolRenderer",
                StackIdentity.TCONSTRUCT_HELD_ITEM_PUBLIC_ITEM_RENDERER_CLASS);
        assertEquals("thaumcraft.common.blocks.BlockHole",
                StackIdentity.THAUMCRAFT_BLOCK_HOLE_CLASS);
        assertEquals("thaumcraft.common.tiles.TileHole",
                StackIdentity.THAUMCRAFT_TILE_HOLE_CLASS);
        assertEquals("thaumcraft.common.items.wands.foci.ItemFocusPortableHole",
                StackIdentity.THAUMCRAFT_PORTABLE_HOLE_FOCUS_CLASS);
        assertEquals("thaumcraft:blank",
                StackIdentity.THAUMCRAFT_BLOCK_HOLE_BLANK_ICON);
        assertEquals("thaumcraft:empty",
                StackIdentity.THAUMCRAFT_BLOCK_HOLE_EMPTY_SENTINEL_ICON);
        assertEquals("thaumcraft.common.blocks.BlockEldritchPortal",
                StackIdentity.THAUMCRAFT_ELDRITCH_PORTAL_BLOCK_CLASS);
        assertEquals("thaumcraft.common.tiles.TileEldritchPortal",
                StackIdentity.THAUMCRAFT_ELDRITCH_PORTAL_TILE_CLASS);
        assertEquals("thaumcraft.common.items.ItemEldritchObject",
                StackIdentity.THAUMCRAFT_ELDRITCH_OBJECT_ITEM_CLASS);
        assertEquals("thaumcraft:blank",
                StackIdentity.THAUMCRAFT_ELDRITCH_PORTAL_BLANK_ICON);
        assertEquals("thaumcraft:eldritch_object",
                StackIdentity.THAUMCRAFT_ELDRITCH_OBJECT_ICON);
        assertEquals("makeo.gadomancy.common.items.ItemBlockAdditionalEldritchPortal",
                StackIdentity.GADOMANCY_ELDRITCH_PORTAL_ITEM_CLASS);
        assertEquals("makeo.gadomancy.common.blocks.BlockAdditionalEldritchPortal",
                StackIdentity.GADOMANCY_ELDRITCH_PORTAL_BLOCK_CLASS);
        assertEquals("gadomancy:eldritch_portal",
                StackIdentity.GADOMANCY_ELDRITCH_PORTAL_ICON);
        assertEquals("com.kentington.thaumichorizons.common.blocks.BlockLight",
                StackIdentity.THAUMIC_HORIZONS_LIGHT_BLOCK_CLASS);
        assertEquals("com.kentington.thaumichorizons.common.blocks.BlockLightSolar",
                StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_BLOCK_CLASS);
        assertEquals("com.kentington.thaumichorizons.common.tiles.TileLight",
                StackIdentity.THAUMIC_HORIZONS_LIGHT_TILE_CLASS);
        assertEquals("com.kentington.thaumichorizons.common.items.ItemFocusIllumination",
                StackIdentity.THAUMIC_HORIZONS_ILLUMINATION_FOCUS_ITEM_CLASS);
        assertEquals("thaumcraft:blank", StackIdentity.THAUMIC_HORIZONS_LIGHT_BLANK_ICON);
        assertEquals("twilightforest.block.BlockTFExperiment115",
                StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_BLOCK_CLASS);
        assertEquals("twilightforest.item.ItemBlockTFMeta",
                StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_ITEM_BLOCK_CLASS);
        assertEquals("twilightforest.item.ItemTFFood",
                StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_PUBLIC_ITEM_CLASS);
        assertEquals("twilightforest.tileentity.TileEntityTFCake",
                StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_TILE_CLASS);
        assertEquals("twilightforest.client.renderer.blocks.RenderBlockTFCake",
                StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_BLOCK_RENDERER_CLASS);

        ItemStack exact = new ItemStack(new Item(), 1, 0);
        assertTrue(StackIdentity.isBareCatalogEntryShape(exact));

        ItemStack tagged = new ItemStack(new Item(), 1, 0);
        tagged.setTagCompound(new NBTTagCompound());
        assertFalse(StackIdentity.isBareCatalogEntryShape(tagged));
        assertFalse(StackIdentity.isBareCatalogEntryShape(new ItemStack(new Item(), 2, 0)));
        assertFalse(StackIdentity.isBareCatalogEntryShape(new ItemStack(new Item(), 1, 1)));
        assertFalse(StackIdentity.isBareCatalogEntryShape(null));

        assertTrue(StackIdentity.isBareCatalogEntryShapeWithMetadataRange(
                new ItemStack(new Item(), 1, 0), 0, 15));
        assertTrue(StackIdentity.isBareCatalogEntryShapeWithMetadataRange(
                new ItemStack(new Item(), 1, 15), 0, 15));
        assertFalse(StackIdentity.isBareCatalogEntryShapeWithMetadataRange(
                new ItemStack(new Item(), 1, 16), 0, 15));
        assertFalse(StackIdentity.isBareCatalogEntryShapeWithMetadataRange(
                new ItemStack(new Item(), 2, 7), 0, 15));
        ItemStack taggedBuriedPetal = new ItemStack(new Item(), 1, 7);
        taggedBuriedPetal.setTagCompound(new NBTTagCompound());
        assertFalse(StackIdentity.isBareCatalogEntryShapeWithMetadataRange(
                taggedBuriedPetal, 0, 15));

        StackIdentity.CatalogExclusionAudit complete = completeCatalogExclusionAudit();
        complete.requireExpected();
        assertEquals(16, complete.count(
                StackIdentity.BOTANIA_BURIED_PETALS_WORLD_STATE_VARIANT));
        assertEquals(0xffff, complete.metadataMask(
                StackIdentity.BOTANIA_BURIED_PETALS_WORLD_STATE_VARIANT));
        assertEquals(1, complete.count(
                StackIdentity.WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK));
        assertEquals(0x1, complete.metadataMask(
                StackIdentity.WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK));
        assertEquals(1, complete.count(
                StackIdentity.AE2_CABLE_BUS_INTERNAL_WORLD_ITEM_BLOCK));
        assertEquals(0x1, complete.metadataMask(
                StackIdentity.AE2_CABLE_BUS_INTERNAL_WORLD_ITEM_BLOCK));
        assertEquals(1, complete.count(
                StackIdentity.AE2_MATRIX_FRAME_INTERNAL_WORLD_ITEM_BLOCK));
        assertEquals(0x1, complete.metadataMask(
                StackIdentity.AE2_MATRIX_FRAME_INTERNAL_WORLD_ITEM_BLOCK));
        assertEquals(1, complete.count(
                StackIdentity.DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL));
        assertEquals(0x1, complete.metadataMask(
                StackIdentity.DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL));
        assertEquals(1, complete.count(
                StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER));
        assertEquals(0x1, complete.metadataMask(
                StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER));
        assertEquals(1, complete.count(
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER));
        assertEquals(0x1, complete.metadataMask(
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER));
        assertEquals(1, complete.count(
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER));
        assertEquals(0x1, complete.metadataMask(
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER));

        StackIdentity.CatalogExclusionAudit missing = completeCatalogExclusionAudit();
        missing.record(StackIdentity.BOTANIA_BIFROST_WORLD_STATE,
                new ItemStack(new Item(), 1, 0));
        assertInvalidExclusionAudit(missing, "Botania:bifrost expected count=1", "got count=2");

        StackIdentity.CatalogExclusionAudit duplicateEldritchPortal =
                completeCatalogExclusionAudit();
        duplicateEldritchPortal.record(
                StackIdentity.THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_WORLD_ITEM_BLOCK,
                new ItemStack(new Item(), 1, 0));
        assertInvalidExclusionAudit(
                duplicateEldritchPortal,
                "Thaumcraft:blockPortalEldritch expected count=1 metadataMask=0x1",
                "got count=2 metadataMask=0x1");

        StackIdentity.CatalogExclusionAudit duplicateWitchingGadgetsCustomAir =
                completeCatalogExclusionAudit();
        duplicateWitchingGadgetsCustomAir.record(
                StackIdentity.WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK,
                new ItemStack(new Item(), 1, 0));
        assertInvalidExclusionAudit(
                duplicateWitchingGadgetsCustomAir,
                "WitchingGadgets:WG_CustomAir expected count=1 metadataMask=0x1",
                "got count=2 metadataMask=0x1");

        StackIdentity.CatalogExclusionAudit duplicateAe2CableBus =
                completeCatalogExclusionAudit();
        duplicateAe2CableBus.record(
                StackIdentity.AE2_CABLE_BUS_INTERNAL_WORLD_ITEM_BLOCK,
                new ItemStack(new Item(), 1, 0));
        assertInvalidExclusionAudit(
                duplicateAe2CableBus,
                "appliedenergistics2:tile.BlockCableBus expected count=1 metadataMask=0x1",
                "got count=2 metadataMask=0x1");

        StackIdentity.CatalogExclusionAudit duplicateAe2MatrixFrame =
                completeCatalogExclusionAudit();
        duplicateAe2MatrixFrame.record(
                StackIdentity.AE2_MATRIX_FRAME_INTERNAL_WORLD_ITEM_BLOCK,
                new ItemStack(new Item(), 1, 0));
        assertInvalidExclusionAudit(
                duplicateAe2MatrixFrame,
                "appliedenergistics2:tile.BlockMatrixFrame expected count=1 metadataMask=0x1",
                "got count=2 metadataMask=0x1");

        StackIdentity.CatalogExclusionAudit duplicateDreamcraftNothing =
                completeCatalogExclusionAudit();
        duplicateDreamcraftNothing.record(
                StackIdentity.DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL,
                new ItemStack(new Item(), 1, 0));
        assertInvalidExclusionAudit(
                duplicateDreamcraftNothing,
                "dreamcraft:item.Nothing expected count=1 metadataMask=0x1",
                "got count=2 metadataMask=0x1");

        StackIdentity.CatalogExclusionAudit duplicateLittleTilesCarrier =
                completeCatalogExclusionAudit();
        duplicateLittleTilesCarrier.record(
                StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER,
                new ItemStack(new Item(), 1, 0));
        assertInvalidExclusionAudit(
                duplicateLittleTilesCarrier,
                "littletiles:BlockLittleTiles expected count=1 metadataMask=0x1",
                "got count=2 metadataMask=0x1");

        StackIdentity.CatalogExclusionAudit duplicateMalisisDoorsCarrier =
                completeCatalogExclusionAudit();
        duplicateMalisisDoorsCarrier.record(
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER,
                new ItemStack(new Item(), 1, 0));
        assertInvalidExclusionAudit(
                duplicateMalisisDoorsCarrier,
                "malisisdoors:item.custom_door expected count=1 metadataMask=0x1",
                "got count=2 metadataMask=0x1");

        StackIdentity.CatalogExclusionAudit duplicateMalisisDoorsMixedBlockCarrier =
                completeCatalogExclusionAudit();
        duplicateMalisisDoorsMixedBlockCarrier.record(
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER,
                new ItemStack(new Item(), 1, 0));
        assertInvalidExclusionAudit(
                duplicateMalisisDoorsMixedBlockCarrier,
                "malisisdoors:mixed_block expected count=1 metadataMask=0x1",
                "got count=2 metadataMask=0x1");

        StackIdentity.CatalogExclusionAudit duplicateBuriedMetadata =
                completeCatalogExclusionAuditWithoutBuriedPetals();
        for (int index = 0; index < 16; index++) {
            duplicateBuriedMetadata.record(
                    StackIdentity.BOTANIA_BURIED_PETALS_WORLD_STATE_VARIANT,
                    new ItemStack(new Item(), 1, 0));
        }
        assertInvalidExclusionAudit(
                duplicateBuriedMetadata,
                "Botania:buriedPetals expected count=16 metadataMask=0xffff",
                "got count=16 metadataMask=0x1");
    }

    @Test
    public void dreamcraftNothingMatcherExcludesOnlyExactBareSentinelShape() {
        eu.usrv.yamcore.items.ItemBase sentinel =
                new eu.usrv.yamcore.items.ItemBase();
        Item unrelated = new Item();
        assertSame(StackIdentity.DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL,
                StackIdentity.matchExactCatalogPolicy(
                        new ItemStack(sentinel, 1, 0), sentinel,
                        StackIdentity.DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(unrelated, 1, 0), sentinel,
                StackIdentity.DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL));

        assertInvalidDreamcraftNothingShape(new ItemStack(sentinel, 2, 0), sentinel);
        assertInvalidDreamcraftNothingShape(new ItemStack(sentinel, 1, 1), sentinel);
        assertInvalidDreamcraftNothingShape(new ItemStack(sentinel, 1, 32767), sentinel);
        ItemStack tagged = new ItemStack(sentinel, 1, 0);
        tagged.setTagCompound(new NBTTagCompound());
        assertInvalidDreamcraftNothingShape(tagged, sentinel);
    }

    @Test
    public void littleTilesMatcherExcludesOnlyBareUnparameterizedCarrier() {
        net.minecraft.block.Block block = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.rock) {};
        com.creativemd.littletiles.common.items.ItemBlockTiles carrier =
                new com.creativemd.littletiles.common.items.ItemBlockTiles(block);
        Item unrelated = new Item();
        assertSame(StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER,
                StackIdentity.matchExactCatalogPolicy(
                        new ItemStack(carrier, 1, 0), carrier,
                        StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(unrelated, 1, 0), carrier,
                StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(carrier, 2, 0), carrier,
                StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(carrier, 1, 1), carrier,
                StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(carrier, 1, 32767), carrier,
                StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER));
        ItemStack tagged = new ItemStack(carrier, 1, 0);
        tagged.setTagCompound(new NBTTagCompound());
        assertNull(StackIdentity.matchExactCatalogPolicy(
                tagged, carrier,
                StackIdentity.LITTLE_TILES_UNPARAMETERIZED_MICROTILE_CARRIER));
    }

    @Test
    public void littleTilesRendererTopologyPinsDistinctTesrAndSharedBlockItemOwner() {
        Object owner = new Object();
        Object distinctTesr = new Object();
        StackIdentity.requirePinnedLittleTilesRendererTopology(
                owner, owner, distinctTesr, owner, 73, 73);

        assertInvalidLittleTilesRendererTopology(
                owner, new Object(), distinctTesr, owner, 73, 73,
                "raw block renderer is not the static owner renderer");
        assertInvalidLittleTilesRendererTopology(
                owner, owner, owner, owner, 73, 73,
                "TESR unexpectedly aliases the distinct static owner/block renderer");
        assertInvalidLittleTilesRendererTopology(
                owner, owner, distinctTesr, new Object(), 73, 73,
                "carrier item renderer is not the static owner renderer");
        assertInvalidLittleTilesRendererTopology(
                owner, owner, distinctTesr, owner, 73, 74,
                "raw block renderer ID drifted");
    }

    @Test
    public void malisisDoorsMatcherRetainsEveryConfiguredOrNonBareVariant() {
        net.malisis.doors.door.item.CustomDoorItem carrier =
                new net.malisis.doors.door.item.CustomDoorItem();
        Item unrelated = new Item();
        assertSame(StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER,
                StackIdentity.matchExactCatalogPolicy(
                        new ItemStack(carrier, 1, 0), carrier,
                        StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER));
        assertTrue(StackIdentity.isExactMalisisDoorsUnconfiguredCustomDoorCarrier(
                new ItemStack(carrier, 1, 0), carrier));
        assertFalse(StackIdentity.isExactMalisisDoorsUnconfiguredCustomDoorCarrier(
                new ItemStack(unrelated, 1, 0), carrier));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(unrelated, 1, 0), carrier,
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(carrier, 2, 0), carrier,
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(carrier, 1, 1), carrier,
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(carrier, 1, 32767), carrier,
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER));
        ItemStack configured = new ItemStack(carrier, 1, 0);
        configured.setTagCompound(new NBTTagCompound());
        assertNull(StackIdentity.matchExactCatalogPolicy(
                configured, carrier,
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_CUSTOM_DOOR_CARRIER));
        assertFalse(StackIdentity.isExactMalisisDoorsUnconfiguredCustomDoorCarrier(
                configured, carrier));
    }

    @Test
    public void malisisDoorsMixedBlockMatcherExcludesOnlyExactBareCarrier() {
        net.minecraft.block.Block block = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.rock) {};
        net.malisis.doors.item.MixedBlockBlockItem carrier =
                new net.malisis.doors.item.MixedBlockBlockItem(block);
        Item unrelated = new Item();
        assertSame(StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER,
                StackIdentity.matchExactCatalogPolicy(
                        new ItemStack(carrier, 1, 0), carrier,
                        StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER));
        assertTrue(StackIdentity.isExactMalisisDoorsUnconfiguredMixedBlockCarrier(
                new ItemStack(carrier, 1, 0), carrier));
        assertFalse(StackIdentity.isExactMalisisDoorsUnconfiguredMixedBlockCarrier(
                new ItemStack(unrelated, 1, 0), carrier));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(unrelated, 1, 0), carrier,
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(carrier, 2, 0), carrier,
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(carrier, 1, 1), carrier,
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(carrier, 1, 32767), carrier,
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER));

        ItemStack emptyNbt = new ItemStack(carrier, 1, 0);
        emptyNbt.setTagCompound(new NBTTagCompound());
        assertNull(StackIdentity.matchExactCatalogPolicy(
                emptyNbt, carrier,
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER));
        assertFalse(StackIdentity.isExactMalisisDoorsUnconfiguredMixedBlockCarrier(
                emptyNbt, carrier));

        ItemStack partialNbt = new ItemStack(carrier, 1, 0);
        NBTTagCompound partial = new NBTTagCompound();
        partial.setInteger("block1", 1);
        partialNbt.setTagCompound(partial);
        assertNull(StackIdentity.matchExactCatalogPolicy(
                partialNbt, carrier,
                StackIdentity.MALISIS_DOORS_UNCONFIGURED_MIXED_BLOCK_CARRIER));
    }

    @Test
    public void inheritedMethodTopologyPinIsObfuscationSafeAndOwnerExact() {
        java.lang.reflect.Method resolved =
                StackIdentity.requireUniquePublicVoidMethodBySignature(
                        ObfuscatedPermutationChild.class,
                        ObfuscatedPermutationBase.class,
                        "fixture permutation producer",
                        Item.class, net.minecraft.creativetab.CreativeTabs.class,
                        java.util.List.class);
        assertEquals("func_150895_a", resolved.getName());
        assertSame(ObfuscatedPermutationBase.class, resolved.getDeclaringClass());

        try {
            StackIdentity.requireUniquePublicVoidMethodBySignature(
                    OverridingPermutationChild.class,
                    ObfuscatedPermutationBase.class,
                    "overridden fixture permutation producer",
                    Item.class, net.minecraft.creativetab.CreativeTabs.class,
                    java.util.List.class);
            fail("Expected exact declaring-owner failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(
                    "must resolve exactly one public void JVM signature declared by"));
        }
    }

    @Test
    public void declaredMethodTopologyPinIsObfuscationSafeAndUnambiguous() {
        java.lang.reflect.Method resolved =
                StackIdentity.requireUniqueDeclaredPublicVoidMethodBySignature(
                        ObfuscatedTickOwner.class, "fixture tick producer");
        assertEquals("func_145845_h", resolved.getName());
        assertSame(ObfuscatedTickOwner.class, resolved.getDeclaringClass());

        try {
            StackIdentity.requireUniqueDeclaredPublicVoidMethodBySignature(
                    AmbiguousObfuscatedTickOwner.class,
                    "ambiguous fixture tick producer");
            fail("Expected same-signature ambiguity failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("matches=2"));
        }
    }

    @Test
    public void malisisDoorsMixedBlockProducerNbtContractIsExact() {
        Item carrier = new Item();
        ItemStack configured = new ItemStack(carrier, 1, 0);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("block1", net.minecraft.block.Block.getIdFromBlock(
                net.minecraft.init.Blocks.stone));
        tag.setInteger("block2", net.minecraft.block.Block.getIdFromBlock(
                net.minecraft.init.Blocks.dirt));
        tag.setInteger("metadata1", 3);
        tag.setInteger("metadata2", 5);
        configured.setTagCompound(tag);
        StackIdentity.requireExactMalisisDoorsMixedBlockProducerNbt(
                configured, carrier,
                net.minecraft.init.Blocks.stone, net.minecraft.init.Blocks.dirt,
                3, 5, "fixture");

        ItemStack extraKey = configured.copy();
        extraKey.getTagCompound().setInteger("unexpected", 1);
        assertInvalidMixedBlockProducerNbt(extraKey, carrier, "keyset drifted");

        ItemStack wrongType = configured.copy();
        wrongType.getTagCompound().setString("metadata2", "5");
        assertInvalidMixedBlockProducerNbt(wrongType, carrier, "is not an NBT int");

        ItemStack wrongValue = configured.copy();
        wrongValue.getTagCompound().setInteger("metadata2", 6);
        assertInvalidMixedBlockProducerNbt(wrongValue, carrier, "values drifted");

        assertInvalidMixedBlockProducerNbt(
                new ItemStack(carrier, 1, 0), carrier, "tagged mixed-block stack");
    }

    @Test
    public void malisisDoorsMixedBlockRendererAndGraphTopologyFailClosed() {
        Object sharedRenderer = new Object();
        StackIdentity.requirePinnedMalisisDoorsMixedBlockRendererTopology(
                sharedRenderer, sharedRenderer, true, true, false, true,
                73, 73, Object.class, Object.class);
        StackIdentity.requireNoMalisisDoorsUnconfiguredMixedBlockGraphReferences(0, 0);

        try {
            StackIdentity.requirePinnedMalisisDoorsMixedBlockRendererTopology(
                    sharedRenderer, new Object(), true, true, false, true,
                    73, 73, Object.class, Object.class);
            fail("Expected mixed-block renderer ownership failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("same exact owner"));
        }
        try {
            StackIdentity.requirePinnedMalisisDoorsMixedBlockRendererTopology(
                    sharedRenderer, sharedRenderer, true, true, true, true,
                    73, 73, Object.class, Object.class);
            fail("Expected mixed-block bare setup failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("reject the unconfigured"));
        }
        try {
            StackIdentity.requirePinnedMalisisDoorsMixedBlockRendererTopology(
                    sharedRenderer, sharedRenderer, true, true, false, true,
                    73, 74, Object.class, Object.class);
            fail("Expected mixed-block render ID failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("render ID drifted"));
        }
        for (int[] references : new int[][] {{1, 0}, {0, 1}, {1, 1}}) {
            try {
                StackIdentity.requireNoMalisisDoorsUnconfiguredMixedBlockGraphReferences(
                        references[0], references[1]);
                fail("Expected mixed-block graph-reference failure");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains(
                        "zero post-discovery graph references"));
            }
        }
    }

    @Test
    public void malisisDoorsRendererTopologyAndGraphPinsFailClosed() {
        Object sharedRenderer = new Object();
        StackIdentity.requirePinnedMalisisDoorsRendererTopology(
                sharedRenderer, sharedRenderer, true, true,
                null, null, Object.class, Object.class);
        StackIdentity.requireNoMalisisDoorsUnconfiguredCustomDoorGraphReferences(0, 0);

        try {
            StackIdentity.requirePinnedMalisisDoorsRendererTopology(
                    sharedRenderer, new Object(), true, true,
                    null, null, Object.class, Object.class);
            fail("Expected MalisisDoors renderer ownership failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("same exact owner"));
        }
        try {
            StackIdentity.requirePinnedMalisisDoorsRendererTopology(
                    sharedRenderer, sharedRenderer, false, true,
                    null, null, Object.class, Object.class);
            fail("Expected MalisisDoors renderer envelope failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("both bare and configured"));
        }
        for (int[] references : new int[][] {{1, 0}, {0, 1}, {1, 1}}) {
            try {
                StackIdentity.requireNoMalisisDoorsUnconfiguredCustomDoorGraphReferences(
                        references[0], references[1]);
                fail("Expected MalisisDoors graph-reference failure");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(),
                        expected.getMessage().contains("zero post-discovery graph references"));
            }
        }
    }

    @Test
    public void dreamcraftNothingTransparentOwnerTextureContractIsExact() {
        StackIdentity.requireExactTransparentDreamcraftNothingIcon(
                new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));

        BufferedImage visiblePixel = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        visiblePixel.setRGB(7, 11, 0x01000000);
        try {
            StackIdentity.requireExactTransparentDreamcraftNothingIcon(visiblePixel);
            fail("Expected visible Dreamcraft Nothing owner-texture pixel failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("7,11"));
        }

        for (BufferedImage invalid : new BufferedImage[] {
                null, new BufferedImage(15, 16, BufferedImage.TYPE_INT_ARGB),
                new BufferedImage(16, 15, BufferedImage.TYPE_INT_ARGB)
        }) {
            try {
                StackIdentity.requireExactTransparentDreamcraftNothingIcon(invalid);
                fail("Expected exact Dreamcraft Nothing owner-texture dimension failure");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("exact 16x16"));
            }
        }
    }

    @Test
    public void pinnedGlobalItemListCardinalityFailsClosedOnAnyBoundaryDrift() {
        StackIdentity.requireExactGlobalItemListCardinality(56038, 46, 55992, 55991);
        assertInvalidGlobalItemListCardinality(56039, 46, 55993, 55991);
        assertInvalidGlobalItemListCardinality(56038, 45, 55993, 55991);
        assertInvalidGlobalItemListCardinality(56038, 46, 55991, 55991);
        assertInvalidGlobalItemListCardinality(56038, 45, 55992, 55991);
        assertInvalidGlobalItemListCardinality(56038, 46, 55992, 55990);
    }

    @Test
    public void carpentersInternalMatcherExcludesOnlyExactBareInternalItemBlock() {
        net.minecraft.block.Block block = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.wood) {};
        ItemBlock internal = new ItemBlock(block);
        Item publicPlacementItem = new Item();

        assertSame(StackIdentity.CARPENTERS_BED_INTERNAL_WORLD_ITEM_BLOCK,
                StackIdentity.matchExactCatalogPolicy(
                        new ItemStack(internal, 1, 0), internal,
                        StackIdentity.CARPENTERS_BED_INTERNAL_WORLD_ITEM_BLOCK));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(publicPlacementItem, 1, 0), internal,
                StackIdentity.CARPENTERS_BED_INTERNAL_WORLD_ITEM_BLOCK));

        ItemStack taggedInternal = new ItemStack(internal, 1, 0);
        taggedInternal.setTagCompound(new NBTTagCompound());
        try {
            StackIdentity.matchExactCatalogPolicy(
                    taggedInternal, internal,
                    StackIdentity.CARPENTERS_BED_INTERNAL_WORLD_ITEM_BLOCK);
            fail("Expected strict Carpenter's Blocks internal ItemBlock shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled stack shape"));
        }
    }

    @Test
    public void tconstructBattlesignMatcherExcludesOnlyExactBareInternalItemBlock() {
        net.minecraft.block.Block block = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.wood) {};
        ItemBlock internal = new ItemBlock(block);
        Item publicTool = new Item();

        assertSame(StackIdentity.TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK,
                StackIdentity.matchExactCatalogPolicy(
                        new ItemStack(internal, 1, 0), internal,
                        StackIdentity.TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(publicTool, 1, 0), internal,
                StackIdentity.TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK));

        ItemStack taggedInternal = new ItemStack(internal, 1, 0);
        taggedInternal.setTagCompound(new NBTTagCompound());
        try {
            StackIdentity.matchExactCatalogPolicy(
                    taggedInternal, internal,
                    StackIdentity.TCONSTRUCT_BATTLESIGN_INTERNAL_WORLD_ITEM_BLOCK);
            fail("Expected strict TConstruct internal BattleSignBlock shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled stack shape"));
        }
    }

    @Test
    public void tconstructHeldItemMatcherExcludesOnlyExactBareInternalItemBlock() {
        net.minecraft.block.Block block = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.wood) {};
        ItemBlock internal = new ItemBlock(block);
        Item publicTool = new Item();

        assertSame(StackIdentity.TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK,
                StackIdentity.matchExactCatalogPolicy(
                        new ItemStack(internal, 1, 0), internal,
                        StackIdentity.TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(publicTool, 1, 0), internal,
                StackIdentity.TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK));

        ItemStack taggedInternal = new ItemStack(internal, 1, 0);
        taggedInternal.setTagCompound(new NBTTagCompound());
        try {
            StackIdentity.matchExactCatalogPolicy(
                    taggedInternal, internal,
                    StackIdentity.TCONSTRUCT_HELD_ITEM_INTERNAL_WORLD_ITEM_BLOCK);
            fail("Expected strict TConstruct internal HeldItemBlock shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled stack shape"));
        }
    }

    @Test
    public void thaumcraftBlockHoleMatcherExcludesOnlyMetadataZeroAndPassesDiagramSentinel() {
        net.minecraft.block.Block block = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.rock) {};
        ItemBlock internal = new ItemBlock(block);
        Item publicFocus = new Item();

        assertSame(StackIdentity.THAUMCRAFT_BLOCK_HOLE_INTERNAL_WORLD_ITEM_BLOCK,
                StackIdentity.matchThaumcraftBlockHoleCatalogPolicy(
                        new ItemStack(internal, 1, 0), internal));
        assertNull(StackIdentity.matchThaumcraftBlockHoleCatalogPolicy(
                new ItemStack(internal, 1, 15), internal));
        assertNull(StackIdentity.matchThaumcraftBlockHoleCatalogPolicy(
                new ItemStack(publicFocus, 1, 0), internal));

        ItemStack taggedMetadataZero = new ItemStack(internal, 1, 0);
        taggedMetadataZero.setTagCompound(new NBTTagCompound());
        assertInvalidThaumcraftBlockHoleShape(taggedMetadataZero, internal);

        ItemStack taggedDiagramSentinel = new ItemStack(internal, 1, 15);
        taggedDiagramSentinel.setTagCompound(new NBTTagCompound());
        assertInvalidThaumcraftBlockHoleShape(taggedDiagramSentinel, internal);

        assertInvalidThaumcraftBlockHoleShape(new ItemStack(internal, 2, 0), internal);
        assertInvalidThaumcraftBlockHoleShape(new ItemStack(internal, 1, 1), internal);
        assertInvalidThaumcraftBlockHoleShape(new ItemStack(internal, 1, 16), internal);
    }

    @Test
    public void thaumcraftEldritchPortalMatcherExcludesOnlyExactBareCoreItemBlock() {
        net.minecraft.block.Block coreBlock = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.portal) {};
        ItemBlock coreInternal = new ItemBlock(coreBlock);
        Item publicEldritchObject = new Item();
        net.minecraft.block.Block addOnBlock = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.portal) {};
        ItemBlock distinctGadomancyPortal = new ItemBlock(addOnBlock) {};

        assertSame(StackIdentity.THAUMCRAFT_ELDRITCH_PORTAL_INTERNAL_WORLD_ITEM_BLOCK,
                StackIdentity.matchThaumcraftEldritchPortalCatalogPolicy(
                        new ItemStack(coreInternal, 1, 0), coreInternal));
        assertNull(StackIdentity.matchThaumcraftEldritchPortalCatalogPolicy(
                new ItemStack(publicEldritchObject, 1, 0), coreInternal));
        assertNull(StackIdentity.matchThaumcraftEldritchPortalCatalogPolicy(
                new ItemStack(distinctGadomancyPortal, 1, 0), coreInternal));

        assertInvalidThaumcraftEldritchPortalShape(
                new ItemStack(coreInternal, 1, 1), coreInternal);
        assertInvalidThaumcraftEldritchPortalShape(
                new ItemStack(coreInternal, 2, 0), coreInternal);
        ItemStack taggedInternal = new ItemStack(coreInternal, 1, 0);
        taggedInternal.setTagCompound(new NBTTagCompound());
        assertInvalidThaumcraftEldritchPortalShape(taggedInternal, coreInternal);
    }

    @Test
    public void thaumicHorizonsLightMatchersExcludeOnlyTheirExactWorldItemBlocks() {
        net.minecraft.block.Block baseBlock = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.air) {};
        ItemBlock baseInternal = new ItemBlock(baseBlock);
        net.minecraft.block.Block solarBlock = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.air) {};
        ItemBlock solarInternal = new ItemBlock(solarBlock);
        Item publicFocus = new Item();

        assertSame(StackIdentity.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK,
                StackIdentity.matchExactCatalogPolicy(
                        new ItemStack(baseInternal, 1, 0), baseInternal,
                        StackIdentity.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK));
        assertSame(StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK,
                StackIdentity.matchExactCatalogPolicy(
                        new ItemStack(solarInternal, 1, 0), solarInternal,
                        StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(baseInternal, 1, 0), solarInternal,
                StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(solarInternal, 1, 0), baseInternal,
                StackIdentity.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(publicFocus, 1, 0), solarInternal,
                StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(publicFocus, 1, 0), baseInternal,
                StackIdentity.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK));

        for (Object[] invalidCase : new Object[][] {
                {new ItemStack(baseInternal, 2, 0), baseInternal,
                        StackIdentity.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK},
                {new ItemStack(baseInternal, 1, 1), baseInternal,
                        StackIdentity.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK},
                {new ItemStack(solarInternal, 2, 0), solarInternal,
                        StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK},
                {new ItemStack(solarInternal, 1, 1), solarInternal,
                        StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK}
        }) {
            try {
                StackIdentity.matchExactCatalogPolicy(
                        (ItemStack) invalidCase[0], (Item) invalidCase[1],
                        (StackIdentity.CatalogExclusion) invalidCase[2]);
                fail("Expected strict Thaumic Horizons light shape failure");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(),
                        expected.getMessage().contains("unmodeled stack shape"));
            }
        }
        ItemStack taggedBase = new ItemStack(baseInternal, 1, 0);
        taggedBase.setTagCompound(new NBTTagCompound());
        try {
            StackIdentity.matchExactCatalogPolicy(
                    taggedBase, baseInternal,
                    StackIdentity.THAUMIC_HORIZONS_BASE_LIGHT_INTERNAL_WORLD_ITEM_BLOCK);
            fail("Expected strict Thaumic Horizons light NBT shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled stack shape"));
        }
        ItemStack tagged = new ItemStack(solarInternal, 1, 0);
        tagged.setTagCompound(new NBTTagCompound());
        try {
            StackIdentity.matchExactCatalogPolicy(
                    tagged, solarInternal,
                    StackIdentity.THAUMIC_HORIZONS_SOLAR_LIGHT_INTERNAL_WORLD_ITEM_BLOCK);
            fail("Expected strict Thaumic Horizons lightSolar NBT shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled stack shape"));
        }
    }

    @Test
    public void thaumicHorizonsIlluminationBoundaryRequiresExcludedBaseAndAllPublicFocusColors() {
        Item baseLight = new Item();
        Item publicFocus = new Item();
        assertTrue(StackIdentity.isExactThaumicHorizonsBaseLightItemListEntry(
                new ItemStack(baseLight, 1, 0), baseLight));
        assertFalse(StackIdentity.isExactThaumicHorizonsBaseLightItemListEntry(
                new ItemStack(publicFocus, 1, 0), baseLight));

        int metadataMask = 0;
        for (int metadata = 0; metadata < 16; metadata++) {
            assertTrue(StackIdentity
                    .isExactThaumicHorizonsIlluminationFocusRetainedItemListEntry(
                            new ItemStack(publicFocus, 1, metadata), publicFocus));
            metadataMask |= 1 << metadata;
        }
        StackIdentity.requireExactThaumicHorizonsIlluminationItemListCounts(
                1, 16, metadataMask);
        assertInvalidThaumicHorizonsIlluminationCounts(0, 16, 0xffff, "baseLight=0");
        assertInvalidThaumicHorizonsIlluminationCounts(2, 16, 0xffff, "baseLight=2");
        assertInvalidThaumicHorizonsIlluminationCounts(1, 15, 0x7fff, "focusVariants=15");

        ItemStack taggedFocus = new ItemStack(publicFocus, 1, 0);
        taggedFocus.setTagCompound(new NBTTagCompound());
        try {
            StackIdentity.isExactThaumicHorizonsIlluminationFocusRetainedItemListEntry(
                    taggedFocus, publicFocus);
            fail("Expected strict public illumination focus NBT shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled ItemList stack shape"));
        }
    }

    @Test
    public void twilightForestExperiment115MatcherExcludesOnlyExactBareInternalItemBlock() {
        net.minecraft.block.Block block = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.cake) {};
        ItemBlockTFMeta internal = new ItemBlockTFMeta(block);
        Item publicFood = new Item();

        assertSame(StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK,
                StackIdentity.matchExactCatalogPolicy(
                        new ItemStack(internal, 1, 0), internal,
                        StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(publicFood, 1, 0), internal,
                StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK));

        for (ItemStack invalid : new ItemStack[] {
                new ItemStack(internal, 2, 0),
                new ItemStack(internal, 1, 1)
        }) {
            assertInvalidTwilightForestExperiment115InternalShape(invalid, internal);
        }
        ItemStack taggedInternal = new ItemStack(internal, 1, 0);
        taggedInternal.setTagCompound(new NBTTagCompound());
        assertInvalidTwilightForestExperiment115InternalShape(taggedInternal, internal);
    }

    @Test
    public void witchingGadgetsCustomAirMatcherExcludesOnlyExactBareInternalItemBlock() {
        net.minecraft.block.Block block = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.air) {};
        ItemBlock internal = new ItemBlock(block);
        Item differentItem = new Item();

        assertSame(StackIdentity.WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK,
                StackIdentity.matchExactCatalogPolicy(
                        new ItemStack(internal, 1, 0), internal,
                        StackIdentity.WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK));
        assertNull(StackIdentity.matchExactCatalogPolicy(
                new ItemStack(differentItem, 1, 0), internal,
                StackIdentity.WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK));

        for (ItemStack invalid : new ItemStack[] {
                new ItemStack(internal, 2, 0),
                new ItemStack(internal, 1, 1)
        }) {
            assertInvalidWitchingGadgetsCustomAirShape(invalid, internal);
        }
        ItemStack taggedInternal = new ItemStack(internal, 1, 0);
        taggedInternal.setTagCompound(new NBTTagCompound());
        assertInvalidWitchingGadgetsCustomAirShape(taggedInternal, internal);
    }

    @Test
    public void twilightForestExperiment115PublicFoodRequiresExactBareIdentityAndCardinality() {
        Item publicFood = new Item();
        Item differentItem = new Item();
        assertTrue(StackIdentity.isExactTwilightForestExperiment115PublicItemListEntry(
                new ItemStack(publicFood, 1, 0), publicFood));
        assertFalse(StackIdentity.isExactTwilightForestExperiment115PublicItemListEntry(
                new ItemStack(differentItem, 1, 0), publicFood));

        for (ItemStack invalid : new ItemStack[] {
                new ItemStack(publicFood, 2, 0),
                new ItemStack(publicFood, 1, 1)
        }) {
            assertInvalidTwilightForestExperiment115PublicShape(invalid, publicFood);
        }
        ItemStack taggedPublicFood = new ItemStack(publicFood, 1, 0);
        taggedPublicFood.setTagCompound(new NBTTagCompound());
        assertInvalidTwilightForestExperiment115PublicShape(
                taggedPublicFood, publicFood);

        StackIdentity.requireExactTwilightForestExperiment115ItemListCounts(1, 1);
        assertInvalidTwilightForestExperiment115Counts(0, 1, "internalBlock=0");
        assertInvalidTwilightForestExperiment115Counts(2, 1, "internalBlock=2");
        assertInvalidTwilightForestExperiment115Counts(1, 0, "publicFood=0");
        assertInvalidTwilightForestExperiment115Counts(1, 2, "publicFood=2");
    }

    @Test
    public void eldritchRetainedEntryPredicatesRequireExactBareIdentityAndCardinality() {
        Item thaumcraftPublicItem = new Item();
        Item differentPublicItem = new Item();
        assertTrue(StackIdentity.isExactThaumcraftEldritchObjectRetainedItemListEntry(
                new ItemStack(thaumcraftPublicItem, 1, 0), thaumcraftPublicItem));
        assertFalse(StackIdentity.isExactThaumcraftEldritchObjectRetainedItemListEntry(
                new ItemStack(differentPublicItem, 1, 0), thaumcraftPublicItem));
        assertInvalidThaumcraftEldritchObjectRetainedShape(
                new ItemStack(thaumcraftPublicItem, 2, 0), thaumcraftPublicItem);
        assertFalse(StackIdentity.isExactThaumcraftEldritchObjectRetainedItemListEntry(
                new ItemStack(thaumcraftPublicItem, 1, 1), thaumcraftPublicItem));
        assertInvalidThaumcraftEldritchObjectRetainedShape(
                new ItemStack(thaumcraftPublicItem, 1, 5), thaumcraftPublicItem);
        ItemStack taggedThaumcraftPublicItem = new ItemStack(thaumcraftPublicItem, 1, 0);
        taggedThaumcraftPublicItem.setTagCompound(new NBTTagCompound());
        assertInvalidThaumcraftEldritchObjectRetainedShape(
                taggedThaumcraftPublicItem, thaumcraftPublicItem);

        net.minecraft.block.Block gadomancyBlock = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.portal) {};
        ItemBlock gadomancyItem = new ItemBlock(gadomancyBlock) {};
        net.minecraft.block.Block differentGadomancyBlock = new net.minecraft.block.Block(
                net.minecraft.block.material.Material.portal) {};
        ItemBlock differentGadomancyItem = new ItemBlock(differentGadomancyBlock) {};
        assertTrue(StackIdentity.isExactGadomancyEldritchPortalRetainedItemListEntry(
                new ItemStack(gadomancyItem, 1, 0), gadomancyItem));
        assertFalse(StackIdentity.isExactGadomancyEldritchPortalRetainedItemListEntry(
                new ItemStack(differentGadomancyItem, 1, 0), gadomancyItem));
        assertInvalidGadomancyEldritchPortalRetainedShape(
                new ItemStack(gadomancyItem, 2, 0), gadomancyItem);
        assertInvalidGadomancyEldritchPortalRetainedShape(
                new ItemStack(gadomancyItem, 1, 1), gadomancyItem);
        ItemStack taggedGadomancyItem = new ItemStack(gadomancyItem, 1, 0);
        taggedGadomancyItem.setTagCompound(new NBTTagCompound());
        assertInvalidGadomancyEldritchPortalRetainedShape(
                taggedGadomancyItem, gadomancyItem);

        StackIdentity.requireExactRetainedEldritchItemListCounts(1, 1);
        assertInvalidRetainedEldritchCounts(0, 1, "Thaumcraft=0", "Gadomancy=1");
        assertInvalidRetainedEldritchCounts(1, 2, "Thaumcraft=1", "Gadomancy=2");
    }

    @Test
    public void stackDescriptorRecordsShapeWithoutSerializingRawNbt() {
        ItemStack stack = validWaterDrop(7);
        String description = StackIdentity.describe(stack);

        assertTrue(description.contains("registryId=<unregistered>"));
        assertTrue(description.contains("runtimeClass=net.minecraft.item.Item"));
        assertTrue(description.contains("stackSize=7"));
        assertTrue(description.contains("metadata=0"));
        assertTrue(description.contains("nbt=sha256:"));
        assertFalse(description.contains("Fluid"));
        assertNull(StackIdentity.catalogOnlyExclusion(stack));
    }

    @Test
    public void unregisteredItemFailsBeforeTheLegacyForgeIdentifierHelper() {
        ItemStack stack = new ItemStack(new Item(), 3, 17);

        try {
            StackIdentity.requireForgeRegistryIdentifier(stack);
            fail("Expected an explicit unregistered-item identity failure");
        } catch (IllegalArgumentException expected) {
            String message = expected.getMessage();
            assertTrue(message, message.startsWith(
                    "ITEM_IDENTITY: item is absent from the namespaced item registry"));
            assertTrue(message, message.contains("registryId=<unregistered>"));
            assertTrue(message, message.contains("runtimeClass=net.minecraft.item.Item"));
            assertTrue(message, message.contains("stackSize=3"));
            assertTrue(message, message.contains("metadata=17"));
            assertTrue(message, message.contains("nbt=absent"));
        }
    }

    private static ItemStack ae2fcDrop(int amount) {
        ItemStack stack = new ItemStack(new Item(), amount, 0);
        stack.setTagCompound(new NBTTagCompound());
        return stack;
    }

    private static ItemStack validWaterDrop(int amount) {
        ItemStack stack = ae2fcDrop(amount);
        stack.getTagCompound().setString("Fluid", "water");
        return stack;
    }

    private static void assertInvalidDrop(ItemStack stack, String expectedMessage) {
        try {
            StackIdentity.parsePinnedAe2fcFluidDropPayload(stack, TEST_FLUID_RESOLVER);
            fail("Expected invalid pinned AE2FC fluid-drop payload");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }

    private static void assertInvalidThaumcraftBlockHoleShape(
            ItemStack stack, Item expectedItem) {
        try {
            StackIdentity.matchThaumcraftBlockHoleCatalogPolicy(stack, expectedItem);
            fail("Expected strict Thaumcraft:blockHole stack-shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled stack shape"));
        }
    }

    private static void assertInvalidThaumcraftEldritchPortalShape(
            ItemStack stack, Item expectedItem) {
        try {
            StackIdentity.matchThaumcraftEldritchPortalCatalogPolicy(stack, expectedItem);
            fail("Expected strict Thaumcraft:blockPortalEldritch stack-shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled stack shape"));
        }
    }

    private static void assertInvalidRetainedEldritchCounts(
            int thaumcraftCount, int gadomancyCount, String... messageParts) {
        try {
            StackIdentity.requireExactRetainedEldritchItemListCounts(
                    thaumcraftCount, gadomancyCount);
            fail("Expected retained Eldritch ItemList cardinality failure");
        } catch (IllegalArgumentException expected) {
            for (String messagePart : messageParts) {
                assertTrue(expected.getMessage(), expected.getMessage().contains(messagePart));
            }
        }
    }

    private static void assertInvalidThaumcraftEldritchObjectRetainedShape(
            ItemStack stack, Item expectedItem) {
        try {
            StackIdentity.isExactThaumcraftEldritchObjectRetainedItemListEntry(
                    stack, expectedItem);
            fail("Expected retained Thaumcraft Eldritch object stack-shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled ItemList stack shape"));
        }
    }

    private static void assertInvalidGadomancyEldritchPortalRetainedShape(
            ItemStack stack, Item expectedItem) {
        try {
            StackIdentity.isExactGadomancyEldritchPortalRetainedItemListEntry(
                    stack, expectedItem);
            fail("Expected retained Gadomancy Eldritch portal stack-shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled ItemList stack shape"));
        }
    }

    private static void assertInvalidTwilightForestExperiment115InternalShape(
            ItemStack stack, Item expectedItem) {
        try {
            StackIdentity.matchExactCatalogPolicy(
                    stack, expectedItem,
                    StackIdentity.TWILIGHT_FOREST_EXPERIMENT_115_INTERNAL_WORLD_ITEM_BLOCK);
            fail("Expected strict Twilight Forest Experiment 115 internal stack-shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled stack shape"));
        }
    }

    private static void assertInvalidWitchingGadgetsCustomAirShape(
            ItemStack stack, Item expectedItem) {
        try {
            StackIdentity.matchExactCatalogPolicy(
                    stack, expectedItem,
                    StackIdentity.WITCHING_GADGETS_CUSTOM_AIR_INTERNAL_WORLD_ITEM_BLOCK);
            fail("Expected strict Witching Gadgets WG_CustomAir stack-shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled stack shape"));
        }
    }

    private static void assertInvalidLittleTilesRendererTopology(
            Object ownerRenderer, Object blockRenderer, Object tileRenderer,
            Object itemRenderer, int modelId, int blockRenderId, String expectedMessage) {
        try {
            StackIdentity.requirePinnedLittleTilesRendererTopology(
                    ownerRenderer, blockRenderer, tileRenderer, itemRenderer,
                    modelId, blockRenderId);
            fail("Expected LittleTiles renderer topology failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }

    private static void assertInvalidTwilightForestExperiment115PublicShape(
            ItemStack stack, Item expectedItem) {
        try {
            StackIdentity.isExactTwilightForestExperiment115PublicItemListEntry(
                    stack, expectedItem);
            fail("Expected retained Twilight Forest Experiment 115 stack-shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled ItemList stack shape"));
        }
    }

    private static void assertInvalidTwilightForestExperiment115Counts(
            int internalBlockEntries, int publicFoodEntries, String expectedMessage) {
        try {
            StackIdentity.requireExactTwilightForestExperiment115ItemListCounts(
                    internalBlockEntries, publicFoodEntries);
            fail("Expected Twilight Forest Experiment 115 cardinality failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }

    private static void assertCatalogPolicy(
            StackIdentity.CatalogExclusion policy, String registryId, String itemClass,
            String blockClass, String contract, String semanticBucket,
            int expectedCount, int expectedMetadataMask, boolean strictIdentity) {
        assertEquals(registryId, policy.registryId);
        assertEquals(itemClass, policy.runtimeClass);
        assertEquals(blockClass, policy.blockRuntimeClass);
        assertEquals(contract, policy.contract);
        assertEquals(semanticBucket, policy.semanticBucket);
        assertEquals(expectedCount, policy.expectedCount);
        assertEquals(expectedMetadataMask, policy.expectedMetadataMask);
        assertEquals(strictIdentity, policy.strictIdentity);
    }

    private static StackIdentity.CatalogExclusionAudit completeCatalogExclusionAudit() {
        StackIdentity.CatalogExclusionAudit audit =
                completeCatalogExclusionAuditWithoutBuriedPetals();
        for (int metadata = 0; metadata < 16; metadata++) {
            audit.record(StackIdentity.BOTANIA_BURIED_PETALS_WORLD_STATE_VARIANT,
                    new ItemStack(new Item(), 1, metadata));
        }
        return audit;
    }

    private static StackIdentity.CatalogExclusionAudit
    completeCatalogExclusionAuditWithoutBuriedPetals() {
        StackIdentity.CatalogExclusionAudit audit = new StackIdentity.CatalogExclusionAudit();
        for (StackIdentity.CatalogExclusion policy
                : StackIdentity.CATALOG_EXCLUSION_POLICIES) {
            if (policy != StackIdentity.BOTANIA_BURIED_PETALS_WORLD_STATE_VARIANT) {
                audit.record(policy, new ItemStack(new Item(), 1, 0));
            }
        }
        return audit;
    }

    private static void assertInvalidExclusionAudit(
            StackIdentity.CatalogExclusionAudit audit, String... messageParts) {
        try {
            audit.requireExpected();
            fail("Expected ItemList exclusion cardinality failure");
        } catch (IllegalArgumentException expected) {
            for (String messagePart : messageParts) {
                assertTrue(expected.getMessage(), expected.getMessage().contains(messagePart));
            }
        }
    }

    private static void assertInvalidMixedBlockProducerNbt(
            ItemStack stack, Item carrier, String messagePart) {
        try {
            StackIdentity.requireExactMalisisDoorsMixedBlockProducerNbt(
                    stack, carrier,
                    net.minecraft.init.Blocks.stone, net.minecraft.init.Blocks.dirt,
                    3, 5, "fixture");
            fail("Expected exact mixed-block producer NBT failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(messagePart));
        }
    }

    private static void assertInvalidDreamcraftNothingShape(
            ItemStack stack, Item sentinel) {
        try {
            StackIdentity.matchExactCatalogPolicy(
                    stack, sentinel,
                    StackIdentity.DREAMCRAFT_NOTHING_LEGACY_LOOT_BAG_SENTINEL);
            fail("Expected strict Dreamcraft Nothing sentinel stack-shape failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("unmodeled stack shape"));
        }
    }

    private static void assertInvalidGlobalItemListCardinality(
            int raw, int excluded, int retained, int retainedUnique) {
        try {
            StackIdentity.requireExactGlobalItemListCardinality(
                    raw, excluded, retained, retainedUnique);
            fail("Expected pinned global ItemList cardinality failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains(
                            "expected raw=56038, excluded=46, retained=55992, "
                                    + "retainedUnique=55991"));
        }
    }

    private static void assertInvalidThaumicHorizonsIlluminationCounts(
            int baseLightEntries, int focusVariants, int focusMetadataMask,
            String expectedMessage) {
        try {
            StackIdentity.requireExactThaumicHorizonsIlluminationItemListCounts(
                    baseLightEntries, focusVariants, focusMetadataMask);
            fail("Expected Thaumic Horizons illumination cardinality failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }
}
