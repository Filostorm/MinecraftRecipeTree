package com.recipetree.jeiexport112;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public final class ExportPublicationTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void replacesAnExistingSnapshotOnlyAfterStagingIsComplete() throws Exception {
        Path parent = temporary.newFolder("publication").toPath();
        Path destination = Files.createDirectory(parent.resolve("export"));
        Files.write(destination.resolve("old.txt"), "old".getBytes(StandardCharsets.UTF_8));
        Path staging = Files.createDirectory(parent.resolve(".export.staging-test"));
        Files.write(staging.resolve("new.txt"), "new".getBytes(StandardCharsets.UTF_8));

        ExportJob.publishTransactional(staging, destination);

        assertEquals("new", new String(
                Files.readAllBytes(destination.resolve("new.txt")), StandardCharsets.UTF_8));
        assertFalse(Files.exists(destination.resolve("old.txt")));
        assertFalse(Files.exists(staging));
    }

    @Test
    public void refusesAFileOrSymbolicLinkIntroducedBeforePublication() throws Exception {
        Path parent = temporary.newFolder("publication").toPath();

        Path fileDestination = parent.resolve("file-export");
        Files.write(fileDestination, "unrelated".getBytes(StandardCharsets.UTF_8));
        Path fileStaging = Files.createDirectory(parent.resolve(".file-export.staging-test"));
        expectFailure(() -> ExportJob.publishTransactional(fileStaging, fileDestination),
                "non-directory");

        Path target = Files.createDirectory(parent.resolve("target"));
        Path linkDestination = parent.resolve("linked-export");
        Files.createSymbolicLink(linkDestination, target);
        Path linkStaging = Files.createDirectory(parent.resolve(".linked-export.staging-test"));
        expectFailure(() -> ExportJob.publishTransactional(linkStaging, linkDestination),
                "symbolic link");
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
