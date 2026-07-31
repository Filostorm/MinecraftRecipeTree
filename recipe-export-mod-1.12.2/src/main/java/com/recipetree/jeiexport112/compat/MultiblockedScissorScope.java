package com.recipetree.jeiexport112.compat;

/** Single-capture state machine kept independent from Minecraft and OpenGL for exact testing. */
final class MultiblockedScissorScope {
    private State active;

    synchronized void begin(Thread owner, int liveGuiScale, int liveScaledHeight,
                            int recipeScale, int targetLogicalHeight,
                            int translateX, int translateY) {
        if (owner == null) {
            throw drift("capture owner thread is null");
        }
        if (active != null) {
            throw drift("nested capture attempted by " + owner.getName() +
                    " while capture is owned by " + active.owner.getName());
        }
        active = new State(
                owner, liveGuiScale, liveScaledHeight,
                recipeScale, targetLogicalHeight, translateX, translateY
        );
    }

    synchronized boolean isActive() {
        return active != null;
    }

    synchronized MultiblockedScissorTransform.Box mapActive(
            Thread caller, int rawX, int rawY, int rawWidth, int rawHeight) {
        if (active == null) {
            throw drift("scissor correction requested without an active capture");
        }
        requireOwner(active, caller, "correct scissor");
        active.correctedCalls = Math.addExact(active.correctedCalls, 1);
        return MultiblockedScissorTransform.map(
                rawX, rawY, rawWidth, rawHeight,
                active.liveGuiScale, active.liveScaledHeight,
                active.recipeScale, active.targetLogicalHeight,
                active.translateX, active.translateY
        );
    }

    /** Clears first so every validation failure leaves the next draw unscoped. */
    synchronized int end(Thread caller) {
        State completed = active;
        active = null;
        if (completed == null) {
            throw drift("end capture requested without an active capture");
        }
        requireOwner(completed, caller, "end capture");
        if (completed.correctedCalls <= 0) {
            throw drift("the exact Multiblocked layout emitted no audited glScissor calls");
        }
        return completed.correctedCalls;
    }

    private static void requireOwner(State state, Thread caller, String operation) {
        if (caller != state.owner) {
            throw drift(operation + " moved from owner thread " + state.owner.getName() +
                    " to " + (caller == null ? "<null>" : caller.getName()));
        }
    }

    private static IllegalStateException drift(String detail) {
        return new IllegalStateException(
                "MULTIBLOCKED_SCISSOR_DRIFT: " + detail +
                        "; refusing a silent framebuffer-scissor fallback."
        );
    }

    private static final class State {
        private final Thread owner;
        private final int liveGuiScale;
        private final int liveScaledHeight;
        private final int recipeScale;
        private final int targetLogicalHeight;
        private final int translateX;
        private final int translateY;
        private int correctedCalls;

        private State(Thread owner, int liveGuiScale, int liveScaledHeight,
                      int recipeScale, int targetLogicalHeight,
                      int translateX, int translateY) {
            this.owner = owner;
            this.liveGuiScale = liveGuiScale;
            this.liveScaledHeight = liveScaledHeight;
            this.recipeScale = recipeScale;
            this.targetLogicalHeight = targetLogicalHeight;
            this.translateX = translateX;
            this.translateY = translateY;
        }
    }
}
