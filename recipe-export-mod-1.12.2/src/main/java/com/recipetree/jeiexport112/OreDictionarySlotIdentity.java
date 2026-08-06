package com.recipetree.jeiexport112;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves one HEI item-alternative slot back to its shared Forge OreDictionary identity. */
final class OreDictionarySlotIdentity {
    private static final Map<String, Integer> CARDINALITY_CACHE =
            new LinkedHashMap<String, Integer>();

    private OreDictionarySlotIdentity() {
    }

    static Resolution resolve(List<?> alternatives) {
        List<Set<String>> namesByAlternative = new ArrayList<Set<String>>();
        Map<String, Integer> cardinalities = new LinkedHashMap<String, Integer>();
        for (Object alternative : alternatives) {
            if (!(alternative instanceof ItemStack) || ((ItemStack) alternative).isEmpty()) {
                return Resolution.none();
            }
            LinkedHashSet<String> names = new LinkedHashSet<String>();
            for (int id : OreDictionary.getOreIDs((ItemStack) alternative)) {
                String name = OreDictionary.getOreName(id);
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                    cardinalities.put(name, cardinality(name));
                }
            }
            if (names.isEmpty()) {
                return Resolution.none();
            }
            namesByAlternative.add(names);
        }
        return resolveNames(namesByAlternative, cardinalities);
    }

    static Resolution resolveNames(List<Set<String>> namesByAlternative) {
        return resolveNames(namesByAlternative, Collections.<String, Integer>emptyMap());
    }

    static Resolution resolveNames(List<Set<String>> namesByAlternative,
                                   Map<String, Integer> cardinalities) {
        if (namesByAlternative.isEmpty()) {
            return Resolution.none();
        }
        LinkedHashSet<String> shared = new LinkedHashSet<String>(namesByAlternative.get(0));
        for (int index = 1; index < namesByAlternative.size(); index++) {
            shared.retainAll(namesByAlternative.get(index));
            if (shared.isEmpty()) {
                return Resolution.none();
            }
        }
        List<String> canonical = new ArrayList<String>(shared);
        Collections.sort(canonical);
        String selected = canonical.get(0);
        int selectedCardinality = normalizedCardinality(cardinalities.get(selected));
        for (int index = 1; index < canonical.size(); index++) {
            String candidate = canonical.get(index);
            int candidateCardinality = normalizedCardinality(cardinalities.get(candidate));
            if (candidateCardinality < selectedCardinality ||
                    (candidateCardinality != Integer.MAX_VALUE &&
                            candidateCardinality == selectedCardinality &&
                            specificity(candidate) > specificity(selected))) {
                selected = candidate;
                selectedCardinality = candidateCardinality;
            }
        }
        return new Resolution("ore:" + selected, canonical);
    }

    private static int cardinality(String name) {
        Integer cached = CARDINALITY_CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        int count = OreDictionary.getOres(name, false).size();
        int normalized = count <= 0 ? Integer.MAX_VALUE : count;
        CARDINALITY_CACHE.put(name, normalized);
        return normalized;
    }

    private static int normalizedCardinality(Integer value) {
        return value == null || value.intValue() <= 0 ? Integer.MAX_VALUE : value.intValue();
    }

    /** Prefer a material-specific name such as oreIron over a broad alias such as ore. */
    private static int specificity(String name) {
        int score = name.length();
        if ("ore".equals(name) || "material".equals(name) || "metal".equals(name)) {
            score -= 1000;
        }
        return score;
    }

    static final class Resolution {
        final String identity;
        final List<String> sharedNames;

        Resolution(String identity, List<String> sharedNames) {
            this.identity = identity;
            this.sharedNames = Collections.unmodifiableList(new ArrayList<String>(sharedNames));
        }

        static Resolution none() {
            return new Resolution(null, Collections.<String>emptyList());
        }

        boolean isPresent() {
            return identity != null;
        }

        boolean isAmbiguous() {
            return sharedNames.size() > 1;
        }
    }
}
