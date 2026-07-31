package com.recipetree.reiexport118.compat;

import java.util.Arrays;

/**
 * Exact constructor contracts used by The One Probe 5.1.2 for its three probe helmets.
 * Keeping this data independent of TOP lets the compatibility mixin remain optional while
 * still refusing to mutate a class whose semantics have drifted.
 */
public final class TopArmorMaterialContract {
    private TopArmorMaterialContract() {
    }

    public static Target requireExact(
            String internalName,
            int durability,
            int[] damageReduction,
            int enchantability,
            String soundId,
            float toughness
    ) {
        Target target = Target.forInternalName(internalName);
        if (target == null) {
            throw new IllegalStateException("unknown null-repair TopArmorMaterial name: " + internalName);
        }
        if (!target.matches(durability, damageReduction, enchantability, soundId, toughness)) {
            throw new IllegalStateException(
                    "drifted TopArmorMaterial tuple for " + internalName
                            + "; expected=" + target.describe()
                            + "; actual={durability=" + durability
                            + ", damageReduction=" + Arrays.toString(damageReduction)
                            + ", enchantability=" + enchantability
                            + ", sound=" + soundId
                            + ", toughness=" + toughness + "}"
            );
        }
        return target;
    }

    public enum Target {
        DIAMOND(
                "diamond_helmet_probe",
                33,
                new int[]{3, 6, 8, 3},
                10,
                "minecraft:item.armor.equip_diamond",
                2.0F
        ),
        GOLD(
                "gold_helmet_probe",
                7,
                new int[]{1, 3, 5, 2},
                25,
                "minecraft:item.armor.equip_gold",
                0.0F
        ),
        IRON(
                "iron_helmet_probe",
                15,
                new int[]{2, 5, 6, 2},
                9,
                "minecraft:item.armor.equip_iron",
                0.0F
        );

        private final String internalName;
        private final int durability;
        private final int[] damageReduction;
        private final int enchantability;
        private final String soundId;
        private final float toughness;

        Target(
                String internalName,
                int durability,
                int[] damageReduction,
                int enchantability,
                String soundId,
                float toughness
        ) {
            this.internalName = internalName;
            this.durability = durability;
            this.damageReduction = damageReduction;
            this.enchantability = enchantability;
            this.soundId = soundId;
            this.toughness = toughness;
        }

        public String internalName() {
            return internalName;
        }

        private boolean matches(
                int actualDurability,
                int[] actualDamageReduction,
                int actualEnchantability,
                String actualSoundId,
                float actualToughness
        ) {
            return durability == actualDurability
                    && Arrays.equals(damageReduction, actualDamageReduction)
                    && enchantability == actualEnchantability
                    && soundId.equals(actualSoundId)
                    && Float.compare(toughness, actualToughness) == 0;
        }

        private String describe() {
            return "{durability=" + durability
                    + ", damageReduction=" + Arrays.toString(damageReduction)
                    + ", enchantability=" + enchantability
                    + ", sound=" + soundId
                    + ", toughness=" + toughness + "}";
        }

        private static Target forInternalName(String internalName) {
            for (Target target : values()) {
                if (target.internalName.equals(internalName)) {
                    return target;
                }
            }
            return null;
        }
    }
}
