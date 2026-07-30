package com.recipetree.reiexport118.compat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UpstreamNativeIconContractTest {
    private static final String ITEM_STACK = "net.minecraft.world.item.ItemStack";
    private static final String BLOCK_ITEM = "net.minecraft.world.item.BlockItem";

    private record Audited(
            String type,
            String id,
            String valueClass,
            String itemClass,
            String blockClass
    ) {
        UpstreamNativeIconContract.Identity identity() {
            return new UpstreamNativeIconContract.Identity(
                    new ResourceLocation(type),
                    new ResourceLocation(id),
                    valueClass,
                    itemClass,
                    blockClass
            );
        }
    }

    private static final List<Audited> AUDITED = List.of(
            item("mekanism:bounding_block", BLOCK_ITEM, "mekanism.common.block.BlockBounding"),
            item(
                    "integrateddynamics:invisible_light",
                    BLOCK_ITEM,
                    "org.cyclops.integrateddynamics.block.BlockInvisibleLight"
            ),
            item(
                    "integrateddynamics:block_menril_resin",
                    BLOCK_ITEM,
                    "org.cyclops.integrateddynamics.block.BlockFluidMenrilResin"
            ),
            item(
                    "integrateddynamics:block_liquid_chorus",
                    BLOCK_ITEM,
                    "org.cyclops.integrateddynamics.block.BlockFluidLiquidChorus"
            ),
            item(
                    "ars_nouveau:debug",
                    "com.hollingsworth.arsnouveau.common.items.Debug",
                    null
            ),
            item(
                    "ars_nouveau:light_block",
                    BLOCK_ITEM,
                    "com.hollingsworth.arsnouveau.common.block.LightBlock"
            ),
            item(
                    "ars_nouveau:portal",
                    BLOCK_ITEM,
                    "com.hollingsworth.arsnouveau.common.block.PortalBlock"
            ),
            item(
                    "multiblocked:symbol",
                    "com.lowdragmc.multiblocked.api.block.ItemComponent",
                    "com.lowdragmc.multiblocked.api.block.BlockComponent"
            ),
            item(
                    "multiblocked:dummy_component",
                    "com.lowdragmc.multiblocked.api.block.ItemComponent",
                    "com.lowdragmc.multiblocked.api.block.BlockComponent"
            ),
            item(
                    "mcjtylib:multipart",
                    "mcjty.lib.multipart.MultipartItemBlock",
                    "mcjty.lib.multipart.MultipartBlock"
            ),
            item(
                    "mininggadgets:minerslight",
                    BLOCK_ITEM,
                    "com.direwolf20.mininggadgets.common.blocks.MinersLight"
            ),
            effect("reliquary:pacification"),
            effect("reliquary:cure"),
            item(
                    "ae2:matrix_frame",
                    "appeng.block.AEBaseBlockItem",
                    "appeng.block.spatial.MatrixFrameBlock"
            ),
            item(
                    "ae2:paint",
                    "appeng.block.AEBaseBlockItem",
                    "appeng.block.paint.PaintSplotchesBlock"
            ),
            item(
                    "ae2:cable_bus",
                    "appeng.block.AEBaseBlockItem",
                    "appeng.block.networking.CableBusBlock"
            )
    );

    @Test
    void acceptsExactlyTheSixteenAuditedZeroAlphaRuntimeIdentities() {
        assertEquals(16, AUDITED.size());
        for (Audited audited : AUDITED) {
            UpstreamNativeIconContract.Identity identity = audited.identity();
            UpstreamNativeIconContract.Omission omission =
                    UpstreamNativeIconContract.omission(identity, 0);
            assertNotNull(omission, audited.id());
            assertEquals(identity, omission.identity(), audited.id());
            assertTrue(!omission.reason().isBlank(), audited.id());
        }
    }

    @Test
    void anyVisiblePixelAlwaysKeepsEveryAuditedEntry() {
        for (Audited audited : AUDITED) {
            assertNull(UpstreamNativeIconContract.omission(audited.identity(), 1), audited.id());
        }
    }

    @Test
    void rejectsDriftInEveryIdentityDimensionAndUnknownTransparency() {
        Audited audited = AUDITED.get(0);
        assertNull(UpstreamNativeIconContract.omission(identity(
                "minecraft:fluid",
                audited.id(),
                audited.valueClass(),
                audited.itemClass(),
                audited.blockClass()
        ), 0));
        assertNull(UpstreamNativeIconContract.omission(identity(
                audited.type(),
                "mekanism:advanced_bounding_block",
                audited.valueClass(),
                audited.itemClass(),
                audited.blockClass()
        ), 0));
        assertNull(UpstreamNativeIconContract.omission(identity(
                audited.type(),
                audited.id(),
                "example.UnreviewedValue",
                audited.itemClass(),
                audited.blockClass()
        ), 0));
        assertNull(UpstreamNativeIconContract.omission(identity(
                audited.type(),
                audited.id(),
                audited.valueClass(),
                "example.UnreviewedItem",
                audited.blockClass()
        ), 0));
        assertNull(UpstreamNativeIconContract.omission(identity(
                audited.type(),
                audited.id(),
                audited.valueClass(),
                audited.itemClass(),
                "example.UnreviewedBlock"
        ), 0));
        assertNull(UpstreamNativeIconContract.omission(identity(
                "minecraft:item",
                "example:transparent_item",
                ITEM_STACK,
                BLOCK_ITEM,
                "example.TransparentBlock"
        ), 0));
    }

    @Test
    void rejectsNegativePixelCountsAndIncompleteRequiredIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UpstreamNativeIconContract.omission(AUDITED.get(0).identity(), -1)
        );
        assertThrows(
                NullPointerException.class,
                () -> new UpstreamNativeIconContract.Identity(
                        new ResourceLocation("minecraft:item"),
                        new ResourceLocation("example:test"),
                        null,
                        null,
                        null
                )
        );
    }

    private static Audited item(String id, String itemClass, String blockClass) {
        return new Audited("minecraft:item", id, ITEM_STACK, itemClass, blockClass);
    }

    private static Audited effect(String id) {
        return new Audited(
                "jeed:jei_plugin_jei_compat_mobeffectinstance",
                id,
                "net.minecraft.world.effect.MobEffectInstance",
                null,
                null
        );
    }

    private static UpstreamNativeIconContract.Identity identity(
            String type,
            String id,
            String valueClass,
            String itemClass,
            String blockClass
    ) {
        return new UpstreamNativeIconContract.Identity(
                new ResourceLocation(type),
                new ResourceLocation(id),
                valueClass,
                itemClass,
                blockClass
        );
    }
}
