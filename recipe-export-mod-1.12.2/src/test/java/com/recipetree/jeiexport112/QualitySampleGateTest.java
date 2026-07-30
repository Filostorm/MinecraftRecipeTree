package com.recipetree.jeiexport112;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class QualitySampleGateTest {
    @Test
    public void fullExportMayPublishWithDiagnosticFailures() throws Exception {
        QualitySampleGate.requireNoFailureEvents(false, 37);
    }

    @Test
    public void cleanQualitySampleMayPublish() throws Exception {
        QualitySampleGate.requireNoFailureEvents(true, 0);
    }

    @Test
    public void anyQualitySampleFailureEventBlocksPublication() throws Exception {
        try {
            QualitySampleGate.requireNoFailureEvents(true, 1);
            fail("Expected quality sample publication to be rejected");
        } catch (IOException error) {
            assertTrue(error.getMessage(), error.getMessage().contains("1 failure event"));
        }
    }
}
