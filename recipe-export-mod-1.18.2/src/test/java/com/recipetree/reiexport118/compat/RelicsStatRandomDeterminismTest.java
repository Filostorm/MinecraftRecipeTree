package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RelicsStatRandomDeterminismTest {
    @Test
    void seedIsStableAndDomainSeparated() {
        long seed = RelicsStatRandomDeterminism.seed(
                "relics:aqua_walker", "walking", "speed");
        assertEquals(seed, RelicsStatRandomDeterminism.seed(
                "relics:aqua_walker", "walking", "speed"));
        assertNotEquals(seed, RelicsStatRandomDeterminism.seed(
                "relics:aqua_walker", "walking", "duration"));
        assertNotEquals(seed, RelicsStatRandomDeterminism.seed(
                "relics:ice_skates", "walking", "speed"));
    }

    @Test
    void seededRandomReplaysTheSameNativeRelicsSequence() {
        Random first = RelicsStatRandomDeterminism.seededRandom(
                "relics:aqua_walker", "walking", "speed");
        Random replay = RelicsStatRandomDeterminism.seededRandom(
                "relics:aqua_walker", "walking", "speed");
        Random otherStat = RelicsStatRandomDeterminism.seededRandom(
                "relics:aqua_walker", "walking", "duration");

        assertEquals(first.nextLong(), replay.nextLong());
        assertEquals(first.nextLong(), replay.nextLong());
        assertNotEquals(
                RelicsStatRandomDeterminism.seededRandom(
                        "relics:aqua_walker", "walking", "speed").nextLong(),
                otherStat.nextLong());
    }

    @Test
    void seededRandomRejectsIncompleteIdentityInsteadOfFallingBack() {
        assertThrows(IllegalStateException.class, () ->
                RelicsStatRandomDeterminism.seededRandom(
                        "relics:aqua_walker", "", "speed"));
        assertThrows(IllegalStateException.class, () ->
                RelicsStatRandomDeterminism.seededRandom(
                        "relics:aqua_walker", "walking", null));
        assertThrows(IllegalStateException.class, () ->
                RelicsStatRandomDeterminism.seededRandom(
                        "minecraft:diamond", "walking", "speed"));
        assertThrows(IllegalStateException.class, () ->
                RelicsStatRandomDeterminism.seededRandom(
                        "not a resource location", "walking", "speed"));
    }
}
