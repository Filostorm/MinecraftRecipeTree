package com.recipetree.jeiexport112;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Orders bounded batches by the exact identity that will be written to exported JSON. */
final class CanonicalKeyOrdering {
    interface Entry {
        String canonicalKey();

        /** Exact, non-lossy metadata signature used to detect ambiguous duplicate keys. */
        String canonicalPayload();
    }

    private static final Comparator<Entry> BY_EXACT_KEY = new Comparator<Entry>() {
        @Override
        public int compare(Entry left, Entry right) {
            return left.canonicalKey().compareTo(right.canonicalKey());
        }
    };

    private CanonicalKeyOrdering() {
    }

    static <T extends Entry> void sortAndValidate(List<T> entries) {
        Collections.sort(entries, BY_EXACT_KEY);
        for (int index = 1; index < entries.size(); index++) {
            Entry previous = entries.get(index - 1);
            Entry current = entries.get(index);
            if (previous.canonicalKey().equals(current.canonicalKey()) &&
                    !previous.canonicalPayload().equals(current.canonicalPayload())) {
                throw new IllegalStateException("canonical ingredient key " + current.canonicalKey() +
                        " resolved to conflicting exact emission metadata");
            }
        }
    }
}
