package com.recipetree.neiexport1710;

import org.apache.logging.log4j.Level;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NeiFailureMonitorTest {
    @Test
    public void convertsOnlyExactNeiCatchAndContinueErrorsIntoFailures() {
        NeiFailureMonitor monitor = new NeiFailureMonitor();
        monitor.markInstalledForTest();

        monitor.observe("OtherLogger", Level.ERROR, "Failed to Load ignored.Plugin", null);
        monitor.observe("NotEnoughItems", Level.WARN, "Failed to Load ignored.Plugin", null);
        monitor.observe("NotEnoughItems", Level.ERROR, "Unrelated NEI error", null);
        assertNull(monitor.failureSummary());

        monitor.observe("NotEnoughItems", Level.ERROR,
                "Failed to Load example.BrokenPlugin", new IllegalStateException("plugin broke"));
        monitor.observe("NotEnoughItems", Level.ERROR,
                "Removing item: example:broken from list.", new RuntimeException("item broke"));
        monitor.observe("NotEnoughItems", Level.ERROR,
                "Ommiting example:item:4 ExampleItem", new RuntimeException("variant broke"));
        monitor.observe("NotEnoughItems", Level.ERROR,
                "Error loading recipe: ", new RuntimeException("recipe broke"));
        monitor.observe("NotEnoughItems", Level.ERROR,
                "Failed to load plugin class missing.LegacyPlugin", new ClassNotFoundException());

        assertEquals(2, monitor.pluginFailureCount());
        assertEquals(2, monitor.itemFailureCount());
        assertEquals(1, monitor.recipeFailureCount());
        String summary = monitor.failureSummary();
        assertTrue(summary, summary.contains("pluginFailures=2"));
        assertTrue(summary, summary.contains("itemFailures=2"));
        assertTrue(summary, summary.contains("recipeFailures=1"));
        assertTrue(summary, summary.contains("example.BrokenPlugin"));
        assertTrue(summary, summary.contains("example:broken"));
        assertTrue(summary, summary.contains("missing.LegacyPlugin"));
    }
}
