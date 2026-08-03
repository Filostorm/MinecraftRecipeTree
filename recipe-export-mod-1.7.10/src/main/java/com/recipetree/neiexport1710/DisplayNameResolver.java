package com.recipetree.neiexport1710;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;

import java.lang.reflect.Method;

/** Resolves display names without consulting mutable player progression. */
final class DisplayNameResolver {
    static final String ITEM_ASPECT_CLASS =
            "com.djgiannuzz.thaumcraftneiplugin.items.ItemAspect";
    static final String FORESTRY_GERMLING_CLASS =
            "forestry.arboriculture.items.ItemGermlingGE";
    static final String FORESTRY_SCANNED_SAPLING_NAME_CONTRACT =
            "gregtech-forestry-scanned-sapling-explicit-custom-name-v1";
    static final String FORESTRY_SCANNED_SAPLING_NAME = "Scanned Sapling";
    static final String FORESTRY_SCANNED_SAPLING_CANONICAL_NBT =
            "10:{1:7:display10:{1:4:Name8:17:\"Scanned Sapling\"}}";
    static final String FORESTRY_SCANNED_SAPLING_CANONICAL_KEY =
            "item|Forestry:sapling|meta=0|nbt="
                    + "2ef7c2d8cc838349c0e3f86e385f092334f4f432cde0d20c2c29af8d6435ca31";
    static final int EXPECTED_FORESTRY_SCANNED_SAPLING_NAMES = 1;
    static final String FORESTRY_SCANNED_POLLEN_NAME_CONTRACT =
            "gregtech-forestry-scanned-pollen-explicit-custom-name-v1";
    static final String FORESTRY_SCANNED_POLLEN_NAME = "Scanned Pollen";
    static final String FORESTRY_SCANNED_POLLEN_CANONICAL_NBT =
            "10:{1:7:display10:{1:4:Name8:16:\"Scanned Pollen\"}}";
    static final String FORESTRY_SCANNED_POLLEN_CANONICAL_KEY =
            "item|Forestry:pollenFertile|meta=0|nbt="
                    + "0357c93060885ca4cb111bf921d3f6d9deb31eb0891f92218fe2d306b8b8dfae";
    static final int EXPECTED_FORESTRY_SCANNED_POLLEN_NAMES = 1;
    private static final String ASPECT_CLASS = "thaumcraft.api.aspects.Aspect";

    interface AspectLookup {
        String displayName(String tag) throws Exception;
    }

    static final class Result {
        final String name;
        final boolean knowledgeIndependentAspect;
        final boolean adaptedForestryScannedSapling;
        final boolean adaptedForestryScannedPollen;

        Result(
                String name,
                boolean knowledgeIndependentAspect,
                boolean adaptedForestryScannedSapling,
                boolean adaptedForestryScannedPollen) {
            this.name = name;
            this.knowledgeIndependentAspect = knowledgeIndependentAspect;
            this.adaptedForestryScannedSapling = adaptedForestryScannedSapling;
            this.adaptedForestryScannedPollen = adaptedForestryScannedPollen;
        }
    }

    private DisplayNameResolver() {
    }

    static Result resolve(
            StackIdentity identity,
            boolean authorizeGregTechForestryScannedSapling,
            boolean authorizeGregTechForestryScannedPollen)
            throws ExportFailure {
        boolean scannedSapling =
                FORESTRY_SCANNED_SAPLING_CANONICAL_KEY.equals(identity.key);
        boolean scannedPollen =
                FORESTRY_SCANNED_POLLEN_CANONICAL_KEY.equals(identity.key);
        if (authorizeGregTechForestryScannedSapling != scannedSapling) {
            throw new ExportFailure(
                    "ITEM_IDENTITY",
                    FORESTRY_SCANNED_SAPLING_NAME_CONTRACT
                            + " source authorization mismatch for " + identity.key
                            + "; authorized=" + authorizeGregTechForestryScannedSapling);
        }
        if (authorizeGregTechForestryScannedPollen != scannedPollen) {
            throw new ExportFailure(
                    "ITEM_IDENTITY",
                    FORESTRY_SCANNED_POLLEN_NAME_CONTRACT
                            + " source authorization mismatch for " + identity.key
                            + "; authorized=" + authorizeGregTechForestryScannedPollen);
        }
        if (identity.isFluid()) {
            return required(identity.fluidDisplayName, false, false, false, identity.key);
        }
        ItemStack stack = identity.stack;
        if (scannedSapling) {
            String itemClass = stack.getItem().getClass().getName();
            if (!FORESTRY_GERMLING_CLASS.equals(itemClass)) {
                throw new ExportFailure(
                        "ITEM_IDENTITY",
                        FORESTRY_SCANNED_SAPLING_NAME_CONTRACT
                                + " owner class drifted; expected " + FORESTRY_GERMLING_CLASS
                                + ", got " + itemClass);
            }
            return required(
                    resolveForestryScannedSaplingName(stack.getTagCompound()),
                    false,
                    true,
                    false,
                    identity.key);
        }
        if (scannedPollen) {
            String itemClass = stack.getItem().getClass().getName();
            if (!FORESTRY_GERMLING_CLASS.equals(itemClass)) {
                throw new ExportFailure(
                        "ITEM_IDENTITY",
                        FORESTRY_SCANNED_POLLEN_NAME_CONTRACT
                                + " owner class drifted; expected " + FORESTRY_GERMLING_CLASS
                                + ", got " + itemClass);
            }
            return required(
                    resolveForestryScannedPollenName(stack.getTagCompound()),
                    false,
                    false,
                    true,
                    identity.key);
        }
        if (!ITEM_ASPECT_CLASS.equals(stack.getItem().getClass().getName())) {
            try {
                return required(stack.getDisplayName(), false, false, false, identity.key);
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                throw new ExportFailure("ITEM_IDENTITY",
                        "display name failed for " + identity.key, error);
            }
        }
        try {
            final ClassLoader loader = stack.getItem().getClass().getClassLoader();
            final Class<?> aspectClass = Class.forName(ASPECT_CLASS, false, loader);
            final Method getAspect = aspectClass.getMethod("getAspect", String.class);
            final Method getName = aspectClass.getMethod("getName");
            if (!java.lang.reflect.Modifier.isStatic(getAspect.getModifiers())
                    || java.lang.reflect.Modifier.isStatic(getName.getModifiers())
                    || getAspect.getReturnType() != aspectClass
                    || getName.getReturnType() != String.class
                    || getName.getParameterTypes().length != 0) {
                throw new ExportFailure("ITEM_IDENTITY",
                        "Thaumcraft Aspect lookup/display-name reflection contract drifted");
            }
            String name = resolveItemAspect(stack.getTagCompound(), new AspectLookup() {
                @Override
                public String displayName(String tag) throws Exception {
                    Object aspect = getAspect.invoke(null, tag);
                    if (aspect == null) {
                        return null;
                    }
                    return (String) getName.invoke(aspect);
                }
            }, StatCollector.translateToLocal("item.itemaspect.aspectprefix"));
            return required(name, true, false, false, identity.key);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY",
                    "knowledge-independent ItemAspect display-name resolution failed for "
                            + identity.key, error);
        }
    }

    static String resolveItemAspect(NBTTagCompound root, AspectLookup lookup, String localizedPrefix)
            throws Exception {
        if (root == null || !root.hasKey("Aspects", 9)) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "ItemAspect requires an Aspects NBT list");
        }
        NBTTagList aspects = root.getTagList("Aspects", 10);
        if (aspects.tagCount() != 1) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "ItemAspect requires exactly one compound in Aspects; got "
                            + aspects.tagCount());
        }
        NBTTagCompound encoded = aspects.getCompoundTagAt(0);
        if (!encoded.hasKey("key", 8) || !encoded.hasKey("amount", 3)) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "ItemAspect entry requires string key and integer amount");
        }
        String tag = encoded.getString("key").trim();
        int amount = encoded.getInteger("amount");
        if (tag.isEmpty() || amount <= 0) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "ItemAspect entry has invalid key/amount: " + tag + "/" + amount);
        }
        String aspectName = lookup.displayName(tag);
        if (aspectName == null || aspectName.trim().isEmpty()) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "ItemAspect references unknown Thaumcraft aspect tag " + tag);
        }
        String prefix = localizedPrefix == null ? null : localizedPrefix.trim();
        if (prefix == null || prefix.isEmpty()
                || "item.itemaspect.aspectprefix".equals(prefix)) {
            throw new ExportFailure("ITEM_IDENTITY",
                    "ItemAspect localized display-name prefix is unavailable");
        }
        return prefix + ":" + aspectName.trim();
    }

    /**
     * Resolves GregTech's exact synthetic scanner output without invoking Forestry's species-based
     * owner name. The stack intentionally has no genome: GregTech supplies its complete public name
     * through vanilla's custom display envelope, but Forestry 4.10.17 dereferences the absent tree
     * species before ItemStack can apply that envelope.
     */
    static String resolveForestryScannedSaplingName(NBTTagCompound root)
            throws ExportFailure {
        return resolveExactForestryScannerName(
                root,
                FORESTRY_SCANNED_SAPLING_NAME_CONTRACT,
                FORESTRY_SCANNED_SAPLING_CANONICAL_NBT,
                FORESTRY_SCANNED_SAPLING_NAME);
    }

    /** Exact counterpart for GregTech's synthetic genome-free fertile-pollen scanner output. */
    static String resolveForestryScannedPollenName(NBTTagCompound root)
            throws ExportFailure {
        return resolveExactForestryScannerName(
                root,
                FORESTRY_SCANNED_POLLEN_NAME_CONTRACT,
                FORESTRY_SCANNED_POLLEN_CANONICAL_NBT,
                FORESTRY_SCANNED_POLLEN_NAME);
    }

    private static String resolveExactForestryScannerName(
            NBTTagCompound root,
            String contract,
            String expectedCanonicalNbt,
            String expectedName) throws ExportFailure {
        if (root == null) {
            throw new ExportFailure(
                    "ITEM_IDENTITY", contract + " requires exact root NBT");
        }
        String canonical = NbtCanonicalizer.canonical(root);
        if (!expectedCanonicalNbt.equals(canonical)) {
            throw new ExportFailure(
                    "ITEM_IDENTITY",
                    contract + " NBT envelope drifted; canonicalSha256="
                            + Naming.sha256(canonical));
        }
        if (!root.hasKey("display", 10)) {
            throw new ExportFailure(
                    "ITEM_IDENTITY", contract + " requires a compound display tag");
        }
        NBTTagCompound display = root.getCompoundTag("display");
        if (!display.hasKey("Name", 8)
                || !expectedName.equals(display.getString("Name"))) {
            throw new ExportFailure(
                    "ITEM_IDENTITY", contract + " requires display.Name=" + expectedName);
        }
        return expectedName;
    }

    private static Result required(
            String value,
            boolean aspect,
            boolean forestryScannedSapling,
            boolean forestryScannedPollen,
            String key)
            throws ExportFailure {
        String plain = Naming.plainText(value);
        if (plain == null || plain.trim().isEmpty()) {
            throw new ExportFailure("ITEM_IDENTITY", "blank display name for " + key);
        }
        return new Result(plain, aspect, forestryScannedSapling, forestryScannedPollen);
    }
}
