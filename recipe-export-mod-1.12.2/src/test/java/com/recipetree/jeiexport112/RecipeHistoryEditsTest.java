package com.recipetree.jeiexport112;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RecipeHistoryEditsTest {
    @Test
    public void ordinaryEditsReplaceTheCurrentHistoryEntry() {
        List<String> history = new ArrayList<String>(
                Collections.singletonList("tree before edit"));

        int selected = RecipeHistoryEdits.commit(
                history, 0, "tree after edit", false);

        assertEquals(Collections.singletonList("tree after edit"), history);
        assertEquals(0, selected);
    }

    @Test
    public void editingASnapshotPreservesItAndCreatesOneWorkingEntry() {
        List<String> history = new ArrayList<String>(
                Collections.singletonList("saved snapshot"));

        int selected = RecipeHistoryEdits.commit(
                history, 0, "edited working tree", true);

        assertEquals(Arrays.asList("saved snapshot", "edited working tree"), history);
        assertEquals(1, selected);
    }

    @Test
    public void editingOlderHistoryDiscardsItsForwardBranchWithoutAppending() {
        List<String> history = new ArrayList<String>(Arrays.asList("older", "newer"));

        int selected = RecipeHistoryEdits.commit(
                history, 0, "changed older", false);

        assertEquals(Collections.singletonList("changed older"), history);
        assertEquals(0, selected);
    }

    @Test
    public void anInvalidSelectionAppendsAWorkingEntry() {
        List<String> history = new ArrayList<String>();

        int selected = RecipeHistoryEdits.commit(
                history, -1, "first tree", false);

        assertEquals(Collections.singletonList("first tree"), history);
        assertEquals(0, selected);
    }

    @Test
    public void snapshotCreatesASavedBaselineAndSelectedWorkingVersion() {
        List<String> history = new ArrayList<String>(
                Collections.singletonList("unsaved current"));

        int selected = RecipeHistoryEdits.saveSnapshot(
                history, 0, "saved version 1", "working version 2");

        assertEquals(Arrays.asList("saved version 1", "working version 2"), history);
        assertEquals(1, selected);
    }

    @Test
    public void anotherSnapshotTurnsTheWorkingVersionIntoTheNextBaseline() {
        List<String> history = new ArrayList<String>(Arrays.asList(
                "saved version 1", "working version 2", "discarded future"));

        int selected = RecipeHistoryEdits.saveSnapshot(
                history, 1, "saved version 2", "working version 3");

        assertEquals(Arrays.asList(
                "saved version 1", "saved version 2", "working version 3"), history);
        assertEquals(2, selected);
    }
}
