package com.recipetree.reiexport118.compat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Exact unit-cardinality semantics for categorical JEI ingredients exposed through REI.
 *
 * <p>These values identify membership in a category; they are not countable stacks and their
 * upstream classes intentionally expose neither {@code getAmount()} nor {@code getCount()}.
 * Matching both the REI entry type and the runtime value class prevents an unrelated custom
 * ingredient from silently inheriting unit cardinality.</p>
 */
public final class CategoricalIngredientAmountContract {
    public static final long UNIT_CARDINALITY = 1L;

    public record ExactPair(String typeId, String valueClassName) {
        public ExactPair {
            if (typeId == null || typeId.isBlank()) {
                throw new IllegalArgumentException("Categorical REI type id must be nonblank");
            }
            if (valueClassName == null || valueClassName.isBlank()) {
                throw new IllegalArgumentException("Categorical value class must be nonblank");
            }
        }
    }

    public record Resolution(ExactPair pair, long amount, String auditWarning) {
        public Resolution {
            if (pair == null) {
                throw new IllegalArgumentException("Categorical exact pair must be nonnull");
            }
            if (amount != UNIT_CARDINALITY) {
                throw new IllegalArgumentException(
                        "Categorical amount must be exact unit cardinality; actual=" + amount);
            }
            if (auditWarning == null || auditWarning.isBlank()) {
                throw new IllegalArgumentException("Categorical audit warning must be nonblank");
            }
        }
    }

    private static final List<ExactPair> EXACT_PAIRS = List.of(
            new ExactPair(
                    "tconstruct:jei_plugin_jei_compat_pattern",
                    "slimeknights.tconstruct.library.recipe.partbuilder.Pattern"),
            new ExactPair(
                    "tconstruct:jei_plugin_jei_compat_modifierentry",
                    "slimeknights.tconstruct.library.modifiers.ModifierEntry"),
            new ExactPair(
                    "tconstruct:jei_plugin_jei_compat_entitytype",
                    "net.minecraft.world.entity.EntityType"),
            new ExactPair(
                    "jeed:jei_plugin_jei_compat_mobeffectinstance",
                    "net.minecraft.world.effect.MobEffectInstance"),
            new ExactPair(
                    "spirit:jei_jei_compat_entityingredient",
                    "me.codexadrian.spirit.compat.jei.ingredients.EntityIngredient")
    );

    private static final Map<String, Resolution> RESOLUTION_BY_TYPE = EXACT_PAIRS.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    ExactPair::typeId,
                    CategoricalIngredientAmountContract::resolution));

    private CategoricalIngredientAmountContract() {
    }

    public static Optional<Resolution> resolve(String typeId, String valueClassName) {
        if (typeId == null || valueClassName == null) {
            return Optional.empty();
        }
        Resolution resolution = RESOLUTION_BY_TYPE.get(typeId);
        if (resolution == null || !resolution.pair().valueClassName().equals(valueClassName)) {
            return Optional.empty();
        }
        return Optional.of(resolution);
    }

    static List<ExactPair> exactPairs() {
        return EXACT_PAIRS;
    }

    private static Resolution resolution(ExactPair pair) {
        return new Resolution(
                pair,
                UNIT_CARDINALITY,
                "CATEGORICAL_UNIT_CARDINALITY typeId=" + pair.typeId()
                        + " valueClass=" + pair.valueClassName()
                        + " amount=1 semantics=identity-membership"
                        + "; exact categorical pair has no upstream stack quantity"
        );
    }
}
