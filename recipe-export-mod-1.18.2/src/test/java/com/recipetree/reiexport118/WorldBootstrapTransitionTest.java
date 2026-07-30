package com.recipetree.reiexport118;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBootstrapTransitionTest {
    private static final String REQUEST_A = "a".repeat(64);
    private static final String REQUEST_B = "b".repeat(64);

    @Test
    void permitsExactlyOneOwnedLogoutAndReturnsToOrdinaryAfterCompletion() {
        WorldBootstrapTransition transition = new WorldBootstrapTransition();
        transition.begin(
                WorldBootstrapTransition.Kind.CREATE,
                "reiexport-mm2-world",
                REQUEST_A);

        assertTrue(transition.consumeExpectedLogout(REQUEST_A));
        assertFalse(transition.consumeExpectedLogout(REQUEST_A));

        transition.markCallReturned();
        transition.requireReadyForActiveLevel(REQUEST_A);
        assertFalse(transition.consumeExpectedLogout(REQUEST_A));
        transition.clear();
        assertFalse(transition.consumeExpectedLogout(REQUEST_A));
    }

    @Test
    void replacementRequestNeverClassifiesLogoutAsOwnedHandoff() {
        WorldBootstrapTransition transition = new WorldBootstrapTransition();
        transition.begin(
                WorldBootstrapTransition.Kind.LOAD,
                "reiexport-mm2-world",
                REQUEST_A);

        assertFalse(transition.consumeExpectedLogout(REQUEST_B));
        assertTrue(transition.consumeExpectedLogout(REQUEST_A));
        transition.markCallReturned();
        assertThrows(
                IllegalStateException.class,
                () -> transition.requireReadyForActiveLevel(REQUEST_B));
    }

    @Test
    void postCallNoLevelTimeoutIsBoundedButNeverRunsBeforeCallReturns() {
        WorldBootstrapTransition transition = new WorldBootstrapTransition();
        transition.begin(
                WorldBootstrapTransition.Kind.CREATE,
                "reiexport-mm2-world",
                REQUEST_A);

        for (int tick = 0; tick < WorldBootstrapTransition.MAX_POST_CALL_NO_LEVEL_TICKS; tick++) {
            assertFalse(transition.tickWithoutLevelTimedOut());
        }

        assertTrue(transition.consumeExpectedLogout(REQUEST_A));
        transition.markCallReturned();
        for (int tick = 1;
                tick < WorldBootstrapTransition.MAX_POST_CALL_NO_LEVEL_TICKS;
                tick++) {
            assertFalse(transition.tickWithoutLevelTimedOut());
        }
        assertTrue(transition.tickWithoutLevelTimedOut());
    }

    @Test
    void transitionCannotBeReenteredOrRecordTwoReturns() {
        WorldBootstrapTransition transition = new WorldBootstrapTransition();
        transition.begin(
                WorldBootstrapTransition.Kind.LOAD,
                "reiexport-mm2-world",
                REQUEST_A);

        assertThrows(IllegalStateException.class, () -> transition.begin(
                WorldBootstrapTransition.Kind.CREATE, "other-world", REQUEST_B));
        assertTrue(transition.consumeExpectedLogout(REQUEST_A));
        transition.markCallReturned();
        assertThrows(IllegalStateException.class, transition::markCallReturned);
    }

    @Test
    void callReturnWithoutTheNativeLogoutFailsClosed() {
        WorldBootstrapTransition transition = new WorldBootstrapTransition();
        transition.begin(
                WorldBootstrapTransition.Kind.CREATE,
                "reiexport-mm2-world",
                REQUEST_A);

        assertThrows(IllegalStateException.class, transition::markCallReturned);
    }
}
