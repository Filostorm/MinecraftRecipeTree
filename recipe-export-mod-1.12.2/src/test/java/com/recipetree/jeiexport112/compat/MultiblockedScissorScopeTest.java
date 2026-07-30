package com.recipetree.jeiexport112.compat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class MultiblockedScissorScopeTest {
    @Test
    public void scopesMappingAndReturnsAnAggregateCallCount() {
        MultiblockedScissorScope scope = new MultiblockedScissorScope();
        Thread owner = Thread.currentThread();
        scope.begin(owner, 2, 240, 2, 92, -121, 4);

        MultiblockedScissorTransform.Box mapped = scope.mapActive(
                owner, 260, 342, 128, 128);

        assertEquals(18, mapped.x);
        assertEquals(38, mapped.y);
        assertEquals(1, scope.end(owner));
        assertFalse(scope.isActive());
    }

    @Test
    public void rejectsNestedScopesWithoutReplacingTheOriginalOwner() {
        MultiblockedScissorScope scope = new MultiblockedScissorScope();
        Thread owner = Thread.currentThread();
        scope.begin(owner, 2, 240, 2, 92, -121, 4);

        try {
            scope.begin(owner, 2, 240, 2, 92, -121, 4);
            fail("Expected nested capture to fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("nested capture"));
        }

        scope.mapActive(owner, 260, 342, 128, 128);
        assertEquals(1, scope.end(owner));
    }

    @Test
    public void clearsBeforeRejectingAnEmptyScope() {
        MultiblockedScissorScope scope = new MultiblockedScissorScope();
        Thread owner = Thread.currentThread();
        scope.begin(owner, 2, 240, 2, 92, -121, 4);

        try {
            scope.end(owner);
            fail("Expected zero corrected calls to fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("no audited glScissor calls"));
        }
        assertFalse(scope.isActive());

        scope.begin(owner, 2, 240, 2, 92, -121, 4);
        assertTrue(scope.isActive());
    }

    @Test
    public void clearsBeforeRejectingTheWrongEndThread() {
        MultiblockedScissorScope scope = new MultiblockedScissorScope();
        Thread owner = Thread.currentThread();
        Thread intruder = new Thread("scissor-intruder");
        scope.begin(owner, 2, 240, 2, 92, -121, 4);
        scope.mapActive(owner, 260, 342, 128, 128);

        try {
            scope.end(intruder);
            fail("Expected wrong owner to fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("moved from owner thread"));
        }
        assertFalse(scope.isActive());
    }
}
