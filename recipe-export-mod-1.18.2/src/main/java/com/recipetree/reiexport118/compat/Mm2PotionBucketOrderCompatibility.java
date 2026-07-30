package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.alchemy.Potion;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Adds the missing registry-ID tie-break to IE's translated-name potion ordering. */
public final class Mm2PotionBucketOrderCompatibility {
    private static final AtomicInteger INVOCATIONS = new AtomicInteger();

    private Mm2PotionBucketOrderCompatibility() {
    }

    public static void sort(List<Potion> potions, Comparator<Potion> upstreamComparator) {
        Mm2DeterminismCompatibility.requireArmed(
                Mm2DeterminismContract.IMMERSIVE_ENGINEERING.modId());
        if (potions == null || upstreamComparator == null) {
            throw new IllegalArgumentException(
                    "IE potion-bucket ordering requires the upstream list and comparator");
        }
        potions.sort(upstreamComparator.thenComparing(
                Mm2PotionBucketOrderCompatibility::registryId));
        int invocation = INVOCATIONS.incrementAndGet();
        ReiExportMod.LOGGER.warn(
                "[reiexport] Canonicalized exact MM2 Immersive Engineering potion-bucket "
                        + "creative ordering: invocation={}, potions={}, tieBreak=registry-id",
                invocation, potions.size());
    }

    private static String registryId(Potion potion) {
        if (potion == null) {
            throw new IllegalStateException(
                    "IE potion-bucket creative list contains a null potion");
        }
        ResourceLocation id = potion.getRegistryName();
        if (id == null) {
            throw new IllegalStateException(
                    "IE potion-bucket creative list contains an unregistered potion");
        }
        return id.toString();
    }
}
