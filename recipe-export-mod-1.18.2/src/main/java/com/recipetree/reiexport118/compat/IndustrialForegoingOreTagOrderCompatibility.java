package com.recipetree.reiexport118.compat;

import com.buuz135.industrial.fluid.OreTitaniumFluidAttributes;
import com.mojang.logging.LogUtils;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.tags.ITagManager;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/** Applies and audits the exact MM2 Industrial Foregoing ore-recipe ordering repair. */
public final class IndustrialForegoingOreTagOrderCompatibility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean ACTIVATION_LOGGED = new AtomicBoolean();
    private static final AtomicLong VERIFIED_INVOCATIONS = new AtomicLong();
    private static final AtomicInteger LAST_TOTAL_TAGS = new AtomicInteger();

    private IndustrialForegoingOreTagOrderCompatibility() {
    }

    public static Stream<TagKey<Item>> canonicalTagNames(ITagManager<Item> tagManager) {
        try {
            Mm2DeterminismCompatibility.requireArmed(
                    IndustrialForegoingOreTagOrderContract.MOD_ID);
            if (tagManager == null) {
                throw new IllegalStateException(
                        "Industrial Foregoing JEICustomPlugin received a null item-tag manager");
            }
            Stream<TagKey<Item>> source = tagManager.getTagNames();
            if (source == null) {
                throw new IllegalStateException(
                        "Industrial Foregoing ITagManager.getTagNames returned null");
            }
            if (source.isParallel()) {
                throw new IllegalStateException(
                        "Industrial Foregoing ITagManager.getTagNames returned an unexpected "
                                + "parallel stream");
            }

            List<TagKey<Item>> sourceTags;
            try (source) {
                sourceTags = source.toList();
            }
            IndustrialForegoingOreTagOrderContract.CanonicalOrder<TagKey<Item>> canonical =
                    IndustrialForegoingOreTagOrderContract.canonicalize(
                            sourceTags,
                            key -> key.location().toString(),
                            key -> OreTitaniumFluidAttributes.isValid(key.location()));

            long invocation = VERIFIED_INVOCATIONS.incrementAndGet();
            LAST_TOTAL_TAGS.set(canonical.values().size());
            if (ACTIVATION_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn(
                        "[reiexport] Activated exact MM2 Industrial Foregoing ore-recipe "
                                + "tag ordering: source=JEICustomPlugin.registerRecipes, "
                                + "totalItemTags={}, verifiedRawTags={}, canonicalFirst={}, "
                                + "canonicalLast={}, inputAlreadyCanonical={}",
                        canonical.values().size(),
                        canonical.validRawTagIds().size(),
                        canonical.validRawTagIds().get(0),
                        canonical.validRawTagIds().get(
                                canonical.validRawTagIds().size() - 1),
                        canonical.inputAlreadyCanonical());
            } else {
                LOGGER.debug(
                        "[reiexport] Reverified exact MM2 Industrial Foregoing ore-recipe "
                                + "tag ordering: invocation={}, totalItemTags={}, "
                                + "verifiedRawTags={}, inputAlreadyCanonical={}",
                        invocation,
                        canonical.values().size(),
                        canonical.validRawTagIds().size(),
                        canonical.inputAlreadyCanonical());
            }
            return canonical.values().stream();
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.error(
                    "[reiexport] MM2 Industrial Foregoing ore-recipe tag-order repair failed; "
                            + "no unsorted fallback stream was returned",
                    failure);
            throw failure;
        }
    }

    /** Fails export publication if the pinned registration seam was never exercised. */
    public static void requireObservedBeforePublication() {
        long invocations = VERIFIED_INVOCATIONS.get();
        if (invocations == 0L) {
            String message = "MM2 Industrial Foregoing ore-recipe tag-order seam was not "
                    + "observed before export publication; no fallback publication is allowed";
            LOGGER.error("[reiexport] {}", message);
            throw new IllegalStateException(message);
        }
        LOGGER.info(
                "[reiexport] Verified exact MM2 Industrial Foregoing ore-recipe ordering "
                        + "before publication: successfulInvocations={}, lastTotalItemTags={}, "
                        + "verifiedRawTags={}",
                invocations,
                LAST_TOTAL_TAGS.get(),
                IndustrialForegoingOreTagOrderContract.EXPECTED_VALID_RAW_TAG_IDS.size());
    }
}
