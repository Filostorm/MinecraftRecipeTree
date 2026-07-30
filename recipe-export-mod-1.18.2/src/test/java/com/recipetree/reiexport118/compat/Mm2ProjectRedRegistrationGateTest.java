package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2ProjectRedRegistrationGateTest {
    @Test
    void nonFabricatedGatesPreserveTheirUpstreamDecision() {
        assertTrue(Mm2ProjectRedRegistrationGate.registrationDecision(false, true));
        assertFalse(Mm2ProjectRedRegistrationGate.registrationDecision(false, false));
    }

    @Test
    void fabricatedGateIsNeverRegisteredByIntegrationInEitherRaceBranch() {
        assertFalse(Mm2ProjectRedRegistrationGate.registrationDecision(true, true));
        assertFalse(Mm2ProjectRedRegistrationGate.registrationDecision(true, false));
    }
}
