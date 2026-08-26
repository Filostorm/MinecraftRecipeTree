package com.recipetree.neiexport1710;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AvaritiaCosmicIconRendererTest {

    @Test
    public void rejectsAZeroShaderProgramInsteadOfPublishingTheRawMask() {
        assertEquals(41, AvaritiaCosmicIconRenderer.requireProgram(41));
        try {
            AvaritiaCosmicIconRenderer.requireProgram(0);
            fail("Expected a zero shader program to fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("unshaded mask texture"));
        }
    }
}
