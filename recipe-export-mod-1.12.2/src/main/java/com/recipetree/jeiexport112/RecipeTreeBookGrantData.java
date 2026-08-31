package com.recipetree.jeiexport112;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Persistent one-shot state for the Recipe Tree book granted with a newly created world.
 *
 * <p>The marker is armed only by Forge's new-spawn event (or the equivalent initial load signal
 * for vanilla debug worlds). Loading an existing world therefore never creates a pending grant
 * merely because Recipe Tree was installed later.</p>
 */
public final class RecipeTreeBookGrantData extends WorldSavedData {
    static final String DATA_ID = "jeiexport_recipe_tree_book_grant";

    private boolean pending;
    private boolean granted;

    public RecipeTreeBookGrantData(String name) {
        super(name);
    }

    boolean isPending() {
        return pending && !granted;
    }

    boolean isGranted() {
        return granted;
    }

    void armForNewWorld() {
        if (granted) return;
        pending = true;
        markDirty();
    }

    void markGranted() {
        pending = false;
        granted = true;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        pending = nbt.getBoolean("pending");
        granted = nbt.getBoolean("granted");
        if (granted) pending = false;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("pending", pending);
        nbt.setBoolean("granted", granted);
        return nbt;
    }
}
