package com.recipetree.reiexport118;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RegistryCensusDiagnosticsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesCanonicalSnapshotOnceAndByteVerifiesIdenticalReuse() throws Exception {
        RegistryCensus.Capture capture = capture(
                Map.of("minecraft:plugins/crafting", 2),
                List.of(
                        observed("example:a", "canonical-a"),
                        observed("example:b", "canonical-b")));

        RegistryCensusDiagnostics.StoredSnapshot first =
                RegistryCensusDiagnostics.publish(temporaryDirectory, capture);
        byte[] original = Files.readAllBytes(first.path());
        RegistryCensusDiagnostics.StoredSnapshot second =
                RegistryCensusDiagnostics.publish(temporaryDirectory, capture);

        assertEquals(first.path(), second.path());
        assertArrayEquals(original, Files.readAllBytes(second.path()));
        assertEquals(
                capture,
                RegistryCensusDiagnostics.findExpected(
                                temporaryDirectory,
                                RegistryCensusDiagnostics.SnapshotId.from(capture.contract()))
                        .orElseThrow()
                        .capture());
    }

    @Test
    void rejectsModifiedSnapshotWithoutOverwritingIt() throws Exception {
        RegistryCensus.Capture capture = capture(
                Map.of("minecraft:plugins/crafting", 1),
                List.of(observed("example:a", "canonical-a")));
        Path target = RegistryCensusDiagnostics.publish(temporaryDirectory, capture).path();
        byte[] modified = "modified".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(target, modified);

        assertThrows(
                IOException.class,
                () -> RegistryCensusDiagnostics.publish(temporaryDirectory, capture));
        assertArrayEquals(modified, Files.readAllBytes(target));
    }

    @Test
    void hardLinkPublicationNeverOverwritesATargetThatAppearsConcurrently() throws Exception {
        Path completeTemporary = temporaryDirectory.resolve("complete.tmp");
        Path target = temporaryDirectory.resolve("immutable.json");
        byte[] canonical = "canonical".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] competing = "competing".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(completeTemporary, canonical);
        Files.write(target, competing);

        assertThrows(
                IOException.class,
                () -> RegistryCensusDiagnostics.publishHardLink(
                        completeTemporary,
                        target,
                        canonical,
                        "test census evidence"));

        assertArrayEquals(competing, Files.readAllBytes(target));
        assertArrayEquals(canonical, Files.readAllBytes(completeTemporary));
    }

    @Test
    void rejectsConflictingDiagnosticBytesForAnExistingAggregateWithoutOverwrite()
            throws Exception {
        RegistryCensus.Capture accepted = capture(
                Map.of("minecraft:plugins/crafting", 1),
                List.of(observed("example:a", "canonical-a")));
        Path target = RegistryCensusDiagnostics.publish(temporaryDirectory, accepted).path();
        byte[] original = Files.readAllBytes(target);

        RegistryCensus.DiagnosticSnapshot conflictingDiagnostics =
                new RegistryCensus.DiagnosticSnapshot(
                        accepted.contract().counts(),
                        accepted.contract().emptyEntries(),
                        RegistryCensus.diagnosticIdentities(
                                List.of(observed("example:a", "different-canonical-identity"))));
        RegistryCensus.Capture conflicting = new RegistryCensus.Capture(
                accepted.contract(), conflictingDiagnostics);

        assertThrows(
                IOException.class,
                () -> RegistryCensusDiagnostics.publish(temporaryDirectory, conflicting));
        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void diffPinpointsCategoryEntryKeyAndSameKeyIdentityMutations() {
        RegistryCensus.ObservedIdentity widgetBefore =
                observed("example:widget", "canonical-before");
        RegistryCensus.Capture expected = capture(
                linkedMap(
                        "minecraft:plugins/crafting", 10,
                        "minecraft:plugins/smelting", 5),
                List.of(
                        widgetBefore,
                        widgetBefore,
                        observed("example:removed", "removed")));
        RegistryCensus.Capture actual = capture(
                linkedMap(
                        "minecraft:plugins/crafting", 12,
                        "minecraft:plugins/smelting", 5),
                List.of(
                        widgetBefore,
                        observed("example:widget", "canonical-after"),
                        observed("example:added", "added")));

        RegistryCensusDiagnostics.Diff diff =
                RegistryCensusDiagnostics.diff(expected, actual);

        assertEquals(
                List.of(new RegistryCensusDiagnostics.CategoryDelta(
                        "minecraft:plugins/crafting", 10, 12)),
                diff.categories());
        assertEquals(3, diff.entries().size());
        RegistryCensusDiagnostics.EntryKeyDelta widget = diff.entries().stream()
                .filter(delta -> "example:widget".equals(delta.key().identifier()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, widget.expectedEntries());
        assertEquals(2, widget.actualEntries());
        assertEquals(1, widget.addedOccurrences());
        assertEquals(1, widget.removedOccurrences());
        assertTrue(diff.conciseSummary(1).contains("omitted=3"));
    }

    @Test
    void diffDistinguishesAnAbsentCategoryFromARegisteredZeroDisplayCategory()
            throws Exception {
        RegistryCensus.ObservedIdentity item = observed("example:a", "a");
        RegistryCensus.Capture expected = capture(
                linkedMap("example:main", 1, "example:registered_empty", 0),
                List.of(item));
        RegistryCensus.Capture actual = capture(Map.of("example:main", 1), List.of(item));

        RegistryCensusDiagnostics.Diff diff =
                RegistryCensusDiagnostics.diff(expected, actual);

        assertEquals(
                List.of(new RegistryCensusDiagnostics.CategoryDelta(
                        "example:registered_empty", 0, null)),
                diff.categories());
        assertTrue(diff.conciseSummary(24).contains("0-><absent>"));

        RegistryCensusDiagnostics.StoredSnapshot expectedStored =
                RegistryCensusDiagnostics.publish(temporaryDirectory, expected);
        RegistryCensusDiagnostics.StoredSnapshot actualStored =
                RegistryCensusDiagnostics.publish(temporaryDirectory, actual);
        RegistryCensusDiagnostics.MismatchEvidence evidence =
                RegistryCensusDiagnostics.compareExpected(
                        temporaryDirectory, expectedStored.id(), actualStored);
        assertTrue(Files.isRegularFile(evidence.diffPath()));
    }

    @Test
    void diffAndPublishedBytesAreDeterministicUnderReversedObservationOrder()
            throws Exception {
        RegistryCensus.ObservedIdentity first = observed("example:a", "a");
        RegistryCensus.ObservedIdentity second = observed("example:b", "b");
        RegistryCensus.Capture expectedForward = capture(
                Map.of("minecraft:plugins/crafting", 2), List.of(first, second));
        RegistryCensus.Capture expectedReverse = capture(
                Map.of("minecraft:plugins/crafting", 2), List.of(second, first));
        RegistryCensus.Capture actual = capture(
                Map.of("minecraft:plugins/crafting", 3),
                List.of(first, observed("example:b", "changed")));

        assertEquals(expectedForward, expectedReverse);
        assertEquals(
                RegistryCensusDiagnostics.diff(expectedForward, actual),
                RegistryCensusDiagnostics.diff(expectedReverse, actual));

        RegistryCensusDiagnostics.StoredSnapshot expectedStored =
                RegistryCensusDiagnostics.publish(temporaryDirectory, expectedForward);
        RegistryCensusDiagnostics.StoredSnapshot actualStored =
                RegistryCensusDiagnostics.publish(temporaryDirectory, actual);
        RegistryCensusDiagnostics.MismatchEvidence firstEvidence =
                RegistryCensusDiagnostics.compareExpected(
                        temporaryDirectory, expectedStored.id(), actualStored);
        byte[] firstDiff = Files.readAllBytes(firstEvidence.diffPath());
        RegistryCensusDiagnostics.MismatchEvidence secondEvidence =
                RegistryCensusDiagnostics.compareExpected(
                        temporaryDirectory, expectedStored.id(), actualStored);

        assertTrue(firstEvidence.baselineAvailable());
        assertEquals(firstEvidence.diffPath(), secondEvidence.diffPath());
        assertArrayEquals(firstDiff, Files.readAllBytes(secondEvidence.diffPath()));
    }

    @Test
    void missingExpectedBaselineIsExplicitAndPreservesCurrentSnapshot() throws Exception {
        RegistryCensus.Capture actual = capture(
                Map.of("minecraft:plugins/crafting", 1),
                List.of(observed("example:a", "a")));
        RegistryCensusDiagnostics.StoredSnapshot actualStored =
                RegistryCensusDiagnostics.publish(temporaryDirectory, actual);
        RegistryCensusDiagnostics.SnapshotId absent = new RegistryCensusDiagnostics.SnapshotId(
                "e".repeat(64), "f".repeat(64));

        RegistryCensusDiagnostics.MismatchEvidence evidence =
                RegistryCensusDiagnostics.compareExpected(
                        temporaryDirectory, absent, actualStored);

        assertFalse(evidence.baselineAvailable());
        assertTrue(evidence.summary().contains("baselineUnavailable"));
        assertTrue(evidence.summary().contains("exact localization requires"));
        assertTrue(Files.isRegularFile(evidence.actualSnapshot()));
        assertFalse(Files.exists(evidence.expectedSnapshot()));
    }

    @Test
    void rejectsDiagnosticRootSymlinkSubstitutionInsteadOfWritingThroughIt()
            throws Exception {
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        Files.createSymbolicLink(
                temporaryDirectory.resolve("reiexport-registry-census"), outside);
        RegistryCensus.Capture capture = capture(
                Map.of("minecraft:plugins/crafting", 1),
                List.of(observed("example:a", "a")));

        assertThrows(
                IOException.class,
                () -> RegistryCensusDiagnostics.publish(temporaryDirectory, capture));
        try (var children = Files.list(outside)) {
            assertEquals(0, children.count());
        }
    }

    private static RegistryCensus.ObservedIdentity observed(
            String identifier, String canonicalIdentity) {
        return RegistryCensus.ObservedIdentity.serialized(
                "minecraft:item", identifier, canonicalIdentity);
    }

    private static RegistryCensus.Capture capture(
            Map<String, Integer> categoryCounts,
            List<RegistryCensus.ObservedIdentity> observations) {
        int displays = Math.toIntExact(categoryCounts.values().stream()
                .mapToLong(Integer::longValue)
                .sum());
        RegistryCensus.Counts counts = new RegistryCensus.Counts(
                observations.size(),
                displays,
                categoryCounts.size(),
                RegistryCensus.digestCategoryCounts(categoryCounts),
                categoryCounts);
        int emptyEntries = Math.toIntExact(observations.stream()
                .filter(observation -> observation.key().kind()
                        == RegistryCensus.EntryIdentityKind.EMPTY)
                .count());
        List<RegistryCensus.EntryIdentity> identities = observations.stream()
                .map(RegistryCensus.ObservedIdentity::identity)
                .toList();
        RegistryCensus.Deep contract = new RegistryCensus.Deep(
                counts,
                emptyEntries,
                RegistryCensus.digestEntryIdentities(identities));
        RegistryCensus.DiagnosticSnapshot diagnostics =
                new RegistryCensus.DiagnosticSnapshot(
                        counts,
                        emptyEntries,
                        RegistryCensus.diagnosticIdentities(observations));
        return new RegistryCensus.Capture(contract, diagnostics);
    }

    private static Map<String, Integer> linkedMap(
            String firstKey, int firstValue, String secondKey, int secondValue) {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put(firstKey, firstValue);
        values.put(secondKey, secondValue);
        return values;
    }
}
