package com.recipetree.reiexport118;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class NamingTest {
    @Test
    void sanitizationAndHashingAreDeterministic() {
        assertEquals("mekanism/chemical", Naming.sanitize("Mekanism/Chemical"));
        assertEquals(Naming.hash128("same"), Naming.hash128("same"));
        assertNotEquals(Naming.hash128("same"), Naming.hash128("different"));
        assertEquals(22, Naming.hash128("length").length());
    }
}
