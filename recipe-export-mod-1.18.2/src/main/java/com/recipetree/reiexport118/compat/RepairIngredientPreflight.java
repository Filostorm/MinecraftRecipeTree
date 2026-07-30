package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public final class RepairIngredientPreflight {
    private RepairIngredientPreflight() {
    }

    /**
     * Mirrors the two item-registry traversals REI performs while constructing its default repair
     * displays. A null repair ingredient violates the effective ArmorMaterial/Tier contracts and
     * would otherwise abort REI plugin registration partway through, producing an incomplete
     * recipe catalog. Both contracts are checked in one O(item-registry) pass.
     */
    public static void validateBeforeReiRegistration() {
        int itemCount = 0;
        int armorItemCount = 0;
        int tieredItemCount = 0;
        List<String> failures = new ArrayList<>();

        for (Item item : ForgeRegistries.ITEMS) {
            itemCount++;
            boolean isArmorItem = item instanceof ArmorItem;
            boolean isTieredItem = item instanceof TieredItem;
            if (!isArmorItem && !isTieredItem) {
                continue;
            }
            String label = itemLabel(item);

            if (isArmorItem) {
                armorItemCount++;
                validateArmorMaterial(label, (ArmorItem) item, failures);
            }
            if (isTieredItem) {
                tieredItemCount++;
                validateToolTier(label, (TieredItem) item, failures);
            }
        }

        if (!failures.isEmpty()) {
            for (String failure : failures) {
                ReiExportMod.LOGGER.error("[reiexport] Repair-ingredient preflight failure: {}", failure);
            }
            throw new IllegalStateException(
                    "Repair-ingredient preflight rejected " + failures.size()
                            + " item contract(s); REI registration would be incomplete"
            );
        }

        ReiExportMod.LOGGER.info(
                "[reiexport] Repair-ingredient preflight passed for {} ArmorItem and {} TieredItem entries across {} registry items",
                armorItemCount,
                tieredItemCount,
                itemCount
        );
    }

    private static void validateArmorMaterial(String label, ArmorItem armorItem, List<String> failures) {
        try {
            Ingredient repairIngredient = armorItem.getMaterial().getRepairIngredient();
            if (repairIngredient == null) {
                failures.add(label + " (contract=ArmorMaterial, material="
                        + armorItem.getMaterial().getClass().getName() + ")");
            }
        } catch (RuntimeException exception) {
            failures.add(label + " (contract=ArmorMaterial, repair lookup threw "
                    + describeException(exception) + ")");
        }
    }

    private static void validateToolTier(String label, TieredItem tieredItem, List<String> failures) {
        try {
            Tier tier = tieredItem.getTier();
            if (tier == null) {
                failures.add(label + " (contract=Tier, tier=null, itemClass="
                        + tieredItem.getClass().getName() + ")");
                return;
            }
            Ingredient repairIngredient = tier.getRepairIngredient();
            if (repairIngredient == null) {
                failures.add(label + " (contract=Tier, tier=" + tier.getClass().getName() + ")");
            }
        } catch (RuntimeException exception) {
            failures.add(label + " (contract=Tier, repair lookup threw "
                    + describeException(exception) + ")");
        }
    }

    private static String itemLabel(Item item) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        return itemId == null ? item.getClass().getName() : itemId.toString();
    }

    private static String describeException(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getName() + (message == null ? "" : ": " + message);
    }
}
