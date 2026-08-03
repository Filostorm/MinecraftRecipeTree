package com.recipetree.neiexport1710;

import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class NbtCanonicalizerTest {
    @Test
    public void compoundInsertionOrderDoesNotAffectIdentity() {
        NBTTagCompound left = new NBTTagCompound();
        left.setString("zeta", "last");
        left.setInteger("alpha", 7);

        NBTTagCompound right = new NBTTagCompound();
        right.setInteger("alpha", 7);
        right.setString("zeta", "last");

        assertEquals(NbtCanonicalizer.canonical(left), NbtCanonicalizer.canonical(right));
    }

    @Test
    public void nestedListCompoundsUseRecursiveCanonicalOrdering() {
        NBTTagCompound leftElement = new NBTTagCompound();
        leftElement.setString("b", "two");
        leftElement.setString("a", "one");
        NBTTagList leftList = new NBTTagList();
        leftList.appendTag(leftElement);

        NBTTagCompound rightElement = new NBTTagCompound();
        rightElement.setString("a", "one");
        rightElement.setString("b", "two");
        NBTTagList rightList = new NBTTagList();
        rightList.appendTag(rightElement);

        assertEquals(NbtCanonicalizer.canonical(leftList), NbtCanonicalizer.canonical(rightList));
        assertEquals(1, leftList.tagCount());
        assertEquals(1, rightList.tagCount());
    }

    @Test
    public void listOrderRemainsSemanticallySignificant() {
        NBTTagList left = new NBTTagList();
        left.appendTag(stringCompound("first"));
        left.appendTag(stringCompound("second"));
        NBTTagList right = new NBTTagList();
        right.appendTag(stringCompound("second"));
        right.appendTag(stringCompound("first"));

        assertNotEquals(NbtCanonicalizer.canonical(left), NbtCanonicalizer.canonical(right));
    }

    @Test
    public void byteArrayContentsRemainSemanticallySignificant() {
        NBTTagByteArray left = new NBTTagByteArray(new byte[] {1, 2});
        NBTTagByteArray right = new NBTTagByteArray(new byte[] {3, 4});

        assertNotEquals(NbtCanonicalizer.canonical(left),
                NbtCanonicalizer.canonical(right));
    }

    private static NBTTagCompound stringCompound(String value) {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setString("value", value);
        return compound;
    }
}
