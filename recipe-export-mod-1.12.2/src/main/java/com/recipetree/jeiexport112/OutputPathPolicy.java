package com.recipetree.jeiexport112;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Prevents the transactional publisher from replacing a game directory, one of its operational
 * directories, a regular file, or a symbolic-link destination because of a mistyped output path.
 */
final class OutputPathPolicy {
    private static final Set<String> PROTECTED_GAME_CHILDREN = new HashSet<String>(Arrays.asList(
            "assets", "config", "crash-reports", "libraries", "logs", "mods",
            "resourcepacks", "saves", "screenshots", "shaderpacks", "versions"
    ));

    private OutputPathPolicy() {
    }

    static Path validate(Path gameDirectory, Path output) throws IOException {
        Path game = gameDirectory.toAbsolutePath().normalize();
        Path destination = output.toAbsolutePath().normalize();
        if (destination.getParent() == null || destination.getFileName() == null) {
            throw new IOException("Export output must have a parent directory and file name: " +
                    destination);
        }
        if (game.startsWith(destination)) {
            throw new IOException(
                    "Refusing to use the game directory or one of its ancestors as export output: " +
                            destination
            );
        }
        if (destination.startsWith(game)) {
            Path relative = game.relativize(destination);
            if (relative.getNameCount() > 0) {
                String name = relative.getName(0).toString().toLowerCase(Locale.ROOT);
                if (PROTECTED_GAME_CHILDREN.contains(name)) {
                    throw new IOException(
                            "Refusing export output inside protected game directory '" + name + "'"
                    );
                }
            }
        }
        if (Files.isSymbolicLink(destination)) {
            throw new IOException("Refusing symbolic-link export output: " + destination);
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Existing export output must be a directory: " + destination);
        }
        return destination;
    }
}
