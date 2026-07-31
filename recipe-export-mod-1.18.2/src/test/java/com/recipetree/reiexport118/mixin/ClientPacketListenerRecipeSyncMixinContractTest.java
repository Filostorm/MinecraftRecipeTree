package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientPacketListenerRecipeSyncMixinContractTest {
    private static final Path PRODUCTION_CLIENT = Path.of(
            "..",
            "minecraft-1.18.2-runtime",
            "libraries",
            "net",
            "minecraft",
            "client",
            "1.18.2-20220404.173914",
            "client-1.18.2-20220404.173914-srg.jar");
    private static final String TARGET_CLASS =
            "net/minecraft/client/multiplayer/ClientPacketListener";
    private static final String TARGET_CLASS_SHA256 =
            "249204f4d6e40bf2cfe77abc489d525fb3fe61b5fdb11945927e763c942f0859";
    private static final String TARGET_DESCRIPTOR =
            "(Lnet/minecraft/network/protocol/game/ClientboundUpdateRecipesPacket;)V";
    private static final String PRODUCTION_TARGET = "m_6327_" + TARGET_DESCRIPTOR;
    private static final String HANDLER_NAME =
            "reiexport$reloadReiAfterAuthoritativeRecipeSync";
    private static final String HANDLER_DESCRIPTOR =
            "(Lnet/minecraft/network/protocol/game/ClientboundUpdateRecipesPacket;"
                    + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";

    @Test
    void exactProductionSrgTargetInstallsRecipesBeforeItsSingleReturn() throws IOException {
        assertFalse(Files.isSymbolicLink(PRODUCTION_CLIENT), PRODUCTION_CLIENT.toString());
        assertTrue(
                Files.isRegularFile(PRODUCTION_CLIENT, LinkOption.NOFOLLOW_LINKS),
                PRODUCTION_CLIENT.toString());

        byte[] bytecode;
        try (JarFile archive = new JarFile(PRODUCTION_CLIENT.toFile())) {
            JarEntry entry = archive.getJarEntry(TARGET_CLASS + ".class");
            assertNotNull(entry, TARGET_CLASS);
            try (InputStream input = archive.getInputStream(entry)) {
                bytecode = input.readAllBytes();
            }
        }
        assertEquals(TARGET_CLASS_SHA256, sha256(bytecode));

        ClassNode target = read(bytecode);
        assertEquals(TARGET_CLASS, target.name);
        List<MethodNode> candidates = target.methods.stream()
                .filter(method -> "m_6327_".equals(method.name))
                .filter(method -> TARGET_DESCRIPTOR.equals(method.desc))
                .toList();
        assertEquals(1, candidates.size(), "exact production recipe-sync target cardinality");
        MethodNode method = candidates.get(0);
        assertEquals(
                method.name + method.desc,
                PRODUCTION_TARGET);
        assertTrue((method.access & Opcodes.ACC_PUBLIC) != 0);
        assertFalse((method.access & Opcodes.ACC_STATIC) != 0);

        int installs = 0;
        int returns = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                returns++;
            }
            if (instruction instanceof MethodInsnNode invocation
                    && "net/minecraft/world/item/crafting/RecipeManager".equals(invocation.owner)
                    && "m_44024_".equals(invocation.name)
                    && "(Ljava/lang/Iterable;)V".equals(invocation.desc)) {
                installs++;
            }
        }
        assertEquals(1, installs, "authoritative RecipeManager replacement call");
        assertEquals(1, returns, "production hook must have one unambiguous RETURN seam");
    }

    @Test
    void compiledHandlerUsesTheExactProductionSelectorAndCallbackDescriptor() throws IOException {
        ClassNode mixin;
        try (InputStream input = ClientPacketListenerRecipeSyncMixinContractTest.class
                .getClassLoader()
                .getResourceAsStream(
                        "com/recipetree/reiexport118/mixin/ClientPacketListenerRecipeSyncMixin.class")) {
            assertNotNull(input, "compiled recipe-sync mixin");
            mixin = read(input.readAllBytes());
        }
        assertEquals(List.of(), mixin.fields,
                "mixin must not declare fields that Mixin would merge into Minecraft");

        MethodNode handler = mixin.methods.stream()
                .filter(method -> HANDLER_NAME.equals(method.name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing compiled recipe-sync handler"));
        assertEquals(HANDLER_DESCRIPTOR, handler.desc);
        assertTrue((handler.access & Opcodes.ACC_PRIVATE) != 0);
        assertFalse((handler.access & Opcodes.ACC_STATIC) != 0);

        int gateCalls = 0;
        for (AbstractInsnNode instruction : handler.instructions) {
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.getOpcode() == Opcodes.INVOKESTATIC
                    && "com/recipetree/reiexport118/compat/Mm2ReiLifecycleGate"
                            .equals(invocation.owner)
                    && "reloadAfterRecipeSync".equals(invocation.name)
                    && "(Lnet/minecraft/client/multiplayer/ClientPacketListener;)V"
                            .equals(invocation.desc)) {
                gateCalls++;
            }
        }
        assertEquals(1, gateCalls);

        AnnotationNode inject = annotation(
                handler,
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        List<?> selectors = assertInstanceOf(List.class, value(inject, "method"));
        assertEquals(List.of(PRODUCTION_TARGET), selectors);
        assertEquals(1, value(inject, "require"));
        assertEquals(false, value(inject, "remap"));

        List<?> injectionPoints = assertInstanceOf(List.class, value(inject, "at"));
        assertEquals(1, injectionPoints.size());
        AnnotationNode at = assertInstanceOf(AnnotationNode.class, injectionPoints.get(0));
        assertEquals("RETURN", value(at, "value"));

        AnnotationNode target = annotation(mixin,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(false, value(target, "remap"));
        List<?> targetTypes = assertInstanceOf(List.class, value(target, "value"));
        assertEquals(
                List.of(Type.getObjectType(TARGET_CLASS)),
                targetTypes);
    }

    @Test
    void pinnedReiForgeMixinDispatchesPreUpdateRecipesExactlyOnceAtHead() throws IOException {
        Mm2DeterminismContract.ClassPin pin =
                Mm2DeterminismContract.REI_FORGE_CLIENT_PACKET_MIXIN;
        List<PinnedResource> resources = new ArrayList<>();
        for (URL resource : Collections.list(
                ClientPacketListenerRecipeSyncMixinContractTest.class
                        .getClassLoader()
                        .getResources(pin.resource()))) {
            try (InputStream input = resource.openStream()) {
                byte[] bytes = input.readAllBytes();
                resources.add(new PinnedResource(resource, bytes, sha256(bytes)));
            }
        }
        List<PinnedResource> exactResources = resources.stream()
                .filter(resource -> pin.sha256().equals(resource.sha256()))
                .toList();
        assertEquals(
                1,
                exactResources.size(),
                "expected one exact pinned REI class; observed " + resources.stream()
                        .map(resource -> resource.url() + " sha256=" + resource.sha256())
                        .toList());
        byte[] bytecode = exactResources.get(0).bytecode();
        assertTrue(Mm2DeterminismContract.CLASS_PINS.contains(pin));

        ClassNode mixin = read(bytecode);
        assertEquals(pin.className().replace('.', '/'), mixin.name);
        List<MethodNode> candidates = mixin.methods.stream()
                .filter(method -> "handleUpdateRecipes".equals(method.name))
                .toList();
        assertEquals(1, candidates.size(), "pinned REI recipe-sync handler cardinality");
        MethodNode handler = candidates.get(0);

        AnnotationNode inject = annotation(
                handler,
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        List<?> selectors = assertInstanceOf(List.class, value(inject, "method"));
        assertEquals(List.of("handleUpdateRecipes"), selectors);
        List<?> injectionPoints = assertInstanceOf(List.class, value(inject, "at"));
        assertEquals(1, injectionPoints.size());
        AnnotationNode at = assertInstanceOf(AnnotationNode.class, injectionPoints.get(0));
        assertEquals("HEAD", value(at, "value"));

        int preUpdateRecipeEventReads = 0;
        int eventInvokerCalls = 0;
        int recipeUpdateCalls = 0;
        for (AbstractInsnNode instruction : handler.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETSTATIC
                    && "me/shedaniel/rei/RoughlyEnoughItemsCoreClient".equals(field.owner)
                    && "PRE_UPDATE_RECIPES".equals(field.name)
                    && "Ldev/architectury/event/Event;".equals(field.desc)) {
                preUpdateRecipeEventReads++;
            }
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.getOpcode() == Opcodes.INVOKEINTERFACE
                    && "dev/architectury/event/Event".equals(invocation.owner)
                    && "invoker".equals(invocation.name)
                    && "()Ljava/lang/Object;".equals(invocation.desc)) {
                eventInvokerCalls++;
            }
            if (instruction instanceof MethodInsnNode invocation
                    && invocation.getOpcode() == Opcodes.INVOKEINTERFACE
                    && "dev/architectury/event/events/client/ClientRecipeUpdateEvent"
                            .equals(invocation.owner)
                    && "update".equals(invocation.name)
                    && "(Lnet/minecraft/world/item/crafting/RecipeManager;)V"
                            .equals(invocation.desc)) {
                recipeUpdateCalls++;
            }
        }
        assertEquals(1, preUpdateRecipeEventReads, "PRE_UPDATE_RECIPES field reads");
        assertEquals(1, eventInvokerCalls, "PRE_UPDATE_RECIPES invoker calls");
        assertEquals(1, recipeUpdateCalls, "PRE_UPDATE_RECIPES update calls");
    }

    private record PinnedResource(URL url, byte[] bytecode, String sha256) {
    }

    private static ClassNode read(byte[] bytecode) {
        ClassNode node = new ClassNode();
        new ClassReader(bytecode).accept(node, 0);
        return node;
    }

    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            annotations.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            annotations.addAll(method.invisibleAnnotations);
        }
        return annotations.stream()
                .filter(annotation -> descriptor.equals(annotation.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "missing annotation " + descriptor + " on " + method.name + method.desc));
    }

    private static AnnotationNode annotation(ClassNode type, String descriptor) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (type.visibleAnnotations != null) {
            annotations.addAll(type.visibleAnnotations);
        }
        if (type.invisibleAnnotations != null) {
            annotations.addAll(type.invisibleAnnotations);
        }
        return annotations.stream()
                .filter(annotation -> descriptor.equals(annotation.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "missing annotation " + descriptor + " on " + type.name));
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

    private static String sha256(byte[] bytecode) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytecode));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
