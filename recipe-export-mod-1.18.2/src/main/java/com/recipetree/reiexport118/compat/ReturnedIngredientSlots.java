package com.recipetree.reiexport118.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.HashSet;
import java.util.Set;

/**
 * Classifies exact input/output slot pairs as retained requirements.
 *
 * Some JEI integrations model reusable molds, patterns, and containers by
 * emitting the same slot once as an input and once as an output instead of
 * assigning the CATALYST role. Exact serialized equality keeps this semantic
 * repair deterministic and avoids display-name heuristics.
 */
public final class ReturnedIngredientSlots {
    private ReturnedIngredientSlots() {
    }

    public record Resolution(
            JsonArray materialInputs,
            JsonArray outputs,
            JsonArray returnedInputs,
            int returnedSlotCount
    ) {
    }

    public static Resolution extract(JsonArray inputs, JsonArray outputs) {
        JsonArray materialInputs = new JsonArray();
        JsonArray remainingOutputs = new JsonArray();
        JsonArray returnedInputs = new JsonArray();
        Set<Integer> claimedOutputs = new HashSet<>();

        for (JsonElement input : inputs) {
            int matchingOutput = findUnclaimedExactMatch(input, outputs, claimedOutputs);
            if (matchingOutput >= 0) {
                claimedOutputs.add(matchingOutput);
                returnedInputs.add(input.deepCopy());
            } else {
                materialInputs.add(input.deepCopy());
            }
        }
        for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
            if (!claimedOutputs.contains(outputIndex)) {
                remainingOutputs.add(outputs.get(outputIndex).deepCopy());
            }
        }

        return new Resolution(
                materialInputs,
                remainingOutputs,
                returnedInputs,
                returnedInputs.size()
        );
    }

    public static void appendUnique(JsonArray target, JsonArray additions) {
        for (JsonElement addition : additions) {
            boolean duplicate = false;
            for (JsonElement existing : target) {
                if (existing.equals(addition)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                target.add(addition.deepCopy());
            }
        }
    }

    private static int findUnclaimedExactMatch(
            JsonElement input,
            JsonArray outputs,
            Set<Integer> claimedOutputs
    ) {
        for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
            if (!claimedOutputs.contains(outputIndex) && input.equals(outputs.get(outputIndex))) {
                return outputIndex;
            }
        }
        return -1;
    }
}
