package com.recipetree.reiexport118.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exact, semantic exemplar repairs for the pinned Multiblock Madness 2 classes. */
public final class Mm2CreativeExemplarRepair {
    private static final String AE2_KEYS = "keys";
    private static final String AE2_AMOUNTS = "amts";
    private static final String AE2_ITEM_COUNT = "ic";
    private static final String AE2_ACTIVE_COLOR = "color";
    private static final long AE2_EXEMPLAR_AMOUNT = 128L;
    private static final Set<String> AE2_EXPECTED_KEYS = Set.of(
            "minecraft:black_dye",
            "minecraft:blue_dye",
            "minecraft:brown_dye",
            "minecraft:cyan_dye",
            "minecraft:gray_dye",
            "minecraft:green_dye",
            "minecraft:light_blue_dye",
            "minecraft:light_gray_dye",
            "minecraft:lime_dye",
            "minecraft:magenta_dye",
            "minecraft:orange_dye",
            "minecraft:pink_dye",
            "minecraft:purple_dye",
            "minecraft:red_dye",
            "minecraft:snowball",
            "minecraft:white_dye",
            "minecraft:yellow_dye"
    );

    private Mm2CreativeExemplarRepair() {
    }

    /** Canonicalizes only the full color-applicator exemplar returned by AE2. */
    public static void canonicalizeAe2ColorApplicator(ItemStack stack) {
        Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.AE2.modId());
        requireExactStack(stack, Mm2DeterminismContract.AE2_COLOR_APPLICATOR_CLASS);
        CompoundTag root = requireTag(stack, "AE2 full color applicator");
        canonicalizeAe2ColorApplicatorTag(root);
    }

    /** Replaces Tombstone's randomized creative-only familiar with one exact cat. */
    public static ItemStack createTombstoneCatExemplar(Item expectedItem, ItemStack stack) {
        Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.TOMBSTONE.modId());
        requireExactStack(stack, Mm2DeterminismContract.TOMBSTONE_RECEPTACLE_CLASS);
        if (stack.getItem() != expectedItem) {
            throw new IllegalStateException(
                    "Tombstone creative familiar stack does not belong to the mixin receiver");
        }
        writeTombstoneCat(stack.getOrCreateTag());
        return stack;
    }

    /** Materializes IF's semantically empty tank map at the exact addNbt seam. */
    public static void ensureIfEmptyTanks(Item expectedItem, ItemStack stack) {
        Mm2DeterminismCompatibility.requireArmed(
                Mm2DeterminismContract.INDUSTRIAL_FOREGOING.modId());
        requireExactStack(stack, Mm2DeterminismContract.INFINITY_BACKPACK_CLASS);
        if (stack.getItem() != expectedItem) {
            throw new IllegalStateException(
                    "Industrial Foregoing backpack stack does not belong to the mixin receiver");
        }
        ensureIfEmptyTanks(requireTag(stack, "Industrial Foregoing infinity backpack"));
    }

    static void canonicalizeAe2ColorApplicatorTag(CompoundTag root) {
        Tag rawKeys = root.get(AE2_KEYS);
        Tag rawAmounts = root.get(AE2_AMOUNTS);
        if (!(rawKeys instanceof ListTag keys)
                || keys.getElementType() != Tag.TAG_COMPOUND
                || !(rawAmounts instanceof LongArrayTag)) {
            throw new IllegalStateException(
                    "AE2 full color applicator must contain compound keys and long amounts");
        }

        long[] amounts = root.getLongArray(AE2_AMOUNTS);
        if (keys.size() != AE2_EXPECTED_KEYS.size() || amounts.length != keys.size()) {
            throw new IllegalStateException(
                    "AE2 full color applicator parallel-array cardinality drift: keys="
                            + keys.size() + ", amounts=" + amounts.length);
        }

        List<Ae2Entry> entries = new ArrayList<>(keys.size());
        Set<String> observedIds = new HashSet<>();
        for (int index = 0; index < keys.size(); index++) {
            CompoundTag key = keys.getCompound(index);
            if (key.size() != 2
                    || !(key.get("id") instanceof StringTag)
                    || !(key.get("#c") instanceof StringTag)
                    || !"ae2:i".equals(key.getString("#c"))) {
                throw new IllegalStateException(
                        "AE2 full color applicator key schema drift at index " + index
                                + ": " + key.getAllKeys());
            }
            String id = key.getString("id");
            if (!AE2_EXPECTED_KEYS.contains(id) || !observedIds.add(id)) {
                throw new IllegalStateException(
                        "AE2 full color applicator contains an unexpected or duplicate key: " + id);
            }
            if (amounts[index] != AE2_EXEMPLAR_AMOUNT) {
                throw new IllegalStateException(
                        "AE2 full color applicator amount drift for " + id
                                + ": expected=" + AE2_EXEMPLAR_AMOUNT
                                + ", actual=" + amounts[index]);
            }
            entries.add(new Ae2Entry(id, key.copy(), amounts[index]));
        }
        if (!observedIds.equals(AE2_EXPECTED_KEYS)) {
            throw new IllegalStateException(
                    "AE2 full color applicator key domain drift: " + observedIds);
        }

        long expectedItemCount = AE2_EXEMPLAR_AMOUNT * AE2_EXPECTED_KEYS.size();
        if (!root.contains(AE2_ITEM_COUNT, Tag.TAG_LONG)
                || root.getLong(AE2_ITEM_COUNT) != expectedItemCount) {
            throw new IllegalStateException(
                    "AE2 full color applicator aggregate item count drift: expected="
                            + expectedItemCount + ", actual=" + root.get(AE2_ITEM_COUNT));
        }

        entries.sort(Comparator.comparing(Ae2Entry::id));
        ListTag canonicalKeys = new ListTag();
        long[] canonicalAmounts = new long[entries.size()];
        for (int index = 0; index < entries.size(); index++) {
            Ae2Entry entry = entries.get(index);
            canonicalKeys.add(entry.key());
            canonicalAmounts[index] = entry.amount();
        }
        root.put(AE2_KEYS, canonicalKeys);
        root.putLongArray(AE2_AMOUNTS, canonicalAmounts);

        CompoundTag white = new CompoundTag();
        white.putString("id", "minecraft:white_dye");
        white.putByte("Count", (byte) 1);
        root.put(AE2_ACTIVE_COLOR, white);
    }

    static void writeTombstoneCat(CompoundTag root) {
        if (root.contains("dead_pet")) {
            throw new IllegalStateException(
                    "Tombstone creative familiar stack was not fresh before deterministic repair");
        }
        CompoundTag familiar = new CompoundTag();
        familiar.putString("id", "minecraft:cat");
        root.put("dead_pet", familiar);
    }

    static void ensureIfEmptyTanks(CompoundTag root) {
        Tag tanks = root.get("Tanks");
        if (tanks == null) {
            root.put("Tanks", new CompoundTag());
            return;
        }
        if (!(tanks instanceof CompoundTag compound) || !compound.isEmpty()) {
            throw new IllegalStateException(
                    "Industrial Foregoing addNbt produced a nonempty or noncompound Tanks tag");
        }
    }

    private static CompoundTag requireTag(ItemStack stack, String label) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            throw new IllegalStateException(label + " did not contain its required NBT");
        }
        return tag;
    }

    private static void requireExactStack(ItemStack stack, String expectedClass) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalStateException(expectedClass + " returned an empty exemplar stack");
        }
        String actualClass = stack.getItem().getClass().getName();
        if (!expectedClass.equals(actualClass)) {
            throw new IllegalStateException(
                    "Exact exemplar target drift: expected=" + expectedClass
                            + ", actual=" + actualClass);
        }
    }

    private record Ae2Entry(String id, CompoundTag key, long amount) {
    }
}
