package com.recipetree.jeiexport112;

import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IIngredientType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HEI 4.x semantic recorder. Recipe wrappers populate this independently from GUI layout creation,
 * so a broken renderer cannot erase ingredients or reverse-index edges.
 */
final class RecordingIngredients implements IIngredients {
    private final IIngredientRegistry registry;
    private final Map<IIngredientType<?>, List<List<?>>> inputs =
            new LinkedHashMap<IIngredientType<?>, List<List<?>>>();
    private final Map<IIngredientType<?>, List<List<?>>> outputs =
            new LinkedHashMap<IIngredientType<?>, List<List<?>>>();

    RecordingIngredients(IIngredientRegistry registry) {
        this.registry = registry;
    }

    Map<IIngredientType<?>, List<List<?>>> allInputs() {
        return inputs;
    }

    Map<IIngredientType<?>, List<List<?>>> allOutputs() {
        return outputs;
    }

    @Override
    public <T> void setInput(IIngredientType<T> type, T input) {
        setFlat(inputs, type, Collections.singletonList(input));
    }

    @Override
    public <T> void setInputs(IIngredientType<T> type, List<T> values) {
        setFlat(inputs, type, values);
    }

    @Override
    public <T> void setInputLists(IIngredientType<T> type, List<List<T>> values) {
        setNested(inputs, type, values);
    }

    @Override
    public <T> void setOutput(IIngredientType<T> type, T output) {
        setFlat(outputs, type, Collections.singletonList(output));
    }

    @Override
    public <T> void setOutputs(IIngredientType<T> type, List<T> values) {
        setFlat(outputs, type, values);
    }

    @Override
    public <T> void setOutputLists(IIngredientType<T> type, List<List<T>> values) {
        setNested(outputs, type, values);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<List<T>> getInputs(IIngredientType<T> type) {
        List<List<?>> value = inputs.get(type);
        return value == null ? Collections.<List<T>>emptyList() : (List<List<T>>) (List<?>) value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> List<List<T>> getOutputs(IIngredientType<T> type) {
        List<List<?>> value = outputs.get(type);
        return value == null ? Collections.<List<T>>emptyList() : (List<List<T>>) (List<?>) value;
    }

    @Override
    public <T> void setInput(Class<? extends T> ingredientClass, T input) {
        setInput(typeFor(ingredientClass), input);
    }

    @Override
    public <T> void setInputs(Class<? extends T> ingredientClass, List<T> values) {
        setInputs(typeFor(ingredientClass), values);
    }

    @Override
    public <T> void setInputLists(Class<? extends T> ingredientClass, List<List<T>> values) {
        setInputLists(typeFor(ingredientClass), values);
    }

    @Override
    public <T> void setOutput(Class<? extends T> ingredientClass, T output) {
        setOutput(typeFor(ingredientClass), output);
    }

    @Override
    public <T> void setOutputs(Class<? extends T> ingredientClass, List<T> values) {
        setOutputs(typeFor(ingredientClass), values);
    }

    @Override
    public <T> void setOutputLists(Class<? extends T> ingredientClass, List<List<T>> values) {
        setOutputLists(typeFor(ingredientClass), values);
    }

    @Override
    public <T> List<List<T>> getInputs(Class<? extends T> ingredientClass) {
        return getInputs(typeFor(ingredientClass));
    }

    @Override
    public <T> List<List<T>> getOutputs(Class<? extends T> ingredientClass) {
        return getOutputs(typeFor(ingredientClass));
    }

    private <T> IIngredientType<T> typeFor(Class<? extends T> ingredientClass) {
        if (ingredientClass == null) {
            throw new IllegalArgumentException("Legacy IIngredients setter supplied a null ingredient class");
        }
        IIngredientType<T> type = registry.getIngredientType(ingredientClass);
        if (type == null) {
            throw new IllegalArgumentException("HEI has no ingredient type registered for legacy class " +
                    ingredientClass.getName());
        }
        return type;
    }

    private <T> void setFlat(Map<IIngredientType<?>, List<List<?>>> target,
                             IIngredientType<T> type, List<T> values) {
        List<List<?>> slots = new ArrayList<List<?>>(values == null ? 0 : values.size());
        IIngredientHelper<T> helper = registry.getIngredientHelper(type);
        if (values != null) {
            for (T value : values) {
                if (value != null) {
                    slots.add(new ArrayList<T>(helper.expandSubtypes(Collections.singletonList(value))));
                }
            }
        }
        target.put(type, slots);
    }

    private <T> void setNested(Map<IIngredientType<?>, List<List<?>>> target,
                               IIngredientType<T> type, List<List<T>> values) {
        List<List<?>> slots = new ArrayList<List<?>>(values == null ? 0 : values.size());
        IIngredientHelper<T> helper = registry.getIngredientHelper(type);
        if (values != null) {
            for (List<T> alternatives : values) {
                if (alternatives == null) {
                    continue;
                }
                List<T> copy = new ArrayList<T>(helper.expandSubtypes(alternatives));
                copy.removeAll(Collections.singleton(null));
                if (!copy.isEmpty()) {
                    slots.add(copy);
                }
            }
        }
        target.put(type, slots);
    }
}
