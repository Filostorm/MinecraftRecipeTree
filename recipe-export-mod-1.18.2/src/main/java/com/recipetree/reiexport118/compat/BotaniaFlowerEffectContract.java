package com.recipetree.reiexport118.compat;

/**
 * Exact Botania 1.18.2-435 constructor contracts for the eight special flowers whose custom
 * effects are registered after the blocks are constructed. Forge's legacy FlowerBlock constructor
 * captures the then-null effect registry name in a RegistryObject, even though Botania later
 * registers the original MobEffect instance normally.
 */
public final class BotaniaFlowerEffectContract {
    public static final String MINECRAFT_VERSION = "1.18.2";
    public static final String FORGE_VERSION = "40.2.17";
    public static final String BOTANIA_VERSION = "1.18.2-435";
    public static final String JEED_VERSION = "1.18.2-1.11";
    public static final String BLOCK_CLASS = "vazkii.botania.common.block.BlockSpecialFlower";
    public static final String LEGACY_EXCEPTION_MESSAGE = "Registry Object not present: null";
    public static final int EXPECTED_FLOWER_BLOCK_COUNT = 111;

    private BotaniaFlowerEffectContract() {
    }

    public static boolean isApplicable(
            String minecraftVersion,
            String forgeVersion,
            String botaniaVersion,
            String jeedVersion
    ) {
        return MINECRAFT_VERSION.equals(minecraftVersion)
                && FORGE_VERSION.equals(forgeVersion)
                && BOTANIA_VERSION.equals(botaniaVersion)
                && JEED_VERSION.equals(jeedVersion);
    }

    public static Target targetFor(String blockId) {
        for (Target target : Target.values()) {
            if (target.blockId.equals(blockId)) {
                return target;
            }
        }
        return null;
    }

    public static Target requireExact(
            String blockId,
            String blockClass,
            String effectId,
            int storedDuration,
            boolean instantaneous,
            RuntimeException legacyFailure
    ) {
        Target target = targetFor(blockId);
        if (target == null) {
            throw new IllegalStateException("unknown Botania flower-effect repair target: " + blockId);
        }

        boolean exactFailure = legacyFailure instanceof NullPointerException
                && LEGACY_EXCEPTION_MESSAGE.equals(legacyFailure.getMessage());
        if (!BLOCK_CLASS.equals(blockClass)
                || !target.effectId.equals(effectId)
                || target.instantaneous != instantaneous
                || target.expectedStoredDuration() != storedDuration
                || !exactFailure) {
            throw new IllegalStateException(
                    "drifted Botania 435 flower-effect tuple for " + blockId
                            + "; expected=" + target.describe()
                            + "; actual={blockClass=" + blockClass
                            + ", effectId=" + effectId
                            + ", storedDuration=" + storedDuration
                            + ", instantaneous=" + instantaneous
                            + ", legacyFailure=" + describeFailure(legacyFailure) + "}"
            );
        }
        return target;
    }

    private static String describeFailure(RuntimeException failure) {
        if (failure == null) {
            return "<none>";
        }
        String message = failure.getMessage();
        return failure.getClass().getName() + (message == null ? "" : ": " + message);
    }

    public enum Target {
        PURE_DAISY("botania:pure_daisy", "botania:clear", 1, true),
        NARSLIMMUS("botania:narslimmus", "botania:feather_feet", 240, false),
        HEISEI_DREAM("botania:heisei_dream", "botania:soul_cross", 300, false),
        TANGLEBERRIE("botania:tangleberrie", "botania:bloodthirst", 120, false),
        TANGLEBERRIE_CHIBI("botania:tangleberrie_chibi", "botania:bloodthirst", 120, false),
        JIYUULIA("botania:jiyuulia", "botania:emptiness", 120, false),
        JIYUULIA_CHIBI("botania:jiyuulia_chibi", "botania:emptiness", 120, false),
        LOONIUM("botania:loonium", "botania:allure", 900, false);

        private final String blockId;
        private final String effectId;
        private final int constructorDuration;
        private final boolean instantaneous;

        Target(String blockId, String effectId, int constructorDuration, boolean instantaneous) {
            this.blockId = blockId;
            this.effectId = effectId;
            this.constructorDuration = constructorDuration;
            this.instantaneous = instantaneous;
        }

        public String blockId() {
            return blockId;
        }

        public String effectId() {
            return effectId;
        }

        public int constructorDuration() {
            return constructorDuration;
        }

        public boolean instantaneous() {
            return instantaneous;
        }

        public int expectedStoredDuration() {
            return instantaneous ? constructorDuration : constructorDuration * 20;
        }

        private String describe() {
            return "{blockClass=" + BLOCK_CLASS
                    + ", effectId=" + effectId
                    + ", constructorDuration=" + constructorDuration
                    + ", storedDuration=" + expectedStoredDuration()
                    + ", instantaneous=" + instantaneous
                    + ", legacyFailure=java.lang.NullPointerException: "
                    + LEGACY_EXCEPTION_MESSAGE + "}";
        }
    }
}
