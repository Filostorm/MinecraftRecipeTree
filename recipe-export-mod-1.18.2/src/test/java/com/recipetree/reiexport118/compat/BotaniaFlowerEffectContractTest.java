package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BotaniaFlowerEffectContractTest {
    @Test
    void appliesOnlyToTheObservedBotaniaAndJeedVersions() {
        assertTrue(BotaniaFlowerEffectContract.isApplicable(
                "1.18.2", "40.2.17", "1.18.2-435", "1.18.2-1.11"
        ));
        assertFalse(BotaniaFlowerEffectContract.isApplicable(
                "1.18.1", "40.2.17", "1.18.2-435", "1.18.2-1.11"
        ));
        assertFalse(BotaniaFlowerEffectContract.isApplicable(
                "1.18.2", "40.2.18", "1.18.2-435", "1.18.2-1.11"
        ));
        assertFalse(BotaniaFlowerEffectContract.isApplicable(
                "1.18.2", "40.2.17", "1.18.2-436", "1.18.2-1.11"
        ));
        assertFalse(BotaniaFlowerEffectContract.isApplicable(
                "1.18.2", "40.2.17", "1.18.2-435", "1.18.2-1.12"
        ));
        assertFalse(BotaniaFlowerEffectContract.isApplicable(
                "1.18.2", "40.2.17", null, "1.18.2-1.11"
        ));
    }

    @Test
    void preservesTheEightStaticBotaniaBlockEffectMappings() {
        Map<String, String> mappings = Arrays.stream(BotaniaFlowerEffectContract.Target.values())
                .collect(Collectors.toMap(
                        BotaniaFlowerEffectContract.Target::blockId,
                        BotaniaFlowerEffectContract.Target::effectId
                ));

        assertEquals(8, mappings.size());
        assertEquals(111, BotaniaFlowerEffectContract.EXPECTED_FLOWER_BLOCK_COUNT);
        assertEquals("botania:clear", mappings.get("botania:pure_daisy"));
        assertEquals("botania:feather_feet", mappings.get("botania:narslimmus"));
        assertEquals("botania:soul_cross", mappings.get("botania:heisei_dream"));
        assertEquals("botania:bloodthirst", mappings.get("botania:tangleberrie"));
        assertEquals("botania:bloodthirst", mappings.get("botania:tangleberrie_chibi"));
        assertEquals("botania:emptiness", mappings.get("botania:jiyuulia"));
        assertEquals("botania:emptiness", mappings.get("botania:jiyuulia_chibi"));
        assertEquals("botania:allure", mappings.get("botania:loonium"));
    }

    @Test
    void acceptsExactConstructorAndStoredDurationContracts() {
        for (BotaniaFlowerEffectContract.Target target : BotaniaFlowerEffectContract.Target.values()) {
            assertEquals(
                    target,
                    BotaniaFlowerEffectContract.requireExact(
                            target.blockId(),
                            BotaniaFlowerEffectContract.BLOCK_CLASS,
                            target.effectId(),
                            target.expectedStoredDuration(),
                            target.instantaneous(),
                            new NullPointerException(BotaniaFlowerEffectContract.LEGACY_EXCEPTION_MESSAGE)
                    )
            );
        }

        assertEquals(1, BotaniaFlowerEffectContract.Target.PURE_DAISY.expectedStoredDuration());
        assertEquals(4_800, BotaniaFlowerEffectContract.Target.NARSLIMMUS.expectedStoredDuration());
        assertEquals(6_000, BotaniaFlowerEffectContract.Target.HEISEI_DREAM.expectedStoredDuration());
        assertEquals(2_400, BotaniaFlowerEffectContract.Target.TANGLEBERRIE.expectedStoredDuration());
        assertEquals(18_000, BotaniaFlowerEffectContract.Target.LOONIUM.expectedStoredDuration());
    }

    @Test
    void rejectsClassEffectDurationAndLegacyFailureDrift() {
        BotaniaFlowerEffectContract.Target target = BotaniaFlowerEffectContract.Target.NARSLIMMUS;

        assertThrows(IllegalStateException.class, () -> require(target, "future.SpecialFlower",
                target.effectId(), target.expectedStoredDuration(), target.instantaneous(), exactFailure()));
        assertThrows(IllegalStateException.class, () -> require(target,
                BotaniaFlowerEffectContract.BLOCK_CLASS, "botania:future_effect",
                target.expectedStoredDuration(), target.instantaneous(), exactFailure()));
        assertThrows(IllegalStateException.class, () -> require(target,
                BotaniaFlowerEffectContract.BLOCK_CLASS, target.effectId(),
                target.expectedStoredDuration() + 1, target.instantaneous(), exactFailure()));
        assertThrows(IllegalStateException.class, () -> require(target,
                BotaniaFlowerEffectContract.BLOCK_CLASS, target.effectId(),
                target.expectedStoredDuration(), target.instantaneous(), null));
        assertThrows(IllegalStateException.class, () -> require(target,
                BotaniaFlowerEffectContract.BLOCK_CLASS, target.effectId(),
                target.expectedStoredDuration(), target.instantaneous(),
                new NullPointerException("different failure")));
    }

    private static BotaniaFlowerEffectContract.Target require(
            BotaniaFlowerEffectContract.Target target,
            String blockClass,
            String effectId,
            int storedDuration,
            boolean instantaneous,
            RuntimeException failure
    ) {
        return BotaniaFlowerEffectContract.requireExact(
                target.blockId(),
                blockClass,
                effectId,
                storedDuration,
                instantaneous,
                failure
        );
    }

    private static RuntimeException exactFailure() {
        return new NullPointerException(BotaniaFlowerEffectContract.LEGACY_EXCEPTION_MESSAGE);
    }
}
