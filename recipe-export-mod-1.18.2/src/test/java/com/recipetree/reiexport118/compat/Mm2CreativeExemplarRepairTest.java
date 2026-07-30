package com.recipetree.reiexport118.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2CreativeExemplarRepairTest {
    private static final List<String> AE2_IDS = List.of(
            "minecraft:black_dye", "minecraft:blue_dye", "minecraft:brown_dye",
            "minecraft:cyan_dye", "minecraft:gray_dye", "minecraft:green_dye",
            "minecraft:light_blue_dye", "minecraft:light_gray_dye",
            "minecraft:lime_dye", "minecraft:magenta_dye", "minecraft:orange_dye",
            "minecraft:pink_dye", "minecraft:purple_dye", "minecraft:red_dye",
            "minecraft:snowball", "minecraft:white_dye", "minecraft:yellow_dye");

    @Test
    void ae2SortsParallelKeysAndAmountsAndSelectsWhite() {
        List<String> shuffled = new ArrayList<>(AE2_IDS);
        Collections.rotate(shuffled, 7);
        CompoundTag root = ae2Tag(shuffled, 128L);

        Mm2CreativeExemplarRepair.canonicalizeAe2ColorApplicatorTag(root);

        ListTag keys = root.getList("keys", Tag.TAG_COMPOUND);
        assertEquals(AE2_IDS, keys.stream()
                .map(tag -> ((CompoundTag) tag).getString("id"))
                .toList());
        assertTrue(keys.stream().allMatch(
                tag -> "ae2:i".equals(((CompoundTag) tag).getString("#c"))));
        long[] expectedAmounts = new long[AE2_IDS.size()];
        java.util.Arrays.fill(expectedAmounts, 128L);
        assertArrayEquals(expectedAmounts, root.getLongArray("amts"));
        CompoundTag color = root.getCompound("color");
        assertEquals("minecraft:white_dye", color.getString("id"));
        assertEquals((byte) 1, color.getByte("Count"));

        Mm2CreativeExemplarRepair.canonicalizeAe2ColorApplicatorTag(root);
        assertEquals(AE2_IDS, root.getList("keys", Tag.TAG_COMPOUND).stream()
                .map(tag -> ((CompoundTag) tag).getString("id"))
                .toList());
    }

    @Test
    void ae2FailsClosedOnPairOrDomainDrift() {
        CompoundTag wrongAmount = ae2Tag(AE2_IDS, 128L);
        long[] amounts = wrongAmount.getLongArray("amts");
        amounts[3] = 127L;
        wrongAmount.putLongArray("amts", amounts);
        assertThrows(IllegalStateException.class,
                () -> Mm2CreativeExemplarRepair
                        .canonicalizeAe2ColorApplicatorTag(wrongAmount));

        List<String> duplicate = new ArrayList<>(AE2_IDS);
        duplicate.set(0, duplicate.get(1));
        CompoundTag wrongDomain = ae2Tag(duplicate, 128L);
        assertThrows(IllegalStateException.class,
                () -> Mm2CreativeExemplarRepair
                        .canonicalizeAe2ColorApplicatorTag(wrongDomain));
    }

    @Test
    void tombstoneWritesExactlyOneNonSpellcasterCatAndRefusesOverwrite() {
        CompoundTag root = new CompoundTag();
        Mm2CreativeExemplarRepair.writeTombstoneCat(root);

        CompoundTag familiar = root.getCompound("dead_pet");
        assertEquals(1, familiar.size());
        assertEquals("minecraft:cat", familiar.getString("id"));
        assertFalse(familiar.contains("is_spellcaster"));
        assertThrows(IllegalStateException.class,
                () -> Mm2CreativeExemplarRepair.writeTombstoneCat(root));
    }

    @Test
    void industrialForegoingMaterializesOnlyAnExactEmptyTankMap() {
        CompoundTag root = new CompoundTag();
        Mm2CreativeExemplarRepair.ensureIfEmptyTanks(root);
        assertTrue(root.getCompound("Tanks").isEmpty());

        Mm2CreativeExemplarRepair.ensureIfEmptyTanks(root);
        CompoundTag nonempty = new CompoundTag();
        nonempty.putInt("Amount", 1);
        root.put("Tanks", nonempty);
        assertThrows(IllegalStateException.class,
                () -> Mm2CreativeExemplarRepair.ensureIfEmptyTanks(root));

        root.put("Tanks", IntTag.valueOf(0));
        assertThrows(IllegalStateException.class,
                () -> Mm2CreativeExemplarRepair.ensureIfEmptyTanks(root));
    }

    private static CompoundTag ae2Tag(List<String> ids, long amount) {
        CompoundTag root = new CompoundTag();
        ListTag keys = new ListTag();
        long[] amounts = new long[ids.size()];
        for (int index = 0; index < ids.size(); index++) {
            CompoundTag key = new CompoundTag();
            key.putString("id", ids.get(index));
            key.putString("#c", "ae2:i");
            keys.add(key);
            amounts[index] = amount;
        }
        root.put("keys", keys);
        root.putLongArray("amts", amounts);
        root.putLong("ic", amount * ids.size());
        return root;
    }
}
