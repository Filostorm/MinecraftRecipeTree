package com.recipetree.neiexport1710;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ExportJobDiagnosticTest {
    @Test
    public void graphIdentityFailureCarriesTheExactTraversalAndSafeStackContext() {
        ItemStack stack = new ItemStack(new Item(), 6, 5631);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("privateFixturePayload", "must-not-be-serialized");
        stack.setTagCompound(tag);
        IllegalArgumentException cause = new IllegalArgumentException(
                "ITEM_IDENTITY: item is absent from the namespaced item registry");

        ExportFailure failure = ExportJob.graphAlternativeIdentityFailure(
                "gtnh:1096b9bd56fbee8260f0514c13b0e9ef",
                "Macerator Recycling", "gt.recipe.macerator",
                "gregtech.nei.GTNEIDefaultHandler", 3580, "output", 0, 2,
                null, stack, cause);

        assertEquals("ITEM_IDENTITY", failure.code);
        assertSame(cause, failure.getCause());
        String message = failure.getMessage();
        assertTrue(message, message.startsWith(
                "ITEM_IDENTITY: recipe graph alternative canonicalization failed"));
        assertTrue(message, message.contains(
                "categoryId=gtnh:1096b9bd56fbee8260f0514c13b0e9ef"));
        assertTrue(message, message.contains("categoryTitle=Macerator Recycling"));
        assertTrue(message, message.contains("handlerId=gt.recipe.macerator"));
        assertTrue(message,
                message.contains("handlerClass=gregtech.nei.GTNEIDefaultHandler"));
        assertTrue(message, message.contains("sourceIndex=3580"));
        assertTrue(message, message.contains("role=output"));
        assertTrue(message, message.contains("slotIndex=0"));
        assertTrue(message, message.contains("alternativeIndex=2"));
        assertTrue(message, message.contains("semanticId=<none>"));
        assertTrue(message, message.contains("registryId=<unregistered>"));
        assertTrue(message, message.contains("stackSize=6"));
        assertTrue(message, message.contains("metadata=5631"));
        assertTrue(message, message.contains("nbt=sha256:"));
        assertTrue(message,
                message.contains("causeType=java.lang.IllegalArgumentException"));
        assertTrue(message, message.contains(
                "causeMessage=ITEM_IDENTITY: item is absent from the namespaced item registry"));
        assertFalse(message, message.contains("privateFixturePayload"));
        assertFalse(message, message.contains("must-not-be-serialized"));
    }
}
