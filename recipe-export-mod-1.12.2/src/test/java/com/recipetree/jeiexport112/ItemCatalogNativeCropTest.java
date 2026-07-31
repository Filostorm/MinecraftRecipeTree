package com.recipetree.jeiexport112;

import mezz.jei.api.ingredients.VanillaTypes;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class ItemCatalogNativeCropTest {
    @BeforeClass
    public static void initializeVanillaRegistries() {
        Bootstrap.register();
    }

    @Test
    public void exactCropCopiesOnlyCenterPixelsWithoutResampling() {
        BufferedImage source = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, 0xff000000 | (x << 8) | y);
            }
        }

        BufferedImage cropped = ItemCatalog.exactCrop(source, 8, 8, 16, 16);

        assertEquals(16, cropped.getWidth());
        assertEquals(16, cropped.getHeight());
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(source.getRGB(x + 8, y + 8), cropped.getRGB(x, y));
            }
        }
    }

    @Test
    public void exactCropRejectsOutOfBoundsGeometry() {
        BufferedImage source = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        try {
            ItemCatalog.exactCrop(source, 17, 8, 16, 16);
            fail("Expected an out-of-bounds crop to fail");
        } catch (IllegalArgumentException expected) {
            org.junit.Assert.assertTrue(expected.getMessage().contains("invalid exact crop"));
        }
    }

    @Test
    public void catalogItemRenderUsesCountOneCopyWithoutMutatingRecipeStack() {
        Item item = new Item();
        ItemStack source = new ItemStack(item, 2, 7);
        NBTTagCompound sourceTag = new NBTTagCompound();
        sourceTag.setString("variant", "source");
        source.setTagCompound(sourceTag);

        ItemStack rendered = ItemCatalog.catalogRenderIngredient(VanillaTypes.ITEM, source);

        assertNotSame(source, rendered);
        assertEquals(2, source.getCount());
        assertEquals(1, rendered.getCount());
        assertSame(item, rendered.getItem());
        assertEquals(source.getItemDamage(), rendered.getItemDamage());
        assertEquals(source.getTagCompound(), rendered.getTagCompound());
        assertNotSame(source.getTagCompound(), rendered.getTagCompound());

        rendered.getTagCompound().setString("variant", "render-copy");
        assertEquals("source", source.getTagCompound().getString("variant"));
    }
}
