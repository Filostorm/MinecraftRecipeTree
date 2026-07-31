package com.recipetree.reiexport118.compat;

/**
 * Exact Delightful 2.6 constructor contract for the self-regenerating Living Knife tier.
 * Delightful's original supplier returns null even though Tier requires a non-null Ingredient;
 * the knife itself rejects anvil repair and regenerates durability over time.
 */
public final class DelightfulLivingTierContract {
    public static final String TIER_CLASS = "net.brdle.delightful.common.item.DelightfulTiers";
    public static final String ITEM_ID = "delightful:living_knife";

    private DelightfulLivingTierContract() {
    }

    public static void requireExact(
            String enumName,
            int ordinal,
            int level,
            int uses,
            float speed,
            float attackDamageBonus,
            int enchantability
    ) {
        if (!"LIVING".equals(enumName)) {
            throw new IllegalStateException("unknown null-repair Delightful tier: " + enumName);
        }
        if (ordinal != 27
                || level != 2
                || uses != 192
                || Float.compare(speed, 6.0F) != 0
                || Float.compare(attackDamageBonus, 2.0F) != 0
                || enchantability != 18) {
            throw new IllegalStateException(
                    "drifted DelightfulTiers.LIVING tuple; expected="
                            + "{ordinal=27, level=2, uses=192, speed=6.0, attackDamageBonus=2.0, enchantability=18}"
                            + "; actual={ordinal=" + ordinal
                            + ", level=" + level
                            + ", uses=" + uses
                            + ", speed=" + speed
                            + ", attackDamageBonus=" + attackDamageBonus
                            + ", enchantability=" + enchantability + "}"
            );
        }
    }
}
