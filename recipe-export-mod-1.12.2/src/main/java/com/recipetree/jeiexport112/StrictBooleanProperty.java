package com.recipetree.jeiexport112;

/**
 * Canonical parser for exporter JVM booleans. Java's Boolean.parseBoolean maps every typo to
 * false, which would silently disable automation or a requested compatibility contract.
 */
public final class StrictBooleanProperty {
    private StrictBooleanProperty() {
    }

    public static boolean read(String propertyName, boolean defaultValue) {
        String value = System.getProperty(propertyName);
        return value == null ? defaultValue : parse(propertyName, value);
    }

    public static boolean parse(String propertyName, String value) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalStateException(
                "[jeiexport] JVM property " + propertyName +
                        " must be exactly true or false; got " + printable(value)
        );
    }

    private static String printable(String value) {
        return value == null ? "<null>" : value.isEmpty() ? "<empty>" : "'" + value + "'";
    }
}
