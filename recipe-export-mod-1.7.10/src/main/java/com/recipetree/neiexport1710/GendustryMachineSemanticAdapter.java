package com.recipetree.neiexport1710;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact Gendustry 1.9.4-GTNH graph-semantic adapter for its eight machine handlers.
 *
 * <p>Five handlers keep authoritative fluids in Gendustry recipe components rather than
 * NEI's item-only ingredient/result API. The other three share the same cached-recipe
 * family and expose reusable or conditional item prerequisites which also need explicit
 * graph roles. Reflection is deliberate: the exact Gendustry JAR is runtime-pinned by
 * {@link PinnedRuntimePolicy}, but is not an exporter compile dependency.
 *
 * <p>The observation build leaves both corpus constants unpromoted and aborts before
 * rendering after logging the complete count vector and order-independent SHA-256. The
 * reviewed constants must be promoted together in a new immutable exporter version.
 */
final class GendustryMachineSemanticAdapter {
    static final String CONTRACT =
            "gtnh-2.8.4-gendustry-1.9.4-machine-graph-semantics-v1";
    static final String UNPROMOTED = "<unpromoted>";
    static final String EXPECTED_COUNT_VECTOR =
            "Liquifier{pages=40,inputSlots=40,outputSlots=40,catalystSlots=0,itemAlternatives=44,fluidAlternatives=40,stochasticInputs=0,fluidInputs=0,fluidOutputs=40};"
            + "MutagenProducer{pages=15,inputSlots=15,outputSlots=15,catalystSlots=0,itemAlternatives=15,fluidAlternatives=15,stochasticInputs=0,fluidInputs=0,fluidOutputs=15};"
            + "Extractor{pages=1578,inputSlots=3156,outputSlots=1578,catalystSlots=1578,itemAlternatives=3156,fluidAlternatives=1578,stochasticInputs=1578,fluidInputs=0,fluidOutputs=1578};"
            + "Replicator{pages=3,inputSlots=6,outputSlots=3,catalystSlots=3,itemAlternatives=6,fluidAlternatives=6,stochasticInputs=0,fluidInputs=6,fluidOutputs=0};"
            + "Transposer{pages=8,inputSlots=16,outputSlots=8,catalystSlots=16,itemAlternatives=32,fluidAlternatives=0,stochasticInputs=8,fluidInputs=0,fluidOutputs=0};"
            + "Mutatron{pages=705,inputSlots=2820,outputSlots=705,catalystSlots=0,itemAlternatives=2820,fluidAlternatives=705,stochasticInputs=0,fluidInputs=705,fluidOutputs=0};"
            + "Sampler{pages=9216,inputSlots=27648,outputSlots=9216,catalystSlots=0,itemAlternatives=36864,fluidAlternatives=0,stochasticInputs=0,fluidInputs=0,fluidOutputs=0};"
            + "Imprinter{pages=1,inputSlots=2,outputSlots=1,catalystSlots=1,itemAlternatives=4,fluidAlternatives=0,stochasticInputs=0,fluidInputs=0,fluidOutputs=0};"
            + "totalPages=11566";
    static final String EXPECTED_SHA256 =
            "309af5c6f49c4326e51ac4b967e9dd1ec496da438c830fa3f53e6315874542d5";

    // These reviewed page counts and the corpus identity above are one promotion unit.
    // Any GTNH/Gendustry drift therefore fails before rendering or manifest publication.
    static final int EXPECTED_LIQUIFIER_PAGES = 40;
    static final int EXPECTED_MUTAGEN_PRODUCER_PAGES = 15;
    static final int EXPECTED_EXTRACTOR_PAGES = 1578;
    static final int EXPECTED_REPLICATOR_PAGES = 3;
    static final int EXPECTED_TRANSPOSER_PAGES = 8;
    static final int EXPECTED_MUTATRON_PAGES = 705;
    static final int EXPECTED_SAMPLER_PAGES = 9216;
    static final int EXPECTED_IMPRINTER_PAGES = 1;

    static final String LIQUIFIER =
            "net.bdew.gendustry.nei.LiquifierHandler";
    static final String MUTAGEN_PRODUCER =
            "net.bdew.gendustry.nei.MutagenProducerHandler";
    static final String EXTRACTOR =
            "net.bdew.gendustry.nei.ExtractorHandler";
    static final String REPLICATOR =
            "net.bdew.gendustry.nei.ReplicatorHandler";
    static final String TRANSPOSER =
            "net.bdew.gendustry.nei.TransposerHandler";
    static final String MUTATRON =
            "net.bdew.gendustry.nei.MutatronHandler";
    static final String SAMPLER =
            "net.bdew.gendustry.nei.SamplerHandler";
    static final String IMPRINTER =
            "net.bdew.gendustry.nei.ImprinterHandler";

    private static final String BASE_HANDLER =
            "net.bdew.gendustry.nei.BaseRecipeHandler";
    private static final String CACHED_BASE =
            BASE_HANDLER + "$CachedRecipeWithComponents";
    private static final String RECIPE_COMPONENT =
            "net.bdew.gendustry.nei.helpers.RecipeComponent";
    private static final String FLUID_COMPONENT =
            "net.bdew.gendustry.nei.helpers.FluidComponent";
    private static final String POWER_COMPONENT =
            "net.bdew.gendustry.nei.helpers.PowerComponent";
    private static final String BASE_RECT = "net.bdew.lib.gui.BaseRect";
    private static final String FLUIDS = "net.bdew.gendustry.config.Fluids$";

    private static final Map<String, HandlerSpec> SPECS;
    private static final List<String> HANDLER_ORDER;
    private static final Map<ICraftingHandler,
            List<CompleteCategoryAdapters.RecipeSemanticOverride>> SEMANTICS =
            new IdentityHashMap<ICraftingHandler,
                    List<CompleteCategoryAdapters.RecipeSemanticOverride>>();
    private static final Map<String, HandlerObservation> OBSERVATIONS =
            new LinkedHashMap<String, HandlerObservation>();

    static {
        Map<String, HandlerSpec> specs = new LinkedHashMap<String, HandlerSpec>();

        add(specs, new HandlerSpec(
                LIQUIFIER, "Liquifier", "liquifier", "LiquifierRecipe",
                new ItemRole[] { consumed("inPositioned", 39, 28) },
                null,
                new FluidRole[] { variableOutput("protein", "protein", 5000,
                        152, 19) },
                "net.bdew.gendustry.machines.liquifier.MachineLiquifier$",
                50000.0f, 100000.0f, null, 0.0d));
        add(specs, new HandlerSpec(
                MUTAGEN_PRODUCER, "MutagenProducer", "mutagen-producer",
                "MutagenProducerRecipe",
                new ItemRole[] { consumed("inPositioned", 39, 28) },
                null,
                new FluidRole[] { variableOutput("mutagen", "mutagen", 10000,
                        152, 19) },
                "net.bdew.gendustry.machines.mproducer.MachineMutagenProducer$",
                200000.0f, 1000000.0f, null, 0.0d));
        add(specs, new HandlerSpec(
                EXTRACTOR, "Extractor", "extractor", "ExtractorRecipe",
                new ItemRole[] {
                        consumed("inPositioned", 39, 28),
                        stochasticLabware("labware", 89, 6)
                },
                null,
                new FluidRole[] { variableOutput("dna", "liquiddna", 5000,
                        152, 19) },
                "net.bdew.gendustry.machines.extractor.MachineExtractor$",
                120000.0f, 400000.0f, "labwareConsumeChance", 0.50d));
        add(specs, new HandlerSpec(
                REPLICATOR, "Replicator", "replicator", "ReplicatorRecipe",
                new ItemRole[] { catalyst("templateStack", 93, 4) },
                new Position(137, 28),
                new FluidRole[] {
                        fixedInput("dna", "liquiddna", 5000, 10000, 32, 19),
                        fixedInput("protein", "protein", 5000, 50000, 56, 19)
                },
                "net.bdew.gendustry.machines.replicator.MachineReplicator$",
                350000.0f, 800000.0f, null, 0.0d));
        add(specs, new HandlerSpec(
                TRANSPOSER, "Transposer", "transposer", "TransposerRecipe",
                new ItemRole[] {
                        catalyst("template", 69, 15),
                        consumed("blank", 36, 36),
                        stochasticLabware("labware", 93, 15)
                },
                new Position(132, 36), new FluidRole[0],
                "net.bdew.gendustry.machines.transposer.MachineTransposer$",
                80000.0f, 100000.0f, "labwareConsumeChance", 0.20d));
        add(specs, new HandlerSpec(
                MUTATRON, "Mutatron", "mutatron", "MutatronRecipe",
                new ItemRole[] {
                        consumed("in1", 55, 17),
                        consumed("in2", 55, 40),
                        consumed("labware", 93, 4)
                },
                new Position(137, 28),
                new FluidRole[] { fixedInput("mutagen", "mutagen", 5000,
                        10000, 32, 19) },
                "net.bdew.gendustry.machines.mutatron.MachineMutatron$",
                2000000.0f, 2000000.0f, "labwareConsumeChance", 1.0d,
                new RuntimePercentage[] {
                        percentage("degradeChanceNatural", 30.0f),
                        percentage("deathChanceArtificial", 80.0f),
                        percentage("secretChance", 10.0f)
                }));
        add(specs, new HandlerSpec(
                SAMPLER, "Sampler", "sampler", "SamplerRecipe",
                new ItemRole[] {
                        consumed("individual", 36, 36),
                        consumed("sampleBlank", 69, 15),
                        consumed("labware", 93, 15)
                },
                new Position(132, 36), new FluidRole[0],
                "net.bdew.gendustry.machines.sampler.MachineSampler$",
                20000.0f, 100000.0f, "labwareConsumeChance", 1.0d));
        add(specs, new HandlerSpec(
                IMPRINTER, "Imprinter", "imprinter", "ImprinterRecipe",
                new ItemRole[] {
                        consumed("input", 36, 36),
                        catalyst("template", 69, 15),
                        consumed("labware", 93, 15)
                },
                new Position(132, 36), new FluidRole[0],
                "net.bdew.gendustry.machines.imprinter.MachineImprinter$",
                120000.0f, 400000.0f, "labwareConsumeChance", 1.0d,
                new RuntimePercentage[] {
                        percentage("deathChanceNatural", 20),
                        percentage("deathChanceArtificial", 40)
                }));

        SPECS = Collections.unmodifiableMap(specs);
        HANDLER_ORDER = Collections.unmodifiableList(
                new ArrayList<String>(specs.keySet()));
    }

    private GendustryMachineSemanticAdapter() {
    }

    static boolean supports(String handlerClass) {
        return handlerClass != null && SPECS.containsKey(handlerClass);
    }

    static Set<String> supportedHandlerClasses() {
        return Collections.unmodifiableSet(new HashSet<String>(SPECS.keySet()));
    }

    static String operationId(String handlerClass) {
        HandlerSpec spec = SPECS.get(handlerClass);
        return spec == null ? null : spec.operationId;
    }

    static String semanticKind(String handlerClass) {
        HandlerSpec spec = SPECS.get(handlerClass);
        return spec == null ? null : spec.kind;
    }

    static String stateDependentOutcomeScope(String handlerClass) {
        HandlerSpec spec = SPECS.get(handlerClass);
        return spec == null || spec.stateDependentOutcomes.length == 0
                ? null : spec.stateDependentOutcomeSummary();
    }

    static boolean requiresDiscovery() {
        return !hasCompletePromotion(EXPECTED_COUNT_VECTOR, EXPECTED_SHA256);
    }

    static void validatePrototype(ICraftingHandler prototype) throws ExportFailure {
        if (prototype == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Gendustry structural validation received a null prototype");
        }
        String handlerClass = prototype.getClass().getName();
        HandlerSpec spec = SPECS.get(handlerClass);
        if (spec == null) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "no exact Gendustry machine adapter exists for " + handlerClass);
        }
        try {
            if (!(prototype instanceof TemplateRecipeHandler)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerClass
                        + " is no longer a TemplateRecipeHandler");
            }
            if (prototype.numRecipes() != 0) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerClass
                        + " prototype must have zero loaded pages; got "
                        + prototype.numRecipes());
            }
            ClassLoader loader = prototype.getClass().getClassLoader();
            Class<?> base = Class.forName(BASE_HANDLER, false, loader);
            if (prototype.getClass().getSuperclass() != base
                    || base.getSuperclass() != TemplateRecipeHandler.class) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerClass
                        + " exact BaseRecipeHandler hierarchy drifted");
            }
            requireIntMethod(base, "offX", prototype, 5);
            requireIntMethod(base, "offY", prototype, 13);
            if (IMPRINTER.equals(handlerClass)) {
                // Gendustry's Imprinter is the only member of this exact family whose
                // complete category is a single synthetic example. Its Scala handler
                // declares addExample(), while the seven registry-backed handlers
                // declare addAllRecipes(). Keep this asymmetry explicit and fail closed.
                exactDeclaredMethod(prototype.getClass(), "addExample", void.class);
            } else {
                exactDeclaredMethod(prototype.getClass(), "addAllRecipes", void.class);
            }
            exactDeclaredMethod(prototype.getClass(), "loadTransferRects", void.class);
            exactDeclaredMethod(prototype.getClass(), "getRecipeName", String.class);

            Class<?> cachedBase = Class.forName(CACHED_BASE, false, loader);
            if (cachedBase.getSuperclass()
                    != TemplateRecipeHandler.CachedRecipe.class) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", CACHED_BASE
                        + " superclass drifted");
            }
            Method components = cachedBase.getMethod("components");
            if (!"scala.collection.immutable.List".equals(
                    components.getReturnType().getName())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", CACHED_BASE
                        + ".components return type drifted");
            }
            Class<?> cached = spec.cachedClass(loader);
            if (cached.getSuperclass() != cachedBase) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", spec.cachedClassName
                        + " superclass drifted");
            }
            validateCachedMethods(spec, cached);
            validateMachine(spec, loader);
        } catch (ExportFailure failure) {
            logFailure("prototype validation", handlerClass, failure);
            throw failure;
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            ExportFailure failure = new ExportFailure("HANDLER_UNLOADED",
                    handlerClass + " exact Gendustry structural validation failed",
                    cause);
            logFailure("prototype validation", handlerClass, failure);
            throw failure;
        }
    }

    static ICraftingHandler loadCompleteCategory(ICraftingHandler prototype)
            throws ExportFailure {
        validatePrototype(prototype);
        String handlerClass = prototype.getClass().getName();
        HandlerSpec spec = SPECS.get(handlerClass);
        try {
            ICraftingHandler loaded = prototype.getRecipeHandler(spec.operationId);
            requireExactClass(loaded, handlerClass);
            if (!(loaded instanceof TemplateRecipeHandler)) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", handlerClass
                        + " complete-category query returned a non-template handler");
            }
            TemplateRecipeHandler target = (TemplateRecipeHandler) loaded;
            if (target.numRecipes() <= 0) {
                throw new ExportFailure("HANDLER_UNLOADED", handlerClass
                        + " complete-category query returned no pages");
            }
            int expectedPages = expectedPages(handlerClass);
            if (expectedPages > 0 && target.numRecipes() != expectedPages) {
                throw new ExportFailure("HANDLER_UNLOADED", handlerClass
                        + " page cardinality drifted; expected " + expectedPages
                        + ", got " + target.numRecipes());
            }

            BuildResult result = build(target, spec);
            if (result.pages.size() != target.numRecipes()) {
                throw new ExportFailure("RECIPE_SEMANTICS", handlerClass
                        + " preview/semantic page counts diverged; previews="
                        + target.numRecipes() + ", semantics=" + result.pages.size());
            }
            HandlerObservation observation = result.finish(spec);
            synchronized (GendustryMachineSemanticAdapter.class) {
                SEMANTICS.put(target, Collections.unmodifiableList(
                        new ArrayList<CompleteCategoryAdapters.RecipeSemanticOverride>(
                                result.pages)));
                HandlerObservation previous = OBSERVATIONS.put(
                        handlerClass, observation);
                if (previous != null
                        && (!previous.countVector.equals(observation.countVector)
                        || !previous.fingerprint.equals(observation.fingerprint))) {
                    throw new ExportFailure("HANDLER_UNLOADED", handlerClass
                            + " changed across complete captures in one boot; first="
                            + previous.countVector + '/' + previous.fingerprint
                            + ", second=" + observation.countVector + '/'
                            + observation.fingerprint);
                }
            }
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Gendustry machine semantic adapter ready: "
                            + "handler={}, countVector={}, fingerprint={}",
                    handlerClass, observation.countVector,
                    observation.fingerprint);
            if (spec.stateDependentOutcomes.length > 0) {
                GtnhNeiExportMod.LOGGER.warn(
                        "[gtnh-nei-export] {} exports the successful NEI transition "
                                + "only; its state-dependent failure/selection branches "
                                + "cannot be represented as unconditional format-2 recipe "
                                + "edges. Pinned runtime percentages: {}",
                        handlerClass, spec.stateDependentOutcomeSummary());
            }
            return target;
        } catch (ExportFailure failure) {
            logFailure("complete-category load", handlerClass, failure);
            throw failure;
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            FatalErrors.rethrowIfFatal(cause);
            ExportFailure failure = new ExportFailure("HANDLER_UNLOADED",
                    handlerClass + " exact Gendustry semantic adapter failed", cause);
            logFailure("complete-category load", handlerClass, failure);
            throw failure;
        }
    }

    static synchronized CompleteCategoryAdapters.RecipeSemanticOverride
            semanticOverride(ICraftingHandler loadedHandler, int recipeIndex)
            throws ExportFailure {
        if (loadedHandler == null || !supports(loadedHandler.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    "Gendustry semantic lookup received an unsupported handler");
        }
        List<CompleteCategoryAdapters.RecipeSemanticOverride> pages =
                SEMANTICS.get(loadedHandler);
        if (pages == null) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    loadedHandler.getClass().getName()
                            + " has no attached Gendustry semantic corpus");
        }
        if (pages.size() != loadedHandler.numRecipes()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    loadedHandler.getClass().getName()
                            + " preview/semantic corpus changed after attachment");
        }
        if (recipeIndex < 0 || recipeIndex >= pages.size()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    loadedHandler.getClass().getName()
                            + " semantic index is out of bounds: " + recipeIndex);
        }
        return pages.get(recipeIndex);
    }

    static synchronized CorpusObservation requirePromotedCorpus()
            throws ExportFailure {
        CorpusObservation observed = corpusObservation(OBSERVATIONS);
        requirePromotion(observed, EXPECTED_COUNT_VECTOR, EXPECTED_SHA256);
        return observed;
    }

    static synchronized void applyDiagnostics(ExportContext context)
            throws ExportFailure {
        if (context == null) {
            throw new ExportFailure("INTERNAL_ERROR",
                    "Gendustry diagnostics received a null export context");
        }
        CorpusObservation corpus = requirePromotedCorpus();
        context.adaptedGendustryLiquifierRecipes = pages(LIQUIFIER);
        context.adaptedGendustryMutagenProducerRecipes = pages(MUTAGEN_PRODUCER);
        context.adaptedGendustryExtractorRecipes = pages(EXTRACTOR);
        context.adaptedGendustryReplicatorRecipes = pages(REPLICATOR);
        context.adaptedGendustryTransposerRecipes = pages(TRANSPOSER);
        context.adaptedGendustryMutatronRecipes = pages(MUTATRON);
        context.adaptedGendustrySamplerRecipes = pages(SAMPLER);
        context.adaptedGendustryImprinterRecipes = pages(IMPRINTER);
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Applied promoted Gendustry diagnostics: "
                        + "countVector={}, fingerprint={}",
                corpus.countVector, corpus.fingerprint);
    }

    private static int pages(String handlerClass) throws ExportFailure {
        HandlerObservation observation = OBSERVATIONS.get(handlerClass);
        if (observation == null) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "missing Gendustry observation for " + handlerClass);
        }
        return observation.pages;
    }

    static int expectedPages(String handlerClass) {
        if (LIQUIFIER.equals(handlerClass)) return EXPECTED_LIQUIFIER_PAGES;
        if (MUTAGEN_PRODUCER.equals(handlerClass)) {
            return EXPECTED_MUTAGEN_PRODUCER_PAGES;
        }
        if (EXTRACTOR.equals(handlerClass)) return EXPECTED_EXTRACTOR_PAGES;
        if (REPLICATOR.equals(handlerClass)) return EXPECTED_REPLICATOR_PAGES;
        if (TRANSPOSER.equals(handlerClass)) return EXPECTED_TRANSPOSER_PAGES;
        if (MUTATRON.equals(handlerClass)) return EXPECTED_MUTATRON_PAGES;
        if (SAMPLER.equals(handlerClass)) return EXPECTED_SAMPLER_PAGES;
        if (IMPRINTER.equals(handlerClass)) return EXPECTED_IMPRINTER_PAGES;
        return -1;
    }

    static void requirePromotionForTest(CorpusObservation observed,
                                        String expectedCountVector,
                                        String expectedSha256)
            throws ExportFailure {
        requirePromotion(observed, expectedCountVector, expectedSha256);
    }

    static void requireNoFluidTagForTest(NBTTagCompound tag)
            throws ExportFailure {
        rejectTaggedFluid(tag, "test fluid");
    }

    static String stablePageMultisetFingerprint(
            String handlerClass, String countVector,
            List<String> pageCanonicals) throws ExportFailure {
        if (handlerClass == null || handlerClass.trim().isEmpty()
                || countVector == null || countVector.trim().isEmpty()
                || pageCanonicals == null) {
            throw new ExportFailure("INTERNAL_ERROR",
                    "Gendustry fingerprint received incomplete state");
        }
        List<String> sorted = new ArrayList<String>(pageCanonicals.size());
        for (int index = 0; index < pageCanonicals.size(); index++) {
            String canonical = pageCanonicals.get(index);
            if (canonical == null) {
                throw new ExportFailure("INTERNAL_ERROR",
                        "Gendustry fingerprint received null page #" + index);
            }
            sorted.add(canonical);
        }
        Collections.sort(sorted);
        StringBuilder basis = new StringBuilder(
                Math.max(512, sorted.size() * 256));
        appendField(basis, CONTRACT);
        appendField(basis, handlerClass);
        appendField(basis, countVector);
        appendField(basis, Integer.toString(sorted.size()));
        for (String canonical : sorted) appendField(basis, canonical);
        return sha256(basis.toString());
    }

    private static BuildResult build(TemplateRecipeHandler target, HandlerSpec spec)
            throws Exception {
        BuildResult result = new BuildResult();
        Class<?> cachedType = spec.cachedClass(target.getClass().getClassLoader());
        for (int index = 0; index < target.numRecipes(); index++) {
            Object cached = target.arecipes.get(index);
            if (cached == null || cached.getClass() != cachedType) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", spec.handlerClass
                        + " cached page #" + index + " class drifted; expected "
                        + cachedType.getName() + ", got "
                        + (cached == null ? "<null>" : cached.getClass().getName()));
            }
            List<PositionedStack> genericInputs = target.getIngredientStacks(index);
            List<PositionedStack> genericOthers = target.getOtherStacks(index);
            PositionedStack genericResult = target.getResultStack(index);
            if (genericInputs == null || genericOthers == null
                    || !genericOthers.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS", spec.handlerClass
                        + " page #" + index
                        + " generic input/other-stack topology drifted");
            }
            if (genericInputs.size() != spec.items.length) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", spec.handlerClass
                        + " page #" + index + " ingredient count drifted; expected "
                        + spec.items.length + ", got " + genericInputs.size());
            }

            List<CompleteCategoryAdapters.SemanticSlot> inputs =
                    new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
            List<CompleteCategoryAdapters.SemanticSlot> outputs =
                    new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
            List<CompleteCategoryAdapters.SemanticSlot> catalysts =
                    new ArrayList<CompleteCategoryAdapters.SemanticSlot>();
            StringBuilder metadata = new StringBuilder(192);

            for (int itemIndex = 0; itemIndex < spec.items.length; itemIndex++) {
                ItemRole role = spec.items[itemIndex];
                PositionedStack accessor = positioned(
                        cachedType, cached, role.accessor, role.position,
                        spec.handlerClass + " page #" + index + ' ' + role.accessor);
                PositionedStack generic = genericInputs.get(itemIndex);
                requireSamePositioned(accessor, generic, spec.handlerClass
                        + " page #" + index + " ingredient #" + itemIndex);
                CompleteCategoryAdapters.SemanticSlot slot = itemSlot(
                        accessor, result, spec.handlerClass + " page #" + index
                                + ' ' + role.accessor);
                if (role.disposition == ItemDisposition.CATALYST) {
                    catalysts.add(slot);
                } else if (role.disposition
                        == ItemDisposition.STOCHASTIC_LABWARE) {
                    if (!(spec.labwareProbability > 0.0d
                            && spec.labwareProbability < 1.0d)) {
                        throw new ExportFailure("RECIPE_SEMANTICS",
                                spec.handlerClass
                                        + " stochastic labware probability is invalid: "
                                        + spec.labwareProbability);
                    }
                    inputs.add(new CompleteCategoryAdapters.SemanticSlot(
                            slot.alternatives, spec.labwareProbability));
                    catalysts.add(slot);
                    result.stochasticInputs++;
                } else {
                    inputs.add(slot);
                }
            }

            List<Object> components = components(cached, spec, index);
            int fluidComponentIndex = 0;
            for (FluidRole fluidRole : spec.fluids) {
                Object component = components.get(fluidComponentIndex++);
                FluidStack fluid = validateFluidComponent(
                        component, cached, spec, fluidRole, index);
                CompleteCategoryAdapters.SemanticSlot slot = fluidSlot(
                        fluid, result, spec.handlerClass + " page #" + index
                                + ' ' + fluidRole.accessor);
                if (fluidRole.output) {
                    outputs.add(slot);
                    result.fluidOutputs++;
                } else {
                    inputs.add(slot);
                    result.fluidInputs++;
                }
                metadata.append(fluidRole.output ? "FO" : "FI")
                        .append(':').append(fluidRole.accessor).append(':')
                        .append(fluid.amount).append(':')
                        .append(fluidRole.capacity).append(';');
            }
            Object power = components.get(components.size() - 1);
            validatePowerComponent(power, spec, index);
            metadata.append("P:")
                    .append(Float.floatToRawIntBits(spec.power))
                    .append(':')
                    .append(Float.floatToRawIntBits(spec.powerCapacity))
                    .append(";L:")
                    .append(Double.doubleToRawLongBits(spec.labwareProbability))
                    .append(';');
            appendStateDependentOutcomeMetadata(metadata, spec);

            if (spec.resultPosition == null) {
                if (genericResult != null) {
                    throw new ExportFailure("RECIPE_SEMANTICS", spec.handlerClass
                            + " page #" + index
                            + " unexpectedly exposed a generic item result");
                }
            } else {
                PositionedStack accessor = positioned(cachedType, cached,
                        "getResult", spec.resultPosition,
                        spec.handlerClass + " page #" + index + " result");
                requireSamePositioned(accessor, genericResult, spec.handlerClass
                        + " page #" + index + " result");
                outputs.add(itemSlot(accessor, result,
                        spec.handlerClass + " page #" + index + " result"));
            }

            if (inputs.isEmpty() || outputs.isEmpty()) {
                throw new ExportFailure("RECIPE_SEMANTICS", spec.handlerClass
                        + " page #" + index
                        + " must expose nonempty exact graph inputs and outputs");
            }
            String canonical = semanticCanonical(
                    spec.kind, inputs, outputs, catalysts, metadata.toString());
            result.pageCanonicals.add(canonical);
            result.inputSlots += inputs.size();
            result.outputSlots += outputs.size();
            result.catalystSlots += catalysts.size();
            result.pages.add(new CompleteCategoryAdapters.RecipeSemanticOverride(
                    "gendustry:" + spec.kind + ':' + Naming.sha256(canonical),
                    inputs, outputs, catalysts));
        }
        return result;
    }

    private static List<Object> components(
            Object cached, HandlerSpec spec, int pageIndex) throws Exception {
        Method method = cached.getClass().getMethod("components");
        Object scalaList = method.invoke(cached);
        if (scalaList == null) {
            throw new ExportFailure("RECIPE_SEMANTICS", spec.handlerClass
                    + " page #" + pageIndex + " components list is null");
        }
        Method sizeMethod = scalaList.getClass().getMethod("size");
        Method applyMethod = scalaList.getClass().getMethod("apply", int.class);
        int size = ((Integer) sizeMethod.invoke(scalaList)).intValue();
        int expected = spec.fluids.length + 1;
        if (size != expected) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", spec.handlerClass
                    + " page #" + pageIndex + " component count drifted; expected "
                    + expected + ", got " + size);
        }
        List<Object> components = new ArrayList<Object>(size);
        for (int index = 0; index < size; index++) {
            Object component = applyMethod.invoke(scalaList, Integer.valueOf(index));
            String expectedClass = index < spec.fluids.length
                    ? FLUID_COMPONENT : POWER_COMPONENT;
            if (component == null
                    || !expectedClass.equals(component.getClass().getName())) {
                throw new ExportFailure("HANDLER_AMBIGUOUS", spec.handlerClass
                        + " page #" + pageIndex + " component #" + index
                        + " drifted; expected " + expectedClass + ", got "
                        + (component == null ? "<null>"
                        : component.getClass().getName()));
            }
            components.add(component);
        }
        return components;
    }

    private static FluidStack validateFluidComponent(
            Object component, Object cached, HandlerSpec spec,
            FluidRole role, int pageIndex) throws Exception {
        Field stackField = exactDeclaredField(
                component.getClass(), "fStack", FluidStack.class);
        Field capacityField = exactDeclaredField(
                component.getClass(), "capacity", int.class);
        FluidStack fluid = (FluidStack) stackField.get(component);
        int capacity = capacityField.getInt(component);
        if (fluid == null || fluid.getFluid() == null || capacity != role.capacity) {
            throw new ExportFailure("RECIPE_SEMANTICS", spec.handlerClass
                    + " page #" + pageIndex + " " + role.accessor
                    + " fluid/capacity drifted");
        }
        rejectTaggedFluid(fluid.tag, spec.handlerClass + " page #" + pageIndex
                + ' ' + role.accessor);
        String name = FluidRegistry.getFluidName(fluid);
        if (!role.registryName.equals(name)) {
            throw new ExportFailure("RECIPE_SEMANTICS", spec.handlerClass
                    + " page #" + pageIndex + " expected fluid "
                    + role.registryName + ", got " + name);
        }
        int expectedAmount = role.variableFromCachedOut
                ? ((Integer) cached.getClass().getMethod("out").invoke(cached)).intValue()
                : role.amount;
        if (expectedAmount <= 0 || fluid.amount != expectedAmount) {
            throw new ExportFailure("QUANTITY_INVALID", spec.handlerClass
                    + " page #" + pageIndex + " " + role.accessor
                    + " amount drifted; expected " + expectedAmount
                    + ", got " + fluid.amount);
        }
        validateFluidSingleton(component.getClass().getClassLoader(),
                role.accessor, role.registryName, fluid.getFluid());
        requireRect(component, role.x, role.y, 16.0f, 58.0f,
                spec.handlerClass + " page #" + pageIndex + ' ' + role.accessor);
        return fluid.copy();
    }

    private static void validateFluidSingleton(
            ClassLoader loader, String accessor, String registryName,
            Fluid observed) throws Exception {
        Class<?> fluids = Class.forName(FLUIDS, false, loader);
        Field moduleField = fluids.getField("MODULE$");
        Object module = moduleField.get(null);
        Method method = fluids.getMethod(accessor);
        if (method.getReturnType() != Fluid.class
                || method.getDeclaringClass() != fluids
                || method.invoke(module) != observed
                || !registryName.equals(FluidRegistry.getFluidName(observed))) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "Gendustry Fluids$." + accessor
                            + " runtime binding drifted from " + registryName);
        }
    }

    private static void validatePowerComponent(
            Object component, HandlerSpec spec, int pageIndex) throws Exception {
        Field powerField = exactDeclaredField(
                component.getClass(), "power", float.class);
        Field capacityField = exactDeclaredField(
                component.getClass(), "capacity", float.class);
        float power = powerField.getFloat(component);
        float capacity = capacityField.getFloat(component);
        if (Float.floatToRawIntBits(power)
                != Float.floatToRawIntBits(spec.power)
                || Float.floatToRawIntBits(capacity)
                != Float.floatToRawIntBits(spec.powerCapacity)) {
            throw new ExportFailure("RECIPE_SEMANTICS", spec.handlerClass
                    + " page #" + pageIndex + " power component drifted");
        }
        requireRect(component, 8.0f, 19.0f, 16.0f, 58.0f,
                spec.handlerClass + " page #" + pageIndex + " power");
    }

    private static void requireRect(Object component,
                                    float x, float y, float width, float height,
                                    String label) throws Exception {
        Method rectMethod = component.getClass().getMethod("rect");
        Object rect = rectMethod.invoke(component);
        if (rect == null || !BASE_RECT.equals(rect.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    label + " component rectangle class drifted");
        }
        requireFloatAccessor(rect, "x", x, label);
        requireFloatAccessor(rect, "y", y, label);
        requireFloatAccessor(rect, "w", width, label);
        requireFloatAccessor(rect, "h", height, label);
    }

    private static void requireFloatAccessor(
            Object owner, String methodName, float expected, String label)
            throws Exception {
        Object raw = owner.getClass().getMethod(methodName).invoke(owner);
        if (!(raw instanceof Number)
                || Float.floatToRawIntBits(((Number) raw).floatValue())
                != Float.floatToRawIntBits(expected)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label + ' ' + methodName
                    + " drifted; expected " + expected + ", got " + raw);
        }
    }

    private static CompleteCategoryAdapters.SemanticSlot itemSlot(
            PositionedStack positioned, BuildResult result, String label)
            throws ExportFailure {
        if (positioned == null || positioned.items == null
                || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " has no item alternatives");
        }
        List<CompleteCategoryAdapters.SemanticAlternative> alternatives =
                new ArrayList<CompleteCategoryAdapters.SemanticAlternative>();
        Set<String> seen = new HashSet<String>();
        for (int index = 0; index < positioned.items.length; index++) {
            ItemStack original = positioned.items[index];
            if (original == null || original.getItem() == null
                    || original.stackSize <= 0) {
                throw new ExportFailure("QUANTITY_INVALID", label
                        + " has invalid alternative #" + index);
            }
            ItemStack copy = original.copy();
            StackIdentity identity = StackIdentity.of(copy);
            String canonical = CompleteCategoryAdapters.canonicalStackIdentity(
                    identity, copy.stackSize);
            if (seen.add(canonical)) {
                alternatives.add(new CompleteCategoryAdapters.SemanticAlternative(
                        copy, copy.stackSize, canonical));
            }
        }
        if (alternatives.isEmpty()) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " lost every exact alternative");
        }
        Collections.sort(alternatives,
                new java.util.Comparator<CompleteCategoryAdapters.SemanticAlternative>() {
                    @Override
                    public int compare(
                            CompleteCategoryAdapters.SemanticAlternative left,
                            CompleteCategoryAdapters.SemanticAlternative right) {
                        return left.canonicalIdentity.compareTo(
                                right.canonicalIdentity);
                    }
                });
        result.itemAlternatives += alternatives.size();
        return new CompleteCategoryAdapters.SemanticSlot(alternatives);
    }

    private static CompleteCategoryAdapters.SemanticSlot fluidSlot(
            FluidStack original, BuildResult result, String label)
            throws ExportFailure {
        if (original == null || original.getFluid() == null
                || original.amount <= 0) {
            throw new ExportFailure("QUANTITY_INVALID",
                    label + " has an invalid fluid amount");
        }
        rejectTaggedFluid(original.tag, label);
        ItemStack proxy = GTUtility.getFluidDisplayStack(original.copy(), true, true);
        if (proxy == null || proxy.getItem() == null) {
            throw new ExportFailure("ITEM_IDENTITY", label
                    + " could not create the pinned GregTech fluid-display proxy");
        }
        StackIdentity identity;
        try {
            identity = StackIdentity.of(proxy);
        } catch (RuntimeException error) {
            throw new ExportFailure("ITEM_IDENTITY", label
                    + " fluid-display proxy could not be decoded", error);
        }
        String expectedKey = "fluid|fluid:" + FluidRegistry.getFluidName(original);
        if (!identity.isFluid() || !expectedKey.equals(identity.key)
                || identity.amount != original.amount
                || identity.canonicalNbt != null) {
            throw new ExportFailure("ITEM_IDENTITY", label
                    + " GregTech proxy identity drifted; expectedKey=" + expectedKey
                    + ", expectedAmount=" + original.amount + ", observedKey="
                    + identity.key + ", observedAmount=" + identity.amount);
        }
        String canonical = CompleteCategoryAdapters.canonicalStackIdentity(
                identity, original.amount);
        result.fluidAlternatives++;
        return new CompleteCategoryAdapters.SemanticSlot(
                Collections.singletonList(
                        new CompleteCategoryAdapters.SemanticAlternative(
                                proxy, original.amount, canonical)));
    }

    private static String semanticCanonical(
            String kind,
            List<CompleteCategoryAdapters.SemanticSlot> inputs,
            List<CompleteCategoryAdapters.SemanticSlot> outputs,
            List<CompleteCategoryAdapters.SemanticSlot> catalysts,
            String metadata) {
        StringBuilder canonical = new StringBuilder(640);
        appendField(canonical, kind);
        appendField(canonical, metadata);
        appendSlots(canonical, 'I', inputs);
        appendSlots(canonical, 'O', outputs);
        appendSlots(canonical, 'C', catalysts);
        canonical.append('\n');
        return canonical.toString();
    }

    private static void appendSlots(
            StringBuilder canonical, char role,
            List<CompleteCategoryAdapters.SemanticSlot> slots) {
        canonical.append(role).append(slots.size()).append(';');
        for (CompleteCategoryAdapters.SemanticSlot slot : slots) {
            canonical.append('S').append(slot.alternatives.size()).append(';');
            canonical.append('P');
            if (slot.probability == null) {
                canonical.append('-');
            } else {
                canonical.append(Double.doubleToRawLongBits(
                        slot.probability.doubleValue()));
            }
            canonical.append(';');
            for (CompleteCategoryAdapters.SemanticAlternative alternative
                    : slot.alternatives) {
                appendField(canonical, alternative.canonicalIdentity);
            }
        }
    }

    private static PositionedStack positioned(
            Class<?> cachedType, Object cached, String methodName,
            Position expected, String label) throws Exception {
        Method method = cachedType.getMethod(methodName);
        if (method.getDeclaringClass() != cachedType
                || method.getReturnType() != PositionedStack.class
                || !Modifier.isPublic(method.getModifiers())
                || Modifier.isStatic(method.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    label + " exact accessor contract drifted");
        }
        Object raw = method.invoke(cached);
        if (!(raw instanceof PositionedStack)) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " returned no PositionedStack");
        }
        PositionedStack positioned = (PositionedStack) raw;
        if (positioned.relx != expected.x || positioned.rely != expected.y) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", label
                    + " position drifted; expected=" + expected.x + ',' + expected.y
                    + ", got=" + positioned.relx + ',' + positioned.rely);
        }
        return positioned;
    }

    private static void requireSamePositioned(
            PositionedStack expected, PositionedStack actual, String label)
            throws ExportFailure {
        if (expected == null || actual == null
                || expected.relx != actual.relx || expected.rely != actual.rely
                || !itemCanonicals(expected).equals(itemCanonicals(actual))) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    label + " accessor/generic NEI view diverged");
        }
    }

    private static List<String> itemCanonicals(PositionedStack positioned)
            throws ExportFailure {
        if (positioned == null || positioned.items == null
                || positioned.items.length == 0) {
            throw new ExportFailure("RECIPE_SEMANTICS",
                    "cannot canonicalize an empty Gendustry PositionedStack");
        }
        List<String> canonicals = new ArrayList<String>(positioned.items.length);
        for (ItemStack stack : positioned.items) {
            if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
                throw new ExportFailure("QUANTITY_INVALID",
                        "Gendustry PositionedStack contains an invalid alternative");
            }
            canonicals.add(CompleteCategoryAdapters.canonicalStackIdentity(
                    StackIdentity.of(stack), stack.stackSize));
        }
        Collections.sort(canonicals);
        return canonicals;
    }

    private static void validateCachedMethods(HandlerSpec spec, Class<?> cached)
            throws Exception {
        exactDeclaredMethod(cached, "getIngredients", List.class);
        boolean positionedResult = false;
        boolean scalaNullResult = false;
        for (Method method : cached.getDeclaredMethods()) {
            if ("getResult".equals(method.getName())
                    && method.getParameterTypes().length == 0
                    && Modifier.isPublic(method.getModifiers())
                    && !Modifier.isStatic(method.getModifiers())) {
                positionedResult |= method.getReturnType() == PositionedStack.class;
                scalaNullResult |= "scala.runtime.Null$".equals(
                        method.getReturnType().getName());
            }
        }
        if (!positionedResult
                || (spec.resultPosition == null) != scalaNullResult) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", spec.cachedClassName
                    + " exact result bridge topology drifted");
        }
        for (ItemRole role : spec.items) {
            exactDeclaredMethod(cached, role.accessor, PositionedStack.class);
        }
        if (spec.resultPosition == null) {
            exactDeclaredMethod(cached, "in", ItemStack.class);
            exactDeclaredMethod(cached, "out", int.class);
        }
    }

    private static void validateMachine(HandlerSpec spec, ClassLoader loader)
            throws Exception {
        Class<?> machine = Class.forName(spec.machineClass, false, loader);
        Field moduleField = machine.getField("MODULE$");
        if (moduleField.getDeclaringClass() != machine
                || !Modifier.isPublic(moduleField.getModifiers())
                || !Modifier.isStatic(moduleField.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    spec.machineClass + ".MODULE$ contract drifted");
        }
        Object module = moduleField.get(null);
        requireFloatMethod(machine, "mjPerItem", module, spec.power);
        requireFloatMethod(machine, "maxStoredEnergy", module,
                spec.powerCapacity);
        if (spec.labwareChanceMethod != null) {
            Method chance = machine.getMethod(spec.labwareChanceMethod);
            Object value = chance.invoke(module);
            double observed;
            if (chance.getReturnType() == int.class) {
                observed = ((Integer) value).intValue() / 100.0d;
            } else if (chance.getReturnType() == float.class) {
                observed = ((Float) value).floatValue() / 100.0d;
            } else {
                throw new ExportFailure("HANDLER_AMBIGUOUS", spec.machineClass
                        + '.' + spec.labwareChanceMethod
                        + " return type drifted");
            }
            if (Double.doubleToRawLongBits(observed)
                    != Double.doubleToRawLongBits(spec.labwareProbability)) {
                throw new ExportFailure("RECIPE_SEMANTICS", spec.machineClass
                        + " labware consumption probability drifted; expected "
                        + spec.labwareProbability + ", got " + observed);
            }
        }
        for (RuntimePercentage outcome : spec.stateDependentOutcomes) {
            requireRuntimePercentage(machine, module, outcome);
        }
    }

    private static void requireRuntimePercentage(
            Class<?> owner, Object receiver, RuntimePercentage expected)
            throws Exception {
        Method method = owner.getMethod(expected.methodName);
        if (method.getDeclaringClass() != owner
                || method.getReturnType() != expected.returnType
                || Modifier.isStatic(method.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", owner.getName() + '.'
                    + expected.methodName + " runtime-percentage contract drifted");
        }
        Object raw = method.invoke(receiver);
        boolean matches;
        if (expected.returnType == int.class) {
            matches = raw instanceof Integer
                    && ((Integer) raw).intValue() == expected.intValue;
        } else {
            matches = raw instanceof Float
                    && Float.floatToRawIntBits(((Float) raw).floatValue())
                    == Float.floatToRawIntBits(expected.floatValue);
        }
        if (!matches) {
            throw new ExportFailure("RECIPE_SEMANTICS", owner.getName() + '.'
                    + expected.methodName + " drifted; expected "
                    + expected.displayValue() + "%, got " + raw + '%');
        }
    }

    private static void appendStateDependentOutcomeMetadata(
            StringBuilder metadata, HandlerSpec spec) {
        for (RuntimePercentage outcome : spec.stateDependentOutcomes) {
            metadata.append("H:").append(outcome.methodName).append(':')
                    .append(outcome.returnType == int.class ? 'I' : 'F').append(':')
                    .append(outcome.rawCanonical()).append(';');
        }
    }

    private static void requireIntMethod(
            Class<?> owner, String name, Object receiver, int expected)
            throws Exception {
        Method method = owner.getMethod(name);
        if (method.getReturnType() != int.class
                || ((Integer) method.invoke(receiver)).intValue() != expected) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    owner.getName() + '.' + name + " drifted");
        }
    }

    private static void requireFloatMethod(
            Class<?> owner, String name, Object receiver, float expected)
            throws Exception {
        Method method = owner.getMethod(name);
        if (method.getDeclaringClass() != owner
                || method.getReturnType() != float.class
                || Float.floatToRawIntBits(
                ((Float) method.invoke(receiver)).floatValue())
                != Float.floatToRawIntBits(expected)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    owner.getName() + '.' + name + " drifted");
        }
    }

    private static synchronized CorpusObservation corpusObservation(
            Map<String, HandlerObservation> observations) throws ExportFailure {
        List<String> missing = new ArrayList<String>();
        for (String handler : HANDLER_ORDER) {
            if (!observations.containsKey(handler)) missing.add(handler);
        }
        if (!missing.isEmpty() || observations.size() != HANDLER_ORDER.size()) {
            throw new ExportFailure("HANDLER_UNLOADED", CONTRACT
                    + " requires all eight exact handler captures; missing=" + missing
                    + ", observed=" + observations.keySet());
        }
        StringBuilder counts = new StringBuilder();
        StringBuilder fingerprintBasis = new StringBuilder();
        int totalPages = 0;
        for (String handler : HANDLER_ORDER) {
            HandlerObservation observation = observations.get(handler);
            if (counts.length() > 0) counts.append(';');
            String shortName = handler.substring(handler.lastIndexOf('.') + 1)
                    .replace("Handler", "");
            counts.append(shortName).append('{')
                    .append(observation.countVector).append('}');
            totalPages += observation.pages;
            appendField(fingerprintBasis, handler);
            appendField(fingerprintBasis, observation.countVector);
            appendField(fingerprintBasis, observation.fingerprint);
        }
        counts.append(";totalPages=").append(totalPages);
        return new CorpusObservation(counts.toString(),
                sha256(fingerprintBasis.toString()));
    }

    private static void requirePromotion(CorpusObservation observed,
                                         String expectedCountVector,
                                         String expectedSha256)
            throws ExportFailure {
        if (observed == null || observed.countVector == null
                || observed.countVector.trim().isEmpty()
                || !isLowerHexSha256(observed.fingerprint)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    CONTRACT + " received an invalid corpus observation");
        }
        boolean countsUnpromoted = UNPROMOTED.equals(expectedCountVector);
        boolean hashUnpromoted = UNPROMOTED.equals(expectedSha256);
        if (countsUnpromoted != hashUnpromoted) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", CONTRACT
                    + " is partially promoted; count vector and SHA-256 must be "
                    + "promoted together");
        }
        if (countsUnpromoted) {
            GtnhNeiExportMod.LOGGER.error(
                    "[gtnh-nei-export] Gendustry semantic corpus is intentionally "
                            + "unpromoted; observedCountVector={}; observedSha256={}; "
                            + "export will abort before rendering",
                    observed.countVector, observed.fingerprint);
            throw new ExportFailure("HANDLER_UNLOADED", CONTRACT
                    + " is intentionally unpromoted; observedCountVector="
                    + observed.countVector + "; observedSha256="
                    + observed.fingerprint + "; export must abort before rendering. "
                    + "Promote reviewed constants only in a new exporter version.");
        }
        if (expectedCountVector == null || expectedCountVector.trim().isEmpty()
                || !isLowerHexSha256(expectedSha256)) {
            throw new ExportFailure("HANDLER_AMBIGUOUS",
                    CONTRACT + " has malformed promotion constants");
        }
        if (!expectedCountVector.equals(observed.countVector)
                || !expectedSha256.equals(observed.fingerprint)) {
            throw new ExportFailure("HANDLER_UNLOADED", CONTRACT
                    + " drifted; expectedCountVector=" + expectedCountVector
                    + "; observedCountVector=" + observed.countVector
                    + "; expectedSha256=" + expectedSha256
                    + "; observedSha256=" + observed.fingerprint);
        }
    }

    private static boolean hasCompletePromotion(String counts, String hash) {
        return counts != null && !counts.trim().isEmpty()
                && !UNPROMOTED.equals(counts) && isLowerHexSha256(hash);
    }

    private static boolean isLowerHexSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f'))) return false;
        }
        return true;
    }

    private static String sha256(String text) throws ExportFailure {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : bytes) {
                hex.append(Character.forDigit((value >>> 4) & 0xf, 16));
                hex.append(Character.forDigit(value & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new ExportFailure("INTERNAL_ERROR",
                    CONTRACT + " requires JVM SHA-256 support", error);
        }
    }

    private static void rejectTaggedFluid(NBTTagCompound tag, String label)
            throws ExportFailure {
        if (tag != null) {
            throw new ExportFailure("RECIPE_SEMANTICS", label
                    + " has tagged fluid state which format 2 cannot preserve");
        }
    }

    private static Method exactDeclaredMethod(
            Class<?> type, String name, Class<?> returnType,
            Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        if (method.getReturnType() != returnType
                || !Modifier.isPublic(method.getModifiers())
                || Modifier.isStatic(method.getModifiers())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + '.' + name
                    + " exact public instance method contract drifted");
        }
        method.setAccessible(true);
        return method;
    }

    private static Field exactDeclaredField(
            Class<?> type, String name, Class<?> expectedType) throws Exception {
        Field field = type.getDeclaredField(name);
        if (field.getType() != expectedType) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", type.getName() + '.' + name
                    + " type drifted; expected " + expectedType.getName()
                    + ", got " + field.getType().getName());
        }
        field.setAccessible(true);
        return field;
    }

    private static void requireExactClass(Object value, String expected)
            throws ExportFailure {
        if (value == null || !expected.equals(value.getClass().getName())) {
            throw new ExportFailure("HANDLER_AMBIGUOUS", "expected " + expected
                    + ", got " + (value == null ? "<null>"
                    : value.getClass().getName()));
        }
    }

    private static void appendField(StringBuilder target, String value) {
        if (value == null) {
            target.append("-1:");
        } else {
            target.append(value.length()).append(':').append(value);
        }
        target.append(';');
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void logFailure(String operation, String handler,
                                   Throwable failure) {
        GtnhNeiExportMod.LOGGER.error(
                "[gtnh-nei-export] Gendustry semantic adapter {} failed closed for {}",
                operation, handler, failure);
    }

    private static void add(Map<String, HandlerSpec> specs, HandlerSpec spec) {
        HandlerSpec previous = specs.put(spec.handlerClass, spec);
        if (previous != null) {
            throw new IllegalStateException(
                    "duplicate Gendustry handler spec " + spec.handlerClass);
        }
    }

    private static ItemRole consumed(String accessor, int x, int y) {
        return new ItemRole(accessor, new Position(x, y),
                ItemDisposition.CONSUMED);
    }

    private static ItemRole catalyst(String accessor, int x, int y) {
        return new ItemRole(accessor, new Position(x, y),
                ItemDisposition.CATALYST);
    }

    private static ItemRole stochasticLabware(String accessor, int x, int y) {
        return new ItemRole(accessor, new Position(x, y),
                ItemDisposition.STOCHASTIC_LABWARE);
    }

    private static RuntimePercentage percentage(String methodName, int value) {
        return new RuntimePercentage(methodName, int.class, value, 0.0f);
    }

    private static RuntimePercentage percentage(String methodName, float value) {
        return new RuntimePercentage(methodName, float.class, 0, value);
    }

    private static FluidRole variableOutput(
            String accessor, String registryName, int capacity, int x, int y) {
        return new FluidRole(accessor, registryName, 0, capacity,
                true, true, x, y);
    }

    private static FluidRole fixedInput(
            String accessor, String registryName, int amount, int capacity,
            int x, int y) {
        return new FluidRole(accessor, registryName, amount, capacity,
                false, false, x, y);
    }

    static final class CorpusObservation {
        final String countVector;
        final String fingerprint;

        CorpusObservation(String countVector, String fingerprint) {
            this.countVector = countVector;
            this.fingerprint = fingerprint;
        }
    }

    private static final class BuildResult {
        final List<CompleteCategoryAdapters.RecipeSemanticOverride> pages =
                new ArrayList<CompleteCategoryAdapters.RecipeSemanticOverride>();
        final List<String> pageCanonicals = new ArrayList<String>();
        int inputSlots;
        int outputSlots;
        int catalystSlots;
        int itemAlternatives;
        int fluidAlternatives;
        int stochasticInputs;
        int fluidInputs;
        int fluidOutputs;

        HandlerObservation finish(HandlerSpec spec) throws ExportFailure {
            String countVector = "pages=" + pages.size()
                    + ",inputSlots=" + inputSlots
                    + ",outputSlots=" + outputSlots
                    + ",catalystSlots=" + catalystSlots
                    + ",itemAlternatives=" + itemAlternatives
                    + ",fluidAlternatives=" + fluidAlternatives
                    + ",stochasticInputs=" + stochasticInputs
                    + ",fluidInputs=" + fluidInputs
                    + ",fluidOutputs=" + fluidOutputs;
            return new HandlerObservation(pages.size(), countVector,
                    stablePageMultisetFingerprint(
                            spec.handlerClass, countVector, pageCanonicals));
        }
    }

    private static final class HandlerObservation {
        final int pages;
        final String countVector;
        final String fingerprint;

        HandlerObservation(int pages, String countVector, String fingerprint) {
            this.pages = pages;
            this.countVector = countVector;
            this.fingerprint = fingerprint;
        }
    }

    private static final class HandlerSpec {
        final String handlerClass;
        final String operationId;
        final String kind;
        final String cachedClassName;
        final ItemRole[] items;
        final Position resultPosition;
        final FluidRole[] fluids;
        final String machineClass;
        final float power;
        final float powerCapacity;
        final String labwareChanceMethod;
        final double labwareProbability;
        final RuntimePercentage[] stateDependentOutcomes;

        HandlerSpec(String handlerClass, String operationId, String kind,
                    String cachedSimpleName, ItemRole[] items,
                    Position resultPosition, FluidRole[] fluids,
                    String machineClass, float power, float powerCapacity,
                    String labwareChanceMethod, double labwareProbability) {
            this(handlerClass, operationId, kind, cachedSimpleName, items,
                    resultPosition, fluids, machineClass, power, powerCapacity,
                    labwareChanceMethod, labwareProbability,
                    new RuntimePercentage[0]);
        }

        HandlerSpec(String handlerClass, String operationId, String kind,
                    String cachedSimpleName, ItemRole[] items,
                    Position resultPosition, FluidRole[] fluids,
                    String machineClass, float power, float powerCapacity,
                    String labwareChanceMethod, double labwareProbability,
                    RuntimePercentage[] stateDependentOutcomes) {
            this.handlerClass = handlerClass;
            this.operationId = operationId;
            this.kind = kind;
            this.cachedClassName = handlerClass + '$' + cachedSimpleName;
            this.items = items;
            this.resultPosition = resultPosition;
            this.fluids = fluids;
            this.machineClass = machineClass;
            this.power = power;
            this.powerCapacity = powerCapacity;
            this.labwareChanceMethod = labwareChanceMethod;
            this.labwareProbability = labwareProbability;
            this.stateDependentOutcomes = stateDependentOutcomes.clone();
        }

        Class<?> cachedClass(ClassLoader loader) throws ClassNotFoundException {
            return Class.forName(cachedClassName, false, loader);
        }

        String stateDependentOutcomeSummary() {
            StringBuilder summary = new StringBuilder();
            for (RuntimePercentage outcome : stateDependentOutcomes) {
                if (summary.length() > 0) summary.append(',');
                summary.append(outcome.methodName).append('=')
                        .append(outcome.displayValue()).append('%');
            }
            return summary.toString();
        }
    }

    private static final class RuntimePercentage {
        final String methodName;
        final Class<?> returnType;
        final int intValue;
        final float floatValue;

        RuntimePercentage(String methodName, Class<?> returnType,
                          int intValue, float floatValue) {
            if (methodName == null || methodName.trim().isEmpty()
                    || (returnType != int.class && returnType != float.class)) {
                throw new IllegalArgumentException(
                        "runtime percentage requires an exact primitive accessor");
            }
            double value = returnType == int.class ? intValue : floatValue;
            if (!Double.isFinite(value) || value < 0.0d || value > 100.0d) {
                throw new IllegalArgumentException(
                        "runtime percentage must be finite and between 0 and 100");
            }
            this.methodName = methodName;
            this.returnType = returnType;
            this.intValue = intValue;
            this.floatValue = floatValue;
        }

        String rawCanonical() {
            return returnType == int.class
                    ? Integer.toString(intValue)
                    : Integer.toString(Float.floatToRawIntBits(floatValue));
        }

        String displayValue() {
            return returnType == int.class
                    ? Integer.toString(intValue) : Float.toString(floatValue);
        }
    }

    private static final class ItemRole {
        final String accessor;
        final Position position;
        final ItemDisposition disposition;

        ItemRole(String accessor, Position position,
                 ItemDisposition disposition) {
            this.accessor = accessor;
            this.position = position;
            this.disposition = disposition;
        }
    }

    private static final class FluidRole {
        final String accessor;
        final String registryName;
        final int amount;
        final int capacity;
        final boolean output;
        final boolean variableFromCachedOut;
        final float x;
        final float y;

        FluidRole(String accessor, String registryName, int amount, int capacity,
                  boolean output, boolean variableFromCachedOut, float x, float y) {
            this.accessor = accessor;
            this.registryName = registryName;
            this.amount = amount;
            this.capacity = capacity;
            this.output = output;
            this.variableFromCachedOut = variableFromCachedOut;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Position {
        final int x;
        final int y;

        Position(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private enum ItemDisposition {
        CONSUMED,
        CATALYST,
        STOCHASTIC_LABWARE
    }
}
