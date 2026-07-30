package com.recipetree.jeiexport112;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class LegacyIngredientIdentityTest {
    @Test
    public void aspectManaAndImpetusUseStableTypeIdentityWhileQuantityStaysOrthogonal() {
        LegacyIngredientIdentity.Identity aspect = LegacyIngredientIdentity.aspect("Aer", "Aer");
        LegacyIngredientIdentity.Identity mana = LegacyIngredientIdentity.mana();
        LegacyIngredientIdentity.Identity impetus = LegacyIngredientIdentity.impetus();

        assertEquals("aspect:aer", aspect.uid);
        assertEquals("thaumcraft:aspect/aer", aspect.resourceId);
        assertEquals("mana", mana.uid);
        assertEquals("modularmachinery:mana", mana.resourceId);
        assertEquals("impetus", impetus.uid);
        assertEquals("modularmachinery:impetus", impetus.resourceId);
    }

    @Test
    public void lifeEssenceSeparatesPerTickFromPerOperationWithoutEmbeddingAmount() {
        LegacyIngredientIdentity.Identity perTick = LegacyIngredientIdentity.lifeEssence(true);
        LegacyIngredientIdentity.Identity perOperation = LegacyIngredientIdentity.lifeEssence(false);

        assertEquals("life_essence:per_tick", perTick.uid);
        assertEquals("Life Essence (per tick)", perTick.displayName);
        assertEquals("modularmachinery:life_essence", perTick.resourceId);
        assertNotEquals(perTick.uid, perOperation.uid);
        assertEquals("modularmachinery:life_essence", perOperation.resourceId);
    }

    @Test
    public void fluxSeparatesChunkRangeWithoutEmbeddingScalableFluxAmount() {
        LegacyIngredientIdentity.Identity local = LegacyIngredientIdentity.flux(0);
        LegacyIngredientIdentity.Identity ranged = LegacyIngredientIdentity.flux(16);

        assertEquals("flux:chunk_range=0", local.uid);
        assertEquals("flux:chunk_range=16", ranged.uid);
        assertEquals("modularmachineryaddons:flux", ranged.resourceId);
        assertEquals("Flux (chunk range 16)", ranged.displayName);
    }

    @Test
    public void meteorFingerprintCoversCatalystCompositionRadiusAndStrength() {
        LegacyIngredientIdentity.MeteorComponent iron =
                new LegacyIngredientIdentity.MeteorComponent("oreIron", 70);
        LegacyIngredientIdentity.MeteorComponent gold =
                new LegacyIngredientIdentity.MeteorComponent("oreGold", 30);
        LegacyIngredientIdentity.Identity baseline = LegacyIngredientIdentity.meteor(
                "minecraft:nether_star|count=1", 8, Float.valueOf(4.0F), Arrays.asList(iron, gold));

        assertTrue(baseline.uid.matches("meteor:[0-9a-f]{64}"));
        assertEquals("modularmachineryaddons:meteor", baseline.resourceId);
        assertNotEquals(baseline.uid, LegacyIngredientIdentity.meteor(
                "minecraft:diamond|count=1", 8, Float.valueOf(4.0F),
                Arrays.asList(iron, gold)).uid);
        assertNotEquals(baseline.uid, LegacyIngredientIdentity.meteor(
                "minecraft:nether_star|count=1", 9, Float.valueOf(4.0F),
                Arrays.asList(iron, gold)).uid);
        assertNotEquals(baseline.uid, LegacyIngredientIdentity.meteor(
                "minecraft:nether_star|count=1", 8, Float.valueOf(5.0F),
                Arrays.asList(iron, gold)).uid);
        assertNotEquals(baseline.uid, LegacyIngredientIdentity.meteor(
                "minecraft:nether_star|count=1", 8, Float.valueOf(4.0F),
                Arrays.asList(gold, iron)).uid);
    }

    @Test
    public void biomeUsesActualResourceOwnerAndDisplayNameInIdentity() {
        LegacyIngredientIdentity.Identity biome =
                LegacyIngredientIdentity.biome("divinerpg:mortum", "Mortum");
        LegacyIngredientIdentity.Identity renamed =
                LegacyIngredientIdentity.biome("divinerpg:mortum", "The Mortum");

        assertEquals("divinerpg:mortum", biome.resourceId);
        assertEquals("divinerpg", biome.modId);
        assertEquals("Mortum", biome.displayName);
        assertNotEquals(biome.uid, renamed.uid);
    }

    @Test
    public void demonWillHasTypedResourceAndReadableLabel() {
        LegacyIngredientIdentity.Identity will = LegacyIngredientIdentity.demonWill("CORROSIVE");

        assertEquals("demon_will:corrosive", will.uid);
        assertEquals("modularmachinery:demon_will/corrosive", will.resourceId);
        assertEquals("Corrosive Will", will.displayName);
        assertEquals("modularmachinery", will.modId);
    }

    @Test
    public void villagerCareerIncludesProfessionAndUsesValidJeiVillagersNamespace() {
        LegacyIngredientIdentity.Identity farmer = LegacyIngredientIdentity.villagerCareer(
                "minecraft:farmer", "farmer", "Farmer");
        LegacyIngredientIdentity.Identity moddedFarmer = LegacyIngredientIdentity.villagerCareer(
                "example:merchant", "farmer", "Farmer");

        assertNotEquals(farmer.uid, moddedFarmer.uid);
        assertEquals("jeivillagers:career/minecraft/farmer/farmer", farmer.resourceId);
        assertEquals("jeivillagers", farmer.modId);
        assertEquals("Farmer", farmer.displayName);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidResourceNamespaceFailsClosed() {
        LegacyIngredientIdentity.villagerCareer("JEI Villagers:farmer", "farmer", "Farmer");
    }
}
