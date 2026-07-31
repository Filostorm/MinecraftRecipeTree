package com.recipetree.reiexport118;

/**
 * Fail-closed ownership state for the title-screen to integrated-world handoff.
 *
 * <p>Minecraft fires one {@code LoggedOutEvent} while {@code loadLevel}/{@code createLevel}
 * replaces the menu connection state. That event is not an abandoned export request. Only the
 * first logout observed inside an explicitly-owned bootstrap call is classified as the native
 * handoff; every later logout remains an ordinary, terminal readiness interruption.</p>
 */
final class WorldBootstrapTransition {
    static final int MAX_POST_CALL_NO_LEVEL_TICKS = 1_200;

    enum Kind {
        LOAD,
        CREATE
    }

    private Kind kind;
    private String worldName;
    private String requestSha256;
    private boolean callReturned;
    private boolean handoffLogoutObserved;
    private int postCallNoLevelTicks;

    synchronized void begin(
            Kind requestedKind,
            String requestedWorldName,
            String requestedSha256
    ) {
        if (kind != null) {
            throw new IllegalStateException(
                    "World bootstrap transition was re-entered while active: " + description());
        }
        if (requestedKind == null) {
            throw new IllegalArgumentException("World bootstrap transition kind is required");
        }
        if (requestedWorldName == null || requestedWorldName.isBlank()) {
            throw new IllegalArgumentException("World bootstrap transition world name is required");
        }
        if (requestedSha256 == null || !requestedSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "World bootstrap transition requires a lowercase request SHA-256");
        }
        kind = requestedKind;
        worldName = requestedWorldName;
        requestSha256 = requestedSha256;
        callReturned = false;
        handoffLogoutObserved = false;
        postCallNoLevelTicks = 0;
    }

    synchronized void markCallReturned() {
        requireActive("World bootstrap call returned without an active transition");
        if (callReturned) {
            throw new IllegalStateException(
                    "World bootstrap call return was recorded twice: " + description());
        }
        if (!handoffLogoutObserved) {
            throw new IllegalStateException(
                    "World bootstrap call returned without exactly one owned native logout: "
                            + description());
        }
        callReturned = true;
    }

    synchronized boolean consumeExpectedLogout(String currentRequestSha256) {
        if (kind == null
                || callReturned
                || handoffLogoutObserved
                || !requestSha256.equals(currentRequestSha256)) {
            return false;
        }
        handoffLogoutObserved = true;
        return true;
    }

    synchronized void requireReadyForActiveLevel(String currentRequestSha256) {
        requireActive("A client level appeared without an active world bootstrap transition");
        if (!callReturned || !handoffLogoutObserved) {
            throw new IllegalStateException(
                    "Client level appeared before the owned world bootstrap handoff completed: "
                            + description());
        }
        if (!requestSha256.equals(currentRequestSha256)) {
            throw new IllegalStateException(
                    "Active export request bytes changed during world bootstrap: expectedSha256="
                            + requestSha256 + ", actualSha256=" + currentRequestSha256);
        }
    }

    synchronized String worldName() {
        requireActive("World bootstrap has no active requested world");
        return worldName;
    }

    synchronized boolean tickWithoutLevelTimedOut() {
        if (kind == null || !callReturned) {
            return false;
        }
        postCallNoLevelTicks++;
        return postCallNoLevelTicks >= MAX_POST_CALL_NO_LEVEL_TICKS;
    }

    synchronized boolean isActive() {
        return kind != null;
    }

    synchronized String description() {
        if (kind == null) {
            return "inactive";
        }
        return "kind=" + kind
                + ", world=" + worldName
                + ", requestSha256=" + requestSha256
                + ", callReturned=" + callReturned
                + ", handoffLogoutObserved=" + handoffLogoutObserved
                + ", postCallNoLevelTicks=" + postCallNoLevelTicks;
    }

    synchronized void clear() {
        kind = null;
        worldName = null;
        requestSha256 = null;
        callReturned = false;
        handoffLogoutObserved = false;
        postCallNoLevelTicks = 0;
    }

    private void requireActive(String message) {
        if (kind == null) {
            throw new IllegalStateException(message);
        }
    }
}
