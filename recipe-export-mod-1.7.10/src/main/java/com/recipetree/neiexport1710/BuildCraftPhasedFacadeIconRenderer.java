package com.recipetree.neiexport1710;

import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic catalog icons for GTNH's closed vanished-castle-door facade family.
 *
 * <p>BuildCraft's owner inventory renderer produces a fully transparent image for this otherwise
 * addressable one-state facades. The graph identities and NBT remain valid. This adapter pins the
 * four canonical metadata identities registered by Twilight Forest and renders each facade's sole
 * material as the stable catalog proxy. It does not provide a generic fallback for malformed
 * facades or broaden the policy to other NBT values.</p>
 */
final class BuildCraftPhasedFacadeIconRenderer {
    static final String CONTRACT =
            "buildcraft-vanished-castle-door-facade-material-catalog-proxy-v3";
    static final int EXPECTED_ITEM_ICONS = 4;

    private static final String ITEM_CLASS = "buildcraft.transport.ItemFacade";
    private static final String STATE_CLASS = "buildcraft.transport.ItemFacade$FacadeState";
    private static final String MATERIAL_ID = "TwilightForest:tile.CastleDoorVanished";
    private static final String RENDERER_CLASS =
            "buildcraft.transport.render.FacadeItemRenderer";
    private static final Map<String, Integer> TARGET_METADATA;

    static {
        Map<String, Integer> targets = new LinkedHashMap<String, Integer>();
        targets.put(targetKey("e65353f73fe4eeabd2c073e0f572d7a8e7aefd880fca3eb521f703843e2791da"), 0);
        targets.put(targetKey("b4ff3c448d5f4c31cdd1c0be34273f30571587b6d83a0f36fa14615bf731d5cc"), 1);
        targets.put(targetKey("946febade4cae60c11635ae1dc639d9adb10ba383b235491ff77b3f90a31f567"), 2);
        targets.put(targetKey("631ba5ce6d759b85e8529d7e129951fdb555f0a28859e21f10765036dab544ec"), 3);
        TARGET_METADATA = Collections.unmodifiableMap(targets);
    }

    private final ItemStack proxyStack;

    private BuildCraftPhasedFacadeIconRenderer(ItemStack proxyStack) {
        this.proxyStack = proxyStack;
    }

    static boolean isPinnedTarget(StackIdentity identity) {
        return identity != null && TARGET_METADATA.containsKey(identity.key);
    }

    private static String targetKey(String nbtDigest) {
        return "item|BuildCraft|Transport:pipeFacade|meta=0|nbt=" + nbtDigest;
    }

    static BuildCraftPhasedFacadeIconRenderer create(StackIdentity identity) throws Exception {
        if (!isPinnedTarget(identity)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: BuildCraft facade adapter received a non-target identity");
        }
        ItemStack stack = identity.stack;
        Integer expectedMetadata = TARGET_METADATA.get(identity.key);
        if (expectedMetadata == null) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned BuildCraft facade metadata contract is absent");
        }
        if (stack == null || stack.getItem() == null
                || !ITEM_CLASS.equals(stack.getItem().getClass().getName())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned BuildCraft facade item class drifted");
        }
        IItemRenderer owner = MinecraftForgeClient.getItemRenderer(
                stack, IItemRenderer.ItemRenderType.INVENTORY);
        if (owner == null || !RENDERER_CLASS.equals(owner.getClass().getName())
                || !owner.handleRenderType(stack, IItemRenderer.ItemRenderType.INVENTORY)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned BuildCraft facade owner-renderer binding drifted");
        }

        Class<?> itemClass = Class.forName(ITEM_CLASS, false, stack.getItem().getClass().getClassLoader());
        Method getStates = itemClass.getDeclaredMethod("getFacadeStates", ItemStack.class);
        if (!Modifier.isStatic(getStates.getModifiers()) || !getStates.getReturnType().isArray()) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: BuildCraft getFacadeStates topology drifted");
        }
        getStates.setAccessible(true);
        Object statesValue = getStates.invoke(null, stack);
        if (!(statesValue instanceof Object[])) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: BuildCraft integration facade returned no state array");
        }
        Object[] states = (Object[]) statesValue;
        if (states.length != 1 || states[0] == null
                || !STATE_CLASS.equals(states[0].getClass().getName())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned BuildCraft integration facade state topology drifted; count="
                            + states.length);
        }
        Field blockField = states[0].getClass().getField("block");
        Field metadataField = states[0].getClass().getField("metadata");
        Field hollowField = states[0].getClass().getField("hollow");
        Object blockValue = blockField.get(states[0]);
        int metadata = metadataField.getInt(states[0]);
        boolean hollow = hollowField.getBoolean(states[0]);
        if (!(blockValue instanceof Block) || metadata != expectedMetadata.intValue() || hollow) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned BuildCraft material facade state drifted; block="
                            + (blockValue == null ? "<null>" : blockValue.getClass().getName())
                            + ", metadata=" + metadata + ", hollow=" + hollow);
        }
        Block block = (Block) blockValue;
        Item material = Item.getItemFromBlock(block);
        GameRegistry.UniqueIdentifier blockId = GameRegistry.findUniqueIdentifierFor(block);
        GameRegistry.UniqueIdentifier materialId = material == null
                ? null : GameRegistry.findUniqueIdentifierFor(material);
        String blockKey = blockId == null ? null : blockId.modId + ":" + blockId.name;
        String materialKey = materialId == null
                ? null : materialId.modId + ":" + materialId.name;
        if (material == null || !MATERIAL_ID.equals(blockKey) || !MATERIAL_ID.equals(materialKey)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned BuildCraft facade material identity drifted; block="
                            + (blockKey == null ? "<unregistered>" : blockKey)
                            + ", item="
                            + (materialKey == null ? "<unregistered>" : materialKey));
        }
        return new BuildCraftPhasedFacadeIconRenderer(new ItemStack(material, 1, metadata));
    }

    void draw() {
        GuiContainerManager.drawItem(0, 0, proxyStack, false, "");
    }
}
