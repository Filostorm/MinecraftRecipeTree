package com.recipetree.jeiexport112;

import java.io.Closeable;
import java.io.IOException;

interface ExportPhase extends Closeable {
    /** Performs one bounded unit of client-thread work. */
    boolean step() throws IOException;

    String label();

    int done();

    int total();

    @Override
    void close() throws IOException;
}
