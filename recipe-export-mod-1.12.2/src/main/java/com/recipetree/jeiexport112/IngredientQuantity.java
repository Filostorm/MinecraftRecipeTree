package com.recipetree.jeiexport112;

import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Extracts the scalable amount represented by an HEI ingredient value.
 *
 * Custom ingredient APIs are intentionally adapted by exact class name. This avoids treating an
 * arbitrary numeric-looking property (for example a radius or a tier) as a consumed quantity.
 */
final class IngredientQuantity {
    private static final String THAUMCRAFT_ASPECT_LIST = "thaumcraft.api.aspects.AspectList";

    private static final Map<String, String> EXPLICIT_AMOUNT_ACCESSORS;
    private static final Map<String, String> EXPLICIT_AMOUNT_FIELDS;
    private static final Set<String> UNIT_VALUE_TYPES;

    static {
        Map<String, String> accessors = new HashMap<String, String>();
        accessors.put("kport.modularmagic.common.integration.jei.ingredient.Mana", "manaAmount");
        accessors.put("kport.modularmagic.common.integration.jei.ingredient.LifeEssence",
                "getEssenceAmount");
        accessors.put("kport.modularmagic.common.integration.jei.ingredient.DemonWill", "getWillAmount");
        accessors.put("kport.modularmagic.common.integration.jei.ingredient.Impetus", "amount");
        accessors.put("github.alecsio.mmceaddons.common.integration.jei.ingredient.Flux", "amount");
        accessors.put("modulardiversity.jei.ingredients.MekLaser", "getConsumedEnergy");
        accessors.put("modulardiversity.jei.ingredients.Embers", "getConsumedEmbers");
        accessors.put("modulardiversity.jei.ingredients.Mana", "getConsumedMana");
        EXPLICIT_AMOUNT_ACCESSORS = Collections.unmodifiableMap(accessors);

        Map<String, String> fields = new HashMap<String, String>();
        fields.put("requious.compat.jei.ingredient.Energy", "energy");
        EXPLICIT_AMOUNT_FIELDS = Collections.unmodifiableMap(fields);

        Set<String> unitTypes = new HashSet<String>();
        // These are identity/configuration values, not divisible resource stacks.
        unitTypes.add("github.alecsio.mmceaddons.common.integration.jei.ingredient.Meteor");
        unitTypes.add("github.alecsio.mmceaddons.common.integration.jei.ingredient.Biome");
        unitTypes.add("net.minecraftforge.fml.common.registry.VillagerRegistry$VillagerCareer");
        unitTypes.add("modulardiversity.jei.ingredients.DimensionIngredient");
        unitTypes.add("modulardiversity.jei.ingredients.MysticalMechanics");
        UNIT_VALUE_TYPES = Collections.unmodifiableSet(unitTypes);
    }

    private IngredientQuantity() {
    }

    interface UnknownQuantityReporter {
        void report(Class<?> ingredientClass);
    }

    /**
     * Returns the represented quantity. Exact zero is a deliberate sentinel for an absent
     * optional resource slot; RecipePhase records and omits that slot. Negative and non-finite
     * values remain hard failures because they cannot describe a consumable ingredient.
     */
    static BigDecimal amount(Object ingredient, ExportContext context) {
        if (context == null) {
            throw new IllegalArgumentException("ingredient quantity export context must not be null");
        }
        return amount(ingredient, new UnknownQuantityReporter() {
            @Override
            public void report(Class<?> ingredientClass) {
                context.warnAmountFallback(ingredientClass);
            }
        });
    }

    /**
     * Runtime-neutral quantity entry point for clients such as the in-game planner. The caller
     * decides whether an unknown ingredient class is unit-valued or unsupported; this helper does
     * not need an export job merely to apply the established legacy quantity adapters.
     */
    static BigDecimal amount(Object ingredient, UnknownQuantityReporter unknownReporter) {
        if (ingredient == null) {
            throw new IllegalArgumentException("ingredient quantity value must not be null");
        }
        if (unknownReporter == null) {
            throw new IllegalArgumentException("unknown quantity reporter must not be null");
        }
        if (ingredient instanceof ItemStack) {
            return nonNegative(((ItemStack) ingredient).getCount(), ingredient, "ItemStack#getCount");
        }
        if (ingredient instanceof FluidStack) {
            return nonNegative(((FluidStack) ingredient).amount, ingredient, "FluidStack#amount");
        }
        if (ingredient instanceof EnchantmentData) {
            return nonNegative(((EnchantmentData) ingredient).enchantmentLevel, ingredient,
                    "EnchantmentData#enchantmentLevel");
        }

        String className = ingredient.getClass().getName();
        if (THAUMCRAFT_ASPECT_LIST.equals(className)) {
            return singletonAspectAmount(ingredient);
        }

        String accessor = explicitMethodAccessor(className);
        if (accessor != null) {
            return invokeNumericAccessor(ingredient, accessor);
        }

        String field = explicitFieldAccessor(className);
        if (field != null) {
            return readNumericField(ingredient, field);
        }

        if (isUnitValueType(className)) {
            return BigDecimal.ONE;
        }

        BigDecimal reflected = conventionalAmount(ingredient);
        if (reflected != null) {
            return reflected;
        }

        unknownReporter.report(ingredient.getClass());
        return BigDecimal.ONE;
    }

    /**
     * ThaumicJEI's ingredient helper identifies an AspectList by its first Aspect. Its factory and
     * recipe wrappers therefore represent each required aspect as a singleton AspectList whose map
     * value is that aspect's amount. A multi-aspect list cannot be represented by that HEI type
     * without losing identities, so reject it explicitly instead of summing unrelated aspects.
     */
    private static BigDecimal singletonAspectAmount(Object ingredient) {
        try {
            Field aspectsField = ingredient.getClass().getField("aspects");
            Object rawAspects = aspectsField.get(ingredient);
            if (!(rawAspects instanceof Map)) {
                throw new IllegalStateException("Thaumcraft AspectList.aspects is not a Map");
            }
            Map<?, ?> aspects = (Map<?, ?>) rawAspects;
            if (aspects.size() != 1) {
                throw new IllegalArgumentException("ThaumicJEI supplied an AspectList with " + aspects.size() +
                        " aspects; its registered helper only supports singleton aspect ingredients");
            }
            Object rawAmount = aspects.values().iterator().next();
            if (!(rawAmount instanceof Number)) {
                throw new IllegalStateException("Thaumcraft AspectList amount is not numeric: " + rawAmount);
            }
            return nonNegative((Number) rawAmount, ingredient, "AspectList.aspects singleton value");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read the supported Thaumcraft AspectList shape", exception);
        }
    }

    private static BigDecimal conventionalAmount(Object ingredient) {
        String[] methods = {"getAmount", "getCount"};
        for (String methodName : methods) {
            final Method method;
            try {
                method = ingredient.getClass().getMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                continue;
            }
            try {
                Object value = method.invoke(ingredient);
                if (value instanceof Number) {
                    return nonNegative((Number) value, ingredient, methodName + "()");
                }
                throw new IllegalStateException("conventional quantity accessor " +
                        ingredient.getClass().getName() + "#" + methodName + " returned non-numeric value " +
                        value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("conventional quantity accessor " +
                        ingredient.getClass().getName() + "#" + methodName + " could not be invoked", exception);
            }
        }

        String[] fields = {"amount", "stackSize"};
        for (String fieldName : fields) {
            final Field field;
            try {
                field = ingredient.getClass().getField(fieldName);
            } catch (NoSuchFieldException ignored) {
                continue;
            }
            try {
                Object value = field.get(ingredient);
                if (value instanceof Number) {
                    return nonNegative((Number) value, ingredient, fieldName);
                }
                throw new IllegalStateException("conventional quantity field " +
                        ingredient.getClass().getName() + "#" + fieldName + " is non-numeric: " + value);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("conventional quantity field " +
                        ingredient.getClass().getName() + "#" + fieldName + " could not be read", exception);
            }
        }
        return null;
    }

    private static BigDecimal invokeNumericAccessor(Object ingredient, String methodName) {
        try {
            Method method = ingredient.getClass().getMethod(methodName);
            Object value = method.invoke(ingredient);
            if (!(value instanceof Number)) {
                throw new IllegalStateException("quantity accessor " + methodName + " on " +
                        ingredient.getClass().getName() + " returned non-numeric value " + value);
            }
            return nonNegative((Number) value, ingredient, methodName + "()");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("supported quantity adapter " + ingredient.getClass().getName() +
                    "#" + methodName + " could not be invoked", exception);
        }
    }

    private static BigDecimal readNumericField(Object ingredient, String fieldName) {
        try {
            Field field = ingredient.getClass().getField(fieldName);
            Object value = field.get(ingredient);
            if (!(value instanceof Number)) {
                throw new IllegalStateException("quantity field " + fieldName + " on " +
                        ingredient.getClass().getName() + " is non-numeric: " + value);
            }
            return nonNegative((Number) value, ingredient, fieldName);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("supported quantity adapter " + ingredient.getClass().getName() +
                    "#" + fieldName + " could not be read", exception);
        }
    }

    static String explicitMethodAccessor(String className) {
        return EXPLICIT_AMOUNT_ACCESSORS.get(className);
    }

    static String explicitFieldAccessor(String className) {
        return EXPLICIT_AMOUNT_FIELDS.get(className);
    }

    static boolean isUnitValueType(String className) {
        return UNIT_VALUE_TYPES.contains(className);
    }

    private static BigDecimal nonNegative(Number number, Object ingredient, String source) {
        return validatedAmount(number, ingredient.getClass().getName(), source);
    }

    static BigDecimal validatedAmount(Number number, String ingredientClassName, String source) {
        if (number instanceof Double && !Double.isFinite(number.doubleValue())) {
            throw invalidNumber(ingredientClassName, source, number);
        }
        if (number instanceof Float && !Float.isFinite(number.floatValue())) {
            throw invalidNumber(ingredientClassName, source, number);
        }

        final BigDecimal amount;
        try {
            amount = new BigDecimal(number.toString()).stripTrailingZeros();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ingredient quantity from " + source + " on " +
                    ingredientClassName + " is not a JSON number: " + number, exception);
        }
        if (amount.signum() < 0) {
            throw invalidNumber(ingredientClassName, source, number);
        }
        return amount;
    }

    private static IllegalArgumentException invalidNumber(String ingredientClassName, String source,
                                                          Number number) {
        return new IllegalArgumentException("ingredient quantity from " + source + " on " +
                ingredientClassName + " must be finite and non-negative, got " + number);
    }
}
