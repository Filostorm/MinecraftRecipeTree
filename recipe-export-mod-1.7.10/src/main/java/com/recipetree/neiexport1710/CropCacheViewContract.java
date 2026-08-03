package com.recipetree.neiexport1710;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structurally audits the two upstream IC2 Crop Plugin cache views.
 *
 * <p>{@code BreedResult.addToMaps} inserts into each input's usage list before inserting
 * into the result's craft list. Every list applies {@code BreedResult.matches} independently.
 * The resulting identity union is diagnostic-only because the preceding global set has
 * JVM-identity-dependent tree-bin behavior; deterministic exported semantics come from
 * {@link DeterministicCropReplayContract}.</p>
 */
final class CropCacheViewContract {
    interface Record {
        Object rawResult();

        String semanticCanonical();

        String diagnosticId();

        Object resultCrop();

        String resultCropId();

        List<Object> inputCrops();

        List<String> inputCropIds();
    }

    interface Capture<R extends Record> {
        R capture(Object rawResult) throws ExportFailure;
    }

    interface Matcher {
        boolean matches(Object left, Object right) throws ExportFailure;
    }

    static final class Snapshot<R extends Record> {
        final List<R> records;
        final int craftResultIdentities;
        final int usageResultIdentities;
        final int bothViewResultIdentities;
        final int craftOnlyResultIdentities;
        final int usageOnlyResultIdentities;
        final int craftOccurrences;
        final int usageOccurrences;
        final int usageOnlySingleInputIdentities;
        final int usageOnlyAllInputIdentities;
        final int conflatedCraftRepresentatives;
        final String membershipCanonical;

        Snapshot(List<R> records,
                 int craftResultIdentities,
                 int usageResultIdentities,
                 int bothViewResultIdentities,
                 int craftOnlyResultIdentities,
                 int usageOnlyResultIdentities,
                 int craftOccurrences,
                 int usageOccurrences,
                 int usageOnlySingleInputIdentities,
                 int usageOnlyAllInputIdentities,
                 int conflatedCraftRepresentatives,
                 String membershipCanonical) {
            this.records = records;
            this.craftResultIdentities = craftResultIdentities;
            this.usageResultIdentities = usageResultIdentities;
            this.bothViewResultIdentities = bothViewResultIdentities;
            this.craftOnlyResultIdentities = craftOnlyResultIdentities;
            this.usageOnlyResultIdentities = usageOnlyResultIdentities;
            this.craftOccurrences = craftOccurrences;
            this.usageOccurrences = usageOccurrences;
            this.usageOnlySingleInputIdentities = usageOnlySingleInputIdentities;
            this.usageOnlyAllInputIdentities = usageOnlyAllInputIdentities;
            this.conflatedCraftRepresentatives = conflatedCraftRepresentatives;
            this.membershipCanonical = membershipCanonical;
        }
    }

    private static final class State<R extends Record> {
        final R record;
        Object craftKey;
        final Set<Object> usageKeys = Collections.newSetFromMap(
                new IdentityHashMap<Object, Boolean>());
        State<R> craftRepresentative;

        State(R record) {
            this.record = record;
        }
    }

    private CropCacheViewContract() {
    }

    static <R extends Record> Snapshot<R> audit(
            Map<?, ?> craft,
            Map<?, ?> usage,
            Class<?> exactResultClass,
            IdentityHashMap<Object, String> cropIdsByIdentity,
            Capture<R> capture,
            Matcher matcher) throws ExportFailure {
        if (craft == null || usage == null || exactResultClass == null
                || cropIdsByIdentity == null || capture == null || matcher == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop cache-view audit received a null dependency");
        }
        if (craft.getClass() != HashMap.class || usage.getClass() != HashMap.class) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 Crop Plugin craft/usage caches must remain exact java.util.HashMap instances; "
                            + "craft=" + craft.getClass().getName()
                            + ", usage=" + usage.getClass().getName());
        }
        if (craft.isEmpty() || usage.isEmpty()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "IC2 Crop Plugin published empty craft/usage maps");
        }
        validateMapKeys(craft, usage, cropIdsByIdentity);

        IdentityHashMap<Object, State<R>> states =
                new IdentityHashMap<Object, State<R>>();
        scanView("craft", craft, true, exactResultClass, cropIdsByIdentity,
                states, capture, matcher);
        scanView("usage", usage, false, exactResultClass, cropIdsByIdentity,
                states, capture, matcher);
        if (states.isEmpty()) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "IC2 crop cache contains no BreedResults in either authoritative view");
        }

        IdentityHashMap<Object, List<State<R>>> craftByResultCrop =
                new IdentityHashMap<Object, List<State<R>>>();
        int craftIdentities = 0;
        int usageIdentities = 0;
        int bothIdentities = 0;
        int craftOnlyIdentities = 0;
        int usageOnlyIdentities = 0;
        int usageOccurrences = 0;
        for (State<R> state : states.values()) {
            boolean inCraft = state.craftKey != null;
            boolean inUsage = !state.usageKeys.isEmpty();
            if (inUsage) {
                usageIdentities++;
                usageOccurrences += state.usageKeys.size();
            }
            if (inCraft) {
                craftIdentities++;
                List<State<R>> bucket = craftByResultCrop.get(state.record.resultCrop());
                if (bucket == null) {
                    bucket = new ArrayList<State<R>>();
                    craftByResultCrop.put(state.record.resultCrop(), bucket);
                }
                bucket.add(state);
            }
            if (inCraft && inUsage) {
                bothIdentities++;
            } else if (inCraft) {
                craftOnlyIdentities++;
            } else if (inUsage) {
                usageOnlyIdentities++;
            } else {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 crop union contains a BreedResult absent from both views: "
                                + state.record.diagnosticId());
            }
        }

        Set<State<R>> conflatedRepresentatives = Collections.newSetFromMap(
                new IdentityHashMap<State<R>, Boolean>());
        int usageOnlySingleInput = 0;
        int usageOnlyAllInputs = 0;
        for (State<R> state : states.values()) {
            validateUsageMembership(state, cropIdsByIdentity);
            if (state.craftKey != null) {
                state.craftRepresentative = state;
                continue;
            }

            List<State<R>> candidates = craftByResultCrop.get(state.record.resultCrop());
            State<R> representative = null;
            int matches = 0;
            if (candidates != null) {
                for (State<R> candidate : candidates) {
                    if (symmetricMatch(state.record, candidate.record, matcher,
                            "usage-only/craft representative")) {
                        representative = candidate;
                        matches++;
                    }
                }
            }
            if (matches != 1 || representative == null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 usage-only BreedResult must match exactly one craft-view "
                                + "representative; matches=" + matches + ", "
                                + diagnostic(state.record, cropIdsByIdentity));
            }
            state.craftRepresentative = representative;
            conflatedRepresentatives.add(representative);
            if (state.usageKeys.size() == 1) {
                usageOnlySingleInput++;
            } else if (state.usageKeys.size() == state.record.inputCrops().size()) {
                usageOnlyAllInputs++;
            }
        }

        List<State<R>> orderedStates = new ArrayList<State<R>>(states.values());
        Collections.sort(orderedStates, new Comparator<State<R>>() {
            @Override
            public int compare(State<R> left, State<R> right) {
                return left.record.semanticCanonical().compareTo(
                        right.record.semanticCanonical());
            }
        });
        String previousCanonical = null;
        List<R> records = new ArrayList<R>(orderedStates.size());
        for (State<R> state : orderedStates) {
            if (state.record.semanticCanonical().equals(previousCanonical)) {
                throw new ExportFailure("HANDLER_DUPLICATE",
                        "IC2 crop cache contains distinct BreedResult identities with one exact "
                                + "clean graph semantic; refusing silent semantic de-duplication: "
                                + diagnostic(state.record, cropIdsByIdentity));
            }
            previousCanonical = state.record.semanticCanonical();
            records.add(state.record);
        }

        String membershipCanonical = membershipCanonical(
                orderedStates, craft, usage, states,
                craftIdentities, usageIdentities,
                bothIdentities, craftOnlyIdentities,
                usageOnlyIdentities, usageOccurrences,
                usageOnlySingleInput, usageOnlyAllInputs,
                conflatedRepresentatives.size(),
                cropIdsByIdentity);
        return new Snapshot<R>(
                Collections.unmodifiableList(records),
                craftIdentities,
                usageIdentities,
                bothIdentities,
                craftOnlyIdentities,
                usageOnlyIdentities,
                craftIdentities,
                usageOccurrences,
                usageOnlySingleInput,
                usageOnlyAllInputs,
                conflatedRepresentatives.size(),
                membershipCanonical);
    }

    private static void validateMapKeys(
            Map<?, ?> craft,
            Map<?, ?> usage,
            IdentityHashMap<Object, String> cropIdsByIdentity) throws ExportFailure {
        Set<Object> craftKeys = Collections.newSetFromMap(
                new IdentityHashMap<Object, Boolean>());
        Set<Object> usageKeys = Collections.newSetFromMap(
                new IdentityHashMap<Object, Boolean>());
        Set<String> canonicalIds = new HashSet<String>();
        for (Object key : craft.keySet()) {
            String id = cropIdsByIdentity.get(key);
            if (key == null || id == null || id.isEmpty()) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "IC2 craft map contains an unregistered/null CropCard key");
            }
            if (!craftKeys.add(key)) {
                throw new ExportFailure("HANDLER_DUPLICATE",
                        "IC2 craft map repeated a CropCard identity");
            }
            if (!canonicalIds.add(id)) {
                throw new ExportFailure("HANDLER_DUPLICATE",
                        "IC2 crop map keys repeat canonical CropCard identity " + id);
            }
        }
        for (Object key : usage.keySet()) {
            String id = cropIdsByIdentity.get(key);
            if (key == null || id == null || id.isEmpty()) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "IC2 usage map contains an unregistered/null CropCard key");
            }
            if (!usageKeys.add(key)) {
                throw new ExportFailure("HANDLER_DUPLICATE",
                        "IC2 usage map repeated a CropCard identity");
            }
        }
        if (craftKeys.size() != usageKeys.size()
                || !craftKeys.containsAll(usageKeys)
                || !usageKeys.containsAll(craftKeys)
                || cropIdsByIdentity.size() != craftKeys.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 crop craft/usage CropCard identity sets differ; craft="
                            + craftKeys.size() + ", usage=" + usageKeys.size()
                            + ", canonical=" + cropIdsByIdentity.size());
        }
    }

    private static <R extends Record> void scanView(
            String view,
            Map<?, ?> source,
            boolean craftView,
            Class<?> exactResultClass,
            IdentityHashMap<Object, String> cropIdsByIdentity,
            IdentityHashMap<Object, State<R>> states,
            Capture<R> capture,
            Matcher matcher) throws ExportFailure {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            Object cropKey = entry.getKey();
            String cropId = cropIdsByIdentity.get(cropKey);
            Object value = entry.getValue();
            if (value == null || value.getClass() != ArrayList.class) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "IC2 crop " + view + " map value for " + cropId
                                + " must remain an exact java.util.ArrayList; got "
                                + (value == null ? "null" : value.getClass().getName()));
            }
            List<?> bucket = (List<?>) value;
            List<R> priorRecords = new ArrayList<R>(bucket.size());
            Set<Object> bucketIdentities = Collections.newSetFromMap(
                    new IdentityHashMap<Object, Boolean>());
            for (Object rawResult : bucket) {
                if (rawResult == null || rawResult.getClass() != exactResultClass) {
                    throw new ExportFailure("HANDLER_AMBIGUOUS",
                            "IC2 crop " + view + " map contains an unexpected result type at "
                                    + cropId);
                }
                if (!bucketIdentities.add(rawResult)) {
                    throw new ExportFailure("HANDLER_DUPLICATE",
                            "IC2 crop " + view + " map repeats one BreedResult identity at "
                                    + cropId);
                }
                State<R> state = states.get(rawResult);
                if (state == null) {
                    R record = capture.capture(rawResult);
                    validateCapturedRecord(record, rawResult, cropIdsByIdentity);
                    state = new State<R>(record);
                    states.put(rawResult, state);
                }
                for (R prior : priorRecords) {
                    if (symmetricMatch(state.record, prior, matcher,
                            view + " bucket " + cropId)) {
                        throw new ExportFailure("HANDLER_DUPLICATE",
                                "IC2 crop " + view + " bucket " + cropId
                                        + " contains two BreedResults conflated by the pinned "
                                        + "BreedResult.matches contract; current="
                                        + state.record.diagnosticId() + ", prior="
                                        + prior.diagnosticId());
                    }
                }
                priorRecords.add(state.record);

                if (craftView) {
                    if (state.craftKey != null) {
                        throw new ExportFailure("HANDLER_DUPLICATE",
                                "IC2 BreedResult identity occurs in more than one craft bucket: "
                                        + state.record.diagnosticId());
                    }
                    if (state.record.resultCrop() != cropKey) {
                        throw new ExportFailure("RECIPE_SEMANTICS",
                                "IC2 craft-map key is not identical to its clean graph result; key="
                                        + cropId + ", "
                                        + diagnostic(state.record, cropIdsByIdentity));
                    }
                    state.craftKey = cropKey;
                } else {
                    if (!containsIdentity(state.record.inputCrops(), cropKey)) {
                        throw new ExportFailure("RECIPE_SEMANTICS",
                                "IC2 usage-map key is not an input of its BreedResult; key="
                                        + cropId + ", "
                                        + diagnostic(state.record, cropIdsByIdentity));
                    }
                    if (!state.usageKeys.add(cropKey)) {
                        throw new ExportFailure("HANDLER_DUPLICATE",
                                "IC2 usage map repeats a BreedResult membership for input "
                                        + cropId + ", result=" + state.record.diagnosticId());
                    }
                }
            }
        }
    }

    private static void validateCapturedRecord(
            Record record,
            Object rawResult,
            IdentityHashMap<Object, String> cropIdsByIdentity) throws ExportFailure {
        if (record == null || record.rawResult() != rawResult
                || record.semanticCanonical() == null
                || record.semanticCanonical().isEmpty()
                || record.diagnosticId() == null
                || record.diagnosticId().isEmpty()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop cache capture returned an invalid record identity/canonical form");
        }
        List<Object> inputs = record.inputCrops();
        List<String> inputIds = record.inputCropIds();
        if (inputs == null || inputIds == null
                || inputs.size() != 2 || inputIds.size() != 2) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 cached BreedResult must retain exactly two graph inputs: "
                            + record.diagnosticId());
        }
        if (inputs.get(0) == inputs.get(1)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 cached BreedResult repeats one input identity: "
                            + record.diagnosticId());
        }
        requireKnownCrop(record.resultCrop(), record.resultCropId(),
                "result", record, cropIdsByIdentity);
        for (int index = 0; index < inputs.size(); index++) {
            requireKnownCrop(inputs.get(index), inputIds.get(index),
                    "input[" + index + "]", record, cropIdsByIdentity);
        }
    }

    private static void requireKnownCrop(
            Object crop,
            String expectedId,
            String role,
            Record record,
            IdentityHashMap<Object, String> cropIdsByIdentity) throws ExportFailure {
        String mapId = cropIdsByIdentity.get(crop);
        if (crop == null || mapId == null || !mapId.equals(expectedId)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 cached BreedResult " + role
                            + " is absent/mismatched in the authoritative cache key set; expected="
                            + expectedId + ", actual=" + mapId + ", result="
                            + record.diagnosticId());
        }
    }

    private static <R extends Record> void validateUsageMembership(
            State<R> state,
            IdentityHashMap<Object, String> cropIdsByIdentity) throws ExportFailure {
        Set<Object> uniqueInputs = Collections.newSetFromMap(
                new IdentityHashMap<Object, Boolean>());
        uniqueInputs.addAll(state.record.inputCrops());
        if (state.usageKeys.isEmpty()
                || state.usageKeys.size() > uniqueInputs.size()
                || !uniqueInputs.containsAll(state.usageKeys)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 cached BreedResult has invalid usage memberships; usage="
                            + sortedIds(state.usageKeys, cropIdsByIdentity) + ", "
                            + diagnostic(state.record, cropIdsByIdentity));
        }
        if (state.craftKey != null
                && (state.usageKeys.size() != uniqueInputs.size()
                || !state.usageKeys.containsAll(uniqueInputs))) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 craft-view representative must occur once under every unique input "
                            + "usage key; usage="
                            + sortedIds(state.usageKeys, cropIdsByIdentity) + ", "
                            + diagnostic(state.record, cropIdsByIdentity));
        }
    }

    private static boolean symmetricMatch(
            Record left,
            Record right,
            Matcher matcher,
            String context) throws ExportFailure {
        boolean forward = matcher.matches(left.rawResult(), right.rawResult());
        boolean reverse = matcher.matches(right.rawResult(), left.rawResult());
        if (forward != reverse) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 BreedResult.matches became asymmetric in " + context
                            + "; left=" + left.diagnosticId()
                            + ", right=" + right.diagnosticId()
                            + ", forward=" + forward + ", reverse=" + reverse);
        }
        return forward;
    }

    private static boolean containsIdentity(List<Object> values, Object expected) {
        for (Object value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }

    private static <R extends Record> String membershipCanonical(
            List<State<R>> states,
            Map<?, ?> craft,
            Map<?, ?> usage,
            IdentityHashMap<Object, State<R>> statesByIdentity,
            int craftIdentities,
            int usageIdentities,
            int bothIdentities,
            int craftOnlyIdentities,
            int usageOnlyIdentities,
            int usageOccurrences,
            int usageOnlySingleInput,
            int usageOnlyAllInputs,
            int conflatedRepresentatives,
            IdentityHashMap<Object, String> cropIdsByIdentity) {
        StringBuilder canonical = new StringBuilder(states.size() * 256);
        canonical.append("ic2-crop-authoritative-cache-views-v2\n");
        canonical.append("unionResultIdentities\t").append(states.size()).append('\n');
        canonical.append("craftResultIdentities\t").append(craftIdentities).append('\n');
        canonical.append("usageResultIdentities\t").append(usageIdentities).append('\n');
        canonical.append("bothViewResultIdentities\t").append(bothIdentities).append('\n');
        canonical.append("craftOnlyResultIdentities\t")
                .append(craftOnlyIdentities).append('\n');
        canonical.append("usageOnlyResultIdentities\t")
                .append(usageOnlyIdentities).append('\n');
        canonical.append("craftOccurrences\t").append(craftIdentities).append('\n');
        canonical.append("usageOccurrences\t").append(usageOccurrences).append('\n');
        canonical.append("usageOnlySingleInputIdentities\t")
                .append(usageOnlySingleInput).append('\n');
        canonical.append("usageOnlyAllInputIdentities\t")
                .append(usageOnlyAllInputs).append('\n');
        canonical.append("conflatedCraftRepresentatives\t")
                .append(conflatedRepresentatives).append('\n');
        appendView(canonical, "craft", craft, statesByIdentity, cropIdsByIdentity);
        appendView(canonical, "usage", usage, statesByIdentity, cropIdsByIdentity);
        canonical.append("representativeLinks\t").append(states.size()).append('\n');
        for (State<R> state : states) {
            canonical.append("representativeLink\t");
            appendField(canonical, state.record.semanticCanonical());
            canonical.append('\t');
            appendField(canonical,
                    state.craftRepresentative.record.semanticCanonical());
            canonical.append('\n');
        }
        return canonical.toString();
    }

    private static <R extends Record> void appendView(
            StringBuilder canonical,
            String role,
            Map<?, ?> view,
            IdentityHashMap<Object, State<R>> statesByIdentity,
            IdentityHashMap<Object, String> cropIdsByIdentity) {
        List<Map.Entry<?, ?>> entries = new ArrayList<Map.Entry<?, ?>>(view.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<?, ?>>() {
            @Override
            public int compare(Map.Entry<?, ?> left, Map.Entry<?, ?> right) {
                return cropIdsByIdentity.get(left.getKey()).compareTo(
                        cropIdsByIdentity.get(right.getKey()));
            }
        });
        canonical.append(role).append("Keys\t").append(entries.size()).append('\n');
        for (Map.Entry<?, ?> entry : entries) {
            List<String> members = new ArrayList<String>();
            for (Object rawResult : (List<?>) entry.getValue()) {
                members.add(statesByIdentity.get(rawResult).record.semanticCanonical());
            }
            Collections.sort(members);
            canonical.append(role).append("Key\t");
            appendField(canonical, cropIdsByIdentity.get(entry.getKey()));
            canonical.append("\tmembers\t").append(members.size());
            for (String member : members) {
                canonical.append('\t');
                appendField(canonical, member);
            }
            canonical.append('\n');
        }
    }

    private static List<String> sortedIds(
            Set<Object> crops,
            IdentityHashMap<Object, String> cropIdsByIdentity) {
        List<String> ids = new ArrayList<String>(crops.size());
        for (Object crop : crops) {
            ids.add(cropIdsByIdentity.get(crop));
        }
        Collections.sort(ids);
        return ids;
    }

    private static String diagnostic(
            Record record,
            IdentityHashMap<Object, String> cropIdsByIdentity) {
        List<String> inputs = new ArrayList<String>();
        for (Object crop : record.inputCrops()) {
            inputs.add(cropIdsByIdentity.get(crop));
        }
        Collections.sort(inputs);
        return "semanticId=" + record.diagnosticId()
                + ", output=" + record.resultCropId()
                + ", inputs=" + inputs;
    }

    private static void appendField(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }
}
