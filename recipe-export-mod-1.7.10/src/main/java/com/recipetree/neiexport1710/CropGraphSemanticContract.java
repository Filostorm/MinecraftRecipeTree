package com.recipetree.neiexport1710;

import codechicken.nei.ItemList;
import codechicken.nei.PositionedStack;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumChatFormatting;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Exact clean-stack graph contract for IC2 Crop Plugin 1.3.1 BreedResult recipes. */
final class CropGraphSemanticContract {
    static final String CROP_API_CLASS = "speiger.src.crops.api.CropPluginAPI";
    static final String BREED_RESULT_CLASS = "speiger.src.crops.prediction.BreedResult";
    static final String PRESENTATION_CORPUS_DOMAIN =
            "ic2-crop-nei-presentation-corpus-v4";
    private static final String PRESENTATION_PAGE_DOMAIN =
            "ic2-crop-nei-presentation-v4";

    /** Narrow test seam; production always supplies the exact StackIdentity implementation. */
    interface StackCanonicalizer {
        String canonicalize(ItemStack stack, int amount, String role) throws ExportFailure;
    }

    private static final StackCanonicalizer PRODUCTION_STACK_CANONICALIZER =
            new StackCanonicalizer() {
                @Override
                public String canonicalize(ItemStack stack, int amount, String role)
                        throws ExportFailure {
                    return productionCanonical(stack, amount, role);
                }
            };

    static final class GraphStack {
        final Object crop;
        final String cropId;
        final ItemStack stack;
        final int amount;
        final String stackCanonical;
        final String cropAndStackCanonical;

        GraphStack(Object crop, String cropId, ItemStack stack, int amount,
                   String stackCanonical) {
            this.crop = crop;
            this.cropId = cropId;
            this.stack = stack;
            this.amount = amount;
            this.stackCanonical = stackCanonical;
            StringBuilder canonical = new StringBuilder(
                    cropId.length() + stackCanonical.length() + 24);
            appendField(canonical, cropId);
            appendField(canonical, stackCanonical);
            this.cropAndStackCanonical = canonical.toString();
        }
    }

    static final class GraphRecipe {
        final Object breedResult;
        final List<GraphStack> inputs;
        final GraphStack output;
        final int points;
        final int total;
        final float chance;
        final String canonical;

        GraphRecipe(Object breedResult, List<GraphStack> inputs, GraphStack output,
                    int points, int total, float chance) {
            this.breedResult = breedResult;
            this.inputs = Collections.unmodifiableList(new ArrayList<GraphStack>(inputs));
            this.output = output;
            this.points = points;
            this.total = total;
            this.chance = chance;
            StringBuilder value = new StringBuilder(512);
            value.append("crop-graph-v1;");
            value.append('P').append(points).append(';');
            value.append('T').append(total).append(';');
            value.append('C').append(String.format(
                    java.util.Locale.ROOT, "%08x", Float.floatToRawIntBits(chance))).append(';');
            value.append('R');
            appendField(value, output.cropAndStackCanonical);
            value.append('I').append(inputs.size()).append(';');
            for (GraphStack input : inputs) {
                appendField(value, input.cropAndStackCanonical);
            }
            this.canonical = value.toString();
        }
    }

    /** Per-page telemetry for the exact NEI presentation transform. */
    static final class PreviewAudit {
        final int renderedAlternativeCount;
        final int renderedGraphCropAlternativeCount;
        final PermutationSource permutationSource;
        final int renderedInputAlternativeCount;
        final int renderedGraphCropInputAlternativeCount;
        final int cropPreservingInputSlots;
        final int lossyInputSlots;
        final int directInputSlots;
        final int wildcardItemListInputSlots;
        final int wildcardEmptyFallbackInputSlots;
        final int wildcardFireFallbackInputSlots;
        final int minimumInputAlternativesPerSlot;
        final int maximumInputAlternativesPerSlot;

        PreviewAudit(int renderedAlternativeCount,
                     int renderedGraphCropAlternativeCount,
                     PermutationSource permutationSource,
                     int renderedInputAlternativeCount,
                     int renderedGraphCropInputAlternativeCount,
                     int cropPreservingInputSlots,
                     int lossyInputSlots,
                     int directInputSlots,
                     int wildcardItemListInputSlots,
                     int wildcardEmptyFallbackInputSlots,
                     int wildcardFireFallbackInputSlots,
                     int minimumInputAlternativesPerSlot,
                     int maximumInputAlternativesPerSlot) {
            this.renderedAlternativeCount = renderedAlternativeCount;
            this.renderedGraphCropAlternativeCount =
                    renderedGraphCropAlternativeCount;
            this.permutationSource = permutationSource;
            this.renderedInputAlternativeCount = renderedInputAlternativeCount;
            this.renderedGraphCropInputAlternativeCount =
                    renderedGraphCropInputAlternativeCount;
            this.cropPreservingInputSlots = cropPreservingInputSlots;
            this.lossyInputSlots = lossyInputSlots;
            this.directInputSlots = directInputSlots;
            this.wildcardItemListInputSlots = wildcardItemListInputSlots;
            this.wildcardEmptyFallbackInputSlots =
                    wildcardEmptyFallbackInputSlots;
            this.wildcardFireFallbackInputSlots =
                    wildcardFireFallbackInputSlots;
            this.minimumInputAlternativesPerSlot =
                    minimumInputAlternativesPerSlot;
            this.maximumInputAlternativesPerSlot =
                    maximumInputAlternativesPerSlot;
        }

        boolean preservesGraphCropInEveryAlternative() {
            return renderedAlternativeCount == renderedGraphCropAlternativeCount;
        }
    }

    private static final class PermutationAudit {
        final int alternativeCount;
        final int graphCropAlternativeCount;
        final PermutationSource source;

        PermutationAudit(
                int alternativeCount,
                int graphCropAlternativeCount,
                PermutationSource source) {
            this.alternativeCount = alternativeCount;
            this.graphCropAlternativeCount = graphCropAlternativeCount;
            this.source = source;
        }

        boolean preservesGraphCropInEveryAlternative() {
            return alternativeCount == graphCropAlternativeCount;
        }
    }

    enum PermutationSource {
        DIRECT_STACK,
        WILDCARD_ITEM_LIST,
        WILDCARD_EMPTY_FALLBACK,
        WILDCARD_FIRE_FALLBACK
    }

    /** One bounded UTF-8 staging buffer for the complete presentation corpus. */
    static final class PresentationDigestStream {
        private static final int BUFFER_SIZE = 4096;

        private final MessageDigest digest;
        private final byte[] buffer = new byte[BUFFER_SIZE];
        private int buffered;
        private boolean finished;

        private PresentationDigestStream(MessageDigest digest) {
            this.digest = digest;
        }

        byte[] finish() throws ExportFailure {
            requireOpen();
            flush();
            finished = true;
            return digest.digest();
        }

        private void write(int value) throws ExportFailure {
            requireOpen();
            if (buffered == buffer.length) {
                flush();
            }
            buffer[buffered++] = (byte) value;
        }

        private void flush() {
            if (buffered > 0) {
                digest.update(buffer, 0, buffered);
                buffered = 0;
            }
        }

        private void requireOpen() throws ExportFailure {
            if (finished) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "IC2 crop presentation digest stream was used after finish()");
            }
        }
    }

    private final CropIdentityContract cropIdentities;
    private final Class<?> breedResultClass;
    private final Method getResult;
    private final Method getInput;
    private final Method getPoints;
    private final Field totalField;
    private final Method getChance;
    private final Method getItemInputs;
    private final Method getItemResult;
    private final Method matches;
    private final Method getCrop;
    private final StackCanonicalizer stackCanonicalizer;

    private CropGraphSemanticContract(
            CropIdentityContract cropIdentities,
            Class<?> breedResultClass,
            Method getResult,
            Method getInput,
            Method getPoints,
            Field totalField,
            Method getChance,
            Method getItemInputs,
            Method getItemResult,
            Method matches,
            Method getCrop,
            StackCanonicalizer stackCanonicalizer) {
        this.cropIdentities = cropIdentities;
        this.breedResultClass = breedResultClass;
        this.getResult = getResult;
        this.getInput = getInput;
        this.getPoints = getPoints;
        this.totalField = totalField;
        this.getChance = getChance;
        this.getItemInputs = getItemInputs;
        this.getItemResult = getItemResult;
        this.matches = matches;
        this.getCrop = getCrop;
        this.stackCanonicalizer = stackCanonicalizer;
    }

    static CropGraphSemanticContract load(ClassLoader loader,
                                          CropIdentityContract cropIdentities)
            throws ExportFailure {
        final Class<?> cropApi;
        final Class<?> breedResult;
        try {
            cropApi = Class.forName(CROP_API_CLASS, false, loader);
            breedResult = Class.forName(BREED_RESULT_CLASS, false, loader);
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            throw new ExportFailure("HANDLER_UNLOADED",
                    "could not load exact IC2 Crop Plugin graph API classes", cause);
        }
        return bind(cropApi, breedResult, cropIdentities,
                PRODUCTION_STACK_CANONICALIZER, true);
    }

    /** Package-private binding seam for focused pinned-shape tests. */
    static CropGraphSemanticContract bindForTesting(
            Class<?> cropApi, Class<?> breedResult,
            CropIdentityContract cropIdentities,
            StackCanonicalizer stackCanonicalizer) throws ExportFailure {
        return bind(cropApi, breedResult, cropIdentities, stackCanonicalizer, false);
    }

    private static CropGraphSemanticContract bind(
            Class<?> cropApi, Class<?> breedResult,
            CropIdentityContract cropIdentities,
            StackCanonicalizer stackCanonicalizer,
            boolean requireExactNames)
            throws ExportFailure {
        if (cropApi == null || breedResult == null || cropIdentities == null
                || stackCanonicalizer == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop graph API binding received a null class/identity contract");
        }
        if (requireExactNames
                && (!CROP_API_CLASS.equals(cropApi.getName())
                || !BREED_RESULT_CLASS.equals(breedResult.getName()))) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop graph API class names drifted; api=" + cropApi.getName()
                            + ", result=" + breedResult.getName());
        }
        if (!isPublicConcreteClass(cropApi) || !isPublicConcreteClass(breedResult)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop graph API and BreedResult must remain public concrete classes");
        }

        try {
            Class<?> cropCard = cropIdentities.cropCardClass();
            Class<?> cropArray = Array.newInstance(cropCard, 0).getClass();
            Method getResult = requireExactMethod(
                    breedResult, "getResult", cropCard, false);
            Method getInput = requireExactMethod(
                    breedResult, "getInput", cropArray, false);
            Method getPoints = requireExactMethod(
                    breedResult, "getPoints", int.class, false);
            Field total = requireExactTotalField(breedResult);
            Method getChance = requireExactMethod(
                    breedResult, "getChance", float.class, false);
            Method getItemInputs = requireExactMethod(
                    breedResult, "getItemInputs", ItemStack[].class, false);
            Method getItemResult = requireExactMethod(
                    breedResult, "getItemResult", ItemStack.class, false);
            Method matches = requireExactMethod(
                    breedResult, "matches", boolean.class, false, breedResult);
            Method getCrop = requireExactMethod(
                    cropApi, "getCrop", cropCard, true, ItemStack.class);
            return new CropGraphSemanticContract(
                    cropIdentities, breedResult, getResult, getInput,
                    getPoints, total, getChance, getItemInputs, getItemResult, matches, getCrop,
                    stackCanonicalizer);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "could not bind exact public IC2 Crop Plugin graph methods", cause);
        }
    }

    /** Invokes the exact public BreedResult.matches method pinned by the shipped class shape. */
    boolean matches(Object left, Object right) throws ExportFailure {
        if (left == null || right == null
                || left.getClass() != breedResultClass
                || right.getClass() != breedResultClass) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop matches audit received a non-exact BreedResult");
        }
        try {
            Object value = matches.invoke(left, right);
            if (!(value instanceof Boolean)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        "IC2 BreedResult.matches did not return an exact boolean value");
            }
            return ((Boolean) value).booleanValue();
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "could not invoke exact IC2 BreedResult.matches", cause);
        }
    }

    GraphRecipe capture(Object breedResult, Map<String, Object> cropsById)
            throws ExportFailure {
        if (breedResult == null || breedResult.getClass() != breedResultClass) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop graph recipe is not an exact " + breedResultClass.getName());
        }
        try {
            Object resultCrop = getResult.invoke(breedResult);
            Object inputValue = getInput.invoke(breedResult);
            if (inputValue == null || inputValue.getClass() != getInput.getReturnType()
                    || Array.getLength(inputValue) != 2) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult.getInput() must return exactly two CropCards");
            }
            Object firstInputCrop = Array.get(inputValue, 0);
            Object secondInputCrop = Array.get(inputValue, 1);
            if (resultCrop == null || firstInputCrop == null || secondInputCrop == null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult contains a null result/input CropCard");
            }
            if (firstInputCrop == secondInputCrop) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult repeats one CropCard as both inputs");
            }
            if (resultCrop == firstInputCrop || resultCrop == secondInputCrop) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult repeats its result CropCard as an input");
            }
            String resultCropId = cropIdentities.requireCanonicalId(resultCrop, cropsById);
            String firstCropId = cropIdentities.requireCanonicalId(firstInputCrop, cropsById);
            String secondCropId = cropIdentities.requireCanonicalId(secondInputCrop, cropsById);

            Object rawInputs = getItemInputs.invoke(breedResult);
            if (rawInputs == null || rawInputs.getClass() != ItemStack[].class
                    || Array.getLength(rawInputs) != 2) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult.getItemInputs() must return exactly two ItemStacks");
            }
            Object rawResult = getItemResult.invoke(breedResult);
            if (!(rawResult instanceof ItemStack)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult.getItemResult() must return one nonnull ItemStack");
            }

            IdentityHashMap<ItemStack, String> originals =
                    new IdentityHashMap<ItemStack, String>();
            IdentityHashMap<ItemStack, String> copies =
                    new IdentityHashMap<ItemStack, String>();
            IdentityHashMap<NBTTagCompound, String> originalTags =
                    new IdentityHashMap<NBTTagCompound, String>();
            IdentityHashMap<NBTTagCompound, String> copiedTags =
                    new IdentityHashMap<NBTTagCompound, String>();
            List<GraphStack> inputs = new ArrayList<GraphStack>(2);
            inputs.add(captureStack(
                    (ItemStack) Array.get(rawInputs, 0), firstInputCrop, firstCropId,
                    "input[0]", originals, copies, originalTags, copiedTags));
            inputs.add(captureStack(
                    (ItemStack) Array.get(rawInputs, 1), secondInputCrop, secondCropId,
                    "input[1]", originals, copies, originalTags, copiedTags));
            GraphStack output = captureStack(
                    (ItemStack) rawResult, resultCrop, resultCropId,
                    "result", originals, copies, originalTags, copiedTags);

            Collections.sort(inputs, new Comparator<GraphStack>() {
                @Override
                public int compare(GraphStack left, GraphStack right) {
                    return left.cropAndStackCanonical.compareTo(right.cropAndStackCanonical);
                }
            });
            if (inputs.get(0).cropAndStackCanonical.equals(
                    inputs.get(1).cropAndStackCanonical)) {
                throw new ExportFailure("HANDLER_DUPLICATE",
                        "IC2 BreedResult has duplicate canonical graph input slots");
            }

            int points = ((Number) getPoints.invoke(breedResult)).intValue();
            int total = totalField.getInt(breedResult);
            float chance = ((Number) getChance.invoke(breedResult)).floatValue();
            float expectedChance = (points / (float) total) * 100.0F;
            if (points <= 0 || total <= 0
                    || !Float.isFinite(chance) || chance <= 0.0F
                    || Float.floatToRawIntBits(chance)
                    != Float.floatToRawIntBits(expectedChance)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult has invalid points/total/chance "
                                + points + "/" + total + "/" + chance);
            }
            return new GraphRecipe(breedResult, inputs, output, points, total, chance);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "could not capture exact clean IC2 BreedResult graph stacks: "
                            + cause.getClass().getName()
                            + (cause.getMessage() == null || cause.getMessage().isEmpty()
                            ? "" : ": " + cause.getMessage()), cause);
        }
    }

    /**
     * Requires the retained two-parent array to follow stable CropCard-ID order.
     *
     * <p>The pinned BreedResult constructor sorts this array by runtime object hash. The
     * deterministic adapter repairs it after construction; this assertion prevents a later
     * mutation from reintroducing JVM-dependent left/right preview placement.</p>
     */
    void validateCanonicalInputOrder(Object breedResult) throws ExportFailure {
        if (breedResult == null || breedResult.getClass() != breedResultClass) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 canonical input-order audit received a non-exact BreedResult");
        }
        try {
            Object inputs = getInput.invoke(breedResult);
            if (inputs == null || inputs.getClass() != getInput.getReturnType()
                    || Array.getLength(inputs) != 2) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult canonical input-order audit requires two CropCards");
            }
            Object first = Array.get(inputs, 0);
            Object second = Array.get(inputs, 1);
            Map<String, Object> cropsById = new java.util.HashMap<String, Object>();
            String firstId = cropIdentities.requireCanonicalId(first, cropsById);
            String secondId = cropIdentities.requireCanonicalId(second, cropsById);
            if (first == second || firstId.compareTo(secondId) >= 0) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult retained inputs are not in canonical CropCard-ID order: "
                                + firstId + " then " + secondId);
            }
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "could not audit canonical IC2 BreedResult input order", cause);
        }
    }

    static PresentationDigestStream beginPresentationDigest(MessageDigest digest)
            throws ExportFailure {
        requirePresentationDigest(digest);
        PresentationDigestStream stream = new PresentationDigestStream(digest);
        updateUtf8(stream, PRESENTATION_CORPUS_DOMAIN);
        updateUtf8(stream, "\n");
        return stream;
    }

    PreviewAudit validateLoreOnlyPreview(
            List<PositionedStack> previewInputs,
            PositionedStack previewOutput,
            GraphRecipe graph,
            int pageIndex,
            String semanticId,
            PresentationDigestStream presentationDigest) throws ExportFailure {
        String context = "IC2 crop page index=" + pageIndex
                + " semanticId=" + (semanticId == null ? "<null>" : semanticId)
                + " graphOutput="
                + (graph == null || graph.output == null
                ? "<null>" : graph.output.cropId);
        try {
            return validateLoreOnlyPreviewInternal(
                    previewInputs, previewOutput, graph, presentationDigest);
        } catch (ExportFailure failure) {
            throw new ExportFailure(failure.code,
                    context + ": " + failureDetail(failure), failure);
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            throw new ExportFailure("RECIPE_SEMANTICS",
                    context + ": could not validate IC2 BreedRecipe semantic/presentation "
                            + "contract: " + cause.getClass().getName()
                            + (cause.getMessage() == null || cause.getMessage().isEmpty()
                            ? "" : ": " + cause.getMessage()), cause);
        }
    }

    private PreviewAudit validateLoreOnlyPreviewInternal(
            List<PositionedStack> previewInputs,
            PositionedStack previewOutput,
            GraphRecipe graph,
            PresentationDigestStream presentationDigest) throws Exception {
        if (graph == null || graph.output == null || graph.inputs == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 BreedRecipe preview received a null/incomplete clean graph");
        }
        if (previewInputs == null || previewInputs.size() != 2) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "preview must expose exactly two input slots; got "
                            + (previewInputs == null ? "null" : previewInputs.size()));
        }
        if (previewOutput == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "rendered output PositionedStack is null");
        }
        requirePresentationDigestStream(presentationDigest);

        Object retainedInputs = getInput.invoke(graph.breedResult);
        if (retainedInputs == null
                || retainedInputs.getClass() != getInput.getReturnType()
                || Array.getLength(retainedInputs) != previewInputs.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 BreedRecipe preview cannot resolve its retained input order");
        }
        if (graph.inputs.size() != 2) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "clean IC2 crop graph must retain exactly two inputs; got "
                            + graph.inputs.size());
        }
        IdentityHashMap<ItemStack, String> previewIdentities =
                new IdentityHashMap<ItemStack, String>();
        IdentityHashMap<NBTTagCompound, String> previewTagIdentities =
                new IdentityHashMap<NBTTagCompound, String>();

        ItemStack semanticPreview = graph.output.stack.copy();
        requireDistinctStack(
                graph.output.stack, semanticPreview,
                "expected pre-permutation preview result");
        requireNbtBearing(graph.output.stack, "clean graph output");
        requireDefensiveNbtCopy(
                graph.output.stack, semanticPreview,
                "expected pre-permutation preview result");
        appendExpectedPreviewLore(semanticPreview, graph);
        registerPreviewIdentity(
                semanticPreview, "expected pre-permutation preview result",
                previewIdentities, previewTagIdentities);
        String semanticCanonical = canonical(
                semanticPreview, semanticPreview.stackSize,
                "expected pre-permutation preview result");
        Object semanticCrop = getCrop.invoke(null, semanticPreview);
        String semanticAfterLookup = canonical(
                semanticPreview, semanticPreview.stackSize,
                "expected pre-permutation preview result");
        if (!semanticCanonical.equals(semanticAfterLookup)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "CropPluginAPI.getCrop mutated the expected pre-permutation preview result");
        }
        if (semanticCrop != graph.output.crop) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "expected pre-permutation preview CropCard differs from graph output; "
                            + "expected=" + graph.output.cropId
                            + ", observed=" + diagnosticCropId(semanticCrop)
                            + ", stack=" + semanticCanonical);
        }

        updateUtf8(presentationDigest, PRESENTATION_PAGE_DOMAIN);
        updateUtf8(presentationDigest, ";");
        updateFramedField(presentationDigest, graph.canonical);
        updateFramedField(presentationDigest, semanticCanonical);
        updateUtf8(presentationDigest, "I2;");

        boolean observedFirstGraphInput = false;
        boolean observedSecondGraphInput = false;
        int renderedInputAlternatives = 0;
        int renderedGraphCropInputAlternatives = 0;
        int cropPreservingInputSlots = 0;
        int lossyInputSlots = 0;
        int directInputSlots = 0;
        int wildcardItemListInputSlots = 0;
        int wildcardEmptyFallbackInputSlots = 0;
        int wildcardFireFallbackInputSlots = 0;
        int minimumInputAlternativesPerSlot = Integer.MAX_VALUE;
        int maximumInputAlternativesPerSlot = Integer.MIN_VALUE;
        for (int index = 0; index < previewInputs.size(); index++) {
            String role = "preview input[" + index + "]";
            Object retainedCrop = Array.get(retainedInputs, index);
            GraphStack expected = graphStackForCrop(graph.inputs, retainedCrop);
            requireNbtBearing(expected.stack, "clean graph input[" + index + "]");
            if (expected == graph.inputs.get(0)) {
                if (observedFirstGraphInput) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "preview repeats clean graph input[0]");
                }
                observedFirstGraphInput = true;
            } else if (expected == graph.inputs.get(1)) {
                if (observedSecondGraphInput) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "preview repeats clean graph input[1]");
                }
                observedSecondGraphInput = true;
            } else {
                throw new ExportFailure("HANDLER_AMBIGUOUS",
                        role + " retained a CropCard outside the clean graph identity set");
            }

            updateUtf8(presentationDigest, "I");
            updateNonNegativeLong(presentationDigest, index);
            updateUtf8(presentationDigest, ";");
            PermutationAudit inputAudit = validatePinnedPermutation(
                    previewInputs.get(index), expected.stack, retainedCrop,
                    index == 0 ? 43 : 107, 40, role, presentationDigest,
                    previewIdentities, previewTagIdentities);
            renderedInputAlternatives += inputAudit.alternativeCount;
            renderedGraphCropInputAlternatives +=
                    inputAudit.graphCropAlternativeCount;
            minimumInputAlternativesPerSlot = Math.min(
                    minimumInputAlternativesPerSlot, inputAudit.alternativeCount);
            maximumInputAlternativesPerSlot = Math.max(
                    maximumInputAlternativesPerSlot, inputAudit.alternativeCount);
            if (inputAudit.preservesGraphCropInEveryAlternative()) {
                cropPreservingInputSlots++;
            } else {
                lossyInputSlots++;
            }
            if (inputAudit.source == PermutationSource.DIRECT_STACK) {
                directInputSlots++;
            } else if (inputAudit.source
                    == PermutationSource.WILDCARD_ITEM_LIST) {
                wildcardItemListInputSlots++;
            } else if (inputAudit.source
                    == PermutationSource.WILDCARD_EMPTY_FALLBACK) {
                wildcardEmptyFallbackInputSlots++;
            } else if (inputAudit.source
                    == PermutationSource.WILDCARD_FIRE_FALLBACK) {
                wildcardFireFallbackInputSlots++;
            } else {
                throw new ExportFailure("HANDLER_AMBIGUOUS", role
                        + " returned an unknown pinned NEI permutation source "
                        + inputAudit.source);
            }
        }
        if (!observedFirstGraphInput || !observedSecondGraphInput) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "preview retained-input CropCard multiset differs from graph inputs");
        }

        updateUtf8(presentationDigest, "O;");
        PermutationAudit outputAudit = validatePinnedPermutation(
                previewOutput, semanticPreview, graph.output.crop, 75, 40,
                "rendered preview output", presentationDigest,
                previewIdentities, previewTagIdentities);
        return new PreviewAudit(
                outputAudit.alternativeCount,
                outputAudit.graphCropAlternativeCount,
                outputAudit.source,
                renderedInputAlternatives,
                renderedGraphCropInputAlternatives,
                cropPreservingInputSlots,
                lossyInputSlots,
                directInputSlots,
                wildcardItemListInputSlots,
                wildcardEmptyFallbackInputSlots,
                wildcardFireFallbackInputSlots,
                minimumInputAlternativesPerSlot,
                maximumInputAlternativesPerSlot);
    }

    private PermutationAudit validatePinnedPermutation(
            PositionedStack positioned,
            ItemStack semanticSource,
            Object expectedCrop,
            int expectedX,
            int expectedY,
            String role,
            PresentationDigestStream presentationDigest,
            IdentityHashMap<ItemStack, String> identities,
            IdentityHashMap<NBTTagCompound, String> tagIdentities) throws Exception {
        if (positioned == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    role + " PositionedStack is null");
        }
        if (positioned.relx != expectedX || positioned.rely != expectedY) {
            throw new ExportFailure("RECIPE_SEMANTICS", role
                    + " coordinates drifted; expected (" + expectedX + ","
                    + expectedY + "), got ("
                    + positioned.relx + "," + positioned.rely + ")");
        }
        if (positioned.items == null || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    role + " has no serialized alternatives");
        }
        if (positioned.item == null || positioned.item.getItem() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    role + " current render stack is null/empty");
        }

        boolean wildcard = semanticSource.getItemDamage() == Short.MAX_VALUE;
        List<ItemStack> livePermutations = wildcard
                ? ItemList.itemMap.get(semanticSource.getItem())
                : Collections.<ItemStack>emptyList();
        int validLivePermutations = 0;
        if (wildcard && !livePermutations.isEmpty()) {
            for (ItemStack live : livePermutations) {
                if (live != null && live.getItem() != null) {
                    validLivePermutations++;
                }
            }
        }
        final PermutationSource permutationSource;
        final int expectedAlternatives;
        if (!wildcard) {
            permutationSource = PermutationSource.DIRECT_STACK;
            expectedAlternatives = 1;
        } else if (livePermutations.isEmpty()) {
            permutationSource = PermutationSource.WILDCARD_EMPTY_FALLBACK;
            expectedAlternatives = 1;
        } else if (validLivePermutations > 0) {
            permutationSource = PermutationSource.WILDCARD_ITEM_LIST;
            expectedAlternatives = validLivePermutations;
        } else {
            permutationSource = PermutationSource.WILDCARD_FIRE_FALLBACK;
            expectedAlternatives = 1;
        }
        if (positioned.items.length != expectedAlternatives) {
            throw new ExportFailure("RECIPE_SEMANTICS", role
                    + " alternative count differs from the exact pinned live ItemList "
                    + "permutation; source=" + permutationSource + ", expected="
                    + expectedAlternatives + ", observed=" + positioned.items.length);
        }

        updateUtf8(presentationDigest, "X");
        updateNonNegativeLong(presentationDigest, expectedX);
        updateUtf8(presentationDigest, ";Y");
        updateNonNegativeLong(presentationDigest, expectedY);
        updateUtf8(presentationDigest, ";A");
        updateNonNegativeLong(presentationDigest, positioned.items.length);
        updateUtf8(presentationDigest, ";");

        int graphCropAlternatives = 0;
        int liveIndex = 0;
        ItemStack firstAlternative = null;
        for (int index = 0; index < positioned.items.length; index++) {
            ItemStack expectedSource;
            Item expectedItem;
            int expectedAmount;
            int expectedMetadata;
            NBTTagCompound expectedTag;
            if (permutationSource == PermutationSource.DIRECT_STACK) {
                expectedSource = semanticSource;
                expectedItem = semanticSource.getItem();
                expectedAmount = semanticSource.stackSize;
                expectedMetadata = semanticSource.getItemDamage();
                expectedTag = semanticSource.getTagCompound();
            } else if (permutationSource == PermutationSource.WILDCARD_ITEM_LIST) {
                do {
                    expectedSource = livePermutations.get(liveIndex++);
                } while (expectedSource == null || expectedSource.getItem() == null);
                expectedItem = expectedSource.getItem();
                expectedAmount = semanticSource.stackSize;
                expectedMetadata = expectedSource.getItemDamage();
                expectedTag = expectedSource.getTagCompound();
            } else if (permutationSource
                    == PermutationSource.WILDCARD_EMPTY_FALLBACK) {
                expectedSource = semanticSource;
                expectedItem = semanticSource.getItem();
                expectedAmount = semanticSource.stackSize;
                expectedMetadata = 0;
                expectedTag = semanticSource.getTagCompound();
            } else {
                expectedSource = null;
                expectedItem = Item.getItemFromBlock(Blocks.fire);
                expectedAmount = 1;
                expectedMetadata = 0;
                expectedTag = null;
            }

            String alternativeRole = role + " alternative[" + index + "]";
            ItemStack alternative = requireStack(
                    positioned.items[index], alternativeRole);
            if (expectedSource != null) {
                requireDistinctStack(
                        expectedSource, alternative,
                        alternativeRole + " pinned NEI defensive copy");
                requireDefensiveNbtCopy(
                        expectedSource, alternative,
                        alternativeRole + " pinned NEI defensive copy");
            }
            requireExactPresentationStack(
                    alternative, expectedItem, expectedAmount, expectedMetadata,
                    expectedTag, alternativeRole, permutationSource);
            registerPreviewIdentity(
                    alternative, alternativeRole, identities, tagIdentities);
            if (firstAlternative == null) {
                firstAlternative = alternative;
            }
            String beforeLookup = canonical(
                    alternative, alternative.stackSize, alternativeRole);
            Object renderedCrop = getCrop.invoke(null, alternative);
            String afterLookup = canonical(
                    alternative, alternative.stackSize, alternativeRole);
            if (!beforeLookup.equals(afterLookup)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "CropPluginAPI.getCrop mutated " + alternativeRole);
            }
            if (renderedCrop == expectedCrop) {
                graphCropAlternatives++;
            }
            updateFramedField(presentationDigest, beforeLookup);
        }

        ItemStack current = requireStack(positioned.item, role + " current");
        requireDistinctStack(
                firstAlternative, current, role + " current defensive copy");
        requireDefensiveNbtCopy(
                firstAlternative, current, role + " current defensive copy");
        int expectedCurrentMetadata = firstAlternative.getItemDamage();
        if (expectedCurrentMetadata == Short.MAX_VALUE
                && firstAlternative.getItem().isRepairable()) {
            expectedCurrentMetadata = 0;
        }
        requireExactPresentationStack(
                current, firstAlternative.getItem(), firstAlternative.stackSize,
                expectedCurrentMetadata, firstAlternative.getTagCompound(),
                role + " current", permutationSource);
        registerPreviewIdentity(
                current, role + " current", identities, tagIdentities);
        String currentCanonical = canonical(
                current, current.stackSize, role + " current");
        updateUtf8(presentationDigest, "C");
        updateFramedField(presentationDigest, currentCanonical);
        updateUtf8(presentationDigest, "G");
        updateNonNegativeLong(presentationDigest, graphCropAlternatives);
        updateUtf8(presentationDigest, ";");
        return new PermutationAudit(
                positioned.items.length, graphCropAlternatives, permutationSource);
    }

    private static ItemStack requireStack(ItemStack stack, String role)
            throws ExportFailure {
        if (stack == null || stack.getItem() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", role + " is null/empty");
        }
        return stack;
    }

    private static void requireDistinctStack(
            ItemStack source, ItemStack copy, String role) throws ExportFailure {
        if (source == null || copy == null || source == copy) {
            throw new ExportFailure("RECIPE_SEMANTICS", role
                    + " must retain a distinct nonnull ItemStack identity");
        }
    }

    private static void requireNbtBearing(ItemStack stack, String role)
            throws ExportFailure {
        if (stack == null || stack.getTagCompound() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", role
                    + " must retain the nonnull CropCard NBTTagCompound");
        }
    }

    private static void requireDefensiveNbtCopy(
            ItemStack source, ItemStack copy, String role) throws ExportFailure {
        NBTTagCompound sourceTag = source == null ? null : source.getTagCompound();
        NBTTagCompound copyTag = copy == null ? null : copy.getTagCompound();
        if (sourceTag != null && copyTag == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", role
                    + " dropped its source's nonnull NBTTagCompound");
        }
        if (sourceTag == null && copyTag != null) {
            throw new ExportFailure("RECIPE_SEMANTICS", role
                    + " acquired an NBTTagCompound absent from its source");
        }
        if (sourceTag != null && copyTag == sourceTag) {
            throw new ExportFailure("RECIPE_SEMANTICS", role
                    + " aliases its source's nonnull NBTTagCompound instead of retaining "
                    + "a deep defensive copy");
        }
    }

    private static void registerPreviewIdentity(
            ItemStack stack,
            String role,
            IdentityHashMap<ItemStack, String> stackIdentities,
            IdentityHashMap<NBTTagCompound, String> tagIdentities)
            throws ExportFailure {
        String previousStack = stackIdentities.put(stack, role);
        if (previousStack != null) {
            throw new ExportFailure("RECIPE_SEMANTICS", role
                    + " aliases the ItemStack identity already retained by "
                    + previousStack);
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            String previousTag = tagIdentities.put(tag, role);
            if (previousTag != null) {
                throw new ExportFailure("RECIPE_SEMANTICS", role
                        + " aliases the nonnull NBTTagCompound identity already retained by "
                        + previousTag);
            }
        }
    }

    private static void requireExactPresentationStack(
            ItemStack observed,
            Item expectedItem,
            int expectedAmount,
            int expectedMetadata,
            NBTTagCompound expectedTag,
            String role,
            PermutationSource source) throws ExportFailure {
        if (observed.getItem() != expectedItem
                || observed.stackSize != expectedAmount
                || observed.getItemDamage() != expectedMetadata
                || !canonicalNullableNbt(observed.getTagCompound()).equals(
                canonicalNullableNbt(expectedTag))) {
            throw new ExportFailure("RECIPE_SEMANTICS", role
                    + " differs from the exact pinned NEI permutation; source=" + source
                    + ", expected=" + presentationStackDiagnostic(
                    expectedItem, expectedAmount, expectedMetadata, expectedTag)
                    + ", observed=" + presentationStackDiagnostic(
                    observed.getItem(), observed.stackSize,
                    observed.getItemDamage(), observed.getTagCompound()));
        }
    }

    private static String canonicalNullableNbt(NBTTagCompound tag) {
        return tag == null ? "-" : NbtCanonicalizer.canonical(tag);
    }

    private static String presentationStackDiagnostic(
            Item item, int amount, int metadata, NBTTagCompound tag) {
        return "itemClass=" + (item == null ? "<null>" : item.getClass().getName())
                + ",amount=" + amount + ",metadata=" + metadata
                + ",nbt=" + canonicalNullableNbt(tag);
    }

    private static void requirePresentationDigest(MessageDigest digest)
            throws ExportFailure {
        if (digest == null || !"SHA-256".equalsIgnoreCase(digest.getAlgorithm())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop presentation audit requires a nonnull SHA-256 MessageDigest");
        }
    }

    private static void requirePresentationDigestStream(
            PresentationDigestStream stream) throws ExportFailure {
        if (stream == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop presentation audit requires a nonnull digest stream");
        }
        stream.requireOpen();
    }

    private static void updateFramedField(
            PresentationDigestStream digest, String value)
            throws ExportFailure {
        if (value == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop presentation cannot frame a null canonical field");
        }
        updateNonNegativeLong(digest, value.length());
        digest.write(':');
        updateUtf8(digest, value);
    }

    /** Streams UTF-8 directly into the digest without a field-sized byte-array allocation. */
    private static void updateUtf8(PresentationDigestStream digest, String value)
            throws ExportFailure {
        if (value == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop presentation cannot digest a null UTF-8 field");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            int codePoint;
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new ExportFailure("RECIPE_SEMANTICS",
                            "IC2 crop presentation canonical field contains an unpaired "
                                    + "high surrogate at UTF-16 index " + index);
                }
                codePoint = Character.toCodePoint(character, value.charAt(++index));
            } else if (Character.isLowSurrogate(character)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 crop presentation canonical field contains an unpaired low "
                                + "surrogate at UTF-16 index " + index);
            } else {
                codePoint = character;
            }
            if (codePoint <= 0x7f) {
                digest.write(codePoint);
            } else if (codePoint <= 0x7ff) {
                digest.write(0xc0 | (codePoint >>> 6));
                digest.write(0x80 | (codePoint & 0x3f));
            } else if (codePoint <= 0xffff) {
                digest.write(0xe0 | (codePoint >>> 12));
                digest.write(0x80 | ((codePoint >>> 6) & 0x3f));
                digest.write(0x80 | (codePoint & 0x3f));
            } else {
                digest.write(0xf0 | (codePoint >>> 18));
                digest.write(0x80 | ((codePoint >>> 12) & 0x3f));
                digest.write(0x80 | ((codePoint >>> 6) & 0x3f));
                digest.write(0x80 | (codePoint & 0x3f));
            }
        }
    }

    private static void updateNonNegativeLong(
            PresentationDigestStream digest, long value)
            throws ExportFailure {
        if (value < 0L) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "IC2 crop presentation cannot digest a negative count " + value);
        }
        if (value >= 10L) {
            updateNonNegativeLong(digest, value / 10L);
        }
        digest.write('0' + (int) (value % 10L));
    }

    private String diagnosticCropId(Object crop) {
        if (crop == null) {
            return "<null>";
        }
        try {
            return cropIdentities.requireCanonicalId(
                    crop, new java.util.HashMap<String, Object>());
        } catch (ExportFailure failure) {
            return "<invalid:" + crop.getClass().getName() + ":"
                    + failure.code + ">";
        }
    }

    private static String failureDetail(ExportFailure failure) {
        String message = failure.getMessage();
        String prefix = failure.code + ": ";
        return message != null && message.startsWith(prefix)
                ? message.substring(prefix.length()) : String.valueOf(message);
    }

    private static GraphStack graphStackForCrop(List<GraphStack> stacks, Object crop)
            throws ExportFailure {
        GraphStack match = null;
        for (GraphStack stack : stacks) {
            if (stack.crop == crop) {
                if (match != null) {
                    throw new ExportFailure("HANDLER_DUPLICATE",
                            "IC2 graph has more than one input slot for one CropCard");
                }
                match = stack;
            }
        }
        if (match == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 BreedRecipe preview resolved an input CropCard absent from its graph");
        }
        return match;
    }

    private static void appendExpectedPreviewLore(ItemStack stack, GraphRecipe graph)
            throws ExportFailure {
        if (stack == null || stack.getItem() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 clean graph output cannot construct its expected preview Lore");
        }
        NBTTagCompound root = stack.hasTagCompound()
                ? stack.getTagCompound() : new NBTTagCompound();
        stack.setTagCompound(root);
        NBTTagCompound display = root.getCompoundTag("display");
        root.setTag("display", display);
        NBTTagList lore = display.getTagList("Lore", 8);
        display.setTag("Lore", lore);
        String prefix = EnumChatFormatting.RESET.toString()
                + EnumChatFormatting.GOLD.toString();
        lore.appendTag(new NBTTagString(
                prefix + "Breeding Points: " + graph.points));
        lore.appendTag(new NBTTagString(
                prefix + "Breeding Chance: "
                        + ItemStack.field_111284_a.format(graph.chance) + "%"));
    }

    private GraphStack captureStack(
            ItemStack original, Object expectedCrop, String cropId, String role,
            IdentityHashMap<ItemStack, String> originals,
            IdentityHashMap<ItemStack, String> copies,
            IdentityHashMap<NBTTagCompound, String> originalTags,
            IdentityHashMap<NBTTagCompound, String> copiedTags)
            throws Exception {
        if (original == null || original.getItem() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 BreedResult clean " + role + " stack is null/empty");
        }
        if (original.stackSize <= 0) {
            throw new ExportFailure("QUANTITY_INVALID",
                    "IC2 BreedResult clean " + role + " stack has non-positive amount "
                            + original.stackSize);
        }
        String priorRole = originals.put(original, role);
        if (priorRole != null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 BreedResult clean stack aliases " + priorRole + " and " + role);
        }
        NBTTagCompound originalTag = original.getTagCompound();
        if (originalTag != null) {
            String priorTagRole = originalTags.put(originalTag, role);
            if (priorTagRole != null) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult clean NBT aliases " + priorTagRole + " and " + role);
            }
        }

        String originalCanonical = canonical(original, original.stackSize, role);
        Object resolvedOriginal = getCrop.invoke(null, original);
        if (resolvedOriginal != expectedCrop) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "CropPluginAPI.getCrop(clean " + role
                            + ") is not identical to BreedResult's internal CropCard");
        }
        if (!originalCanonical.equals(canonical(original, original.stackSize, role))) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "CropPluginAPI.getCrop mutated the clean " + role + " stack");
        }

        ItemStack copy = original.copy();
        if (copy == original || copy == null || copy.getItem() == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 BreedResult clean " + role + " stack could not be defensively copied");
        }
        if (copies.put(copy, role) != null || originals.containsKey(copy)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 BreedResult defensive " + role + " stack copy aliases another stack");
        }
        NBTTagCompound copiedTag = copy.getTagCompound();
        if (originalTag != null && (copiedTag == null || copiedTag == originalTag)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 BreedResult defensive " + role + " copy did not deep-copy NBT");
        }
        if (copiedTag != null) {
            String priorTagRole = copiedTags.put(copiedTag, role);
            if (priorTagRole != null || originalTags.containsKey(copiedTag)) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult defensive NBT copy aliases another graph stack");
            }
        }
        String copiedCanonical = canonical(copy, copy.stackSize, role);
        if (copy.stackSize != original.stackSize
                || !originalCanonical.equals(copiedCanonical)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "IC2 BreedResult defensive " + role
                            + " copy changed amount, item identity, or full NBT");
        }
        Object resolvedCopy = getCrop.invoke(null, copy);
        if (resolvedCopy != expectedCrop) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "CropPluginAPI.getCrop(defensive " + role
                            + " copy) is not identical to BreedResult's internal CropCard");
        }
        if (!copiedCanonical.equals(canonical(copy, copy.stackSize, role))) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "CropPluginAPI.getCrop mutated the defensive " + role + " stack copy");
        }
        return new GraphStack(
                expectedCrop, cropId, copy, copy.stackSize, copiedCanonical);
    }

    private String canonical(ItemStack stack, int amount, String role)
            throws ExportFailure {
        return stackCanonicalizer.canonicalize(stack, amount, role);
    }

    private static String productionCanonical(ItemStack stack, int amount, String role)
            throws ExportFailure {
        try {
            StackIdentity identity = StackIdentity.of(stack);
            if (identity.isFluid()) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult " + role
                                + " decoded as a fluid proxy instead of a crop ItemStack");
            }
            if (identity.amount != amount) {
                throw new ExportFailure("RECIPE_SEMANTICS",
                        "IC2 BreedResult " + role
                                + " stack amount disagrees with its exact ItemStack identity");
            }
            return CompleteCategoryAdapters.canonicalStackIdentity(identity, amount);
        } catch (ExportFailure failure) {
            throw failure;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("ITEM_IDENTITY",
                    "could not canonicalize IC2 BreedResult clean " + role + " stack", error);
        }
    }

    private static Method requireExactMethod(
            Class<?> type, String name, Class<?> returnType,
            boolean requireStatic, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        int modifiers = method.getModifiers();
        if (method.getReturnType() != returnType
                || !Modifier.isPublic(modifiers)
                || Modifier.isStatic(modifiers) != requireStatic
                || Modifier.isAbstract(modifiers)
                || method.isBridge()
                || method.isSynthetic()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    type.getName() + "." + name
                            + " exact public method shape drifted");
        }
        return method;
    }

    private static Field requireExactTotalField(Class<?> breedResult) throws Exception {
        Field field = breedResult.getDeclaredField("total");
        int modifiers = field.getModifiers();
        if (field.getType() != int.class
                || Modifier.isStatic(modifiers)
                || Modifier.isPublic(modifiers)
                || Modifier.isPrivate(modifiers)
                || Modifier.isProtected(modifiers)
                || Modifier.isFinal(modifiers)
                || field.isSynthetic()) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    breedResult.getName()
                            + ".total must remain the exact mutable package-private int field");
        }
        field.setAccessible(true);
        return field;
    }

    private static boolean isPublicConcreteClass(Class<?> type) {
        int modifiers = type.getModifiers();
        return Modifier.isPublic(modifiers)
                && !Modifier.isAbstract(modifiers)
                && !type.isInterface();
    }

    private static void appendField(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
