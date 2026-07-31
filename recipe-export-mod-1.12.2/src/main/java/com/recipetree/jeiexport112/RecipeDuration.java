package com.recipetree.jeiexport112;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;

/** Extracts recipe duration from the same wrapper values that JEI/HEI layouts display. */
final class RecipeDuration {
    private static final List<String> DURATION_METHODS = Arrays.asList(
            "getDurationTicks",
            "getDurationInTicks",
            "getTimeTicks",
            "getTickTime",
            "getProcessingTicks",
            "getProcessTicks",
            "getProcessingTime",
            "getProcessTime",
            "getBaseDuration",
            "getRecipeTime",
            "getCookTime",
            "getCookingTime",
            "getDuration",
            "getTicks",
            "getTime"
    );
    private static final List<String> TOTAL_ENERGY_METHODS = Arrays.asList(
            "getTotalEnergy",
            "getRequiredEnergy",
            "getEnergyRequired",
            "getTotalPower",
            "getTotalEU",
            "getTotalFE"
    );
    private static final List<String> ENERGY_PER_TICK_METHODS = Arrays.asList(
            "getEnergyPerTick",
            "getPowerPerTick",
            "getEnergyRate",
            "getEUt",
            "getRfPerTick",
            "getRFPerTick",
            "getFEPerTick",
            "getUsagePerTick"
    );

    private RecipeDuration() {}

    static OptionalLong ticks(Object recipe) {
        if (recipe == null) return OptionalLong.empty();
        OptionalLong direct = firstPositiveIntegral(recipe, DURATION_METHODS);
        if (direct.isPresent()) return direct;

        OptionalLong totalEnergy = firstPositiveIntegral(recipe, TOTAL_ENERGY_METHODS);
        OptionalLong energyPerTick = firstPositiveIntegral(recipe, ENERGY_PER_TICK_METHODS);
        if (!totalEnergy.isPresent() || !energyPerTick.isPresent()) {
            return OptionalLong.empty();
        }
        long total = totalEnergy.getAsLong();
        long perTick = energyPerTick.getAsLong();
        if (total % perTick != 0) return OptionalLong.empty();
        long ticks = total / perTick;
        return ticks > 0 ? OptionalLong.of(ticks) : OptionalLong.empty();
    }

    private static OptionalLong firstPositiveIntegral(Object target, List<String> methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                if (method.getParameterTypes().length != 0) continue;
                if (!method.isAccessible()) method.setAccessible(true);
                Object value = method.invoke(target);
                if (!(value instanceof Number)) continue;
                Number number = (Number) value;
                double floating = number.doubleValue();
                long integral = number.longValue();
                if (!Double.isNaN(floating)
                        && !Double.isInfinite(floating)
                        && floating > 0
                        && integral > 0
                        && Math.abs(floating - integral) < 1e-9) {
                    return OptionalLong.of(integral);
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next conventional HEI/mod wrapper accessor.
            } catch (IllegalAccessException ignored) {
                // Optional metadata must not abort an export.
            } catch (InvocationTargetException ignored) {
                // Optional metadata must not abort an export.
            } catch (RuntimeException ignored) {
                // Optional metadata must not abort an export.
            }
        }
        return OptionalLong.empty();
    }
}
