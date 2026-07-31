package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Repairs the exact Botania 435/JEED 1.11 FlowerBlock registry-timing incompatibility, then
 * mirrors JEED's effect-provider traversal before REI registration. The repair reuses Botania's
 * original, now-registered MobEffect instances; it does not synthesize effects or omit providers.
 */
public final class FlowerEffectPreflight {
    private FlowerEffectPreflight() {
    }

    public static void repairAndValidateBeforeReiRegistration() {
        String minecraftVersion = modVersion("minecraft");
        String forgeVersion = modVersion("forge");
        String botaniaVersion = modVersion("botania");
        String jeedVersion = modVersion("jeed");
        if (!BotaniaFlowerEffectContract.isApplicable(
                minecraftVersion,
                forgeVersion,
                botaniaVersion,
                jeedVersion
        )) {
            if (botaniaVersion != null || jeedVersion != null) {
                ReiExportMod.LOGGER.info(
                        "[reiexport] Botania/JEED flower compatibility not applied; required minecraft={}, forge={}, botania={}, jeed={}; actual minecraft={}, forge={}, botania={}, jeed={}",
                        BotaniaFlowerEffectContract.MINECRAFT_VERSION,
                        BotaniaFlowerEffectContract.FORGE_VERSION,
                        BotaniaFlowerEffectContract.BOTANIA_VERSION,
                        BotaniaFlowerEffectContract.JEED_VERSION,
                        minecraftVersion,
                        forgeVersion,
                        botaniaVersion,
                        jeedVersion
                );
            }
            return;
        }

        int blockCount = 0;
        int flowerBlockCount = 0;
        int validUnchangedCount = 0;
        Set<BotaniaFlowerEffectContract.Target> seenTargets = EnumSet.noneOf(
                BotaniaFlowerEffectContract.Target.class
        );
        List<RepairCandidate> candidates = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (Block block : ForgeRegistries.BLOCKS) {
            blockCount++;
            if (!(block instanceof FlowerBlock)) {
                continue;
            }

            flowerBlockCount++;
            FlowerBlock flowerBlock = (FlowerBlock) block;
            String blockId = blockLabel(block);
            BotaniaFlowerEffectContract.Target target = BotaniaFlowerEffectContract.targetFor(blockId);
            if (target == null) {
                if (validateRegisteredEffect(blockId, flowerBlock, failures)) {
                    validUnchangedCount++;
                }
                continue;
            }

            if (!seenTargets.add(target)) {
                failures.add("duplicate target block=" + blockId);
                continue;
            }
            inspectRepairCandidate(blockId, flowerBlock, target, candidates, failures);
        }

        for (BotaniaFlowerEffectContract.Target target : BotaniaFlowerEffectContract.Target.values()) {
            if (!seenTargets.contains(target)) {
                failures.add("missing target block=" + target.blockId());
            }
        }
        if (flowerBlockCount != BotaniaFlowerEffectContract.EXPECTED_FLOWER_BLOCK_COUNT) {
            failures.add(
                    "FlowerBlock corpus drift: observed=" + flowerBlockCount
                            + ", expected=" + BotaniaFlowerEffectContract.EXPECTED_FLOWER_BLOCK_COUNT
            );
        }

        if (!failures.isEmpty()) {
            failClosed(failures, flowerBlockCount, blockCount, "validation before mutation");
        }

        int repairedCount = 0;
        for (RepairCandidate candidate : candidates) {
            candidate.access.reiexport$setSuspiciousStewEffectSupplier(() -> candidate.effect);
            try {
                MobEffect resolvedEffect = candidate.block.getSuspiciousStewEffect();
                ResourceLocation resolvedEffectId = ForgeRegistries.MOB_EFFECTS.getKey(resolvedEffect);
                if (resolvedEffect != candidate.effect
                        || resolvedEffectId == null
                        || !candidate.target.effectId().equals(resolvedEffectId.toString())) {
                    failures.add(
                            "post-repair mismatch block=" + candidate.target.blockId()
                                    + ", expectedEffect=" + candidate.target.effectId()
                                    + ", actualEffect=" + resolvedEffectId
                                    + ", sameInstance=" + (resolvedEffect == candidate.effect)
                    );
                    continue;
                }
                repairedCount++;
                ReiExportMod.LOGGER.warn(
                        "[reiexport] Corrected Botania 435 special-flower effect supplier: block={}, effect={}, constructorDuration={}, storedDuration={}, class={} (legacy failure was {}: {})",
                        candidate.target.blockId(),
                        candidate.target.effectId(),
                        candidate.target.constructorDuration(),
                        candidate.target.expectedStoredDuration(),
                        BotaniaFlowerEffectContract.BLOCK_CLASS,
                        NullPointerException.class.getName(),
                        BotaniaFlowerEffectContract.LEGACY_EXCEPTION_MESSAGE
                );
            } catch (RuntimeException exception) {
                failures.add(
                        "post-repair lookup threw for block=" + candidate.target.blockId()
                                + ": " + describeException(exception)
                );
            }
        }

        int resolvedEffectCount = validUnchangedCount + repairedCount;
        if (repairedCount != BotaniaFlowerEffectContract.Target.values().length
                || resolvedEffectCount != flowerBlockCount) {
            failures.add(
                    "resolution-count drift: repaired=" + repairedCount
                            + ", expectedRepairs=" + BotaniaFlowerEffectContract.Target.values().length
                            + ", resolved=" + resolvedEffectCount
                            + ", flowers=" + flowerBlockCount
            );
        }
        if (!failures.isEmpty()) {
            failClosed(failures, flowerBlockCount, blockCount, "post-repair verification");
        }

        ReiExportMod.LOGGER.warn(
                "[reiexport] Botania {}/JEED {} flower compatibility passed: repaired {} exact stale suppliers and resolved all {} FlowerBlock effects across {} registered blocks; no JEED providers were omitted",
                botaniaVersion,
                jeedVersion,
                repairedCount,
                resolvedEffectCount,
                blockCount
        );
    }

    private static void inspectRepairCandidate(
            String blockId,
            FlowerBlock block,
            BotaniaFlowerEffectContract.Target target,
            List<RepairCandidate> candidates,
            List<String> failures
    ) {
        if (!(block instanceof FlowerBlockContractAccess)) {
            failures.add("FlowerBlock contract bridge missing for block=" + blockId);
            return;
        }

        FlowerBlockContractAccess access = (FlowerBlockContractAccess) block;
        MobEffect legacyEffect = access.reiexport$getLegacySuspiciousStewEffect();
        ResourceLocation effectId = legacyEffect == null ? null : ForgeRegistries.MOB_EFFECTS.getKey(legacyEffect);
        RuntimeException legacyFailure = null;
        try {
            block.getSuspiciousStewEffect();
        } catch (RuntimeException exception) {
            legacyFailure = exception;
        }

        try {
            BotaniaFlowerEffectContract.Target exactTarget = BotaniaFlowerEffectContract.requireExact(
                    blockId,
                    block.getClass().getName(),
                    effectId == null ? null : effectId.toString(),
                    access.reiexport$getStoredEffectDuration(),
                    legacyEffect != null && legacyEffect.isInstantenous(),
                    legacyFailure
            );
            candidates.add(new RepairCandidate(block, access, legacyEffect, exactTarget));
        } catch (IllegalStateException exception) {
            failures.add(exception.getMessage());
        }
    }

    private static boolean validateRegisteredEffect(
            String blockId,
            FlowerBlock block,
            List<String> failures
    ) {
        try {
            MobEffect effect = block.getSuspiciousStewEffect();
            if (effect == null) {
                failures.add("block=" + blockId + ", class=" + block.getClass().getName()
                        + ", result=null MobEffect");
                return false;
            }
            ResourceLocation effectId = ForgeRegistries.MOB_EFFECTS.getKey(effect);
            if (effectId == null) {
                failures.add("block=" + blockId + ", class=" + block.getClass().getName()
                        + ", result=unregistered MobEffect, effectClass=" + effect.getClass().getName());
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            failures.add("block=" + blockId + ", class=" + block.getClass().getName()
                    + ", exception=" + describeException(exception));
            return false;
        }
    }

    private static void failClosed(
            List<String> failures,
            int flowerBlockCount,
            int blockCount,
            String phase
    ) {
        for (String failure : failures) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Botania/JEED flower compatibility failure ({}): {}",
                    phase,
                    failure
            );
        }
        throw new IllegalStateException(
                "Botania/JEED flower compatibility rejected " + failures.size()
                        + " contract(s) during " + phase
                        + "; scanned " + flowerBlockCount
                        + " FlowerBlock entries across " + blockCount + " registered blocks"
        );
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }

    private static String blockLabel(Block block) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        return blockId == null ? "<unregistered:" + block.getClass().getName() + ">" : blockId.toString();
    }

    private static String describeException(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getName() + (message == null ? "" : ": " + message);
    }

    private static final class RepairCandidate {
        private final FlowerBlock block;
        private final FlowerBlockContractAccess access;
        private final MobEffect effect;
        private final BotaniaFlowerEffectContract.Target target;

        private RepairCandidate(
                FlowerBlock block,
                FlowerBlockContractAccess access,
                MobEffect effect,
                BotaniaFlowerEffectContract.Target target
        ) {
            this.block = block;
            this.access = access;
            this.effect = effect;
            this.target = target;
        }
    }
}
