package com.recipetree.jeiexport112;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class OutputPathPolicyTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void acceptsDedicatedDirectoriesInsideOrOutsideTheGameDirectory() throws Exception {
        Path root = temporary.newFolder("root").toPath();
        Path game = Files.createDirectory(root.resolve("instance"));

        assertEquals(game.resolve("jei-exports"),
                OutputPathPolicy.validate(game, game.resolve("jei-exports")));
        assertEquals(root.resolve("published-export"),
                OutputPathPolicy.validate(game, root.resolve("published-export")));
    }

    @Test
    public void rejectsGameAncestorsAndProtectedOperationalTrees() throws Exception {
        Path root = temporary.newFolder("root").toPath();
        Path game = Files.createDirectory(root.resolve("instance"));

        expectFailure(() -> OutputPathPolicy.validate(game, game), "ancestors");
        expectFailure(() -> OutputPathPolicy.validate(game, root), "ancestors");
        expectFailure(() -> OutputPathPolicy.validate(game, game.resolve("mods")), "protected");
        expectFailure(() -> OutputPathPolicy.validate(
                game, game.resolve("saves").resolve("automation")), "protected");
    }

    @Test
    public void rejectsExistingFilesAndSymbolicLinkDestinations() throws Exception {
        Path root = temporary.newFolder("root").toPath();
        Path game = Files.createDirectory(root.resolve("instance"));
        Path regularFile = root.resolve("not-a-directory");
        Files.write(regularFile, "data".getBytes(StandardCharsets.UTF_8));
        expectFailure(() -> OutputPathPolicy.validate(game, regularFile), "must be a directory");

        Path target = Files.createDirectory(root.resolve("target"));
        Path link = root.resolve("linked-export");
        Files.createSymbolicLink(link, target);
        expectFailure(() -> OutputPathPolicy.validate(game, link), "symbolic-link");
    }

    private static void expectFailure(ThrowingRunnable operation, String fragment) throws Exception {
        try {
            operation.run();
            fail("Expected IOException containing " + fragment);
        } catch (IOException expected) {
            if (!expected.getMessage().contains(fragment)) {
                throw new AssertionError("Expected '" + fragment + "' in '" +
                        expected.getMessage() + "'", expected);
            }
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
