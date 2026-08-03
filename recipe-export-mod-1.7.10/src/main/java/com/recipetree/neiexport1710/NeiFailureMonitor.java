package com.recipetree.neiexport1710;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Converts NEI's upstream catch-log-and-continue omission paths into exporter
 * failures without modifying NEI internals.
 */
final class NeiFailureMonitor {
    private static final String LOGGER_NAME = "NotEnoughItems";
    private static final String APPENDER_NAME = "RecipeTreeExactNeiFailureMonitor";
    private static final int MAX_SAMPLES = 16;

    private final AtomicInteger pluginFailures = new AtomicInteger();
    private final AtomicInteger itemFailures = new AtomicInteger();
    private final AtomicInteger recipeFailures = new AtomicInteger();
    private final AtomicReference<String> monitorFailure = new AtomicReference<String>();
    private final List<String> samples = Collections.synchronizedList(new ArrayList<String>());
    private volatile boolean installed;

    void install() {
        if (installed || monitorFailure.get() != null) {
            return;
        }
        try {
            org.apache.logging.log4j.Logger apiLogger = LogManager.getLogger(LOGGER_NAME);
            if (!(apiLogger instanceof org.apache.logging.log4j.core.Logger)) {
                throw new IllegalStateException("NotEnoughItems logger is not a Log4j core Logger: "
                        + apiLogger.getClass().getName());
            }
            final org.apache.logging.log4j.core.Logger coreLogger =
                    (org.apache.logging.log4j.core.Logger) apiLogger;
            if (coreLogger.getAppenders().containsKey(APPENDER_NAME)) {
                throw new IllegalStateException("NEI failure-monitor appender name is already registered");
            }
            AbstractAppender appender = new AbstractAppender(APPENDER_NAME, null, null, true) {
                @Override
                public void append(LogEvent event) {
                    try {
                        if (!LOGGER_NAME.equals(event.getLoggerName()) || event.getLevel() == null
                                || !event.getLevel().isAtLeastAsSpecificAs(Level.ERROR)) {
                            return;
                        }
                        String formatted = event.getMessage() == null
                                ? "" : event.getMessage().getFormattedMessage();
                        observe(event.getLoggerName(), event.getLevel(), formatted, event.getThrown());
                    } catch (Throwable error) {
                        FatalErrors.rethrowIfFatal(error);
                        recordMonitorFailure("could not inspect NEI log event: " + safe(error));
                    }
                }
            };
            appender.start();
            coreLogger.addAppender(appender);
            if (coreLogger.getAppenders().get(APPENDER_NAME) != appender) {
                throw new IllegalStateException("NEI logger did not retain the failure-monitor appender");
            }
            installed = true;
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Installed exact NEI omission monitor for plugin/item load errors");
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            recordMonitorFailure("installation failed: " + safe(error));
            GtnhNeiExportMod.LOGGER.error(
                    "[gtnh-nei-export] Could not install exact NEI omission monitor", error);
        }
    }

    void observe(String loggerName, Level level, String formattedMessage, Throwable thrown) {
        if (!LOGGER_NAME.equals(loggerName) || level == null
                || !level.isAtLeastAsSpecificAs(Level.ERROR)) {
            return;
        }
        String message = formattedMessage == null ? "" : formattedMessage;
        if (message.startsWith("Failed to Load ")
                || message.startsWith("Failed to load plugin class ")) {
            pluginFailures.incrementAndGet();
            sample("plugin", message, thrown);
        } else if ((message.startsWith("Removing item: ") && message.endsWith(" from list."))
                || message.startsWith("Ommiting ")) {
            itemFailures.incrementAndGet();
            sample("item", message, thrown);
        } else if (message.startsWith("Error loading recipe:")
                || message.startsWith("Error in getOtherStacks:")) {
            recipeFailures.incrementAndGet();
            sample("recipe", message, thrown);
        }
    }

    String failureSummary() {
        String internal = monitorFailure.get();
        if (internal != null) {
            return "NEI omission monitor " + internal;
        }
        if (!installed) {
            return "NEI omission monitor was not installed";
        }
        int plugins = pluginFailures.get();
        int items = itemFailures.get();
        int recipes = recipeFailures.get();
        if (plugins == 0 && items == 0 && recipes == 0) {
            return null;
        }
        List<String> copy;
        synchronized (samples) {
            copy = new ArrayList<String>(samples);
        }
        return "NEI caught and continued upstream omissions; pluginFailures=" + plugins
                + ", itemFailures=" + items + ", recipeFailures=" + recipes
                + ", samples=" + copy;
    }

    int pluginFailureCount() {
        return pluginFailures.get();
    }

    int itemFailureCount() {
        return itemFailures.get();
    }

    int recipeFailureCount() {
        return recipeFailures.get();
    }

    void markInstalledForTest() {
        installed = true;
    }

    private void sample(String kind, String message, Throwable thrown) {
        synchronized (samples) {
            if (samples.size() >= MAX_SAMPLES) {
                return;
            }
            String detail = kind + ": " + safe(message);
            if (thrown != null) {
                detail += " [" + safe(thrown) + "]";
            }
            samples.add(detail);
        }
    }

    private void recordMonitorFailure(String message) {
        monitorFailure.compareAndSet(null, safe(message));
    }

    private static String safe(Object value) {
        String text = String.valueOf(value).replaceAll("[\\p{Cntrl}&&[^\\t]]", " ");
        return text.length() <= 500 ? text : text.substring(0, 500) + "…";
    }
}
