package com.recipetree.jeiexport112;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Exact NBT identity repairs for item families whose HEI subtype helpers omit meaningful NBT. */
final class ItemNbtIdentity {
    private static final String TECH_REBORN_CELL = "techreborn:dynamiccell";
    private static final String VOX_PONDS_TOKENS = "aoa3:vox_ponds_tokens";

    private ItemNbtIdentity() {
    }

    static String refine(String uid, String resourceId, Object ingredient) {
        if (!(ingredient instanceof ItemStack)) return uid;
        ItemStack stack = (ItemStack) ingredient;
        return refine(uid, resourceId, stack.getTagCompound());
    }

    static String refine(String uid, String resourceId, NBTTagCompound tag) {
        if (uid == null || resourceId == null || tag == null || tag.isEmpty()) return uid;
        String normalizedId = resourceId.trim().toLowerCase(java.util.Locale.ROOT);
        if (!TECH_REBORN_CELL.equals(normalizedId) && !VOX_PONDS_TOKENS.equals(normalizedId)) {
            return uid;
        }
        return uid + "|nbt=" + NbtCanonicalizer.canonical(tag);
    }
}
