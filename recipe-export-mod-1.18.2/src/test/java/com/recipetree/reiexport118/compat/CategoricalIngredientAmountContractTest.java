package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CategoricalIngredientAmountContractTest {
    private static final Map<String, String> EXPECTED = Map.of(
            "tconstruct:jei_plugin_jei_compat_pattern",
            "slimeknights.tconstruct.library.recipe.partbuilder.Pattern",
            "tconstruct:jei_plugin_jei_compat_modifierentry",
            "slimeknights.tconstruct.library.modifiers.ModifierEntry",
            "tconstruct:jei_plugin_jei_compat_entitytype",
            "net.minecraft.world.entity.EntityType",
            "jeed:jei_plugin_jei_compat_mobeffectinstance",
            "net.minecraft.world.effect.MobEffectInstance",
            "spirit:jei_jei_compat_entityingredient",
            "me.codexadrian.spirit.compat.jei.ingredients.EntityIngredient"
    );

    @Test
    void exactCategoricalPairsResolveToAuditedUnitCardinality() {
        assertEquals(5, CategoricalIngredientAmountContract.exactPairs().size());
        assertEquals(EXPECTED.keySet(), CategoricalIngredientAmountContract.exactPairs().stream()
                .map(CategoricalIngredientAmountContract.ExactPair::typeId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));

        for (Map.Entry<String, String> expected : EXPECTED.entrySet()) {
            CategoricalIngredientAmountContract.Resolution resolution =
                    CategoricalIngredientAmountContract.resolve(
                                    expected.getKey(), expected.getValue())
                            .orElseThrow();

            assertEquals(1L, resolution.amount());
            assertEquals(
                    "CATEGORICAL_UNIT_CARDINALITY typeId=" + expected.getKey()
                            + " valueClass=" + expected.getValue()
                            + " amount=1 semantics=identity-membership"
                            + "; exact categorical pair has no upstream stack quantity",
                    resolution.auditWarning());
        }
    }

    @Test
    void typeAndRuntimeClassMustMatchTheSameExactPair() {
        assertTrue(CategoricalIngredientAmountContract.resolve(
                "tconstruct:jei_plugin_jei_compat_pattern",
                "slimeknights.tconstruct.library.modifiers.ModifierEntry").isEmpty());
        assertTrue(CategoricalIngredientAmountContract.resolve(
                "unknown:categorical",
                "slimeknights.tconstruct.library.recipe.partbuilder.Pattern").isEmpty());
        assertTrue(CategoricalIngredientAmountContract.resolve(
                "tconstruct:jei_plugin_jei_compat_pattern",
                "java.lang.Object").isEmpty());
        assertTrue(CategoricalIngredientAmountContract.resolve(null, null).isEmpty());
    }
}
