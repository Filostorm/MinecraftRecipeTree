package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2BlockAtlasCanonicalizationContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class TextureAtlasCanonicalizationMixinContractTest {
    private static final String MIXIN_PACKAGE = "com/recipetree/reiexport118/mixin/";
    private static final String CANONICALIZER =
            "com/recipetree/reiexport118/compat/Mm2BlockAtlasCanonicalization";

    @TempDir
    Path temporaryDirectory;

    @Test
    void compiledAnimationMixinCancelsOnlyThroughTheScopedIdentityGate() throws IOException {
        ClassNode mixin = readResource(MIXIN_PACKAGE + "TextureAtlasAnimationMixin.class");
        assertMinecraftMixinTarget(mixin);
        MethodNode handler = method(mixin, "reiexport$holdCanonicalFrames");
        assertFalse((handler.access & Opcodes.ACC_STATIC) != 0);

        AnnotationNode inject = annotation(handler,
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of(Mm2BlockAtlasCanonicalizationContract.TEXTURE_ATLAS_CYCLE_METHOD),
                value(inject, "method"));
        assertEquals(true, value(inject, "cancellable"));
        assertEquals(1, value(inject, "require"));
        assertEquals(false, value(inject, "remap"));
        List<?> injectionPoints = assertInstanceOf(List.class, value(inject, "at"));
        assertEquals(1, injectionPoints.size());
        AnnotationNode at = assertInstanceOf(AnnotationNode.class, injectionPoints.get(0));
        assertEquals("HEAD", value(at, "value"));

        assertEquals(1, callCount(handler, Opcodes.INVOKESTATIC, CANONICALIZER,
                "suppressCycleIfScoped",
                "(Lnet/minecraft/client/renderer/texture/TextureAtlas;)Z"));
        assertEquals(1, callCount(handler, Opcodes.INVOKEVIRTUAL,
                "org/spongepowered/asm/mixin/injection/callback/CallbackInfo",
                "cancel", "()V"));
    }

    @Test
    void accessorPinsTheProductionSrgSpriteMapAndExactMinecraftTarget() throws Exception {
        Method getter = TextureAtlasSpritesAccessor.class
                .getDeclaredMethod("reiexport$getTexturesByName");
        org.spongepowered.asm.mixin.gen.Accessor accessor = getter.getAnnotation(
                org.spongepowered.asm.mixin.gen.Accessor.class);
        assertNotNull(accessor);
        assertEquals(Mm2BlockAtlasCanonicalizationContract.TEXTURE_ATLAS_SPRITES_FIELD,
                accessor.value());
        assertFalse(accessor.remap());

        ClassNode mixin = readResource(MIXIN_PACKAGE + "TextureAtlasSpritesAccessor.class");
        assertMinecraftMixinTarget(mixin);
    }

    @Test
    void mixinsAreRequestScopedAndAppearExactlyOnceInTheRequiredConfig() throws Exception {
        Path absent = Files.createDirectory(temporaryDirectory.resolve("absent"));
        ReiExportMixinConfigPlugin disabled = new ReiExportMixinConfigPlugin(absent);
        assertFalse(disabled.shouldApplyMixin(
                Mm2BlockAtlasCanonicalizationContract.TEXTURE_ATLAS.className(),
                "com.recipetree.reiexport118.mixin.TextureAtlasAnimationMixin"));
        assertFalse(disabled.shouldApplyMixin(
                Mm2BlockAtlasCanonicalizationContract.TEXTURE_ATLAS.className(),
                "com.recipetree.reiexport118.mixin.TextureAtlasSpritesAccessor"));

        String config;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("reiexport.mixins.json")) {
            assertNotNull(input);
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(1, occurrences(config, "\"TextureAtlasAnimationMixin\""));
        assertEquals(1, occurrences(config, "\"TextureAtlasSpritesAccessor\""));
    }

    @Test
    void exportJobOwnsOneScopeAcrossAllRenderingAndClosesBothTerminalPaths()
            throws IOException {
        ClassNode job = readResource("com/recipetree/reiexport118/ExportJob.class");
        MethodNode tick = method(job, "tick");
        MethodNode finish = method(job, "finish");
        MethodNode fail = method(job, "fail");
        MethodNode begin = method(job, "beginBlockAtlasScope");
        MethodNode close = method(job, "closeBlockAtlasScope");

        assertEquals(1, callCount(tick, Opcodes.INVOKEVIRTUAL, job.name,
                "beginBlockAtlasScope", "()V"));
        assertEquals(1, callCount(finish, Opcodes.INVOKEVIRTUAL, job.name,
                "closeBlockAtlasScope", "()V"));
        assertEquals(1, callCount(fail, Opcodes.INVOKEVIRTUAL, job.name,
                "closeBlockAtlasScope", "()V"));
        assertEquals(1, callCount(begin, Opcodes.INVOKESTATIC, CANONICALIZER,
                "beginIfApplicable",
                "()Lcom/recipetree/reiexport118/compat/"
                        + "Mm2BlockAtlasCanonicalization$Scope;"));
        assertEquals(1, callCount(close, Opcodes.INVOKEVIRTUAL,
                "com/recipetree/reiexport118/compat/"
                        + "Mm2BlockAtlasCanonicalization$Scope",
                "close", "()V"));
    }

    private static void assertMinecraftMixinTarget(ClassNode mixin) {
        AnnotationNode annotation = annotation(mixin,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(false, value(annotation, "remap"));
        assertEquals(
                List.of(Type.getObjectType(
                        Mm2BlockAtlasCanonicalizationContract.TEXTURE_ATLAS
                                .className().replace('.', '/'))),
                value(annotation, "value"));
    }

    private static ClassNode readResource(String path) throws IOException {
        try (InputStream input = TextureAtlasCanonicalizationMixinContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            ClassNode node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        List<MethodNode> methods = owner.methods.stream()
                .filter(method -> name.equals(method.name))
                .toList();
        assertEquals(1, methods.size(), owner.name + "." + name);
        return methods.get(0);
    }

    private static int callCount(
            MethodNode method,
            int opcode,
            String owner,
            String name,
            String descriptor
    ) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == opcode
                    && owner.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                count++;
            }
        }
        return count;
    }

    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            annotations.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            annotations.addAll(method.invisibleAnnotations);
        }
        return annotation(annotations, descriptor);
    }

    private static AnnotationNode annotation(ClassNode type, String descriptor) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (type.visibleAnnotations != null) {
            annotations.addAll(type.visibleAnnotations);
        }
        if (type.invisibleAnnotations != null) {
            annotations.addAll(type.invisibleAnnotations);
        }
        return annotation(annotations, descriptor);
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations.stream()
                .filter(annotation -> descriptor.equals(annotation.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing annotation " + descriptor));
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values == null) {
            return null;
        }
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (name.equals(annotation.values.get(index))) {
                return annotation.values.get(index + 1);
            }
        }
        return null;
    }

    private static int occurrences(String source, String target) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }
}
