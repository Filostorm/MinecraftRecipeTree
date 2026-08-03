package com.recipetree.neiexport1710;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/** Fail-closed reflection contract for GTNH 2.8.4's BetterQuesting choice reward. */
final class BetterQuestingChoiceContract {
    private final Class<?> choiceClass;
    private final Class<?> bigItemStackClass;
    private final Field choicesField;
    private final Method declaredGetItemOutputs;
    private final Method interfaceGetItemOutputs;

    private BetterQuestingChoiceContract(
            Class<?> choiceClass, Class<?> bigItemStackClass, Field choicesField,
            Method declaredGetItemOutputs, Method interfaceGetItemOutputs) {
        this.choiceClass = choiceClass;
        this.bigItemStackClass = bigItemStackClass;
        this.choicesField = choicesField;
        this.declaredGetItemOutputs = declaredGetItemOutputs;
        this.interfaceGetItemOutputs = interfaceGetItemOutputs;
    }

    static BetterQuestingChoiceContract bind(
            Class<?> choiceClass, String expectedClassName,
            Class<?> rewardItemOutputInterface, Class<?> bigItemStackClass,
            Method interfaceGetItemOutputs) throws ExportFailure {
        try {
            if (!expectedClassName.equals(choiceClass.getName())) {
                throw ambiguous("expected exact choice class " + expectedClassName
                        + ", got " + choiceClass.getName());
            }
            int classModifiers = choiceClass.getModifiers();
            if (!Modifier.isPublic(classModifiers)
                    || Modifier.isAbstract(classModifiers)
                    || choiceClass.isInterface() || choiceClass.isEnum()
                    || choiceClass.isAnnotation()) {
                throw ambiguous(choiceClass.getName()
                        + " must remain a public concrete class");
            }
            if (!rewardItemOutputInterface.isInterface()
                    || !rewardItemOutputInterface.isAssignableFrom(choiceClass)) {
                throw ambiguous(choiceClass.getName() + " must implement exact interface "
                        + rewardItemOutputInterface.getName());
            }
            if (interfaceGetItemOutputs.getDeclaringClass() != rewardItemOutputInterface
                    || interfaceGetItemOutputs.getReturnType() != List.class
                    || interfaceGetItemOutputs.getParameterTypes().length != 0
                    || interfaceGetItemOutputs.getModifiers()
                    != (Modifier.PUBLIC | Modifier.ABSTRACT)) {
                throw ambiguous(rewardItemOutputInterface.getName()
                        + ".getItemOutputs erased method contract drifted");
            }
            requireListOf(interfaceGetItemOutputs.getGenericReturnType(), bigItemStackClass,
                    rewardItemOutputInterface.getName() + ".getItemOutputs return signature");

            Field choices = choiceClass.getDeclaredField("choices");
            if (choices.getDeclaringClass() != choiceClass
                    || choices.getType() != List.class
                    || choices.getModifiers() != (Modifier.PUBLIC | Modifier.FINAL)) {
                throw ambiguous(choiceClass.getName()
                        + ".choices must remain an exact public final List field");
            }
            requireListOf(choices.getGenericType(), bigItemStackClass,
                    choiceClass.getName() + ".choices signature");

            Method declared = choiceClass.getDeclaredMethod("getItemOutputs");
            if (declared.getDeclaringClass() != choiceClass
                    || declared.getReturnType() != List.class
                    || declared.getParameterTypes().length != 0
                    || declared.getModifiers() != Modifier.PUBLIC) {
                throw ambiguous(choiceClass.getName()
                        + ".getItemOutputs erased method contract drifted");
            }
            requireListOf(declared.getGenericReturnType(), bigItemStackClass,
                    choiceClass.getName() + ".getItemOutputs return signature");
            return new BetterQuestingChoiceContract(
                    choiceClass, bigItemStackClass, choices, declared,
                    interfaceGetItemOutputs);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "BetterQuesting RewardChoice reflection contract changed", error);
        }
    }

    boolean isChoice(Object owner) {
        return owner != null && choiceClass.isInstance(owner);
    }

    List<?> choices(Object owner) throws ExportFailure {
        if (owner == null || owner.getClass() != choiceClass) {
            throw ambiguous("choice provider must be the exact " + choiceClass.getName()
                    + " class; got " + (owner == null ? "null" : owner.getClass().getName()));
        }
        try {
            Object fieldValue = choicesField.get(owner);
            Object declaredValue = declaredGetItemOutputs.invoke(owner);
            Object interfaceValue = interfaceGetItemOutputs.invoke(owner);
            if (!(fieldValue instanceof List)
                    || declaredValue != fieldValue || interfaceValue != fieldValue) {
                throw ambiguous(choiceClass.getName()
                        + ".getItemOutputs must return its exact choices List instance");
            }
            List<?> choices = (List<?>) fieldValue;
            if (choices.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "BetterQuesting RewardChoice contains no selectable item choices");
            }
            for (int index = 0; index < choices.size(); index++) {
                Object choice = choices.get(index);
                if (choice == null || choice.getClass() != bigItemStackClass) {
                    throw ambiguous(choiceClass.getName() + ".choices[" + index
                            + "] must be the exact " + bigItemStackClass.getName()
                            + " class; got "
                            + (choice == null ? "null" : choice.getClass().getName()));
                }
            }
            return choices;
        } catch (ExportFailure failure) {
            throw failure;
        } catch (ReflectiveOperationException error) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "could not inspect exact BetterQuesting RewardChoice instance", error);
        }
    }

    private static void requireListOf(Type type, Class<?> elementClass, String label)
            throws ExportFailure {
        if (!(type instanceof ParameterizedType)) {
            throw ambiguous(label + " lost its parameterized List signature");
        }
        ParameterizedType parameterized = (ParameterizedType) type;
        Type[] arguments = parameterized.getActualTypeArguments();
        if (parameterized.getRawType() != List.class
                || arguments.length != 1 || arguments[0] != elementClass) {
            throw ambiguous(label + " must remain List<" + elementClass.getName() + ">");
        }
    }

    private static ExportFailure ambiguous(String message) {
        return new ExportFailure("HANDLER_AMBIGUOUS", message);
    }
}
