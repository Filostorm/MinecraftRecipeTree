package com.recipetree.neiexport1710;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DisplayNameResolverTest {
    @Test
    public void resolvesOneAspectWithoutPlayerKnowledge() throws Exception {
        NBTTagCompound root = aspectTag("aer", 2);
        String name = DisplayNameResolver.resolveItemAspect(
                root,
                new DisplayNameResolver.AspectLookup() {
                    @Override
                    public String displayName(String tag) {
                        return "Air";
                    }
                },
                "Aspect");
        assertEquals("Aspect:Air", name);
    }

    @Test(expected = ExportFailure.class)
    public void rejectsUnknownAspectInsteadOfFallingBack() throws Exception {
        DisplayNameResolver.resolveItemAspect(
                aspectTag("missing", 2),
                new DisplayNameResolver.AspectLookup() {
                    @Override
                    public String displayName(String tag) {
                        return null;
                    }
                },
                "Aspect");
    }

    @Test(expected = ExportFailure.class)
    public void rejectsMultipleAspectEntries() throws Exception {
        NBTTagCompound root = aspectTag("aer", 2);
        root.getTagList("Aspects", 10).appendTag(aspectEntry("ignis", 2));
        DisplayNameResolver.resolveItemAspect(
                root,
                new DisplayNameResolver.AspectLookup() {
                    @Override
                    public String displayName(String tag) {
                        return tag;
                    }
                },
                "Aspect");
    }

    @Test
    public void resolvesExactGregTechForestryScannedSaplingCustomName() throws Exception {
        NBTTagCompound root = scannedSaplingTag();
        assertEquals(
                DisplayNameResolver.FORESTRY_SCANNED_SAPLING_CANONICAL_NBT,
                NbtCanonicalizer.canonical(root));
        assertEquals(
                "2ef7c2d8cc838349c0e3f86e385f092334f4f432cde0d20c2c29af8d6435ca31",
                Naming.sha256(NbtCanonicalizer.canonical(root)));
        assertEquals(
                "Scanned Sapling",
                DisplayNameResolver.resolveForestryScannedSaplingName(root));
    }

    @Test(expected = ExportFailure.class)
    public void rejectsGregTechForestryScannedSaplingNbtDrift() throws Exception {
        NBTTagCompound root = scannedSaplingTag();
        root.setByte("unexpected", (byte) 1);
        DisplayNameResolver.resolveForestryScannedSaplingName(root);
    }

    @Test
    public void resolvesExactGregTechForestryScannedPollenCustomName() throws Exception {
        NBTTagCompound root = scannedPollenTag();
        assertEquals(
                DisplayNameResolver.FORESTRY_SCANNED_POLLEN_CANONICAL_NBT,
                NbtCanonicalizer.canonical(root));
        assertEquals(
                "0357c93060885ca4cb111bf921d3f6d9deb31eb0891f92218fe2d306b8b8dfae",
                Naming.sha256(NbtCanonicalizer.canonical(root)));
        assertEquals(
                "Scanned Pollen",
                DisplayNameResolver.resolveForestryScannedPollenName(root));
    }

    @Test(expected = ExportFailure.class)
    public void rejectsGregTechForestryScannedPollenNbtDrift() throws Exception {
        NBTTagCompound root = scannedPollenTag();
        root.setByte("unexpected", (byte) 1);
        DisplayNameResolver.resolveForestryScannedPollenName(root);
    }

    @Test(expected = ExportFailure.class)
    public void rejectsSaplingEnvelopeAsScannedPollen() throws Exception {
        DisplayNameResolver.resolveForestryScannedPollenName(scannedSaplingTag());
    }

    private static NBTTagCompound aspectTag(String key, int amount) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList aspects = new NBTTagList();
        aspects.appendTag(aspectEntry(key, amount));
        root.setTag("Aspects", aspects);
        return root;
    }

    private static NBTTagCompound aspectEntry(String key, int amount) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("key", key);
        entry.setInteger("amount", amount);
        return entry;
    }

    private static NBTTagCompound scannedSaplingTag() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", "Scanned Sapling");
        root.setTag("display", display);
        return root;
    }

    private static NBTTagCompound scannedPollenTag() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", "Scanned Pollen");
        root.setTag("display", display);
        return root;
    }
}
