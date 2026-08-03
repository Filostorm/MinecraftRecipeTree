package com.recipetree.neiexport1710;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class BetterQuestingChoiceContractTest {
    @Test
    public void exactChoiceFieldAndMethodShareOneOrderedList() throws Exception {
        BigStackFixture first = new BigStackFixture("first");
        BigStackFixture second = new BigStackFixture("second");
        ValidChoice choice = new ValidChoice(first, second);

        BetterQuestingChoiceContract contract = bind(ValidChoice.class);

        List<?> observed = contract.choices(choice);
        assertSame(choice.choices, observed);
        assertSame(first, observed.get(0));
        assertSame(second, observed.get(1));
    }

    @Test
    public void copiedGetItemOutputsListFailsClosed() throws Exception {
        CopyingChoice choice = new CopyingChoice(new BigStackFixture("copy"));
        BetterQuestingChoiceContract contract = bind(CopyingChoice.class);

        assertFailure("HANDLER_AMBIGUOUS", new ThrowingAction() {
            @Override
            public void run() throws Exception {
                contract.choices(choice);
            }
        });
    }

    @Test
    public void emptyChoiceProviderFailsClosed() throws Exception {
        final ValidChoice choice = new ValidChoice();
        final BetterQuestingChoiceContract contract = bind(ValidChoice.class);

        assertFailure("RECIPE_SEMANTICS", new ThrowingAction() {
            @Override
            public void run() throws Exception {
                contract.choices(choice);
            }
        });
    }

    @Test
    public void wrongErasedRuntimeEntryFailsClosed() throws Exception {
        final ValidChoice choice = new ValidChoice(new BigStackFixture("valid"));
        @SuppressWarnings("unchecked")
        List<Object> erased = (List<Object>) (List<?>) choice.choices;
        erased.add("not-a-big-stack");
        final BetterQuestingChoiceContract contract = bind(ValidChoice.class);

        assertFailure("HANDLER_AMBIGUOUS", new ThrowingAction() {
            @Override
            public void run() throws Exception {
                contract.choices(choice);
            }
        });
    }

    @Test
    public void mutableChoicesFieldContractFailsAtBindTime() throws Exception {
        Method interfaceMethod = RewardOutputFixture.class.getDeclaredMethod("getItemOutputs");
        assertFailure("HANDLER_AMBIGUOUS", new ThrowingAction() {
            @Override
            public void run() throws Exception {
                BetterQuestingChoiceContract.bind(
                        MutableChoice.class, MutableChoice.class.getName(),
                        RewardOutputFixture.class, BigStackFixture.class,
                        interfaceMethod);
            }
        });
    }

    private static BetterQuestingChoiceContract bind(Class<?> type) throws Exception {
        Method interfaceMethod = RewardOutputFixture.class.getDeclaredMethod("getItemOutputs");
        return BetterQuestingChoiceContract.bind(
                type, type.getName(), RewardOutputFixture.class,
                BigStackFixture.class, interfaceMethod);
    }

    private static void assertFailure(String code, ThrowingAction action) throws Exception {
        try {
            action.run();
            fail("expected ExportFailure " + code);
        } catch (ExportFailure failure) {
            assertEquals(code, failure.code);
        }
    }

    private interface ThrowingAction {
        void run() throws Exception;
    }

    public interface RewardOutputFixture {
        List<BigStackFixture> getItemOutputs();
    }

    public static final class BigStackFixture {
        final String id;

        BigStackFixture(String id) {
            this.id = id;
        }
    }

    public static class ValidChoice implements RewardOutputFixture {
        public final List<BigStackFixture> choices = new ArrayList<BigStackFixture>();

        ValidChoice(BigStackFixture... choices) {
            Collections.addAll(this.choices, choices);
        }

        @Override
        public List<BigStackFixture> getItemOutputs() {
            return choices;
        }
    }

    public static class CopyingChoice implements RewardOutputFixture {
        public final List<BigStackFixture> choices = new ArrayList<BigStackFixture>();

        CopyingChoice(BigStackFixture... choices) {
            Collections.addAll(this.choices, choices);
        }

        @Override
        public List<BigStackFixture> getItemOutputs() {
            return new ArrayList<BigStackFixture>(choices);
        }
    }

    public static class MutableChoice implements RewardOutputFixture {
        public List<BigStackFixture> choices = new ArrayList<BigStackFixture>();

        @Override
        public List<BigStackFixture> getItemOutputs() {
            return choices;
        }
    }
}
