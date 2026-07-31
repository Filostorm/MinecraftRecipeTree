package com.recipetree.reiexport118.compat;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2EntryCanonicalizationTest {
    @Test
    void materializesOnlyAbsentRootsForTheTwoExactCofhTargets() {
        CompoundTag reservoir = Mm2EntryCanonicalization.canonicalRoot(
                Mm2EntryCanonicalization.FLUID_RESERVOIR_ID, null);
        CompoundTag crystal = Mm2EntryCanonicalization.canonicalRoot(
                Mm2EntryCanonicalization.XP_CRYSTAL_ID, null);

        assertNotNull(reservoir);
        assertNotNull(crystal);
        assertTrue(reservoir.isEmpty());
        assertTrue(crystal.isEmpty());
        assertNull(Mm2EntryCanonicalization.canonicalRoot("minecraft:stone", null));
    }

    @Test
    void preservesExistingEmptyAndSemanticRootsByIdentity() {
        for (String identifier : new String[]{
                Mm2EntryCanonicalization.FLUID_RESERVOIR_ID,
                Mm2EntryCanonicalization.XP_CRYSTAL_ID
        }) {
            CompoundTag empty = new CompoundTag();
            assertSame(empty, Mm2EntryCanonicalization.canonicalRoot(identifier, empty));

            CompoundTag semantic = new CompoundTag();
            semantic.putInt("Amount", 1000);
            assertSame(semantic, Mm2EntryCanonicalization.canonicalRoot(identifier, semantic));
            assertEquals(1000, semantic.getInt("Amount"));
        }
    }

    @Test
    void preservesUnrelatedExistingRootWithoutInspection() {
        CompoundTag unrelated = new CompoundTag();
        unrelated.putString("semantic", "value");

        assertSame(
                unrelated,
                Mm2EntryCanonicalization.canonicalRoot(
                        "thermal:not_an_audited_target", unrelated));
        assertEquals("value", unrelated.getString("semantic"));
    }
}
