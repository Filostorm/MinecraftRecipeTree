package com.recipetree.jeiexport112;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ItemNbtIdentityTest {
    @Test
    public void techRebornCellsIncludeCanonicalFluidNbt() {
        NBTTagCompound first = new NBTTagCompound();
        first.setString("FluidName", "potion");
        first.setString("Potion", "minecraft:healing");
        NBTTagCompound reordered = new NBTTagCompound();
        reordered.setString("Potion", "minecraft:healing");
        reordered.setString("FluidName", "potion");

        String refined = ItemNbtIdentity.refine(
                "techreborn:dynamiccell:potion;", "techreborn:dynamiccell", first);
        assertEquals(refined, ItemNbtIdentity.refine(
                "techreborn:dynamiccell:potion;", "techreborn:dynamiccell", reordered));

        NBTTagCompound harming = new NBTTagCompound();
        harming.setString("FluidName", "potion");
        harming.setString("Potion", "minecraft:harming");
        assertNotEquals(refined, ItemNbtIdentity.refine(
                "techreborn:dynamiccell:potion;", "techreborn:dynamiccell", harming));
    }

    @Test
    public void voxPondsTokensUseNbtButUnrelatedItemsKeepHelperIdentity() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Value", 25);
        assertNotEquals(
                "aoa3:vox_ponds_tokens",
                ItemNbtIdentity.refine(
                        "aoa3:vox_ponds_tokens", "aoa3:vox_ponds_tokens", tag));
        assertEquals(
                "example:cell",
                ItemNbtIdentity.refine("example:cell", "example:cell", tag));
    }
}
