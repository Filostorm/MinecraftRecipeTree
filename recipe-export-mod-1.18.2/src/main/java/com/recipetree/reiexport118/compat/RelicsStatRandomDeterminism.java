package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import com.recipetree.reiexport118.mixin.ReiExportMixinConfigPlugin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/** Supplies deterministic entropy to Relics while preserving its native stack initialization. */
public final class RelicsStatRandomDeterminism {
    private static final String SEED_DOMAIN = "mrt-relic-creative-exemplar-v1\0";
    private static final AtomicLong RUNTIME_APPLICATIONS = new AtomicLong();

    private RelicsStatRandomDeterminism() {
    }

    public static Random randomFor(
            Item owner,
            ItemStack stack,
            String abilityId,
            String statId
    ) {
        // Relics can construct stacks while Forge constructs mods in parallel, before our @Mod
        // constructor. Mixin has already made and audited this irreversible target selection.
        ReiExportMixinConfigPlugin.requireExactRelicsStatRandomSelection(
                FMLLoader.getGamePath());
        if (owner == null || stack == null || stack.getItem() != owner) {
            throw new IllegalStateException(
                    "Relics randomizeStat seam drift: stack does not belong to the target relic");
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(owner);
        if (itemId == null || ForgeRegistries.ITEMS.getValue(itemId) != owner
                || !"relics".equals(itemId.getNamespace())) {
            throw new IllegalStateException(
                    "Relics randomizeStat owner lacks an exact registered relics identity");
        }
        Random random = seededRandom(itemId.toString(), abilityId, statId);
        RUNTIME_APPLICATIONS.incrementAndGet();
        return random;
    }

    /** Proves that Mixin merged and dispatched through the override, not merely selected it. */
    public static void requireObservedRuntimeApplication() {
        long applications = RUNTIME_APPLICATIONS.get();
        if (applications <= 0) {
            throw new IllegalStateException(
                    "Relics deterministic randomizeStat override was selected but never "
                            + "executed; "
                            + "refusing an export with unproven creative exemplar stability");
        }
        ReiExportMod.LOGGER.info(
                "[reiexport] Verified runtime application of the deterministic Relics "
                        + "randomizeStat override: invocations={}",
                applications);
    }

    static Random seededRandom(String itemId, String abilityId, String statId) {
        ResourceLocation parsedItemId = ResourceLocation.tryParse(
                requireIdentity(itemId, "item"));
        if (parsedItemId == null || !"relics".equals(parsedItemId.getNamespace())) {
            throw new IllegalStateException(
                    "Relics randomizeStat item identity is outside the relics namespace: "
                            + itemId);
        }
        requireIdentity(abilityId, "ability");
        requireIdentity(statId, "stat");
        return new Random(seed(parsedItemId.toString(), abilityId, statId));
    }

    static long seed(String itemId, String abilityId, String statId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(SEED_DOMAIN.getBytes(StandardCharsets.UTF_8));
            digest.update(itemId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(abilityId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(statId.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireIdentity(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Relics randomizeStat supplied an empty " + label + " identity");
        }
        return value;
    }
}
