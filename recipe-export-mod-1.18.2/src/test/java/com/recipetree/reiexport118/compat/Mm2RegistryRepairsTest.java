package com.recipetree.reiexport118.compat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2RegistryRepairsTest {
    private static final Mm2RegistryRepairs.EntryShape PLAIN_ITEM =
            new Mm2RegistryRepairs.EntryShape(true, false);
    private static final Mm2RegistryRepairs.EntryShape TAGGED_ITEM =
            new Mm2RegistryRepairs.EntryShape(true, true);
    private static final Mm2RegistryRepairs.EntryShape NON_ITEM =
            new Mm2RegistryRepairs.EntryShape(false, false);

    @Test
    void projectRedKeepsZeroOrRemovesOnlyOneExactBlank() {
        assertEquals(
                Mm2RegistryRepairs.ProjectRedAction.KEEP_ZERO,
                Mm2RegistryRepairs.projectRedAction(List.of()));
        assertEquals(
                Mm2RegistryRepairs.ProjectRedAction.REMOVE_EXACT_BLANK,
                Mm2RegistryRepairs.projectRedAction(List.of(PLAIN_ITEM)));
    }

    @Test
    void projectRedFailsClosedOnSemanticShapeOrCardinalityDrift() {
        assertThrows(
                IllegalStateException.class,
                () -> Mm2RegistryRepairs.projectRedAction(List.of(TAGGED_ITEM)));
        assertThrows(
                IllegalStateException.class,
                () -> Mm2RegistryRepairs.projectRedAction(List.of(NON_ITEM)));
        assertThrows(
                IllegalStateException.class,
                () -> Mm2RegistryRepairs.projectRedAction(List.of(PLAIN_ITEM, PLAIN_ITEM)));
        assertThrows(
                IllegalArgumentException.class,
                () -> Mm2RegistryRepairs.projectRedAction(null));
    }

    @Test
    void natureAuraAddsZeroOrKeepsOnlyOneExactBlank() {
        assertEquals(
                Mm2RegistryRepairs.NatureAuraAction.ADD_EXACT_BLANK,
                Mm2RegistryRepairs.natureAuraAction(List.of()));
        assertEquals(
                Mm2RegistryRepairs.NatureAuraAction.KEEP_ONE_PLAIN,
                Mm2RegistryRepairs.natureAuraAction(List.of(PLAIN_ITEM)));
    }

    @Test
    void natureAuraFailsClosedOnSemanticShapeOrCardinalityDrift() {
        assertThrows(
                IllegalStateException.class,
                () -> Mm2RegistryRepairs.natureAuraAction(List.of(TAGGED_ITEM)));
        assertThrows(
                IllegalStateException.class,
                () -> Mm2RegistryRepairs.natureAuraAction(List.of(NON_ITEM)));
        assertThrows(
                IllegalStateException.class,
                () -> Mm2RegistryRepairs.natureAuraAction(List.of(PLAIN_ITEM, PLAIN_ITEM)));
        assertThrows(
                IllegalArgumentException.class,
                () -> Mm2RegistryRepairs.natureAuraAction(null));
    }

    @Test
    void settlementReportsEveryExactMutationWithoutInferringFromNetEntryCount() {
        assertFalse(new Mm2RegistryRepairs.SettlementResult(false, false, false).changed());
        assertTrue(new Mm2RegistryRepairs.SettlementResult(true, false, false).changed());
        assertTrue(new Mm2RegistryRepairs.SettlementResult(false, true, false).changed());
        assertTrue(new Mm2RegistryRepairs.SettlementResult(false, false, true).changed());
        assertTrue(new Mm2RegistryRepairs.SettlementResult(true, true, true).changed());
    }

    @Test
    void pigmentOriginsRequireOneHundredSeventySevenUniqueObjectsAndRecipeIds() {
        List<Mm2RegistryRepairs.RecipeOrigin> origins = new ArrayList<>();
        for (int index = 0; index < 177; index++) {
            origins.add(new Mm2RegistryRepairs.RecipeOrigin(
                    new Object(), new ResourceLocation("mekanism", "pigment_" + index)));
        }

        Mm2RegistryRepairs.assertUniqueRecipeOrigins(
                "mekanism:pigment_extractor", 177, origins);
    }

    @Test
    void pigmentOriginsRejectDuplicateObjectIdentityOrRecipeId() {
        Object duplicate = new Object();
        assertThrows(
                IllegalStateException.class,
                () -> Mm2RegistryRepairs.assertUniqueRecipeOrigins(
                        "mekanism:pigment_extractor",
                        2,
                        List.of(
                                new Mm2RegistryRepairs.RecipeOrigin(
                                        duplicate, new ResourceLocation("test", "first")),
                                new Mm2RegistryRepairs.RecipeOrigin(
                                        duplicate, new ResourceLocation("test", "second")))));

        ResourceLocation duplicateId = new ResourceLocation("test", "duplicate");
        assertThrows(
                IllegalStateException.class,
                () -> Mm2RegistryRepairs.assertUniqueRecipeOrigins(
                        "mekanism:pigment_extractor",
                        2,
                        List.of(
                                new Mm2RegistryRepairs.RecipeOrigin(new Object(), duplicateId),
                                new Mm2RegistryRepairs.RecipeOrigin(new Object(), duplicateId))));
    }

    @Test
    void pigmentOriginsRejectWrongTotalAndNullIdentityData() {
        assertThrows(
                IllegalStateException.class,
                () -> Mm2RegistryRepairs.assertUniqueRecipeOrigins(
                        "mekanism:pigment_extractor",
                        2,
                        List.of(new Mm2RegistryRepairs.RecipeOrigin(
                                new Object(), new ResourceLocation("test", "only")))));
        assertThrows(
                IllegalStateException.class,
                () -> Mm2RegistryRepairs.assertUniqueRecipeOrigins(
                        "mekanism:pigment_extractor",
                        1,
                        List.of(new Mm2RegistryRepairs.RecipeOrigin(
                                new Object(), null))));
    }
}
