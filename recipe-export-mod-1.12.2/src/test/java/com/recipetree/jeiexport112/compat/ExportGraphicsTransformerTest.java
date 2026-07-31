package com.recipetree.jeiexport112.compat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.Assume;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class ExportGraphicsTransformerTest {
    private static final String TARGET_DOTTED =
            "com.cleanroommc.multiblocked.client.shader.management.Shader";
    private static final String TARGET_INTERNAL =
            "com/cleanroommc/multiblocked/client/shader/management/Shader";
    private static final String TARGET_ENTRY = TARGET_INTERNAL + ".class";
    private static final String TARGET_TYPE = TARGET_INTERNAL + "$ShaderType";
    private static final String CONSTRUCTOR_DESC =
            "(L" + TARGET_TYPE + ";Ljava/lang/String;)V";
    private static final String COMPILE_DESC = "()L" + TARGET_INTERNAL + ";";
    private static final String GL20 = "org/lwjgl/opengl/GL20";
    private static final String OPEN_GL_HELPER =
            "net/minecraft/client/renderer/OpenGlHelper";
    private static final String BRIDGE =
            "com/recipetree/jeiexport112/compat/MultiblockedShaderBridge";
    private static final String CLIENT_PROXY_DOTTED =
            "com.cleanroommc.multiblocked.client.ClientProxy";
    private static final String CLIENT_PROXY_INTERNAL =
            "com/cleanroommc/multiblocked/client/ClientProxy";
    private static final String CLIENT_PROXY_ENTRY = CLIENT_PROXY_INTERNAL + ".class";
    private static final String COMMON_PROXY_INTERNAL =
            "com/cleanroommc/multiblocked/CommonProxy";
    private static final String SHADERS_INTERNAL =
            "com/cleanroommc/multiblocked/client/shader/Shaders";
    private static final String FIXTURE_ENV = "JEIEXPORT_MULTIBLOCKED_SHADER_FIXTURE";
    private static final String RANDOMPATCHES_WINDOW_DOTTED =
            "com.therandomlabs.randompatches.config.RPConfig$Window";
    private static final String RANDOMPATCHES_WINDOW_INTERNAL =
            "com/therandomlabs/randompatches/config/RPConfig$Window";
    private static final String RANDOMPATCHES_WINDOW_ENTRY =
            RANDOMPATCHES_WINDOW_INTERNAL + ".class";
    private static final String DISPLAY = "org/lwjgl/opengl/Display";
    private static final String GL_STATE_MANAGER =
            "net/minecraft/client/renderer/GlStateManager";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String GL_ALLOCATION_DOTTED =
            "net.minecraft.client.renderer.GLAllocation";
    private static final String GL_ALLOCATION_INTERNAL =
            "net/minecraft/client/renderer/GLAllocation";
    private static final String DISPLAY_LIST_GUARD =
            "com/recipetree/jeiexport112/compat/DisplayListGuard";
    private static final String MINECRAFT_DOTTED = "net.minecraft.client.Minecraft";
    private static final String MINECRAFT_INTERNAL = "net/minecraft/client/Minecraft";
    private static final String SPLASH_PROGRESS =
            "net/minecraftforge/fml/client/SplashProgress";
    private static final String TEXTURE_SIZE_GUARD =
            "com/recipetree/jeiexport112/compat/TextureSizeGuard";
    private static final String TEXTURE_SIZE_VALIDATOR =
            "validateForgeCachedMaximumTextureSize";
    private static final String FML_CLIENT_HANDLER =
            "net/minecraftforge/fml/client/FMLClientHandler";
    private static final String RENDERER_BOOTSTRAP_GUARD =
            "ensureDisplayCurrentForRendererBootstrap";
    private static final String WINDOW_ICON_HANDLER =
            "com/therandomlabs/randompatches/client/WindowIconHandler";
    private static final String RANDOMPATCHES_FIXTURE_ENV =
            "JEIEXPORT_RANDOMPATCHES_WINDOW_FIXTURE";

    @Test
    public void patchesTheExactSixShaderObjectCalls() {
        byte[] transformed = transform(exactSyntheticClass());

        assertEquals(0, countCalls(transformed, null, GL20, null, null));
        assertEquals(1, countCalls(transformed, "<init>", BRIDGE, "createShader", "(I)I"));
        assertEquals(
                1,
                countCalls(
                        transformed,
                        "compileShader",
                        BRIDGE,
                        "shaderSource",
                        "(ILjava/lang/CharSequence;)V"
                )
        );
        assertEquals(
                1,
                countCalls(transformed, "compileShader", BRIDGE, "compileShader", "(I)V")
        );
        assertEquals(
                2,
                countCalls(transformed, "compileShader", BRIDGE, "getShaderi", "(II)I")
        );
        assertEquals(
                1,
                countCalls(
                        transformed,
                        "compileShader",
                        BRIDGE,
                        "getShaderInfoLog",
                        "(II)Ljava/lang/String;"
                )
        );
    }

    @Test
    public void patchesConfiguredCanonicalMultiblockedFixture() throws IOException {
        String fixturePath = System.getenv(FIXTURE_ENV);
        Assume.assumeTrue(
                "Set " + FIXTURE_ENV + " to exercise the exact multiblocked-0.8.0 artifact",
                fixturePath != null && !fixturePath.trim().isEmpty()
        );

        File fixture = new File(fixturePath);
        try (JarFile jar = new JarFile(fixture)) {
            JarEntry entry = jar.getJarEntry(TARGET_ENTRY);
            assertNotNull("Missing " + TARGET_ENTRY + " in " + fixture, entry);
            byte[] transformed;
            try (InputStream input = jar.getInputStream(entry)) {
                transformed = transform(readAll(input));
            }
            assertEquals(0, countCalls(transformed, null, GL20, null, null));
            assertEquals(6, countCalls(transformed, null, BRIDGE, null, null));
        }
    }

    @Test
    public void skipsOnlyTheExactEagerMultiblockedShaderBootstrap() {
        byte[] transformed = transformClientProxy(exactSyntheticClientProxy());

        assertEquals(
                0,
                countCalls(transformed, "preInit", SHADERS_INTERNAL, "init", "()V")
        );
        assertEquals(
                1,
                countCalls(
                        transformed,
                        "preInit",
                        COMMON_PROXY_INTERNAL,
                        "preInit",
                        "()V"
                )
        );
        assertEquals(
                1,
                countCalls(
                        transformed,
                        "preInit",
                        "net/minecraft/client/resources/data/MetadataSerializer",
                        "func_110504_a",
                        "(Lnet/minecraft/client/resources/data/IMetadataSectionSerializer;" +
                                "Ljava/lang/Class;)V"
                )
        );
    }

    @Test
    public void skipsEagerBootstrapInConfiguredCanonicalMultiblockedFixture()
            throws IOException {
        String fixturePath = System.getenv(FIXTURE_ENV);
        Assume.assumeTrue(
                "Set " + FIXTURE_ENV + " to exercise the exact multiblocked-0.8.0 artifact",
                fixturePath != null && !fixturePath.trim().isEmpty()
        );

        File fixture = new File(fixturePath);
        try (JarFile jar = new JarFile(fixture)) {
            JarEntry entry = jar.getJarEntry(CLIENT_PROXY_ENTRY);
            assertNotNull("Missing " + CLIENT_PROXY_ENTRY + " in " + fixture, entry);
            byte[] transformed;
            try (InputStream input = jar.getInputStream(entry)) {
                transformed = transformClientProxy(readAll(input));
            }
            assertEquals(
                    0,
                    countCalls(transformed, "preInit", SHADERS_INTERNAL, "init", "()V")
            );
            assertEquals(
                    1,
                    countCalls(
                            transformed,
                            "preInit",
                            COMMON_PROXY_INTERNAL,
                            "preInit",
                            "()V"
                    )
            );
            assertEquals(
                    1,
                    countCalls(
                            transformed,
                            "preInit",
                            "net/minecraft/client/resources/data/MetadataSerializer",
                            "func_110504_a",
                            "(Lnet/minecraft/client/resources/data/IMetadataSectionSerializer;" +
                                    "Ljava/lang/Class;)V"
                    )
            );
        }
    }

    @Test
    public void disablesTheExactRandomPatchesCosmeticWindowReload() {
        byte[] transformed = transformRandomPatches(exactSyntheticRandomPatchesWindow());

        assertExplicitReturn(transformed, "onReloadClient", "()V");
        assertEquals(
                0,
                countCalls(transformed, "onReloadClient", DISPLAY, null, null)
        );
        assertEquals(
                0,
                countCalls(
                        transformed,
                        "onReloadClient",
                        WINDOW_ICON_HANDLER,
                        null,
                        null
                )
        );
    }

    @Test
    public void patchesConfiguredCanonicalRandomPatchesFixture() throws IOException {
        String fixturePath = System.getenv(RANDOMPATCHES_FIXTURE_ENV);
        Assume.assumeTrue(
                "Set " + RANDOMPATCHES_FIXTURE_ENV +
                        " to exercise the exact RandomPatches 1.22.1.10 artifact",
                fixturePath != null && !fixturePath.trim().isEmpty()
        );

        File fixture = new File(fixturePath);
        try (JarFile jar = new JarFile(fixture)) {
            JarEntry entry = jar.getJarEntry(RANDOMPATCHES_WINDOW_ENTRY);
            assertNotNull(
                    "Missing " + RANDOMPATCHES_WINDOW_ENTRY + " in " + fixture,
                    entry
            );
            byte[] transformed;
            try (InputStream input = jar.getInputStream(entry)) {
                transformed = transformRandomPatches(readAll(input));
            }
            assertExplicitReturn(transformed, "onReloadClient", "()V");
        }
    }

    @Test
    public void bridgeDelegatesOnlyThroughOpenGlHelper() throws IOException {
        byte[] bridge = readClasspathResource(
                "/com/recipetree/jeiexport112/compat/MultiblockedShaderBridge.class"
        );

        assertEquals(0, countCalls(bridge, null, GL20, null, null));
        assertEquals(1, countCalls(bridge, "createShader", OPEN_GL_HELPER,
                "glCreateShader", "(I)I"));
        assertEquals(1, countCalls(bridge, "shaderSource", OPEN_GL_HELPER,
                "glShaderSource", "(ILjava/nio/ByteBuffer;)V"));
        assertEquals(1, countCalls(bridge, "compileShader", OPEN_GL_HELPER,
                "glCompileShader", "(I)V"));
        assertEquals(1, countCalls(bridge, "getShaderi", OPEN_GL_HELPER,
                "glGetShaderi", "(II)I"));
        assertEquals(1, countCalls(bridge, "getShaderInfoLog", OPEN_GL_HELPER,
                "glGetShaderInfoLog", "(II)Ljava/lang/String;"));
        assertEquals(1, countCalls(bridge, "shaderSource", "org/lwjgl/BufferUtils",
                "createByteBuffer", "(I)Ljava/nio/ByteBuffer;"));
    }

    @Test
    public void replacesCanonicalDisplayListAllocatorAfterExactValidation()
            throws IOException {
        byte[] source = readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class");
        LoggedTransform result = transformGlAllocationWithLog(source);
        byte[] transformed = result.bytes;

        assertExactDisplayListGuardBody(transformed, "generateDisplayLists");
        assertTrue(
                "Expected the successful transform to log the audited development mapping",
                result.log.contains("display-list-call-mapping=audited-development")
        );
        assertEquals(
                0,
                countCalls(
                        transformed,
                        "generateDisplayLists",
                        GL_STATE_MANAGER,
                        "glGenLists",
                        "(I)I"
                )
        );
    }

    @Test
    public void acceptsOnlyTheCoherentForgeRuntimeSrgCallMappingAndLogsMode()
            throws IOException {
        ClassNode classNode = readClass(
                readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class")
        );
        MethodNode target = findMethod(classNode, "generateDisplayLists", "(I)I");
        findCall(target, "glGenLists").name = "func_187442_t";
        findCall(target, "glGetError").name = "func_187434_L";

        LoggedTransform result = transformGlAllocationWithLog(writeClass(classNode));

        assertExactDisplayListGuardBody(result.bytes, "generateDisplayLists");
        assertTrue(
                "Expected the successful transform to log the Forge runtime SRG mapping",
                result.log.contains("display-list-call-mapping=forge-runtime-srg")
        );
    }

    @Test
    public void acceptsExactSrgDisplayListAllocatorName() throws IOException {
        ClassNode classNode = readClass(
                readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class")
        );
        findMethod(classNode, "generateDisplayLists", "(I)I").name = "func_74526_a";

        byte[] transformed = transformGlAllocation(writeClass(classNode));
        assertExactDisplayListGuardBody(transformed, "func_74526_a");
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDisplayListAllocatorClassBytesForDifferentOwner()
            throws IOException {
        ClassNode classNode = readClass(
                readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class")
        );
        classNode.name = "example/ImpostorGLAllocation";
        transformGlAllocation(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDuplicateDisplayListAllocatorNames() throws IOException {
        byte[] source = readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class");
        ClassNode classNode = readClass(source);
        ClassNode duplicateSource = readClass(source);
        MethodNode duplicate = findMethod(
                duplicateSource,
                "generateDisplayLists",
                "(I)I"
        );
        duplicate.name = "func_74526_a";
        classNode.methods.add(duplicate);
        transformGlAllocation(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDisplayListAllocatorWithoutExactAccess() throws IOException {
        ClassNode classNode = readClass(
                readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class")
        );
        MethodNode target = findMethod(classNode, "generateDisplayLists", "(I)I");
        target.access &= ~Opcodes.ACC_SYNCHRONIZED;
        transformGlAllocation(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDisplayListAllocatorClassVersionDrift() throws IOException {
        ClassNode classNode = readClass(
                readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class")
        );
        classNode.version = Opcodes.V1_7;
        transformGlAllocation(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnauditedDisplayListAllocationCallAlias() throws IOException {
        ClassNode classNode = readClass(
                readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class")
        );
        MethodNode target = findMethod(classNode, "generateDisplayLists", "(I)I");
        findCall(target, "glGenLists").name = "func_187443_u";
        transformGlAllocation(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMixedDevelopmentAndForgeRuntimeSrgCallNames()
            throws IOException {
        ClassNode classNode = readClass(
                readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class")
        );
        MethodNode target = findMethod(classNode, "generateDisplayLists", "(I)I");
        findCall(target, "glGenLists").name = "func_187442_t";
        transformGlAllocation(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsForgeRuntimeSrgCallWithChangedDescriptor() throws IOException {
        ClassNode classNode = readClass(
                readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class")
        );
        MethodNode target = findMethod(classNode, "generateDisplayLists", "(I)I");
        MethodInsnNode allocation = findCall(target, "glGenLists");
        allocation.name = "func_187442_t";
        allocation.desc = "(J)I";
        findCall(target, "glGetError").name = "func_187434_L";
        transformGlAllocation(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsForgeRuntimeSrgErrorCallWithArbitraryAlias()
            throws IOException {
        ClassNode classNode = readClass(
                readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class")
        );
        MethodNode target = findMethod(classNode, "generateDisplayLists", "(I)I");
        findCall(target, "glGenLists").name = "func_187442_t";
        findCall(target, "glGetError").name = "func_187435_M";
        transformGlAllocation(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDisplayListAllocatorWithChangedGlCall() throws IOException {
        ClassNode classNode = readClass(
                readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class")
        );
        MethodNode target = findMethod(classNode, "generateDisplayLists", "(I)I");
        findCall(target, "glGenLists").owner = "example/ChangedGlStateManager";
        transformGlAllocation(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDisplayListAllocatorWithExtraExecutableOpcode() throws IOException {
        ClassNode classNode = readClass(
                readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class")
        );
        MethodNode target = findMethod(classNode, "generateDisplayLists", "(I)I");
        target.instructions.insertBefore(
                target.instructions.getLast(),
                new InsnNode(Opcodes.NOP)
        );
        transformGlAllocation(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsAlreadyTransformedDisplayListAllocator() throws IOException {
        byte[] source = readClasspathResource("/" + GL_ALLOCATION_INTERNAL + ".class");
        transformGlAllocation(transformGlAllocation(source));
    }

    @Test
    public void preservesCanonicalForgeTextureCacheDelegationAndAppendsValidation()
            throws IOException {
        byte[] source = readClasspathResource("/" + MINECRAFT_INTERNAL + ".class");
        assertEquals(
                1,
                countCalls(
                        source,
                        "getGLMaximumTextureSize",
                        SPLASH_PROGRESS,
                        "getMaxTextureSize",
                        "()I"
                )
        );
        assertEquals(
                0,
                countCalls(
                        source,
                        "getGLMaximumTextureSize",
                        TEXTURE_SIZE_GUARD,
                        TEXTURE_SIZE_VALIDATOR,
                        "(I)I"
                )
        );

        byte[] transformed = transformMinecraft(source);
        assertEquals(
                1,
                countCalls(
                        transformed,
                        "getGLMaximumTextureSize",
                        SPLASH_PROGRESS,
                        "getMaxTextureSize",
                        "()I"
                )
        );
        assertEquals(
                1,
                countCalls(
                        transformed,
                        "getGLMaximumTextureSize",
                        TEXTURE_SIZE_GUARD,
                        TEXTURE_SIZE_VALIDATOR,
                        "(I)I"
                )
        );
        assertExactTextureValidationSequence(
                transformed,
                "getGLMaximumTextureSize"
        );
        assertRendererBootstrapGuardImmediatelyAfterLoadingHandoff(transformed, "init");
    }

    @Test
    public void acceptsOnlyTheCoherentForgeRuntimeSrgRendererBootstrapMapping() {
        ClassNode classNode = readClass(exactSyntheticMinecraft());
        MethodNode init = findMethod(classNode, "init", "()V");
        init.name = "func_71384_a";
        renameFieldRead(init, "defaultResourcePacks", "field_110449_ao");
        renameFieldRead(init, "resourceManager", "field_110451_am");
        renameFieldRead(init, "metadataSerializer", "field_110452_an");

        byte[] transformed = transformMinecraft(writeClass(classNode));

        assertRendererBootstrapGuardImmediatelyAfterLoadingHandoff(
                transformed, "func_71384_a");
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsRendererBootstrapWithInstructionBetweenHandoffAndTextureManager() {
        ClassNode classNode = readClass(exactSyntheticMinecraft());
        MethodNode init = findMethod(classNode, "init", "()V");
        MethodInsnNode begin = findCall(init, "beginMinecraftLoading");
        init.instructions.insert(begin, new InsnNode(Opcodes.NOP));

        transformMinecraft(writeClass(classNode));
    }

    @Test
    public void preservesSrgNamedForgeTextureCacheDelegation() {
        ClassNode classNode = readClass(exactSyntheticMinecraft());
        findMethod(
                classNode,
                "getGLMaximumTextureSize",
                "()I"
        ).name = "func_71369_N";

        byte[] transformed = transformMinecraft(writeClass(classNode));
        assertEquals(
                1,
                countCalls(
                        transformed,
                        "func_71369_N",
                        SPLASH_PROGRESS,
                        "getMaxTextureSize",
                        "()I"
                )
        );
        assertEquals(
                1,
                countCalls(
                        transformed,
                        "func_71369_N",
                        TEXTURE_SIZE_GUARD,
                        TEXTURE_SIZE_VALIDATOR,
                        "(I)I"
                )
        );
        assertExactTextureValidationSequence(transformed, "func_71369_N");
    }

    @Test
    public void textureSizeValidatorNeverTouchesOpenGlOrDisplay() throws IOException {
        byte[] guard = readClasspathResource("/" + TEXTURE_SIZE_GUARD + ".class");

        assertEquals(0, countCalls(guard, null, DISPLAY, null, null));
        assertEquals(0, countCalls(guard, null, GL_STATE_MANAGER, null, null));
        assertEquals(0, countCalls(guard, null, GL11, null, null));
        assertEquals(0, countCallsWithOwnerPrefix(guard, "org/lwjgl/"));
    }

    @Test
    public void acceptsExactSaneForgeTextureCacheValues() {
        for (int value : new int[]{2_048, 4_096, 8_192, 16_384}) {
            assertEquals(
                    value,
                    TextureSizeGuard.validateForgeCachedMaximumTextureSize(value)
            );
        }
    }

    @Test
    public void rejectsInvalidForgeTextureCacheValuesWithoutFallback() {
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 1_024, 3_000, 32_768}) {
            try {
                TextureSizeGuard.validateForgeCachedMaximumTextureSize(value);
                fail("Expected invalid Forge texture cache value to fail: " + value);
            } catch (IllegalStateException expected) {
                // Exact fail-closed behavior; no substitute capability is returned.
            }
        }
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMinecraftTextureGetterWithChangedForgeDelegation() {
        ClassNode classNode = readClass(exactSyntheticMinecraft());
        MethodNode getter = findMethod(
                classNode,
                "getGLMaximumTextureSize",
                "()I"
        );
        findCall(getter, "getMaxTextureSize").owner = "example/ChangedSplashProgress";
        transformMinecraft(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMinecraftTextureGetterWithExtraExecutableInstruction() {
        ClassNode classNode = readClass(exactSyntheticMinecraft());
        MethodNode getter = findMethod(
                classNode,
                "getGLMaximumTextureSize",
                "()I"
        );
        getter.instructions.insertBefore(
                getter.instructions.getLast(),
                new InsnNode(Opcodes.NOP)
        );
        transformMinecraft(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDuplicateMinecraftTextureGetterNames() {
        ClassNode classNode = readClass(exactSyntheticMinecraft());
        classNode.methods.add(exactSyntheticMaximumTextureGetter("func_71369_N"));
        transformMinecraft(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMinecraftTextureClassBytesForDifferentOwner() {
        ClassNode classNode = readClass(exactSyntheticMinecraft());
        classNode.name = "example/ImpostorMinecraft";
        transformMinecraft(writeClass(classNode));
    }

    @Test
    public void unrelatedClassIsReturnedWithoutRewriting() {
        byte[] source = syntheticUnrelatedClass();
        assertSame(
                source,
                new ExportGraphicsTransformer().transform(
                        "example.Unrelated", "example.Unrelated", source
                )
        );
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMissingExpectedShaderCall() {
        ClassNode classNode = readClass(exactSyntheticClass());
        MethodNode compile = findMethod(classNode, "compileShader", COMPILE_DESC);
        removeFirstCall(compile, "glGetShaderInfoLog");
        transform(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsReorderedShaderCalls() {
        ClassNode classNode = readClass(exactSyntheticClass());
        MethodNode compile = findMethod(classNode, "compileShader", COMPILE_DESC);
        MethodInsnNode source = findCall(compile, "glShaderSource");
        MethodInsnNode compiler = findCall(compile, "glCompileShader");
        String previousName = source.name;
        String previousDesc = source.desc;
        source.name = compiler.name;
        source.desc = compiler.desc;
        compiler.name = previousName;
        compiler.desc = previousDesc;
        transform(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnexpectedDirectGl20CallOutsideValidatedMethods() {
        ClassNode classNode = readClass(exactSyntheticClass());
        MethodNode decoy = new MethodNode(Opcodes.ACC_PUBLIC, "decoy", "()V", null, null);
        decoy.instructions.add(new InsnNode(Opcodes.ICONST_1));
        decoy.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                GL20,
                "glDeleteShader",
                "(I)V",
                false
        ));
        decoy.instructions.add(new InsnNode(Opcodes.RETURN));
        decoy.maxStack = 1;
        decoy.maxLocals = 1;
        classNode.methods.add(decoy);
        transform(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnexpectedInvocationOpcode() {
        ClassNode classNode = readClass(exactSyntheticClass());
        MethodNode constructor = findMethod(classNode, "<init>", CONSTRUCTOR_DESC);
        MethodInsnNode create = findCall(constructor, "glCreateShader");
        constructor.instructions.set(create, new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                GL20,
                "glCreateShader",
                "(I)I",
                false
        ));
        transform(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsClassBytesForDifferentOwner() {
        ClassNode classNode = readClass(exactSyntheticClass());
        classNode.name = "example/Impostor";
        transform(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMissingEagerMultiblockedShaderBootstrap() {
        ClassNode classNode = readClass(exactSyntheticClientProxy());
        MethodNode preInit = findMethod(classNode, "preInit", "()V");
        removeFirstCall(preInit, "init");
        transformClientProxy(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsAdditionalShadersCallDuringMultiblockedPreInit() {
        ClassNode classNode = readClass(exactSyntheticClientProxy());
        MethodNode preInit = findMethod(classNode, "preInit", "()V");
        preInit.instructions.insertBefore(
                preInit.instructions.getLast(),
                new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        SHADERS_INTERNAL,
                        "reload",
                        "()V",
                        false
                )
        );
        transformClientProxy(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsClientProxyClassBytesForDifferentOwner() {
        ClassNode classNode = readClass(exactSyntheticClientProxy());
        classNode.name = "example/ImpostorClientProxy";
        transformClientProxy(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsRandomPatchesWindowReloadWithMissingCall() {
        ClassNode classNode = readClass(exactSyntheticRandomPatchesWindow());
        MethodNode reload = findMethod(classNode, "onReloadClient", "()V");
        removeFirstCall(reload, "setTitle");
        transformRandomPatches(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsRandomPatchesWindowReloadWithReorderedCalls() {
        ClassNode classNode = readClass(exactSyntheticRandomPatchesWindow());
        MethodNode reload = findMethod(classNode, "onReloadClient", "()V");
        MethodInsnNode isCreated = findCall(reload, "isCreated");
        MethodInsnNode setWindowIcon = findCall(reload, "setWindowIcon");
        reload.instructions.set(isCreated, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                WINDOW_ICON_HANDLER,
                "setWindowIcon",
                "()V",
                false
        ));
        reload.instructions.set(setWindowIcon, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                DISPLAY,
                "isCreated",
                "()Z",
                false
        ));
        transformRandomPatches(writeClass(classNode));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDuplicateRandomPatchesWindowReloadMethod() {
        ClassNode classNode = readClass(exactSyntheticRandomPatchesWindow());
        MethodNode duplicate = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "onReloadClient",
                "()V",
                null,
                null
        );
        duplicate.instructions.add(new InsnNode(Opcodes.RETURN));
        duplicate.maxStack = 0;
        duplicate.maxLocals = 0;
        classNode.methods.add(duplicate);
        transformRandomPatches(writeClass(classNode));
    }

    private static byte[] transform(byte[] source) {
        return new ExportGraphicsTransformer().transform(
                TARGET_DOTTED,
                TARGET_DOTTED,
                source
        );
    }

    private static byte[] transformRandomPatches(byte[] source) {
        return new ExportGraphicsTransformer().transform(
                RANDOMPATCHES_WINDOW_DOTTED,
                RANDOMPATCHES_WINDOW_DOTTED,
                source
        );
    }

    private static byte[] transformClientProxy(byte[] source) {
        return new ExportGraphicsTransformer().transform(
                CLIENT_PROXY_DOTTED,
                CLIENT_PROXY_DOTTED,
                source
        );
    }

    private static byte[] transformMinecraft(byte[] source) {
        return new ExportGraphicsTransformer().transform(
                MINECRAFT_DOTTED,
                MINECRAFT_DOTTED,
                source
        );
    }

    private static byte[] transformGlAllocation(byte[] source) {
        return new ExportGraphicsTransformer().transform(
                GL_ALLOCATION_DOTTED,
                GL_ALLOCATION_DOTTED,
                source
        );
    }

    private static LoggedTransform transformGlAllocationWithLog(byte[] source) {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(output);
        try {
            System.setOut(capture);
            return new LoggedTransform(transformGlAllocation(source), output.toString());
        } finally {
            System.setOut(original);
            capture.close();
        }
    }

    private static byte[] exactSyntheticClass() {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = TARGET_INTERNAL;
        classNode.superName = "java/lang/Object";

        MethodNode constructor = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "<init>",
                CONSTRUCTOR_DESC,
                null,
                null
        );
        constructor.instructions.add(new InsnNode(Opcodes.ICONST_1));
        constructor.instructions.add(call("glCreateShader", "(I)I"));
        constructor.instructions.add(new InsnNode(Opcodes.POP));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxStack = 1;
        constructor.maxLocals = 3;
        classNode.methods.add(constructor);

        MethodNode compile = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "compileShader",
                COMPILE_DESC,
                null,
                null
        );
        compile.instructions.add(new InsnNode(Opcodes.ICONST_1));
        compile.instructions.add(new LdcInsnNode("void main() {}"));
        compile.instructions.add(call("glShaderSource", "(ILjava/lang/CharSequence;)V"));
        compile.instructions.add(new InsnNode(Opcodes.ICONST_1));
        compile.instructions.add(call("glCompileShader", "(I)V"));
        compile.instructions.add(new InsnNode(Opcodes.ICONST_1));
        compile.instructions.add(new InsnNode(Opcodes.ICONST_1));
        compile.instructions.add(call("glGetShaderi", "(II)I"));
        compile.instructions.add(new InsnNode(Opcodes.POP));
        compile.instructions.add(new InsnNode(Opcodes.ICONST_1));
        compile.instructions.add(new InsnNode(Opcodes.ICONST_1));
        compile.instructions.add(call("glGetShaderi", "(II)I"));
        compile.instructions.add(new InsnNode(Opcodes.POP));
        compile.instructions.add(new InsnNode(Opcodes.ICONST_1));
        compile.instructions.add(new InsnNode(Opcodes.ICONST_1));
        compile.instructions.add(call(
                "glGetShaderInfoLog",
                "(II)Ljava/lang/String;"
        ));
        compile.instructions.add(new InsnNode(Opcodes.POP));
        compile.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        compile.instructions.add(new InsnNode(Opcodes.ARETURN));
        compile.maxStack = 2;
        compile.maxLocals = 1;
        classNode.methods.add(compile);

        return writeClass(classNode);
    }

    private static byte[] exactSyntheticRandomPatchesWindow() {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL;
        classNode.name = RANDOMPATCHES_WINDOW_INTERNAL;
        classNode.superName = "java/lang/Object";

        MethodNode reload = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "onReloadClient",
                "()V",
                null,
                null
        );
        reload.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                DISPLAY,
                "isCreated",
                "()Z",
                false
        ));
        reload.instructions.add(new InsnNode(Opcodes.POP));
        reload.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                WINDOW_ICON_HANDLER,
                "setWindowIcon",
                "()V",
                false
        ));
        reload.instructions.add(new LdcInsnNode("Minecraft 1.12.2"));
        reload.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                DISPLAY,
                "setTitle",
                "(Ljava/lang/String;)V",
                false
        ));
        reload.instructions.add(new InsnNode(Opcodes.RETURN));
        reload.maxStack = 1;
        reload.maxLocals = 0;
        classNode.methods.add(reload);

        return writeClass(classNode);
    }

    private static byte[] exactSyntheticClientProxy() {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = CLIENT_PROXY_INTERNAL;
        classNode.superName = COMMON_PROXY_INTERNAL;

        MethodNode preInit = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "preInit",
                "()V",
                null,
                null
        );
        preInit.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        preInit.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                COMMON_PROXY_INTERNAL,
                "preInit",
                "()V",
                false
        ));
        preInit.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                SHADERS_INTERNAL,
                "init",
                "()V",
                false
        ));
        preInit.instructions.add(new LdcInsnNode(
                Type.getObjectType("net/minecraft/client/Minecraft")
        ));
        preInit.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "net/minecraft/client/Minecraft",
                "func_71410_x",
                "()Lnet/minecraft/client/Minecraft;",
                false
        ));
        preInit.instructions.add(new LdcInsnNode("field_110452_an"));
        preInit.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "net/minecraftforge/fml/common/ObfuscationReflectionHelper",
                "getPrivateValue",
                "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
                false
        ));
        preInit.instructions.add(new TypeInsnNode(
                Opcodes.CHECKCAST,
                "net/minecraft/client/resources/data/MetadataSerializer"
        ));
        preInit.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        preInit.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        preInit.instructions.add(new TypeInsnNode(
                Opcodes.NEW,
                "com/cleanroommc/multiblocked/client/model/custommodel/" +
                        "MetadataSectionEmissive$Serializer"
        ));
        preInit.instructions.add(new InsnNode(Opcodes.DUP));
        preInit.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "com/cleanroommc/multiblocked/client/model/custommodel/" +
                        "MetadataSectionEmissive$Serializer",
                "<init>",
                "()V",
                false
        ));
        preInit.instructions.add(new LdcInsnNode(Type.getObjectType(
                "com/cleanroommc/multiblocked/client/model/custommodel/" +
                        "MetadataSectionEmissive"
        )));
        preInit.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/resources/data/MetadataSerializer",
                "func_110504_a",
                "(Lnet/minecraft/client/resources/data/IMetadataSectionSerializer;" +
                        "Ljava/lang/Class;)V",
                false
        ));
        preInit.instructions.add(new InsnNode(Opcodes.RETURN));
        preInit.maxStack = 3;
        preInit.maxLocals = 2;
        classNode.methods.add(preInit);

        return writeClass(classNode);
    }

    private static byte[] exactSyntheticMinecraft() {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = MINECRAFT_INTERNAL;
        classNode.superName = "java/lang/Object";
        classNode.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "defaultResourcePacks",
                "Ljava/util/List;",
                null,
                null
        ));
        classNode.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE,
                "resourceManager",
                "Lnet/minecraft/client/resources/IReloadableResourceManager;",
                null,
                null
        ));
        classNode.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "metadataSerializer",
                "Lnet/minecraft/client/resources/data/MetadataSerializer;",
                null,
                null
        ));
        classNode.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC,
                "renderEngine",
                "Lnet/minecraft/client/renderer/texture/TextureManager;",
                null,
                null
        ));
        classNode.methods.add(exactSyntheticMinecraftInit());
        classNode.methods.add(
                exactSyntheticMaximumTextureGetter("getGLMaximumTextureSize")
        );
        return writeClass(classNode);
    }

    private static MethodNode exactSyntheticMinecraftInit() {
        MethodNode init = new MethodNode(
                Opcodes.ACC_PRIVATE,
                "init",
                "()V",
                null,
                new String[]{"org/lwjgl/LWJGLException", "java/io/IOException"}
        );
        init.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                FML_CLIENT_HANDLER,
                "instance",
                "()Lnet/minecraftforge/fml/client/FMLClientHandler;",
                false
        ));
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD,
                MINECRAFT_INTERNAL,
                "defaultResourcePacks",
                "Ljava/util/List;"
        ));
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD,
                MINECRAFT_INTERNAL,
                "resourceManager",
                "Lnet/minecraft/client/resources/IReloadableResourceManager;"
        ));
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD,
                MINECRAFT_INTERNAL,
                "metadataSerializer",
                "Lnet/minecraft/client/resources/data/MetadataSerializer;"
        ));
        init.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                FML_CLIENT_HANDLER,
                "beginMinecraftLoading",
                "(Lnet/minecraft/client/Minecraft;Ljava/util/List;" +
                        "Lnet/minecraft/client/resources/IReloadableResourceManager;" +
                        "Lnet/minecraft/client/resources/data/MetadataSerializer;)V",
                false
        ));
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new TypeInsnNode(
                Opcodes.NEW,
                "net/minecraft/client/renderer/texture/TextureManager"
        ));
        init.instructions.add(new InsnNode(Opcodes.DUP));
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD,
                MINECRAFT_INTERNAL,
                "resourceManager",
                "Lnet/minecraft/client/resources/IReloadableResourceManager;"
        ));
        init.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "net/minecraft/client/renderer/texture/TextureManager",
                "<init>",
                "(Lnet/minecraft/client/resources/IResourceManager;)V",
                false
        ));
        init.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD,
                MINECRAFT_INTERNAL,
                "renderEngine",
                "Lnet/minecraft/client/renderer/texture/TextureManager;"
        ));
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        init.maxStack = 5;
        init.maxLocals = 1;
        return init;
    }

    private static MethodNode exactSyntheticMaximumTextureGetter(String name) {
        MethodNode getter = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name,
                "()I",
                null,
                null
        );
        getter.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                SPLASH_PROGRESS,
                "getMaxTextureSize",
                "()I",
                false
        ));
        getter.instructions.add(new InsnNode(Opcodes.IRETURN));
        getter.maxStack = 1;
        getter.maxLocals = 0;
        return getter;
    }

    private static byte[] syntheticUnrelatedClass() {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC;
        classNode.name = "example/Unrelated";
        classNode.superName = "java/lang/Object";
        return writeClass(classNode);
    }

    private static MethodInsnNode call(String name, String descriptor) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, GL20, name, descriptor, false);
    }

    private static ClassNode readClass(byte[] bytes) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);
        return classNode;
    }

    private static byte[] writeClass(ClassNode classNode) {
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode findMethod(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        throw new AssertionError("Missing method " + name + descriptor);
    }

    private static MethodInsnNode findCall(MethodNode method, String name) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (name.equals(call.name)) {
                    return call;
                }
            }
        }
        throw new AssertionError("Missing call " + name + " in " + method.name);
    }

    private static void removeFirstCall(MethodNode method, String name) {
        method.instructions.remove(findCall(method, name));
    }

    private static void renameFieldRead(MethodNode method, String oldName, String newName) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instruction;
                if (field.getOpcode() == Opcodes.GETFIELD && oldName.equals(field.name)) {
                    field.name = newName;
                }
            }
        }
    }

    private static void assertRendererBootstrapGuardImmediatelyAfterLoadingHandoff(
            byte[] bytes, String initName) {
        MethodNode init = findMethod(readClass(bytes), initName, "()V");
        MethodInsnNode begin = findCall(init, "beginMinecraftLoading");
        AbstractInsnNode after = nextExecutable(begin.getNext());
        assertTrue(after instanceof MethodInsnNode);
        MethodInsnNode guard = (MethodInsnNode) after;
        assertEquals(Opcodes.INVOKESTATIC, guard.getOpcode());
        assertEquals(DISPLAY_LIST_GUARD, guard.owner);
        assertEquals(RENDERER_BOOTSTRAP_GUARD, guard.name);
        assertEquals("()V", guard.desc);
        assertEquals(false, guard.itf);
        AbstractInsnNode originalNext = nextExecutable(guard.getNext());
        assertTrue(originalNext instanceof VarInsnNode);
        assertEquals(Opcodes.ALOAD, originalNext.getOpcode());
        assertEquals(0, ((VarInsnNode) originalNext).var);
    }

    private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction;
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    private static int countCalls(byte[] bytes, String methodName, String owner,
                                  String callName, String descriptor) {
        ClassNode classNode = readClass(bytes);
        int matches = 0;
        for (MethodNode method : classNode.methods) {
            if (methodName != null && !methodName.equals(method.name)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (owner.equals(call.owner) &&
                        (callName == null || callName.equals(call.name)) &&
                        (descriptor == null || descriptor.equals(call.desc))) {
                    matches++;
                }
            }
        }
        return matches;
    }

    private static int countCallsWithOwnerPrefix(byte[] bytes, String ownerPrefix) {
        ClassNode classNode = readClass(bytes);
        int matches = 0;
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode &&
                        ((MethodInsnNode) instruction).owner.startsWith(ownerPrefix)) {
                    matches++;
                }
            }
        }
        return matches;
    }

    private static void assertExplicitReturn(byte[] bytes, String methodName,
                                             String descriptor) {
        MethodNode method = findMethod(readClass(bytes), methodName, descriptor);
        assertEquals(1, method.instructions.size());
        assertEquals(Opcodes.RETURN, method.instructions.getFirst().getOpcode());
        assertEquals(0, method.maxStack);
        assertEquals(0, method.maxLocals);
        assertEquals(0, method.tryCatchBlocks.size());
    }

    private static void assertExactTextureValidationSequence(byte[] bytes,
                                                             String methodName) {
        MethodNode method = findMethod(readClass(bytes), methodName, "()I");
        int executableIndex = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() < 0) {
                continue;
            }
            if (executableIndex == 0) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
                assertEquals(SPLASH_PROGRESS, call.owner);
                assertEquals("getMaxTextureSize", call.name);
                assertEquals("()I", call.desc);
            } else if (executableIndex == 1) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
                assertEquals(TEXTURE_SIZE_GUARD, call.owner);
                assertEquals(TEXTURE_SIZE_VALIDATOR, call.name);
                assertEquals("(I)I", call.desc);
            } else if (executableIndex == 2) {
                assertEquals(Opcodes.IRETURN, instruction.getOpcode());
            } else {
                fail("Unexpected executable instruction after texture validation return");
            }
            executableIndex++;
        }
        assertEquals(3, executableIndex);
    }

    private static void assertExactDisplayListGuardBody(byte[] bytes,
                                                        String methodName) {
        MethodNode method = findMethod(readClass(bytes), methodName, "(I)I");
        assertEquals(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED,
                method.access
        );
        assertEquals(3, method.instructions.size());
        VarInsnNode load = (VarInsnNode) method.instructions.getFirst();
        assertEquals(Opcodes.ILOAD, load.getOpcode());
        assertEquals(0, load.var);
        MethodInsnNode call = (MethodInsnNode) load.getNext();
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals(DISPLAY_LIST_GUARD, call.owner);
        assertEquals("generateDisplayLists", call.name);
        assertEquals("(I)I", call.desc);
        assertEquals(false, call.itf);
        assertEquals(Opcodes.IRETURN, call.getNext().getOpcode());
        assertEquals(0, method.tryCatchBlocks.size());
        assertEquals(1, method.maxStack);
        assertEquals(1, method.maxLocals);
    }

    private static final class LoggedTransform {
        private final byte[] bytes;
        private final String log;

        private LoggedTransform(byte[] bytes, String log) {
            this.bytes = bytes;
            this.log = log;
        }
    }

    private static byte[] readClasspathResource(String path) throws IOException {
        InputStream input = ExportGraphicsTransformerTest.class.getResourceAsStream(path);
        if (input == null) {
            throw new IOException("Missing test classpath resource " + path);
        }
        try (InputStream closeable = input) {
            return readAll(closeable);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
