package com.recipetree.jeiexport112;

import java.io.IOException;

/** Final fail-closed publication requirements that apply only to explicit quality samples. */
final class QualitySampleGate {
    private QualitySampleGate() {
    }

    static void requireNoFailureEvents(boolean qualitySampleEnabled, int failureEvents)
            throws IOException {
        if (failureEvents < 0) {
            throw new IllegalArgumentException("failureEvents must not be negative");
        }
        if (qualitySampleEnabled && failureEvents != 0) {
            throw new IOException("Quality sample recorded " + failureEvents +
                    " failure event(s); refusing transactional publication");
        }
    }
}
