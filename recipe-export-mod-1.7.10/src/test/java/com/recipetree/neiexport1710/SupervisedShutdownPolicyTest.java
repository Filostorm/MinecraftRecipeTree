package com.recipetree.neiexport1710;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SupervisedShutdownPolicyTest {
    @Test
    public void acceptsUnchangedActiveIntegratedSession() {
        Object world = new Object();
        Object player = new Object();
        Object server = new Object();
        SupervisedShutdownPolicy.RenderState scheduled =
                SupervisedShutdownPolicy.capture(world, player, true, server, false);

        SupervisedShutdownPolicy.requireUnchangedRenderState(
                scheduled, world, player, true, server, false);
        assertTrue(scheduled.describe(), scheduled.describe().contains("active"));
    }

    @Test
    public void acceptsUnchangedIdleClientState() {
        SupervisedShutdownPolicy.RenderState scheduled =
                SupervisedShutdownPolicy.capture(null, null, false, null, false);

        SupervisedShutdownPolicy.requireUnchangedRenderState(
                scheduled, null, null, false, null, false);
        assertTrue(scheduled.describe(), scheduled.describe().contains("idle"));
    }

    @Test
    public void reportsEveryChangedRenderCriticalState() {
        Object world = new Object();
        Object player = new Object();
        Object server = new Object();
        SupervisedShutdownPolicy.RenderState scheduled =
                SupervisedShutdownPolicy.capture(world, player, true, server, false);
        try {
            SupervisedShutdownPolicy.requireUnchangedRenderState(
                    scheduled, new Object(), new Object(), false, new Object(), true);
            fail("Expected changed render-critical state to fail closed");
        } catch (IllegalStateException error) {
            assertTrue(error.getMessage(), error.getMessage().contains("world"));
            assertTrue(error.getMessage(), error.getMessage().contains("player"));
            assertTrue(error.getMessage(),
                    error.getMessage().contains("integratedServerRunning"));
            assertTrue(error.getMessage(),
                    error.getMessage().contains("integratedServer"));
            assertTrue(error.getMessage(),
                    error.getMessage().contains("DreamCoreMod.showConfirmExitWindow"));
        }
    }

    @Test
    public void rejectsEachChangedStateIndependentlyByIdentity() {
        Object world = new Object();
        Object player = new Object();
        Object server = new Object();
        SupervisedShutdownPolicy.RenderState scheduled =
                SupervisedShutdownPolicy.capture(world, player, true, server, false);

        assertChanged(scheduled, new Object(), player, true, server, false, "world");
        assertChanged(scheduled, world, new Object(), true, server, false, "player");
        assertChanged(scheduled, world, player, false, server, false,
                "integratedServerRunning");
        assertChanged(scheduled, world, player, true, new Object(), false,
                "integratedServer");
        assertChanged(scheduled, world, player, true, server, true,
                "DreamCoreMod.showConfirmExitWindow");
    }

    @Test
    public void rejectsIncoherentScheduledStates() {
        assertIncoherent(new Object(), null, true, new Object(), false,
                "world/player presence");
        assertIncoherent(new Object(), new Object(), false, null, false,
                "client-world/integrated-server lifecycle");
        assertIncoherent(null, null, true, null, false,
                "integrated-server flag/reference");
        assertIncoherent(null, null, false, new Object(), false,
                "integrated-server flag/reference");
        assertIncoherent(null, null, false, null, true,
                "DreamCoreMod.showConfirmExitWindow enabled");
    }

    @Test
    public void rejectsAbsentScheduledSnapshot() {
        try {
            SupervisedShutdownPolicy.requireUnchangedRenderState(
                    null, null, null, false, null, false);
            fail("Expected absent scheduled state to fail closed");
        } catch (IllegalStateException error) {
            assertTrue(error.getMessage(), error.getMessage().contains("absent"));
        }
    }

    private static void assertChanged(SupervisedShutdownPolicy.RenderState scheduled,
                                      Object world,
                                      Object player,
                                      boolean integratedServerRunning,
                                      Object integratedServer,
                                      boolean confirmExitWindowEnabled,
                                      String expectedState) {
        try {
            SupervisedShutdownPolicy.requireUnchangedRenderState(
                    scheduled, world, player,
                    integratedServerRunning, integratedServer,
                    confirmExitWindowEnabled);
            fail("Expected changed " + expectedState + " state to fail closed");
        } catch (IllegalStateException error) {
            assertTrue(error.getMessage(), error.getMessage().contains(expectedState));
        }
    }

    private static void assertIncoherent(Object world,
                                         Object player,
                                         boolean integratedServerRunning,
                                         Object integratedServer,
                                         boolean confirmExitWindowEnabled,
                                         String expectedReason) {
        try {
            SupervisedShutdownPolicy.capture(
                    world, player, integratedServerRunning, integratedServer,
                    confirmExitWindowEnabled);
            fail("Expected incoherent render-critical state to fail closed");
        } catch (IllegalStateException error) {
            assertTrue(error.getMessage(), error.getMessage().contains(expectedReason));
        }
    }
}
