package com.recipetree.neiexport1710;

/**
 * Fail-closed state gate for the one-shot client's normal {@code Minecraft.run} exit.
 *
 * <p>In the pinned 1.7.10 client, an END-phase client-tick subscriber runs at the tail of
 * {@code Minecraft.runTick}, but the enclosing {@code runGameLoop} frame still performs sound
 * listener and world-renderer work afterward. Therefore the subscriber must retain the exact
 * world, player, and integrated-server objects through that frame. The pinned BetterCrashes
 * outer-loop replacement retains the vanilla ownership sequence: once {@code Minecraft.shutdown}
 * clears the loop flag, the current frame finishes and the outer client loop invokes final
 * application cleanup. The pinned DreamCore confirmation injection can cancel that flag change,
 * so its public runtime gate must also be verified disabled immediately before the request.
 */
final class SupervisedShutdownPolicy {
    private SupervisedShutdownPolicy() {
    }

    static RenderState capture(Object world,
                               Object player,
                               boolean integratedServerRunning,
                               Object integratedServer,
                               boolean confirmExitWindowEnabled) {
        requireCoherent("scheduled", world, player,
                integratedServerRunning, integratedServer,
                confirmExitWindowEnabled);
        return new RenderState(world, player, integratedServerRunning, integratedServer,
                confirmExitWindowEnabled);
    }

    static void requireUnchangedRenderState(RenderState scheduled,
                                            Object world,
                                            Object player,
                                            boolean integratedServerRunning,
                                            Object integratedServer,
                                            boolean confirmExitWindowEnabled) {
        if (scheduled == null) {
            throw new IllegalStateException("Scheduled render-critical state is absent");
        }
        StringBuilder changed = new StringBuilder();
        appendChanged(changed, scheduled.world != world, "world");
        appendChanged(changed, scheduled.player != player, "player");
        appendChanged(changed,
                scheduled.integratedServerRunning != integratedServerRunning,
                "integratedServerRunning");
        appendChanged(changed, scheduled.integratedServer != integratedServer,
                "integratedServer");
        appendChanged(changed,
                scheduled.confirmExitWindowEnabled != confirmExitWindowEnabled,
                "DreamCoreMod.showConfirmExitWindow");
        if (changed.length() != 0) {
            throw new IllegalStateException(
                    "Render-critical state changed before the supervised game-loop exit: "
                            + changed);
        }
        requireCoherent("exit", world, player,
                integratedServerRunning, integratedServer,
                confirmExitWindowEnabled);
    }

    private static void requireCoherent(String stage,
                                        Object world,
                                        Object player,
                                        boolean integratedServerRunning,
                                        Object integratedServer,
                                        boolean confirmExitWindowEnabled) {
        boolean worldPresent = world != null;
        boolean playerPresent = player != null;
        boolean serverPresent = integratedServer != null;
        StringBuilder incoherent = new StringBuilder();
        appendChanged(incoherent, worldPresent != playerPresent, "world/player presence");
        appendChanged(incoherent, integratedServerRunning != serverPresent,
                "integrated-server flag/reference");
        appendChanged(incoherent, worldPresent != integratedServerRunning,
                "client-world/integrated-server lifecycle");
        appendChanged(incoherent, confirmExitWindowEnabled,
                "DreamCoreMod.showConfirmExitWindow enabled");
        if (incoherent.length() != 0) {
            throw new IllegalStateException(
                    "Incoherent " + stage + " render-critical state: " + incoherent
                            + "; world=" + presence(world)
                            + ", player=" + presence(player)
                            + ", integratedServerRunning=" + integratedServerRunning
                            + ", integratedServer=" + presence(integratedServer)
                            + ", showConfirmExitWindow=" + confirmExitWindowEnabled);
        }
    }

    private static String presence(Object value) {
        return value == null ? "absent" : "present";
    }

    private static void appendChanged(StringBuilder changed,
                                      boolean condition,
                                      String name) {
        if (!condition) {
            return;
        }
        if (changed.length() != 0) {
            changed.append(',');
        }
        changed.append(name);
    }

    static final class RenderState {
        private final Object world;
        private final Object player;
        private final boolean integratedServerRunning;
        private final Object integratedServer;
        private final boolean confirmExitWindowEnabled;

        private RenderState(Object world,
                            Object player,
                            boolean integratedServerRunning,
                            Object integratedServer,
                            boolean confirmExitWindowEnabled) {
            this.world = world;
            this.player = player;
            this.integratedServerRunning = integratedServerRunning;
            this.integratedServer = integratedServer;
            this.confirmExitWindowEnabled = confirmExitWindowEnabled;
        }

        String describe() {
            if (world == null) {
                return "stable idle client state";
            }
            return "stable active integrated-session state";
        }
    }
}
