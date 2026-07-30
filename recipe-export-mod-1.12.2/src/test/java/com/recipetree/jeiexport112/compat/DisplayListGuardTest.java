package com.recipetree.jeiexport112.compat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class DisplayListGuardTest {
    @Test
    public void rendererBootstrapLeavesAnAlreadyCurrentDisplayUntouched() {
        FakeLifecycle lifecycle = new FakeLifecycle();
        lifecycle.current = true;

        DisplayListGuard.ensureDisplayCurrentForRendererBootstrap(lifecycle);

        assertEquals(Arrays.asList("display-current:true"), lifecycle.events);
        assertEquals(0, lifecycle.inspectCalls);
        assertEquals(0, lifecycle.errorCalls);
    }

    @Test
    public void rendererBootstrapReacquiresExactDisabledSplashBeforeAnyGlUpload() {
        FakeLifecycle lifecycle = new FakeLifecycle();
        lifecycle.useExactDisabledSplashPolicy();
        lifecycle.useReportedErrors(1281, 0);

        DisplayListGuard.ensureDisplayCurrentForRendererBootstrap(lifecycle);

        assertEquals(true, lifecycle.current);
        assertEquals(2, lifecycle.inspectCalls);
        assertEquals(2, lifecycle.errorCalls);
        assertTrue(lifecycle.events.indexOf("make-current") <
                lifecycle.events.indexOf("error:1281"));
    }

    @Test
    public void rendererBootstrapRejectsMissingDisabledSplashPolicyWithoutTransfer() {
        FakeLifecycle lifecycle = new FakeLifecycle();

        try {
            DisplayListGuard.ensureDisplayCurrentForRendererBootstrap(lifecycle);
            fail("Expected missing disabled-splash policy to fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains(
                    "without the launcher-authorized disabled-splash policy"));
        }
        assertEquals(false, lifecycle.events.contains("make-current"));
        assertEquals(0, lifecycle.errorCalls);
    }

    @Test
    public void rendererBootstrapRejectsDriftedDisabledSplashOwnershipBeforeTransfer() {
        FakeLifecycle lifecycle = new FakeLifecycle();
        lifecycle.useExactDisabledSplashPolicy();
        lifecycle.splashThreadPresent = true;

        try {
            DisplayListGuard.ensureDisplayCurrentForRendererBootstrap(lifecycle);
            fail("Expected constructed splash thread to fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("thread=null"));
        }
        assertEquals(false, lifecycle.events.contains("make-current"));
        assertEquals(0, lifecycle.errorCalls);
    }

    @Test
    public void returnsInitialAllocationWithoutInspectingOwnership() {
        FakeLifecycle lifecycle = new FakeLifecycle(37);

        assertEquals(37, DisplayListGuard.generateDisplayLists(9, lifecycle));
        assertEquals(Arrays.asList("generate:9"), lifecycle.events);
    }

    @Test
    public void performsOneForgeManagedRetryAndVerifiesRestoredSharedDrawable() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 41);

        assertEquals(41, DisplayListGuard.generateDisplayLists(1, lifecycle));
        assertEquals(2, lifecycle.generateCalls);
        assertEquals(1, lifecycle.resumeCalls);
        assertEquals(false, lifecycle.current);
        assertEquals(true, lifecycle.splashDrawableCurrent);
        assertEquals(false, lifecycle.splashPaused);
        assertEquals(3, lifecycle.inspectCalls);
    }

    @Test
    public void activeSplashDrainsPreexistingContextErrorBeforeSoleRetryAndRestores()
            throws Exception {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 41);
        lifecycle.useReportedErrors(0, 1281, 0, 0);

        LoggedAllocation allocation = generateWithCapturedErrorLog(lifecycle, 1);

        assertEquals(41, allocation.displayList);
        assertTrue(allocation.log.contains("Completed bounded pre-operation GL error drain"));
        assertTrue(allocation.log.contains("Forge-managed context transfer"));
        assertTrue(allocation.log.contains("drained=1"));
        assertTrue(allocation.log.contains("GL error 1281"));
        assertEquals(2, lifecycle.generateCalls);
        assertEquals(4, lifecycle.errorCalls);
        assertEquals(1, lifecycle.resumeCalls);
        assertEquals(false, lifecycle.current);
        assertEquals(true, lifecycle.splashDrawableCurrent);
        assertEquals(false, lifecycle.splashPaused);
        assertRetryBracketedByTerminalDrainAndPostCallSample(lifecycle);
    }

    @Test
    public void refusesRetryWhenPauseDidNotMakeDisplayCurrent() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 41);
        lifecycle.pauseMakesDisplayCurrent = false;

        expectFailure(lifecycle, 1, "did not make the Display drawable current");
        assertEquals(1, lifecycle.generateCalls);
        assertEquals(1, lifecycle.resumeCalls);
        assertEquals(false, lifecycle.current);
        assertEquals(true, lifecycle.splashDrawableCurrent);
    }

    @Test
    public void reacquiresSameDisplayOnlyForExactUninitializedDisabledSplash() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 43);
        lifecycle.useExactDisabledSplashPolicy();

        assertEquals(43, DisplayListGuard.generateDisplayLists(1, lifecycle));
        assertEquals(2, lifecycle.generateCalls);
        assertEquals(0, lifecycle.resumeCalls);
        assertEquals(true, lifecycle.current);
        assertEquals(2, lifecycle.inspectCalls);
        assertTrue(lifecycle.events.contains("make-current"));
    }

    @Test
    public void disabledSplashDrainsPreexistingContextErrorBeforeSoleRetry()
            throws Exception {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 43);
        lifecycle.useExactDisabledSplashPolicy();
        lifecycle.useReportedErrors(0, 1281, 0, 0);

        LoggedAllocation allocation = generateWithCapturedErrorLog(lifecycle, 1);

        assertEquals(43, allocation.displayList);
        assertTrue(allocation.log.contains("Completed bounded pre-operation GL error drain"));
        assertTrue(allocation.log.contains("same-Display reacquisition"));
        assertTrue(allocation.log.contains("drained=1"));
        assertTrue(allocation.log.contains("GL error 1281"));
        assertEquals(2, lifecycle.generateCalls);
        assertEquals(4, lifecycle.errorCalls);
        assertEquals(0, lifecycle.resumeCalls);
        assertEquals(true, lifecycle.current);
        assertRetryBracketedByTerminalDrainAndPostCallSample(lifecycle);
    }

    @Test
    public void disabledSplashWithoutLauncherPolicyIsRejected() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 43);
        lifecycle.splashEnabled = false;
        lifecycle.splashDrawablePresent = false;
        lifecycle.splashThreadPresent = false;
        lifecycle.splashDrawableCurrent = false;

        expectFailure(lifecycle, 1, "active-splash ownership invariant failed");
        assertEquals(1, lifecycle.generateCalls);
    }

    @Test
    public void launcherDisabledPolicyRejectsActiveSplash() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 43);
        lifecycle.directDisplayRecoveryAuthorized = true;

        expectFailure(lifecycle, 1, "uninitialized disabled SplashProgress state");
        assertEquals(1, lifecycle.generateCalls);
    }

    @Test
    public void disabledSplashRejectsConstructedSharedDrawable() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 43);
        lifecycle.useExactDisabledSplashPolicy();
        lifecycle.splashDrawablePresent = true;

        expectFailure(lifecycle, 1, "d=null");
        assertEquals(1, lifecycle.generateCalls);
        assertEquals(false, lifecycle.events.contains("make-current"));
    }

    @Test
    public void disabledSplashRejectsConstructedThread() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 43);
        lifecycle.useExactDisabledSplashPolicy();
        lifecycle.splashThreadPresent = true;

        expectFailure(lifecycle, 1, "thread=null");
        assertEquals(1, lifecycle.generateCalls);
        assertEquals(false, lifecycle.events.contains("make-current"));
    }

    @Test
    public void validatesOnlyExactInstalledSplashProgressClassHash() {
        DisplayListGuard.validateSplashProgressClassSha256(
                DisplayListGuard.EXPECTED_SPLASH_PROGRESS_SHA256
        );
        try {
            DisplayListGuard.validateSplashProgressClassSha256(
                    "091894c9af9d7daaacf7b2179a190482cacbb4f41692891fb34acdd661318682"
            );
            fail("Expected class-hash drift refusal");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("SHA-256 drifted"));
        }
    }

    @Test
    public void disabledSplashReacquireMustMakeDisplayCurrent() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 43);
        lifecycle.useExactDisabledSplashPolicy();
        lifecycle.directMakeMakesDisplayCurrent = false;

        expectFailure(lifecycle, 1, "did not make it current");
        assertEquals(1, lifecycle.generateCalls);
        assertEquals(0, lifecycle.resumeCalls);
    }

    @Test
    public void refusesRecoveryForRangeOtherThanObservedOne() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 43);
        lifecycle.useExactDisabledSplashPolicy();

        expectFailure(lifecycle, 4, "range=4");
        assertEquals(1, lifecycle.generateCalls);
        assertEquals(0, lifecycle.inspectCalls);
        assertEquals(false, lifecycle.events.contains("make-current"));
    }

    @Test
    public void refusesRecoveryForNonzeroInitialGlError() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 43);
        lifecycle.useExactDisabledSplashPolicy();
        lifecycle.error = 1282;

        expectFailure(lifecycle, 1, "initial GL error=1282");
        assertEquals(1, lifecycle.generateCalls);
        assertEquals(0, lifecycle.inspectCalls);
    }

    @Test
    public void refusesRecoveryWhenDisplayWasAlreadyCurrent() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 43);
        lifecycle.useExactDisabledSplashPolicy();
        lifecycle.current = true;

        expectFailure(lifecycle, 1, "Display.isCurrent()=true");
        assertEquals(1, lifecycle.generateCalls);
        assertEquals(0, lifecycle.inspectCalls);
    }

    @Test
    public void failedActiveRetryRestoresOwnershipAndNeverFabricatesId() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 0);

        expectFailure(lifecycle, 1, "still returned 0");
        assertEquals(2, lifecycle.generateCalls);
        assertEquals(1, lifecycle.resumeCalls);
        assertEquals(false, lifecycle.current);
        assertEquals(true, lifecycle.splashDrawableCurrent);
    }

    @Test
    public void failedDisabledRetryNeverFabricatesId() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 0);
        lifecycle.useExactDisabledSplashPolicy();

        expectFailure(lifecycle, 1, "still returned 0");
        assertEquals(2, lifecycle.generateCalls);
        assertEquals(0, lifecycle.resumeCalls);
    }

    @Test
    public void disabledRetryRejectsNonzeroIdWithFreshPostCall1281() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 47);
        lifecycle.useExactDisabledSplashPolicy();
        lifecycle.useReportedErrors(0, 0, 1281);

        expectFailure(lifecycle, 1, "immediately sampled retry GL error was 1281");
        assertEquals(2, lifecycle.generateCalls);
        assertEquals(3, lifecycle.errorCalls);
        assertEquals(0, lifecycle.resumeCalls);
    }

    @Test
    public void activeRetryRejectsFreshPostCall1281AfterRestoringOwnership() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 53);
        lifecycle.useReportedErrors(0, 0, 1281);

        expectFailure(lifecycle, 1, "immediately sampled retry GL error was 1281");
        assertEquals(2, lifecycle.generateCalls);
        assertEquals(3, lifecycle.errorCalls);
        assertEquals(1, lifecycle.resumeCalls);
        assertEquals(false, lifecycle.current);
        assertEquals(true, lifecycle.splashDrawableCurrent);
        assertEquals(false, lifecycle.splashPaused);
    }
    @Test
    public void disabledSplashDrainBoundRejectsWithoutAllocationRetry() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 67);
        lifecycle.useExactDisabledSplashPolicy();
        lifecycle.useReportedErrors(repeatedPreRetryErrors(1281));

        expectFailure(
                lifecycle,
                1,
                "did not reach GL error 0 within the bounded " +
                        DisplayListGuard.MAX_PRE_RETRY_GL_ERROR_SAMPLES
        );
        assertEquals(1, lifecycle.generateCalls);
        assertEquals(
                1 + DisplayListGuard.MAX_PRE_RETRY_GL_ERROR_SAMPLES,
                lifecycle.errorCalls
        );
        assertEquals(0, lifecycle.resumeCalls);
    }

    @Test
    public void activeSplashDrainBoundRejectsWithoutRetryAndRestoresOwnership() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 71);
        lifecycle.useReportedErrors(repeatedPreRetryErrors(1281));

        expectFailure(
                lifecycle,
                1,
                "did not reach GL error 0 within the bounded " +
                        DisplayListGuard.MAX_PRE_RETRY_GL_ERROR_SAMPLES
        );
        assertEquals(1, lifecycle.generateCalls);
        assertEquals(
                1 + DisplayListGuard.MAX_PRE_RETRY_GL_ERROR_SAMPLES,
                lifecycle.errorCalls
        );
        assertEquals(1, lifecycle.resumeCalls);
        assertEquals(false, lifecycle.current);
        assertEquals(true, lifecycle.splashDrawableCurrent);
        assertEquals(false, lifecycle.splashPaused);
    }

    @Test
    public void activeSplashErrorDrainExceptionRejectsWithoutRetryAndRestoresOwnership() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 73);
        RuntimeException queryFailure = new RuntimeException("glGetError failed");
        lifecycle.errorFailure = queryFailure;
        lifecycle.errorFailureCall = 1;

        try {
            DisplayListGuard.generateDisplayLists(1, lifecycle);
            fail("Expected pre-retry error-query failure");
        } catch (RuntimeException expected) {
            assertSame(queryFailure, expected);
        }
        assertEquals(1, lifecycle.generateCalls);
        assertEquals(1, lifecycle.resumeCalls);
        assertEquals(false, lifecycle.current);
        assertEquals(true, lifecycle.splashDrawableCurrent);
        assertEquals(false, lifecycle.splashPaused);
    }

    @Test
    public void restorationFailureRejectsSuccessfulRetry() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 59);
        RuntimeException restoration = new RuntimeException("resume failed");
        lifecycle.resumeFailure = restoration;

        try {
            DisplayListGuard.generateDisplayLists(1, lifecycle);
            fail("Expected restoration failure");
        } catch (RuntimeException expected) {
            assertSame(restoration, expected);
        }
        assertEquals(2, lifecycle.generateCalls);
        assertEquals(1, lifecycle.resumeCalls);
    }

    @Test
    public void retryAndRestorationFailuresAreBothSurfaced() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 0);
        RuntimeException restoration = new RuntimeException("resume failed");
        lifecycle.resumeFailure = restoration;

        try {
            DisplayListGuard.generateDisplayLists(1, lifecycle);
            fail("Expected retry failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("still returned 0"));
            assertEquals(1, expected.getSuppressed().length);
            assertSame(restoration, expected.getSuppressed()[0]);
        }
    }

    @Test
    public void activeSplashResumeNoOpIsRejectedByPostcondition() {
        FakeLifecycle lifecycle = new FakeLifecycle(0, 61);
        lifecycle.resumeRestoresOwnership = false;

        expectFailure(lifecycle, 1, "did not restore exact shared ownership");
        assertEquals(2, lifecycle.generateCalls);
        assertEquals(1, lifecycle.resumeCalls);
        assertEquals(true, lifecycle.current);
        assertEquals(false, lifecycle.splashDrawableCurrent);
    }

    private static void expectFailure(FakeLifecycle lifecycle, int range,
                                      String expectedMessage) {
        try {
            DisplayListGuard.generateDisplayLists(range, lifecycle);
            fail("Expected failure containing: " + expectedMessage);
        } catch (IllegalStateException expected) {
            assertTrue(
                    "Expected message to contain " + expectedMessage + ", got: " +
                            expected.getMessage(),
                    expected.getMessage().contains(expectedMessage)
            );
        }
    }

    private static LoggedAllocation generateWithCapturedErrorLog(
            FakeLifecycle lifecycle, int range) throws Exception {
        PrintStream original = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(output, true, "UTF-8");
        int displayList;
        try {
            System.setErr(capture);
            displayList = DisplayListGuard.generateDisplayLists(range, lifecycle);
        } finally {
            capture.flush();
            System.setErr(original);
            capture.close();
        }
        return new LoggedAllocation(displayList, output.toString("UTF-8"));
    }

    private static void assertRetryBracketedByTerminalDrainAndPostCallSample(
            FakeLifecycle lifecycle) {
        int retryIndex = lifecycle.events.lastIndexOf("generate:1");
        assertTrue("Expected the single allocation retry", retryIndex > 0);
        assertEquals("error:0", lifecycle.events.get(retryIndex - 1));
        assertEquals("error:0", lifecycle.events.get(retryIndex + 1));
    }

    private static int[] repeatedPreRetryErrors(int error) {
        int[] errors = new int[1 + DisplayListGuard.MAX_PRE_RETRY_GL_ERROR_SAMPLES];
        errors[0] = 0;
        Arrays.fill(errors, 1, errors.length, error);
        return errors;
    }

    private static final class LoggedAllocation {
        final int displayList;
        final String log;

        LoggedAllocation(int displayList, String log) {
            this.displayList = displayList;
            this.log = log;
        }
    }

    private static final class FakeLifecycle implements DisplayListGuard.GraphicsLifecycle {
        final List<String> events = new ArrayList<String>();
        final int[] generatedIds;
        int generateCalls;
        int resumeCalls;
        int inspectCalls;
        int error;
        int errorCalls;
        int[] reportedErrors;
        RuntimeException errorFailure;
        int errorFailureCall = -1;
        boolean current;
        boolean directDisplayRecoveryAuthorized;
        boolean splashEnabled = true;
        boolean splashPaused;
        boolean splashDrawablePresent = true;
        boolean splashThreadPresent = true;
        boolean splashDrawableCurrent = true;
        boolean directMakeMakesDisplayCurrent = true;
        boolean pauseMakesDisplayCurrent = true;
        boolean resumeRestoresOwnership = true;
        RuntimeException resumeFailure;

        FakeLifecycle(int... generatedIds) {
            this.generatedIds = generatedIds;
        }

        void useExactDisabledSplashPolicy() {
            directDisplayRecoveryAuthorized = true;
            splashEnabled = false;
            splashPaused = false;
            splashDrawablePresent = false;
            splashThreadPresent = false;
            splashDrawableCurrent = false;
        }

        void useReportedErrors(int... errors) {
            reportedErrors = errors;
        }

        @Override
        public int generateDisplayLists(int range) {
            events.add("generate:" + range);
            if (generateCalls >= generatedIds.length) {
                throw new AssertionError("Unexpected extra glGenLists call");
            }
            return generatedIds[generateCalls++];
        }

        @Override
        public int getError() {
            if (errorCalls == errorFailureCall) {
                throw errorFailure;
            }
            if (reportedErrors != null && errorCalls >= reportedErrors.length) {
                throw new AssertionError("Unexpected extra glGetError call");
            }
            int reportedError = reportedErrors == null
                    ? error
                    : reportedErrors[errorCalls];
            errorCalls++;
            events.add("error:" + reportedError);
            return reportedError;
        }

        @Override
        public boolean isDisplayCurrent() {
            events.add("display-current:" + current);
            return current;
        }

        @Override
        public boolean isDirectDisplayRecoveryAuthorized() {
            events.add("policy:" + directDisplayRecoveryAuthorized);
            return directDisplayRecoveryAuthorized;
        }

        @Override
        public DisplayListGuard.SplashOwnership inspectSplashOwnership() {
            inspectCalls++;
            DisplayListGuard.SplashOwnership ownership =
                    new DisplayListGuard.SplashOwnership(
                            splashEnabled,
                            splashPaused,
                            splashDrawablePresent,
                            splashThreadPresent,
                            splashDrawableCurrent
                    );
            events.add("splash:{" + ownership.describe() + "}");
            return ownership;
        }

        @Override
        public void makeDisplayCurrent() {
            events.add("make-current");
            if (directMakeMakesDisplayCurrent) {
                current = true;
            }
        }

        @Override
        public void pauseSplash() {
            events.add("pause");
            splashPaused = true;
            splashDrawableCurrent = false;
            if (pauseMakesDisplayCurrent) {
                current = true;
            }
        }

        @Override
        public void resumeSplash() {
            events.add("resume");
            resumeCalls++;
            if (resumeFailure != null) {
                throw resumeFailure;
            }
            if (resumeRestoresOwnership) {
                current = false;
                splashPaused = false;
                splashDrawableCurrent = true;
            }
        }
    }
}
