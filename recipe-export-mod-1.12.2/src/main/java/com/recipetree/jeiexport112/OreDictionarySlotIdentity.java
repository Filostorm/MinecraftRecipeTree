package com.recipetree.jeiexport112;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves one HEI item-alternative slot back to its shared Forge OreDictionary identity. */
final class OreDictionarySlotIdentity {
    private OreDictionarySlotIdentity() {
    }

    static Resolution resolve(List<?> alternatives) {
        List<Set<String>> namesByAlternative = new ArrayList<Set<String>>();
        for (Object alternative : alternatives) {
            if (!(alternative instanceof ItemStack) || ((ItemStack) alternative).isEmpty()) {
                return Resolution.none();
            }
            LinkedHashSet<String> names = new LinkedHashSet<String>();
            for (int id : OreDictionary.getOreIDs((ItemStack) alternative)) {
                String name = OreDictionary.getOreName(id);
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
            if (names.isEmpty()) {
                return Resolution.none();
            }
            namesByAlternative.add(names);
        }
        return resolveNames(namesByAlternative);
    }

    static Resolution resolveNames(List<Set<String>> namesByAlternative) {
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
        return new Resolution("ore:" + canonical.get(0), canonical);
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
