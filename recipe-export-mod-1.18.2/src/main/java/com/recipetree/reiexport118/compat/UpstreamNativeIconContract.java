package com.recipetree.reiexport118.compat;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;

/**
 * Audited MM2 entry forms whose upstream-native render intentionally has no visible pixels.
 *
 * <p>The contract is deliberately pinned to the complete runtime identity available at the
 * native-render boundary: entry type, identifier, value class, item class, and backing block
 * class. Identifier-only or class-family matching would conceal model, registration, or REI
 * adapter drift. Unknown transparent renders remain publication-blocking.</p>
 */
public final class UpstreamNativeIconContract {
    /** Nullable item/block classes are part of the exact identity for non-item REI entries. */
    public record Identity(
            ResourceLocation typeId,
            ResourceLocation identifier,
            String valueClass,
            String itemClass,
            String blockClass
    ) {
        public Identity {
            Objects.requireNonNull(typeId, "typeId");
            Objects.requireNonNull(identifier, "identifier");
            Objects.requireNonNull(valueClass, "valueClass");
        }
    }

    public record Omission(Identity identity, String reason) {
        public Omission {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(reason, "reason");
        }
    }

    private static final String ITEM_STACK = "net.minecraft.world.item.ItemStack";
    private static final String BLOCK_ITEM = "net.minecraft.world.item.BlockItem";
    private static final ResourceLocation ITEM_TYPE = new ResourceLocation("minecraft", "item");
    private static final ResourceLocation JEED_EFFECT_TYPE =
            new ResourceLocation("jeed", "jei_plugin_jei_compat_mobeffectinstance");

    private static final Map<Identity, String> AUDITED = Map.ofEntries(
            auditedItem(
                    "mekanism:bounding_block",
                    BLOCK_ITEM,
                    "mekanism.common.block.BlockBounding",
                    "invisible multiblock proxy with an upstream zero-geometry item model"
            ),
            auditedItem(
                    "integrateddynamics:invisible_light",
                    BLOCK_ITEM,
                    "org.cyclops.integrateddynamics.block.BlockInvisibleLight",
                    "explicitly invisible light block with empty collision and a transparent upstream model"
            ),
            auditedItem(
                    "integrateddynamics:block_menril_resin",
                    BLOCK_ITEM,
                    "org.cyclops.integrateddynamics.block.BlockFluidMenrilResin",
                    "technical fluid backing BlockItem whose upstream model contains only a particle texture"
            ),
            auditedItem(
                    "integrateddynamics:block_liquid_chorus",
                    BLOCK_ITEM,
                    "org.cyclops.integrateddynamics.block.BlockFluidLiquidChorus",
                    "technical fluid backing BlockItem whose upstream model contains only a particle texture"
            ),
            auditedItem(
                    "ars_nouveau:debug",
                    "com.hollingsworth.arsnouveau.common.items.Debug",
                    null,
                    "debug-only item whose upstream generated model declares no texture layers"
            ),
            auditedItem(
                    "ars_nouveau:light_block",
                    BLOCK_ITEM,
                    "com.hollingsworth.arsnouveau.common.block.LightBlock",
                    "invisible light block whose upstream item model resolves to a zero-alpha empty texture"
            ),
            auditedItem(
                    "ars_nouveau:portal",
                    BLOCK_ITEM,
                    "com.hollingsworth.arsnouveau.common.block.PortalBlock",
                    "technical portal block whose upstream item model resolves to a zero-alpha empty texture"
            ),
            auditedItem(
                    "multiblocked:symbol",
                    "com.lowdragmc.multiblocked.api.block.ItemComponent",
                    "com.lowdragmc.multiblocked.api.block.BlockComponent",
                    "dynamic component with no upstream standalone item model or default placed-state geometry"
            ),
            auditedItem(
                    "multiblocked:dummy_component",
                    "com.lowdragmc.multiblocked.api.block.ItemComponent",
                    "com.lowdragmc.multiblocked.api.block.BlockComponent",
                    "technical dynamic component with no upstream standalone item model"
            ),
            auditedItem(
                    "mcjtylib:multipart",
                    "mcjty.lib.multipart.MultipartItemBlock",
                    "mcjty.lib.multipart.MultipartBlock",
                    "multipart container whose upstream dynamic model has no geometry without world parts"
            ),
            auditedItem(
                    "mininggadgets:minerslight",
                    BLOCK_ITEM,
                    "com.direwolf20.mininggadgets.common.blocks.MinersLight",
                    "invisible placed-light block whose upstream item texture is fully transparent"
            ),
            Map.entry(
                    new Identity(
                            JEED_EFFECT_TYPE,
                            new ResourceLocation("reliquary", "pacification"),
                            "net.minecraft.world.effect.MobEffectInstance",
                            null,
                            null
                    ),
                    "JEED effect entry whose exact upstream Reliquary mob-effect texture is fully transparent"
            ),
            Map.entry(
                    new Identity(
                            JEED_EFFECT_TYPE,
                            new ResourceLocation("reliquary", "cure"),
                            "net.minecraft.world.effect.MobEffectInstance",
                            null,
                            null
                    ),
                    "JEED effect entry whose exact upstream Reliquary mob-effect texture is fully transparent"
            ),
            auditedItem(
                    "ae2:matrix_frame",
                    "appeng.block.AEBaseBlockItem",
                    "appeng.block.spatial.MatrixFrameBlock",
                    "technical spatial frame whose upstream inventory model contains no visible geometry"
            ),
            auditedItem(
                    "ae2:paint",
                    "appeng.block.AEBaseBlockItem",
                    "appeng.block.paint.PaintSplotchesBlock",
                    "world-state paint container whose upstream inventory model is empty"
            ),
            auditedItem(
                    "ae2:cable_bus",
                    "appeng.block.AEBaseBlockItem",
                    "appeng.block.networking.CableBusBlock",
                    "world-state cable-bus container whose upstream inventory model is empty"
            )
    );

    private UpstreamNativeIconContract() {
    }

    /** Returns an audited omission only when the exact native render has zero visible pixels. */
    public static Omission omission(Identity identity, int visiblePixels) {
        if (visiblePixels < 0) {
            throw new IllegalArgumentException("visiblePixels must be non-negative");
        }
        if (visiblePixels != 0) {
            return null;
        }
        String reason = AUDITED.get(Objects.requireNonNull(identity, "identity"));
        return reason == null ? null : new Omission(identity, reason);
    }

    private static Map.Entry<Identity, String> auditedItem(
            String identifier,
            String itemClass,
            String blockClass,
            String reason
    ) {
        return Map.entry(
                new Identity(
                        ITEM_TYPE,
                        new ResourceLocation(identifier),
                        ITEM_STACK,
                        itemClass,
                        blockClass
                ),
                reason
        );
    }
}
