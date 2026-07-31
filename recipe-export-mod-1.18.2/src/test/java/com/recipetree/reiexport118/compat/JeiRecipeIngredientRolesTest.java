package com.recipetree.reiexport118.compat;

import mezz.jei.api.recipe.RecipeIngredientRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class JeiRecipeIngredientRolesTest {
    @Test
    void partitionsConsumedInputsFromReusableCatalystsWithoutReordering() {
        JeiRecipeIngredientRoles.Partition<String> partition =
                JeiRecipeIngredientRoles.partition(List.of(
                        new JeiRecipeIngredientRoles.RoleSlot<>(RecipeIngredientRole.INPUT, "ingot"),
                        new JeiRecipeIngredientRoles.RoleSlot<>(RecipeIngredientRole.CATALYST, "mold"),
                        new JeiRecipeIngredientRoles.RoleSlot<>(RecipeIngredientRole.RENDER_ONLY, "label"),
                        new JeiRecipeIngredientRoles.RoleSlot<>(RecipeIngredientRole.INPUT, "wire")
                ));

        assertEquals(List.of("ingot", "wire"), partition.materialInputs());
        assertEquals(List.of("mold"), partition.catalysts());
        assertEquals(List.of("ingot", "mold", "wire"), partition.flattenedInputs());
    }

    @Test
    void acceptsEquivalentShapelessSlotsWhenJeiReplaysThemInAnotherOrder() {
        JeiRecipeIngredientRoles.requireSameFlattenedInputs(
                List.of("plate", "mold", "plate"),
                List.of("plate", "plate", "mold"),
                "create:automatic_shapeless"
        );
    }

    @Test
    void rejectsRoleReconstructionWithDifferentMembershipOrDuplicateCardinality() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> JeiRecipeIngredientRoles.requireSameFlattenedInputs(
                        List.of("ingot", "mold", "mold"),
                        List.of("ingot", "mold", "plate"),
                        "immersiveengineering:metal_press"
                )
        );

        assertEquals(
                "JEI_ROLE_RECONSTRUCTION_MISMATCH category=immersiveengineering:metal_press"
                        + " reiInputs=3 reconstructedInputs=3",
                error.getMessage()
        );
    }
}
