package com.recipetree.reiexport118.mixin;

import java.nio.file.Path;

/** Thread-safe fail-closed publication for an irreversible, bytecode-audited Mixin choice. */
final class ExactRuntimeSelection {
    private final String label;
    private Path selectedGameDirectory;

    ExactRuntimeSelection(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("exact runtime selection requires a label");
        }
        this.label = label;
    }

    synchronized boolean publish(Path gameDirectory) {
        Path normalized = normalize(gameDirectory);
        if (selectedGameDirectory == null) {
            selectedGameDirectory = normalized;
            return true;
        }
        if (!selectedGameDirectory.equals(normalized)) {
            throw new IllegalStateException(label + " changed game directories: prior="
                    + selectedGameDirectory + ", current=" + normalized);
        }
        return false;
    }

    synchronized void require(Path gameDirectory) {
        Path expected = normalize(gameDirectory);
        if (!expected.equals(selectedGameDirectory)) {
            throw new IllegalStateException(label + " was not published for the active game "
                    + "directory: expected=" + expected + ", selected=" + selectedGameDirectory);
        }
    }

    private static Path normalize(Path gameDirectory) {
        if (gameDirectory == null) {
            throw new IllegalStateException("exact runtime selection has no game directory");
        }
        return gameDirectory.toAbsolutePath().normalize();
    }
}
