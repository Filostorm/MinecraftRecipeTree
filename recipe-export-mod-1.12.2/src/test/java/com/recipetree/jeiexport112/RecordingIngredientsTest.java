package com.recipetree.jeiexport112;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IIngredientType;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RecordingIngredientsTest {
    @Test
    public void flatOptionalSlotsWithNoExpandedIngredientAreOmitted() {
        final IIngredientType<String> type = proxy(IIngredientType.class,
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] arguments) {
                        if ("getIngredientClass".equals(method.getName())) return String.class;
                        return defaultValue(method.getReturnType());
                    }
                });
        IIngredientHelper<String> helper = proxy(IIngredientHelper.class,
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] arguments) {
                        if ("expandSubtypes".equals(method.getName())) {
                            List<?> values = (List<?>) arguments[0];
                            if (values.contains("optional-empty")) return Collections.emptyList();
                            return values;
                        }
                        return defaultValue(method.getReturnType());
                    }
                });
        IIngredientRegistry registry = proxy(IIngredientRegistry.class,
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] arguments) {
                        if ("getIngredientHelper".equals(method.getName())) return helper;
                        return defaultValue(method.getReturnType());
                    }
                });

        RecordingIngredients recording = new RecordingIngredients(registry);
        recording.setInputs(type, Arrays.asList("kept", "optional-empty"));

        List<List<?>> slots = recording.allInputs().get(type);
        assertEquals(1, slots.size());
        assertEquals(Collections.singletonList("kept"), slots.get(0));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == char.class) return '\0';
        return null;
    }
}
