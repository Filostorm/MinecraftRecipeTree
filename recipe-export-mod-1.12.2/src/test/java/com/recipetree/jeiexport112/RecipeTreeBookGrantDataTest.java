package com.recipetree.jeiexport112;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RecipeTreeBookGrantDataTest {
    @Test
    public void newWorldGrantIsOneShotAndPersists() {
        RecipeTreeBookGrantData data = new RecipeTreeBookGrantData("test");
        assertFalse(data.isPending());
        assertFalse(data.isGranted());

        data.armForNewWorld();
        assertTrue(data.isPending());

        NBTTagCompound armedTag = data.writeToNBT(new NBTTagCompound());
        RecipeTreeBookGrantData restored = new RecipeTreeBookGrantData("test");
        restored.readFromNBT(armedTag);
        assertTrue(restored.isPending());

        restored.markGranted();
        NBTTagCompound grantedTag = restored.writeToNBT(new NBTTagCompound());
        RecipeTreeBookGrantData reloaded = new RecipeTreeBookGrantData("test");
        reloaded.readFromNBT(grantedTag);
        assertFalse(reloaded.isPending());
        assertTrue(reloaded.isGranted());

        reloaded.armForNewWorld();
        assertFalse(reloaded.isPending());
        assertTrue(reloaded.isGranted());
    }

    @Test
    public void grantedStateWinsOverMalformedPendingState() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("pending", true);
        tag.setBoolean("granted", true);

        RecipeTreeBookGrantData data = new RecipeTreeBookGrantData("test");
        data.readFromNBT(tag);

        assertFalse(data.isPending());
        assertTrue(data.isGranted());
    }

    @Test
    public void markerDetectionRequiresTheExplicitBookFlag() {
        assertFalse(RecipeTreeBook.hasMarker(null));
        assertFalse(RecipeTreeBook.hasMarker(new NBTTagCompound()));

        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("jeiexportRecipeTreeBook", true);
        assertTrue(RecipeTreeBook.hasMarker(tag));
    }

    @Test
    public void bookFactoryCreatesAValidMarkedVanillaWrittenBook() {
        Bootstrap.register();

        ItemStack book = RecipeTreeBook.createBook();

        assertSame(Items.WRITTEN_BOOK, book.getItem());
        assertTrue(RecipeTreeBook.isRecipeTreeBook(book));
        assertEquals("Recipe Tree Book", book.getTagCompound().getString("title"));
        assertEquals("Filostorm", book.getTagCompound().getString("author"));
        assertEquals(2, book.getTagCompound().getTagList("pages", 8).tagCount());
    }
}
