package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Canonicalizes only exporter-owned copies of the two audited CoFH item roots. */
public final class Mm2EntryCanonicalization {
    static final String FLUID_RESERVOIR_ID = "thermal:fluid_reservoir";
    static final String XP_CRYSTAL_ID = "thermal:xp_crystal";
    private static final ResourceLocation ITEM_ENTRY_TYPE =
            new ResourceLocation("minecraft", "item");
    private static final Set<String> TARGETS = Set.of(FLUID_RESERVOIR_ID, XP_CRYSTAL_ID);
    private static final Set<String> LOGGED_NORMALIZATIONS = ConcurrentHashMap.newKeySet();

    private Mm2EntryCanonicalization() {
    }

    /** Copies, REI-normalizes, and then canonicalizes the exporter identity copy. */
    public static EntryStack<?> canonicalIdentityCopy(EntryStack<?> original) {
        if (original == null || original.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot canonicalize an empty REI exporter identity stack");
        }
        EntryStack<?> copied = original.copy();
        if (copied == null || copied.isEmpty()) {
            throw new IllegalStateException(
                    "REI EntryStack.copy() returned an empty exporter identity stack");
        }
        EntryStack<?> copy = copied.normalize();
        if (copy == null || copy.isEmpty()) {
            throw new IllegalStateException(
                    "REI EntryStack.normalize() returned an empty exporter identity stack");
        }
        canonicalizeOwnedStack(copy, original.getValue());
        return copy;
    }

    /** Canonicalizes an already-copied stack used by the standalone catalog renderer. */
    public static void normalizeExporterOwnedEntry(EntryStack<?> copy) {
        if (copy == null || copy.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot canonicalize an empty exporter-owned REI stack");
        }
        canonicalizeOwnedStack(copy, null);
    }

    private static void canonicalizeOwnedStack(EntryStack<?> copy, Object originalValue) {
        if (!Mm2DeterminismCompatibility.isLifecycleArmed()) {
            return;
        }
        String identifier = copy.getIdentifier().toString();
        if (!TARGETS.contains(identifier)) {
            return;
        }
        Mm2RegistryRepairContract.requireArmed();
        if (!ITEM_ENTRY_TYPE.equals(copy.getType().getId())) {
            throw new IllegalStateException(
                    "MM2 CoFH canonicalization target is not a minecraft:item entry: "
                            + copy.getType().getId() + " " + identifier);
        }
        if (!(copy.getValue() instanceof ItemStack itemStack)) {
            throw new IllegalStateException(
                    "MM2 CoFH canonicalization target does not contain an ItemStack: "
                            + copy.getValue().getClass().getName() + " " + identifier);
        }
        if (originalValue != null && itemStack == originalValue) {
            throw new IllegalStateException(
                    "MM2 CoFH identity copy aliases its mutable source ItemStack: " + identifier);
        }

        CompoundTag previous = itemStack.getTag();
        CompoundTag canonical = canonicalRoot(identifier, previous);
        if (canonical != previous) {
            itemStack.setTag(canonical);
        }
        if (canonical != null && canonical.isEmpty()
                && LOGGED_NORMALIZATIONS.add(identifier)) {
            ReiExportMod.LOGGER.info(
                    "[reiexport] Canonicalized CoFH exporter-owned root tag to exact empty "
                            + "compound id={} materializedAbsentRoot={}",
                    identifier, previous == null);
        }
    }

    static CompoundTag canonicalRoot(String identifier, CompoundTag current) {
        if (TARGETS.contains(identifier) && current == null) {
            return new CompoundTag();
        }
        return current;
    }
}
