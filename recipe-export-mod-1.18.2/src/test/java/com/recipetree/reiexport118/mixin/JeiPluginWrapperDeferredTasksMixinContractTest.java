package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JeiPluginWrapperDeferredTasksMixinContractTest {
    private static final String TARGET =
            "me/shedaniel/rei/jeicompat/JEIPluginDetector$JEIPluginWrapper";
    private static final String POST_STAGE_DESCRIPTOR =
            "(Lme/shedaniel/rei/api/common/plugins/PluginManager;"
                    + "Lme/shedaniel/rei/api/common/registry/ReloadStage;)V";
    private static final String POST_STAGE_SELECTOR = "postStage" + POST_STAGE_DESCRIPTOR;
    private static final String REGISTER_CATEGORIES_SELECTOR =
            "registerCategories(Lme/shedaniel/rei/api/client/registry/category/"
                    + "CategoryRegistry;)V";
    private static final String REGISTER_DISPLAYS_SELECTOR =
            "registerDisplays(Lme/shedaniel/rei/api/client/registry/display/"
                    + "DisplayRegistry;)V";
    private static final String REGISTER_TRANSFER_HANDLERS_SELECTOR =
            "registerTransferHandlers(Lme/shedaniel/rei/api/client/registry/transfer/"
                    + "TransferHandlerRegistry;)V";
    private static final String MIXIN =
            "com/recipetree/reiexport118/mixin/JeiPluginWrapperDeferredTasksMixin";

    @Test
    void pinnedWrapperHasThreeAuditedPostProducerRoutesAndConsumesQueuesOnlyAtEnd()
            throws IOException {
        ClassNode wrapper = readPinned(Mm2DeterminismContract.JEI_PLUGIN_WRAPPER);
        assertEquals(TARGET, wrapper.name);

        MethodNode registerDisplays = method(
                wrapper,
                "registerDisplays",
                "(Lme/shedaniel/rei/api/client/registry/display/DisplayRegistry;)V");
        assertEquals(
                1,
                typeInstructionCount(
                        registerDisplays,
                        Opcodes.NEW,
                        "me/shedaniel/rei/jeicompat/wrap/JEIRecipeRegistration"));
        assertEquals(
                1,
                callCount(
                        registerDisplays,
                        "mezz/jei/api/IModPlugin",
                        "registerRecipes",
                        "(Lmezz/jei/api/registration/IRecipeRegistration;)V"));

        MethodNode registerCategories = method(
                wrapper,
                "registerCategories",
                "(Lme/shedaniel/rei/api/client/registry/category/CategoryRegistry;)V");
        assertEquals(
                1,
                callCount(
                        registerCategories,
                        "mezz/jei/api/IModPlugin",
                        "registerCategories",
                        "(Lmezz/jei/api/registration/IRecipeCategoryRegistration;)V"));
        MethodNode categoryPostProducer = method(
                wrapper,
                "lambda$registerCategories$4",
                "(Lme/shedaniel/rei/jeicompat/wrap/JEIWrappedCategory;)V");
        assertEquals(1, fieldCount(categoryPostProducer, TARGET, "post", Opcodes.GETFIELD));
        assertEquals(1, callCount(
                categoryPostProducer, "java/util/List", "add", "(Ljava/lang/Object;)Z"));

        MethodNode registerTransferHandlers = method(
                wrapper,
                "registerTransferHandlers",
                "(Lme/shedaniel/rei/api/client/registry/transfer/TransferHandlerRegistry;)V");
        assertEquals(1, fieldCount(registerDisplays, TARGET, "post", Opcodes.GETFIELD));
        assertEquals(1, fieldCount(
                registerTransferHandlers, TARGET, "post", Opcodes.GETFIELD));
        assertEquals(
                1,
                typeInstructionCount(
                        registerTransferHandlers,
                        Opcodes.NEW,
                        "me/shedaniel/rei/jeicompat/wrap/JEIRecipeTransferRegistration"));

        Set<String> postFieldMethods = wrapper.methods.stream()
                .filter(candidate -> fieldCount(candidate, TARGET, "post", -1) > 0)
                .map(candidate -> candidate.name)
                .collect(Collectors.toSet());
        assertEquals(
                Set.of(
                        "<init>", "registerDisplays",
                        "registerTransferHandlers", "postStage", "lambda$registerCategories$4"),
                postFieldMethods,
                "all pinned post-field access routes must remain explicitly accounted for");

        MethodNode postStage = method(wrapper, "postStage", POST_STAGE_DESCRIPTOR);
        assertEquals(
                1,
                fieldCount(
                        postStage,
                        Opcodes.GETSTATIC,
                        "me/shedaniel/rei/api/common/registry/ReloadStage",
                        "END"));
        assertEquals(2, callCount(postStage, "java/lang/Runnable", "run", "()V"));
        assertEquals(2, callCount(postStage, "java/lang/Throwable", "printStackTrace", "()V"));
        assertEquals(2, callCount(postStage, "java/util/List", "clear", "()V"));
        assertEquals(2, fieldCount(postStage, TARGET, "entryRegistry", Opcodes.GETFIELD));
        assertEquals(3, fieldCount(postStage, TARGET, "post", Opcodes.GETFIELD));
    }

    @Test
    void everyDeferredRegistrationAndDisplayStorageClassRemainsExactlyPinned()
            throws IOException {
        for (Mm2DeterminismContract.ClassPin pin : List.of(
                Mm2DeterminismContract.JEI_PLUGIN_WRAPPER,
                Mm2DeterminismContract.JEI_RECIPE_REGISTRATION,
                Mm2DeterminismContract.JEI_RECIPE_TRANSFER_REGISTRATION,
                Mm2DeterminismContract.REI_DISPLAYS_HOLDER)) {
            assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(pin), pin.className());
            assertEquals(pin.className().replace('.', '/'), readPinned(pin).name);
        }
    }

    @Test
    void compiledMixinCountsEveryProducerAndGuardsEndHeadAndBothStageReturns()
            throws IOException {
        ClassNode mixin = readResource(MIXIN + ".class");
        AnnotationNode mixinAnnotation = annotation(
                mixin.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(List.of(TARGET.replace('/', '.')), value(mixinAnnotation, "targets"));
        assertEquals(false, value(mixinAnnotation, "remap"));

        assertProducerInject(
                mixin,
                "reiexport$observeRegisterCategories",
                REGISTER_CATEGORIES_SELECTOR,
                "observeRegisterCategories");
        assertProducerInject(
                mixin,
                "reiexport$observeRegisterDisplays",
                REGISTER_DISPLAYS_SELECTOR,
                "observeRegisterDisplays");
        assertProducerInject(
                mixin,
                "reiexport$observeRegisterTransferHandlers",
                REGISTER_TRANSFER_HANDLERS_SELECTOR,
                "observeRegisterTransferHandlers");

        MethodNode begin = methodByName(mixin, "reiexport$beginAuthoritativeDeferredTasks");
        assertInject(begin, POST_STAGE_SELECTOR, "HEAD");
        assertEquals(
                1,
                fieldCount(
                        begin,
                        Opcodes.GETSTATIC,
                        "me/shedaniel/rei/api/common/registry/ReloadStage",
                        "END"));
        assertEquals(
                1,
                callCount(
                        begin,
                        "com/recipetree/reiexport118/compat/Mm2JeiDeferredTaskGate",
                        "beginAuthoritativeWrapper",
                        "(Ljava/lang/Object;Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V"));

        MethodNode finish = methodByName(mixin, "reiexport$finishDeferredTasks");
        assertInject(finish, POST_STAGE_SELECTOR, "RETURN");
        assertEquals(
                1,
                callCount(
                        finish,
                        "com/recipetree/reiexport118/compat/Mm2JeiDeferredTaskGate",
                        "finishWrapper",
                        "(Ljava/lang/Object;Ljava/lang/String;Ljava/util/List;Ljava/util/List;"
                                + "Lme/shedaniel/rei/api/common/registry/ReloadStage;)V"));

        assertFailureRedirect(
                methodByName(mixin, "reiexport$rejectSwallowedEntryRegistryFailure"), 0,
                "JEIPluginWrapper.postStage entryRegistry task");
        assertFailureRedirect(
                methodByName(mixin, "reiexport$rejectSwallowedPostFailure"), 1,
                "JEIPluginWrapper.postStage post task");
    }

    @Test
    void compiledStageRoutingNeverClearsStartAndRecordsOnlyEndPostTotals()
            throws IOException {
        ClassNode gate = readResource(
                "com/recipetree/reiexport118/compat/Mm2JeiDeferredTaskGate.class");
        MethodNode finish = method(
                gate,
                "finishWrapper",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/util/List;Ljava/util/List;"
                        + "Lme/shedaniel/rei/api/common/registry/ReloadStage;)V");
        assertEquals(
                1,
                callCount(
                        finish,
                        "com/recipetree/reiexport118/compat/Mm2JeiDeferredTaskSequence",
                        "recordPreliminaryWrapper",
                        "(Ljava/lang/Object;Ljava/lang/String;Ljava/util/List;Ljava/util/List;"
                                + "Ljava/lang/Thread;)V"));
        assertEquals(
                1,
                callCount(
                        finish,
                        "com/recipetree/reiexport118/compat/Mm2JeiDeferredTaskSequence",
                        "finishAuthoritative",
                        "(Ljava/lang/Object;Ljava/lang/String;Ljava/util/List;Ljava/util/List;"
                                + "Ljava/lang/Thread;)V"));
        assertEquals(0, callCount(finish, "java/util/List", "clear", "()V"));

        ClassNode sequence = readResource(
                "com/recipetree/reiexport118/compat/Mm2JeiDeferredTaskSequence.class");
        MethodNode startObservation = method(
                sequence,
                "recordPreliminaryWrapper",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/util/List;Ljava/util/List;"
                        + "Ljava/lang/Thread;)V");
        assertEquals(
                0,
                callCount(startObservation, "java/util/List", "clear", "()V"),
                "START is an assertion seam, never a mutation seam");

        MethodNode beginEnd = method(
                sequence,
                "beginAuthoritative",
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/util/List;Ljava/util/List;"
                        + "Ljava/lang/Thread;)V");
        assertEquals(
                2,
                callCount(beginEnd, "java/util/List", "size", "()I"),
                "one entryRegistry diagnostic read plus one authoritative post total");
        assertEquals(0, callCount(beginEnd, "java/util/List", "clear", "()V"));

    }

    @Test
    void mixinConfigurationIncludesTheExactWrapperGuardOnce() throws IOException {
        String config;
        try (InputStream input = JeiPluginWrapperDeferredTasksMixinContractTest.class
                .getClassLoader().getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input, "compiled mixin configuration");
            config = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(config, "\"JeiPluginWrapperDeferredTasksMixin\""));
    }

    private static void assertProducerInject(
            ClassNode mixin,
            String handlerName,
            String selector,
            String gateMethod
    ) {
        MethodNode handler = methodByName(mixin, handlerName);
        assertInject(handler, selector, "HEAD");
        assertEquals(
                1,
                callCount(
                        handler,
                        "com/recipetree/reiexport118/compat/Mm2ReiLifecycleGate",
                        "isOwnedReloadActiveForCompatibility",
                        "()Z"));
        assertEquals(
                1,
                callCount(
                        handler,
                        "com/recipetree/reiexport118/compat/Mm2JeiDeferredTaskGate",
                        gateMethod,
                        "(Ljava/lang/Object;Ljava/lang/String;)V"));
    }

    private static void assertInject(
            MethodNode handler,
            String selector,
            String position
    ) {
        AnnotationNode inject = annotation(
                annotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of(selector), value(inject, "method"));
        assertEquals(1, value(inject, "require"));
        assertEquals(false, value(inject, "remap"));
        List<?> points = (List<?>) value(inject, "at");
        assertEquals(1, points.size());
        assertEquals(position, value((AnnotationNode) points.get(0), "value"));
    }

    private static void assertFailureRedirect(
            MethodNode handler,
            int ordinal,
            String seam
    ) {
        AnnotationNode redirect = annotation(
                annotations(handler),
                "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        assertEquals(List.of(POST_STAGE_SELECTOR), value(redirect, "method"));
        assertEquals(1, value(redirect, "require"));
        assertEquals(false, value(redirect, "remap"));
        AnnotationNode point = (AnnotationNode) value(redirect, "at");
        assertEquals("INVOKE", value(point, "value"));
        assertEquals("Ljava/lang/Throwable;printStackTrace()V", value(point, "target"));
        assertEquals(ordinal, value(point, "ordinal"));
        assertEquals(1, callCount(handler, "java/lang/Throwable", "printStackTrace", "()V"));
        assertEquals(
                1,
                callCount(
                        handler,
                        "com/recipetree/reiexport118/compat/Mm2ReiLifecycleGate",
                        "rejectSwallowedPluginFailure",
                        "(Ljava/lang/String;Ljava/lang/Throwable;)V"));
        assertTrue(stringConstants(handler).contains(seam));
    }

    private static ClassNode readPinned(Mm2DeterminismContract.ClassPin pin)
            throws IOException {
        List<byte[]> exact = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        for (URL url : Collections.list(
                JeiPluginWrapperDeferredTasksMixinContractTest.class
                        .getClassLoader().getResources(pin.resource()))) {
            try (InputStream input = url.openStream()) {
                byte[] bytecode = input.readAllBytes();
                String hash = sha256(bytecode);
                observed.add(url + " sha256=" + hash);
                if (pin.sha256().equals(hash)) {
                    exact.add(bytecode);
                }
            }
        }
        assertTrue(!observed.isEmpty(), "missing pinned resource " + pin.resource());
        assertEquals(
                observed.size(), exact.size(),
                "every visible copy must match the exact pin; observed=" + observed);
        return read(exact.get(0));
    }

    private static ClassNode readResource(String resource) throws IOException {
        try (InputStream input = JeiPluginWrapperDeferredTasksMixinContractTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return read(input.readAllBytes());
        }
    }

    private static ClassNode read(byte[] bytecode) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(
                node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        List<MethodNode> matches = owner.methods.stream()
                .filter(candidate -> name.equals(candidate.name))
                .filter(candidate -> descriptor.equals(candidate.desc))
                .toList();
        assertEquals(1, matches.size(), name + descriptor);
        return matches.get(0);
    }

    private static MethodNode methodByName(ClassNode owner, String name) {
        List<MethodNode> matches = owner.methods.stream()
                .filter(candidate -> name.equals(candidate.name))
                .toList();
        assertEquals(1, matches.size(), name);
        return matches.get(0);
    }

    private static int callCount(
            MethodNode method,
            String owner,
            String name,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                count++;
            }
        }
        return count;
    }

    private static int typeInstructionCount(
            MethodNode method,
            int opcode,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type
                    && type.getOpcode() == opcode
                    && descriptor.equals(type.desc)) {
                count++;
            }
        }
        return count;
    }

    private static int fieldCount(
            MethodNode method,
            String owner,
            String name,
            int opcode
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && (opcode < 0 || field.getOpcode() == opcode)
                    && owner.equals(field.owner)
                    && name.equals(field.name)) {
                count++;
            }
        }
        return count;
    }

    private static int fieldCount(
            MethodNode method,
            int opcode,
            String owner,
            String name
    ) {
        return fieldCount(method, owner, name, opcode);
    }

    private static AnnotationNode annotation(
            List<AnnotationNode> annotations,
            String descriptor
    ) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (descriptor.equals(annotation.desc)) {
                    return annotation;
                }
            }
        }
        throw new AssertionError("missing annotation " + descriptor);
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            annotations.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            annotations.addAll(method.invisibleAnnotations);
        }
        return annotations;
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values != null) {
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if (name.equals(annotation.values.get(index))) {
                    return annotation.values.get(index + 1);
                }
            }
        }
        throw new AssertionError("missing annotation value " + name + " on " + annotation.desc);
    }

    private static List<String> stringConstants(MethodNode method) {
        List<String> constants = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.LdcInsnNode constant
                    && constant.cst instanceof String value) {
                constants.add(value);
            }
        }
        return constants;
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static String sha256(byte[] bytecode) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytecode));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
