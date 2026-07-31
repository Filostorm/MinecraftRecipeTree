package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TopArmorMaterialContractTest {
    @Test
    void acceptsTheOneProbeFiveOneTwoExactTuples() {
        assertEquals(
                TopArmorMaterialContract.Target.DIAMOND,
                TopArmorMaterialContract.requireExact(
                        "diamond_helmet_probe", 33, new int[]{3, 6, 8, 3}, 10,
                        "minecraft:item.armor.equip_diamond", 2.0F
                )
        );
        assertEquals(
                TopArmorMaterialContract.Target.GOLD,
                TopArmorMaterialContract.requireExact(
                        "gold_helmet_probe", 7, new int[]{1, 3, 5, 2}, 25,
                        "minecraft:item.armor.equip_gold", 0.0F
                )
        );
        assertEquals(
                TopArmorMaterialContract.Target.IRON,
                TopArmorMaterialContract.requireExact(
                        "iron_helmet_probe", 15, new int[]{2, 5, 6, 2}, 9,
                        "minecraft:item.armor.equip_iron", 0.0F
                )
        );
    }

    @Test
    void rejectsUnknownNullRepairMaterialNames() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> TopArmorMaterialContract.requireExact(
                        "future_probe_helmet", 33, new int[]{3, 6, 8, 3}, 10,
                        "minecraft:item.armor.equip_diamond", 2.0F
                )
        );
        assertTrue(exception.getMessage().contains("unknown null-repair"));
    }

    @Test
    void rejectsTupleDriftInsteadOfGuessing() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> TopArmorMaterialContract.requireExact(
                        "diamond_helmet_probe", 33, new int[]{3, 6, 8, 4}, 10,
                        "minecraft:item.armor.equip_diamond", 2.0F
                )
        );
        assertTrue(exception.getMessage().contains("drifted TopArmorMaterial tuple"));
    }

    @Test
    void rejectsSoundAndToughnessDrift() {
        assertThrows(
                IllegalStateException.class,
                () -> TopArmorMaterialContract.requireExact(
                        "gold_helmet_probe", 7, new int[]{1, 3, 5, 2}, 25,
                        "minecraft:item.armor.equip_iron", 0.0F
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> TopArmorMaterialContract.requireExact(
                        "iron_helmet_probe", 15, new int[]{2, 5, 6, 2}, 9,
                        "minecraft:item.armor.equip_iron", 1.0F
                )
        );
    }
}
