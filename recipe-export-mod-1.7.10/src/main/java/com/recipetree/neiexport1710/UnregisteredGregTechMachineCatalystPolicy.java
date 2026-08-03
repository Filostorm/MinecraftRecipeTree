package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.MetaTileEntityIDs;
import gregtech.common.blocks.ItemMachines;
import net.minecraft.item.ItemStack;

/**
 * Excludes one non-addressable NEI furnace catalyst that targets an unregistered GregTech
 * machine-metadata gap. GTNH 2.8.4 exposes metadata 31087 through NEI's furnace catalyst
 * expansion even though neither GregTech's enum nor its runtime meta-tile registry owns that ID.
 * The stack therefore has no machine semantics or renderable owner and must not enter the public
 * graph as a real item.
 */
final class UnregisteredGregTechMachineCatalystPolicy {
    static final String CONTRACT =
            "gtnh-2.8.4-unregistered-gregtech-machine-furnace-catalyst-exclusion-v1";
    static final String CATEGORY_ID = "gtnh:332529e70abb7d6d783af3920199a141";
    static final String HANDLER_ID = "codechicken.nei.recipe.FurnaceRecipeHandler";
    static final String CANONICAL_KEY =
            "item|gregtech:gt.blockmachines|meta=31087|nbt=-";
    static final int METADATA = 31087;
    static final int EXPECTED_EXCLUSIONS = 1;

    private UnregisteredGregTechMachineCatalystPolicy() {}

    static boolean shouldExclude(
            HandlerCategoryPlan plan,
            PositionedStack positioned,
            int catalystIndex) throws ExportFailure {
        if (positioned == null || positioned.items == null) {
            return false;
        }

        ItemStack target = null;
        for (ItemStack stack : positioned.items) {
            if (stack == null) {
                continue;
            }
            StackIdentity identity = StackIdentity.of(stack);
            if (!CANONICAL_KEY.equals(identity.key)) {
                continue;
            }
            if (target != null || positioned.items.length != 1) {
                throw new ExportFailure(
                        "RECIPE_SEMANTICS",
                        CONTRACT + " target is no longer one exact catalyst alternative at index "
                                + catalystIndex);
            }
            target = stack;
        }
        if (target == null) {
            return false;
        }

        if (!CATEGORY_ID.equals(plan.categoryId) || !HANDLER_ID.equals(plan.handlerId)) {
            throw new ExportFailure(
                    "RECIPE_SEMANTICS",
                    CONTRACT + " target moved outside its pinned furnace category: categoryId="
                            + plan.categoryId + ", handlerId=" + plan.handlerId);
        }
        if (!(target.getItem() instanceof ItemMachines)
                || target.stackSize != 1
                || target.getItemDamage() != METADATA
                || target.hasTagCompound()) {
            throw new ExportFailure(
                    "RECIPE_SEMANTICS",
                    CONTRACT + " target runtime topology drifted at catalyst index "
                            + catalystIndex);
        }
        if (ItemMachines.getMetaTileEntity(target) != null
                || METADATA < 0
                || METADATA >= GregTechAPI.METATILEENTITIES.length
                || GregTechAPI.METATILEENTITIES[METADATA] != null) {
            throw new ExportFailure(
                    "RECIPE_SEMANTICS",
                    CONTRACT + " target acquired a registered runtime meta-tile entity");
        }
        for (MetaTileEntityIDs id : MetaTileEntityIDs.values()) {
            if (id.ID == METADATA) {
                throw new ExportFailure(
                        "RECIPE_SEMANTICS",
                        CONTRACT + " target acquired a declared MetaTileEntityIDs owner "
                                + id.name());
            }
        }
        return true;
    }
}
