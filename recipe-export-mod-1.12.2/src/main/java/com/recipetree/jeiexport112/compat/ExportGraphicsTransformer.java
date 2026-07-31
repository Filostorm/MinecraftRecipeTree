package com.recipetree.jeiexport112.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Export-only OpenGL compatibility transforms, registered by an explicit launch property. */
public final class ExportGraphicsTransformer implements IClassTransformer {
    private static final String FRAMEBUFFER_CLASS = "net.minecraft.client.shader.Framebuffer";
    private static final String GL_ALLOCATION_CLASS = "net.minecraft.client.renderer.GLAllocation";
    private static final String GL_ALLOCATION_INTERNAL =
            "net/minecraft/client/renderer/GLAllocation";
    private static final String GL_STATE_MANAGER_INTERNAL =
            "net/minecraft/client/renderer/GlStateManager";
    private static final int GL_ALLOCATION_CLASS_VERSION = Opcodes.V1_8;
    private static final String MINECRAFT_CLASS = "net.minecraft.client.Minecraft";
    private static final String MINECRAFT_INTERNAL = "net/minecraft/client/Minecraft";
    private static final String FML_CLIENT_HANDLER_INTERNAL =
            "net/minecraftforge/fml/client/FMLClientHandler";
    private static final String RELOADABLE_RESOURCE_MANAGER_INTERNAL =
            "net/minecraft/client/resources/IReloadableResourceManager";
    private static final String METADATA_SERIALIZER_INTERNAL =
            "net/minecraft/client/resources/data/MetadataSerializer";
    private static final String TEXTURE_MANAGER_INTERNAL =
            "net/minecraft/client/renderer/texture/TextureManager";
    private static final String SPLASH_PROGRESS_CLASS =
            "net.minecraftforge.fml.client.SplashProgress";
    private static final String SPLASH_PROGRESS_INTERNAL =
            "net/minecraftforge/fml/client/SplashProgress";
    private static final String MULTIBLOCKED_SHADER_CLASS =
            "com.cleanroommc.multiblocked.client.shader.management.Shader";
    private static final String MULTIBLOCKED_CLIENT_PROXY_CLASS =
            "com.cleanroommc.multiblocked.client.ClientProxy";
    private static final String MULTIBLOCKED_RENDER_UTILS_CLASS =
            "com.cleanroommc.multiblocked.client.util.RenderUtils";
    private static final String RANDOMPATCHES_WINDOW_CLASS =
            "com.therandomlabs.randompatches.config.RPConfig$Window";
    private static final String RANDOMPATCHES_WINDOW_INTERNAL =
            "com/therandomlabs/randompatches/config/RPConfig$Window";
    private static final String MULTIBLOCKED_SHADER_INTERNAL =
            "com/cleanroommc/multiblocked/client/shader/management/Shader";
    private static final String MULTIBLOCKED_CLIENT_PROXY_INTERNAL =
            "com/cleanroommc/multiblocked/client/ClientProxy";
    private static final String MULTIBLOCKED_RENDER_UTILS_INTERNAL =
            "com/cleanroommc/multiblocked/client/util/RenderUtils";
    private static final String MULTIBLOCKED_SHADERS_INTERNAL =
            "com/cleanroommc/multiblocked/client/shader/Shaders";
    private static final String MULTIBLOCKED_SHADER_TYPE_INTERNAL =
            MULTIBLOCKED_SHADER_INTERNAL + "$ShaderType";
    private static final String GL20_INTERNAL = "org/lwjgl/opengl/GL20";
    private static final String GL11_INTERNAL = "org/lwjgl/opengl/GL11";
    private static final String DISPLAY_LIST_GUARD_CLASS =
            "com/recipetree/jeiexport112/compat/DisplayListGuard";
    private static final int[] DISPLAY_LIST_ALLOCATION_OPCODES = {
            Opcodes.ILOAD,
            Opcodes.INVOKESTATIC,
            Opcodes.ISTORE,
            Opcodes.ILOAD,
            Opcodes.IFNE,
            Opcodes.INVOKESTATIC,
            Opcodes.ISTORE,
            Opcodes.LDC,
            Opcodes.ASTORE,
            Opcodes.ILOAD,
            Opcodes.IFEQ,
            Opcodes.ILOAD,
            Opcodes.INVOKESTATIC,
            Opcodes.ASTORE,
            Opcodes.NEW,
            Opcodes.DUP,
            Opcodes.NEW,
            Opcodes.DUP,
            Opcodes.INVOKESPECIAL,
            Opcodes.LDC,
            Opcodes.INVOKEVIRTUAL,
            Opcodes.ILOAD,
            Opcodes.INVOKEVIRTUAL,
            Opcodes.LDC,
            Opcodes.INVOKEVIRTUAL,
            Opcodes.ILOAD,
            Opcodes.INVOKEVIRTUAL,
            Opcodes.LDC,
            Opcodes.INVOKEVIRTUAL,
            Opcodes.ALOAD,
            Opcodes.INVOKEVIRTUAL,
            Opcodes.INVOKEVIRTUAL,
            Opcodes.INVOKESPECIAL,
            Opcodes.ATHROW,
            Opcodes.ILOAD,
            Opcodes.IRETURN
    };
    private static final String TEXTURE_SIZE_GUARD_CLASS =
            "com/recipetree/jeiexport112/compat/TextureSizeGuard";
    private static final String MULTIBLOCKED_SHADER_BRIDGE_CLASS =
            "com/recipetree/jeiexport112/compat/MultiblockedShaderBridge";
    private static final String MULTIBLOCKED_SCISSOR_BRIDGE_CLASS =
            "com/recipetree/jeiexport112/compat/MultiblockedScissorBridge";
    private static final int[] MULTIBLOCKED_APPLY_SCISSOR_OPCODES = {
            Opcodes.INVOKESTATIC,
            Opcodes.GETFIELD,
            Opcodes.CHECKCAST,
            Opcodes.INVOKEVIRTUAL,
            Opcodes.ASTORE,
            Opcodes.ALOAD,
            Opcodes.INVOKEVIRTUAL,
            Opcodes.ISTORE,
            Opcodes.ALOAD,
            Opcodes.INVOKEVIRTUAL,
            Opcodes.ILOAD,
            Opcodes.ISUB,
            Opcodes.ILOAD,
            Opcodes.ISUB,
            Opcodes.ISTORE,
            Opcodes.ILOAD,
            Opcodes.ILOAD,
            Opcodes.IMUL,
            Opcodes.ILOAD,
            Opcodes.ILOAD,
            Opcodes.IMUL,
            Opcodes.ILOAD,
            Opcodes.ILOAD,
            Opcodes.IMUL,
            Opcodes.ILOAD,
            Opcodes.ILOAD,
            Opcodes.IMUL,
            Opcodes.INVOKESTATIC,
            Opcodes.RETURN
    };
    private static final ExactMethodShape[] MULTIBLOCKED_RENDER_UTILS_METHODS = {
            new ExactMethodShape(Opcodes.ACC_PUBLIC, "<init>", "()V"),
            new ExactMethodShape(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "useScissor",
                    "(IIIILjava/lang/Runnable;)V"),
            new ExactMethodShape(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                    "peekFirstScissorOrFullScreen", "()[I"),
            new ExactMethodShape(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "pushScissorFrame", "(IIII)V"),
            new ExactMethodShape(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "popScissorFrame", "()V"),
            new ExactMethodShape(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                    "applyScissor", "(IIII)V"),
            new ExactMethodShape(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "renderBlockOverLay",
                    "(Lnet/minecraft/util/math/BlockPos;FFFF)V"),
            new ExactMethodShape(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "renderCubeFace",
                    "(Lnet/minecraft/client/renderer/BufferBuilder;DDDDDDFFFF)V"),
            new ExactMethodShape(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "useLightMap", "(FFLjava/lang/Runnable;)V"),
            new ExactMethodShape(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "moveToFace", "(DDDLnet/minecraft/util/EnumFacing;)V"),
            new ExactMethodShape(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "rotateToFace",
                    "(Lnet/minecraft/util/EnumFacing;Lnet/minecraft/util/EnumFacing;)V"),
            new ExactMethodShape(Opcodes.ACC_STATIC, "<clinit>", "()V")
    };
    private static final String MULTIBLOCKED_SHADER_CONSTRUCTOR_DESC =
            "(L" + MULTIBLOCKED_SHADER_TYPE_INTERNAL + ";Ljava/lang/String;)V";
    private static final String MULTIBLOCKED_SHADER_COMPILE_DESC =
            "()L" + MULTIBLOCKED_SHADER_INTERNAL + ";";
    private static final ExactInvocationShape[] MULTIBLOCKED_CLIENT_PREINIT_CALLS = {
            new ExactInvocationShape(
                    Opcodes.INVOKESPECIAL,
                    "com/cleanroommc/multiblocked/CommonProxy",
                    "preInit",
                    "()V"
            ),
            new ExactInvocationShape(
                    Opcodes.INVOKESTATIC,
                    MULTIBLOCKED_SHADERS_INTERNAL,
                    "init",
                    "()V"
            ),
            new ExactInvocationShape(
                    Opcodes.INVOKESTATIC,
                    "net/minecraft/client/Minecraft",
                    "func_71410_x",
                    "()Lnet/minecraft/client/Minecraft;"
            ),
            new ExactInvocationShape(
                    Opcodes.INVOKESTATIC,
                    "net/minecraftforge/fml/common/ObfuscationReflectionHelper",
                    "getPrivateValue",
                    "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
            ),
            new ExactInvocationShape(
                    Opcodes.INVOKESPECIAL,
                    "com/cleanroommc/multiblocked/client/model/custommodel/" +
                            "MetadataSectionEmissive$Serializer",
                    "<init>",
                    "()V"
            ),
            new ExactInvocationShape(
                    Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/client/resources/data/MetadataSerializer",
                    "func_110504_a",
                    "(Lnet/minecraft/client/resources/data/IMetadataSectionSerializer;" +
                            "Ljava/lang/Class;)V"
            )
    };
    private static final ExactCallShape[] RANDOMPATCHES_WINDOW_RELOAD_CALLS = {
            new ExactCallShape("org/lwjgl/opengl/Display", "isCreated", "()Z"),
            new ExactCallShape(
                    "com/therandomlabs/randompatches/client/WindowIconHandler",
                    "setWindowIcon",
                    "()V"
            ),
            new ExactCallShape(
                    "org/lwjgl/opengl/Display",
                    "setTitle",
                    "(Ljava/lang/String;)V"
            )
    };

    private static final ShaderCallPatch[] MULTIBLOCKED_CONSTRUCTOR_CALLS = {
            new ShaderCallPatch("glCreateShader", "(I)I", "createShader")
    };
    private static final ShaderCallPatch[] MULTIBLOCKED_COMPILE_CALLS = {
            new ShaderCallPatch(
                    "glShaderSource",
                    "(ILjava/lang/CharSequence;)V",
                    "shaderSource"
            ),
            new ShaderCallPatch("glCompileShader", "(I)V", "compileShader"),
            new ShaderCallPatch("glGetShaderi", "(II)I", "getShaderi"),
            new ShaderCallPatch("glGetShaderi", "(II)I", "getShaderi"),
            new ShaderCallPatch(
                    "glGetShaderInfoLog",
                    "(II)Ljava/lang/String;",
                    "getShaderInfoLog"
            )
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (FRAMEBUFFER_CLASS.equals(transformedName)) {
            return patchStencilRequest(basicClass);
        }
        if (GL_ALLOCATION_CLASS.equals(transformedName)) {
            return patchDisplayListAllocation(basicClass);
        }
        if (MINECRAFT_CLASS.equals(transformedName)) {
            return patchMinecraftGraphicsLifecycle(basicClass);
        }
        if (SPLASH_PROGRESS_CLASS.equals(transformedName)) {
            return patchVanillaSplashDraw(basicClass);
        }
        if (MULTIBLOCKED_SHADER_CLASS.equals(transformedName)) {
            return patchMultiblockedShaderCalls(basicClass);
        }
        if (MULTIBLOCKED_CLIENT_PROXY_CLASS.equals(transformedName)) {
            return disableEagerMultiblockedShaderBootstrap(basicClass);
        }
        if (MULTIBLOCKED_RENDER_UTILS_CLASS.equals(transformedName)) {
            return patchMultiblockedScissorCall(basicClass);
        }
        if (RANDOMPATCHES_WINDOW_CLASS.equals(transformedName)) {
            return disableEarlyRandomPatchesWindowReload(basicClass);
        }
        return basicClass;
    }

    private static byte[] patchStencilRequest(byte[] basicClass) {
        ClassNode classNode = readClass(basicClass);
        MethodNode target = findMethod(classNode, "enableStencil", "()Z");
        if (target == null) {
            throw missingMethod("Framebuffer.enableStencil()Z");
        }

        target.instructions = new InsnList();
        target.instructions.add(new InsnNode(Opcodes.ICONST_0));
        target.instructions.add(new InsnNode(Opcodes.IRETURN));
        resetMethodMetadata(target, 1, 1);

        System.out.println(
                "[jeiexport] Patched Framebuffer.enableStencil() for this export launch; stencil effects are disabled."
        );
        return writeClass(classNode);
    }

    private static byte[] patchDisplayListAllocation(byte[] basicClass) {
        ClassNode classNode = readClass(basicClass);
        if (!GL_ALLOCATION_INTERNAL.equals(classNode.name)) {
            throw displayListDrift(
                    "received class bytes for " + classNode.name +
                            " while transforming " + GL_ALLOCATION_INTERNAL
            );
        }
        if (classNode.version != GL_ALLOCATION_CLASS_VERSION) {
            throw displayListDrift(
                    "class version=" + classNode.version +
                            "; expected exact Java 8 version=" +
                            GL_ALLOCATION_CLASS_VERSION
            );
        }

        MethodNode target = null;
        for (MethodNode candidate : classNode.methods) {
            boolean expectedName = "generateDisplayLists".equals(candidate.name) ||
                    "func_74526_a".equals(candidate.name);
            if (!expectedName || !"(I)I".equals(candidate.desc)) {
                continue;
            }
            if (target != null) {
                throw displayListDrift(
                        "found duplicate MCP/SRG generateDisplayLists(I)I methods"
                );
            }
            target = candidate;
        }
        if (target == null) {
            throw displayListDrift(
                    "could not locate generateDisplayLists()/func_74526_a(I)I"
            );
        }

        int exactAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED;
        if (target.access != exactAccess) {
            throw displayListDrift(
                    target.name + "(I)I access=" + target.access +
                            "; expected exact public static synchronized concrete access=" +
                            exactAccess
            );
        }
        DisplayListCallMapping mapping = validateExactDisplayListAllocation(target);

        target.instructions = new InsnList();
        target.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        target.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                DISPLAY_LIST_GUARD_CLASS,
                "generateDisplayLists",
                "(I)I",
                false
        ));
        target.instructions.add(new InsnNode(Opcodes.IRETURN));
        resetMethodMetadata(target, 1, 1);

        System.out.println(
                "[jeiexport] Replaced the exact vanilla GLAllocation.generateDisplayLists(I)I " +
                        "body with the fail-closed DisplayListGuard after validating owner, " +
                        "class version, unique method, access flags, and every original " +
                        "executable opcode/call; display-list-call-mapping=" +
                        mapping.logName + " (" + GL_STATE_MANAGER_INTERNAL + "." +
                        mapping.allocationMethodName + "(I)I + " +
                        mapping.errorMethodName + "()I)."
        );
        return writeClass(classNode);
    }

    private static DisplayListCallMapping validateExactDisplayListAllocation(
            MethodNode target) {
        if (!target.tryCatchBlocks.isEmpty()) {
            throw displayListDrift(
                    target.name + "(I)I unexpectedly contains " +
                            target.tryCatchBlocks.size() + " try/catch blocks"
            );
        }

        List<AbstractInsnNode> executable = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction = target.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) {
                executable.add(instruction);
            }
        }

        if (executable.size() != DISPLAY_LIST_ALLOCATION_OPCODES.length) {
            throw displayListDrift(
                    target.name + "(I)I exposed " + executable.size() +
                            " executable instructions; expected exactly " +
                            DISPLAY_LIST_ALLOCATION_OPCODES.length
            );
        }
        for (int index = 0; index < DISPLAY_LIST_ALLOCATION_OPCODES.length; index++) {
            int actualOpcode = executable.get(index).getOpcode();
            int expectedOpcode = DISPLAY_LIST_ALLOCATION_OPCODES[index];
            if (actualOpcode != expectedOpcode) {
                throw displayListDrift(
                        target.name + "(I)I executable instruction " + (index + 1) +
                                " opcode=" + actualOpcode + "; expected " + expectedOpcode
                );
            }
        }

        requireVar(executable, 0, 0);
        DisplayListCallMapping mapping = requireDisplayListCallMapping(executable, 1);
        requireVar(executable, 2, 1);
        requireVar(executable, 3, 1);
        requireJumpTarget(executable, 4, executable.get(34));
        requireCall(executable, 5, Opcodes.INVOKESTATIC, GL_STATE_MANAGER_INTERNAL,
                mapping.errorMethodName, "()I");
        requireVar(executable, 6, 2);
        requireLdc(executable, 7, "No error code reported");
        requireVar(executable, 8, 3);
        requireVar(executable, 9, 2);
        requireJumpTarget(executable, 10, executable.get(14));
        requireVar(executable, 11, 2);
        requireCall(executable, 12, Opcodes.INVOKESTATIC, "org/lwjgl/util/glu/GLU",
                "gluErrorString", "(I)Ljava/lang/String;");
        requireVar(executable, 13, 3);
        requireType(executable, 14, "java/lang/IllegalStateException");
        requireType(executable, 16, "java/lang/StringBuilder");
        requireCall(executable, 18, Opcodes.INVOKESPECIAL, "java/lang/StringBuilder",
                "<init>", "()V");
        requireLdc(executable, 19,
                "glGenLists returned an ID of 0 for a count of ");
        requireCall(executable, 20, Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        requireVar(executable, 21, 0);
        requireCall(executable, 22, Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                "append", "(I)Ljava/lang/StringBuilder;");
        requireLdc(executable, 23, ", GL error (");
        requireCall(executable, 24, Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        requireVar(executable, 25, 2);
        requireCall(executable, 26, Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                "append", "(I)Ljava/lang/StringBuilder;");
        requireLdc(executable, 27, "): ");
        requireCall(executable, 28, Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        requireVar(executable, 29, 3);
        requireCall(executable, 30, Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        requireCall(executable, 31, Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                "toString", "()Ljava/lang/String;");
        requireCall(executable, 32, Opcodes.INVOKESPECIAL,
                "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V");
        requireVar(executable, 34, 1);
        return mapping;
    }

    private static DisplayListCallMapping requireDisplayListCallMapping(
            List<AbstractInsnNode> executable, int index) {
        AbstractInsnNode instruction = executable.get(index);
        if (!(instruction instanceof MethodInsnNode)) {
            throw displayListDrift(
                    "generateDisplayLists(I)I instruction " + (index + 1) +
                            " is not the expected display-list allocation call"
            );
        }
        MethodInsnNode call = (MethodInsnNode) instruction;
        if (call.getOpcode() != Opcodes.INVOKESTATIC || call.itf ||
                !GL_STATE_MANAGER_INTERNAL.equals(call.owner) ||
                !"(I)I".equals(call.desc)) {
            throw displayListDrift(
                    "generateDisplayLists(I)I instruction " + (index + 1) +
                            " has call shape " + formatCall(call) +
                            "; expected opcode=" + Opcodes.INVOKESTATIC + " " +
                            GL_STATE_MANAGER_INTERNAL +
                            ".{glGenLists|func_187442_t}(I)I"
            );
        }

        DisplayListCallMapping mapping =
                DisplayListCallMapping.forAllocationMethodName(call.name);
        if (mapping == null) {
            throw displayListDrift(
                    "generateDisplayLists(I)I instruction " + (index + 1) +
                            " called unaudited " + formatCall(call) +
                            "; accepted names are exactly glGenLists (audited development) " +
                            "or func_187442_t (Forge runtime SRG)"
            );
        }
        return mapping;
    }

    private static void requireVar(List<AbstractInsnNode> executable, int index,
                                   int expectedVariable) {
        AbstractInsnNode instruction = executable.get(index);
        if (!(instruction instanceof VarInsnNode) ||
                ((VarInsnNode) instruction).var != expectedVariable) {
            throw displayListDrift(
                    "generateDisplayLists(I)I instruction " + (index + 1) +
                            " did not reference exact local " + expectedVariable
            );
        }
    }

    private static void requireCall(List<AbstractInsnNode> executable, int index,
                                    int opcode, String owner, String name,
                                    String descriptor) {
        AbstractInsnNode instruction = executable.get(index);
        if (!(instruction instanceof MethodInsnNode)) {
            throw displayListDrift(
                    "generateDisplayLists(I)I instruction " + (index + 1) +
                            " is not the expected method call"
            );
        }
        MethodInsnNode call = (MethodInsnNode) instruction;
        if (call.getOpcode() != opcode || call.itf || !owner.equals(call.owner) ||
                !name.equals(call.name) || !descriptor.equals(call.desc)) {
            throw displayListDrift(
                    "generateDisplayLists(I)I instruction " + (index + 1) +
                            " has call shape " + formatCall(call) +
                            "; expected opcode=" + opcode + " " + owner + "." +
                            name + descriptor
            );
        }
    }

    private static void requireType(List<AbstractInsnNode> executable, int index,
                                    String expectedType) {
        AbstractInsnNode instruction = executable.get(index);
        if (!(instruction instanceof TypeInsnNode) ||
                !expectedType.equals(((TypeInsnNode) instruction).desc)) {
            throw displayListDrift(
                    "generateDisplayLists(I)I instruction " + (index + 1) +
                            " did not reference exact type " + expectedType
            );
        }
    }

    private static void requireLdc(List<AbstractInsnNode> executable, int index,
                                   String expectedValue) {
        AbstractInsnNode instruction = executable.get(index);
        if (!(instruction instanceof LdcInsnNode) ||
                !expectedValue.equals(((LdcInsnNode) instruction).cst)) {
            throw displayListDrift(
                    "generateDisplayLists(I)I instruction " + (index + 1) +
                            " did not contain exact constant " +
                            String.valueOf(expectedValue)
            );
        }
    }

    private static void requireJumpTarget(List<AbstractInsnNode> executable, int index,
                                          AbstractInsnNode expectedTarget) {
        AbstractInsnNode instruction = executable.get(index);
        if (!(instruction instanceof JumpInsnNode) ||
                nextExecutable(((JumpInsnNode) instruction).label) != expectedTarget) {
            throw displayListDrift(
                    "generateDisplayLists(I)I instruction " + (index + 1) +
                            " did not target the exact vanilla branch destination"
            );
        }
    }

    private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction;
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    private static AbstractInsnNode previousExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode cursor = instruction.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        return cursor;
    }

    private static byte[] patchMinecraftGraphicsLifecycle(byte[] basicClass) {
        return patchMaximumTextureSize(patchRendererBootstrapOwnership(basicClass));
    }

    private static byte[] patchRendererBootstrapOwnership(byte[] basicClass) {
        ClassNode classNode = readClass(basicClass);
        if (!MINECRAFT_INTERNAL.equals(classNode.name)) {
            throw rendererBootstrapDrift(
                    "received class bytes for " + classNode.name +
                            " while transforming " + MINECRAFT_INTERNAL
            );
        }
        if (classNode.version != Opcodes.V1_8) {
            throw rendererBootstrapDrift(
                    "class version=" + classNode.version +
                            "; expected exact Java 8 version=" + Opcodes.V1_8
            );
        }

        final String beginDescriptor =
                "(L" + MINECRAFT_INTERNAL + ";Ljava/util/List;L" +
                        RELOADABLE_RESOURCE_MANAGER_INTERNAL + ";L" +
                        METADATA_SERIALIZER_INTERNAL + ";)V";
        MethodNode target = null;
        MethodInsnNode beginLoading = null;
        RendererBootstrapMapping mapping = null;
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!FML_CLIENT_HANDLER_INTERNAL.equals(call.owner) ||
                        !"beginMinecraftLoading".equals(call.name) ||
                        !beginDescriptor.equals(call.desc)) {
                    continue;
                }
                if (target != null) {
                    throw rendererBootstrapDrift(
                            "found duplicate FMLClientHandler.beginMinecraftLoading calls in " +
                                    target.name + target.desc + " and " + method.name + method.desc
                    );
                }
                target = method;
                beginLoading = call;
                mapping = RendererBootstrapMapping.forInitMethodName(method.name);
            }
        }
        if (target == null || beginLoading == null) {
            throw rendererBootstrapDrift(
                    "could not locate the unique FMLClientHandler.beginMinecraftLoading" +
                            beginDescriptor + " call"
            );
        }
        if (mapping == null || !"()V".equals(target.desc)) {
            throw rendererBootstrapDrift(
                    "beginMinecraftLoading moved to unaudited method " +
                            target.name + target.desc +
                            "; accepted init names are exactly init and func_71384_a"
            );
        }
        if (target.access != Opcodes.ACC_PRIVATE) {
            throw rendererBootstrapDrift(
                    target.name + "()V access=" + target.access +
                            "; expected exact private concrete access=" + Opcodes.ACC_PRIVATE
            );
        }
        if (beginLoading.getOpcode() != Opcodes.INVOKEVIRTUAL || beginLoading.itf) {
            throw rendererBootstrapDrift(
                    "beginMinecraftLoading has call shape " + formatCall(beginLoading) +
                            "; expected INVOKEVIRTUAL"
            );
        }

        validateBeginMinecraftLoadingArguments(beginLoading, mapping);
        AbstractInsnNode next = nextExecutable(beginLoading.getNext());
        if (!(next instanceof VarInsnNode) || next.getOpcode() != Opcodes.ALOAD ||
                ((VarInsnNode) next).var != 0) {
            throw rendererBootstrapDrift(
                    "first executable instruction after beginMinecraftLoading is not ALOAD 0"
            );
        }
        AbstractInsnNode textureManagerCreation = nextExecutable(next.getNext());
        if (!(textureManagerCreation instanceof TypeInsnNode) ||
                textureManagerCreation.getOpcode() != Opcodes.NEW ||
                !TEXTURE_MANAGER_INTERNAL.equals(
                        ((TypeInsnNode) textureManagerCreation).desc)) {
            throw rendererBootstrapDrift(
                    "beginMinecraftLoading is no longer immediately followed by the original " +
                            "TextureManager construction"
            );
        }

        target.instructions.insert(beginLoading, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                DISPLAY_LIST_GUARD_CLASS,
                "ensureDisplayCurrentForRendererBootstrap",
                "()V",
                false
        ));

        System.out.println(
                "[jeiexport] Inserted the renderer-bootstrap Display ownership guard " +
                        "immediately after Minecraft." + target.name +
                        " returns from the unique exact FMLClientHandler.beginMinecraftLoading " +
                        "call; renderer-bootstrap-call-mapping=" + mapping.logName +
                        ". Font, dynamic, and primary atlas textures retain their original " +
                        "allocation/upload paths after the audited sole Display is current."
        );
        return writeClass(classNode);
    }

    private static void validateBeginMinecraftLoadingArguments(
            MethodInsnNode beginLoading, RendererBootstrapMapping mapping) {
        List<AbstractInsnNode> prefix = new ArrayList<AbstractInsnNode>();
        AbstractInsnNode cursor = beginLoading;
        for (int index = 0; index < 8; index++) {
            cursor = previousExecutable(cursor);
            if (cursor == null) {
                throw rendererBootstrapDrift(
                        "beginMinecraftLoading does not have its exact eight-instruction " +
                                "receiver/argument prefix"
                );
            }
            prefix.add(0, cursor);
        }
        requireRendererBootstrapCall(prefix, 0, Opcodes.INVOKESTATIC,
                FML_CLIENT_HANDLER_INTERNAL, "instance",
                "()L" + FML_CLIENT_HANDLER_INTERNAL + ";");
        requireRendererBootstrapVar(prefix, 1, 0, "Minecraft argument");
        requireRendererBootstrapVar(prefix, 2, 0, "resource-pack receiver");
        requireRendererBootstrapField(prefix, 3,
                mapping.defaultResourcePacksFieldName, "Ljava/util/List;");
        requireRendererBootstrapVar(prefix, 4, 0, "resource-manager receiver");
        requireRendererBootstrapField(prefix, 5,
                mapping.resourceManagerFieldName,
                "L" + RELOADABLE_RESOURCE_MANAGER_INTERNAL + ";");
        requireRendererBootstrapVar(prefix, 6, 0, "metadata-serializer receiver");
        requireRendererBootstrapField(prefix, 7,
                mapping.metadataSerializerFieldName,
                "L" + METADATA_SERIALIZER_INTERNAL + ";");
    }

    private static void requireRendererBootstrapVar(
            List<AbstractInsnNode> prefix, int index, int variable, String label) {
        AbstractInsnNode instruction = prefix.get(index);
        if (!(instruction instanceof VarInsnNode) ||
                instruction.getOpcode() != Opcodes.ALOAD ||
                ((VarInsnNode) instruction).var != variable) {
            throw rendererBootstrapDrift(
                    label + " prefix instruction has opcode=" + instruction.getOpcode() +
                            "; expected ALOAD " + variable
            );
        }
    }

    private static void requireRendererBootstrapField(
            List<AbstractInsnNode> prefix, int index, String name, String descriptor) {
        AbstractInsnNode instruction = prefix.get(index);
        if (!(instruction instanceof FieldInsnNode)) {
            throw rendererBootstrapDrift(
                    "renderer-bootstrap prefix instruction " + (index + 1) +
                            " is not the expected field read"
            );
        }
        FieldInsnNode field = (FieldInsnNode) instruction;
        if (field.getOpcode() != Opcodes.GETFIELD ||
                !MINECRAFT_INTERNAL.equals(field.owner) ||
                !name.equals(field.name) || !descriptor.equals(field.desc)) {
            throw rendererBootstrapDrift(
                    "renderer-bootstrap prefix instruction " + (index + 1) + " reads " +
                            field.owner + "." + field.name + field.desc +
                            "; expected GETFIELD " + MINECRAFT_INTERNAL + "." +
                            name + descriptor
            );
        }
    }

    private static void requireRendererBootstrapCall(
            List<AbstractInsnNode> prefix, int index, int opcode, String owner,
            String name, String descriptor) {
        AbstractInsnNode instruction = prefix.get(index);
        if (!(instruction instanceof MethodInsnNode)) {
            throw rendererBootstrapDrift(
                    "renderer-bootstrap prefix instruction " + (index + 1) +
                            " is not the expected method call"
            );
        }
        MethodInsnNode call = (MethodInsnNode) instruction;
        if (call.getOpcode() != opcode || call.itf || !owner.equals(call.owner) ||
                !name.equals(call.name) || !descriptor.equals(call.desc)) {
            throw rendererBootstrapDrift(
                    "renderer-bootstrap prefix instruction " + (index + 1) +
                            " has call shape " + formatCall(call) +
                            "; expected opcode=" + opcode + " " + owner + "." +
                            name + descriptor
            );
        }
    }

    private static byte[] patchMaximumTextureSize(byte[] basicClass) {
        ClassNode classNode = readClass(basicClass);
        if (!MINECRAFT_INTERNAL.equals(classNode.name)) {
            throw textureSizeDrift(
                    "received class bytes for " + classNode.name +
                            " while transforming " + MINECRAFT_INTERNAL
            );
        }

        MethodNode target = null;
        for (MethodNode candidate : classNode.methods) {
            boolean expectedName = "getGLMaximumTextureSize".equals(candidate.name) ||
                    "func_71369_N".equals(candidate.name);
            if (!expectedName || !"()I".equals(candidate.desc)) {
                continue;
            }
            if (target != null) {
                throw textureSizeDrift(
                        "found duplicate MCP/SRG maximum-texture getter methods"
                );
            }
            target = candidate;
        }
        if (target == null) {
            throw textureSizeDrift(
                    "could not locate getGLMaximumTextureSize()/func_71369_N()I"
            );
        }
        int requiredAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        if ((target.access & requiredAccess) != requiredAccess ||
                (target.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            throw textureSizeDrift(
                    target.name + "()I is not a concrete public static method"
            );
        }

        MethodInsnNode forgeDelegation = validateMaximumTextureDelegation(target);
        target.instructions.insert(forgeDelegation, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                TEXTURE_SIZE_GUARD_CLASS,
                "validateForgeCachedMaximumTextureSize",
                "(I)I",
                false
        ));
        target.maxStack = Math.max(target.maxStack, 1);

        System.out.println(
                "[jeiexport] Preserved Minecraft.getGLMaximumTextureSize()'s exact Forge " +
                        "SplashProgress cache delegation and appended fail-closed integer " +
                        "validation. The exporter issues no OpenGL query or context operation " +
                        "on this lifecycle."
        );
        return writeClass(classNode);
    }

    private static MethodInsnNode validateMaximumTextureDelegation(MethodNode target) {
        MethodInsnNode delegation = null;
        int executableIndex = 0;
        for (AbstractInsnNode instruction = target.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            int opcode = instruction.getOpcode();
            if (opcode < 0) {
                continue;
            }

            if (executableIndex == 0 && instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKESTATIC && !call.itf &&
                        SPLASH_PROGRESS_INTERNAL.equals(call.owner) &&
                        "getMaxTextureSize".equals(call.name) &&
                        "()I".equals(call.desc)) {
                    delegation = call;
                    executableIndex++;
                    continue;
                }
            } else if (executableIndex == 1 && opcode == Opcodes.IRETURN) {
                executableIndex++;
                continue;
            }

            throw textureSizeDrift(
                    target.name + "()I executable instruction " +
                            (executableIndex + 1) + " has opcode=" + opcode +
                            "; expected exact INVOKESTATIC " +
                            SPLASH_PROGRESS_INTERNAL + ".getMaxTextureSize()I followed by IRETURN"
            );
        }

        if (executableIndex != 2 || delegation == null) {
            throw textureSizeDrift(
                    target.name + "()I exposed " + executableIndex +
                            " executable instructions; expected exact INVOKESTATIC " +
                            SPLASH_PROGRESS_INTERNAL + ".getMaxTextureSize()I followed by IRETURN"
            );
        }
        return delegation;
    }

    private static byte[] patchVanillaSplashDraw(byte[] basicClass) {
        ClassNode classNode = readClass(basicClass);
        MethodNode target = findMethod(
                classNode,
                "drawVanillaScreen",
                "(Lnet/minecraft/client/renderer/texture/TextureManager;)V"
        );
        if (target == null) {
            throw missingMethod("SplashProgress.drawVanillaScreen(TextureManager)V");
        }

        target.instructions = new InsnList();
        target.instructions.add(new InsnNode(Opcodes.RETURN));
        resetMethodMetadata(target, 0, 1);

        System.out.println(
                "[jeiexport] Disabled the decorative vanilla startup splash for this export launch; offscreen FBO rendering remains enabled."
        );
        return writeClass(classNode);
    }

    private static byte[] patchMultiblockedShaderCalls(byte[] basicClass) {
        ClassNode classNode = readClass(basicClass);
        if (!MULTIBLOCKED_SHADER_INTERNAL.equals(classNode.name)) {
            throw graphicsDrift(
                    "received class bytes for " + classNode.name + " while transforming " +
                            MULTIBLOCKED_SHADER_INTERNAL
            );
        }

        MethodNode constructor = findOnlyMethod(
                classNode,
                "<init>",
                MULTIBLOCKED_SHADER_CONSTRUCTOR_DESC,
                "Multiblocked Shader constructor"
        );
        MethodNode compile = findOnlyMethod(
                classNode,
                "compileShader",
                MULTIBLOCKED_SHADER_COMPILE_DESC,
                "Multiblocked Shader.compileShader"
        );

        patchExactGl20Sequence(
                constructor,
                "Multiblocked Shader constructor",
                MULTIBLOCKED_CONSTRUCTOR_CALLS
        );
        patchExactGl20Sequence(
                compile,
                "Multiblocked Shader.compileShader",
                MULTIBLOCKED_COMPILE_CALLS
        );
        rejectRemainingDirectGl20Calls(classNode);

        System.out.println(
                "[jeiexport] Patched all 6 validated multiblocked-0.8.0 Shader GL20 " +
                        "call sites through the OpenGlHelper ARB/core compatibility bridge."
        );
        return writeClass(classNode);
    }

    private static byte[] disableEagerMultiblockedShaderBootstrap(byte[] basicClass) {
        ClassNode classNode = readClass(basicClass);
        if (!MULTIBLOCKED_CLIENT_PROXY_INTERNAL.equals(classNode.name)) {
            throw multiblockedBootstrapDrift(
                    "received class bytes for " + classNode.name + " while transforming " +
                            MULTIBLOCKED_CLIENT_PROXY_INTERNAL
            );
        }

        MethodNode preInit = null;
        for (MethodNode candidate : classNode.methods) {
            if (!"preInit".equals(candidate.name) || !"()V".equals(candidate.desc)) {
                continue;
            }
            if (preInit != null) {
                throw multiblockedBootstrapDrift("found duplicate preInit()V methods");
            }
            preInit = candidate;
        }
        if (preInit == null) {
            throw multiblockedBootstrapDrift("could not locate preInit()V");
        }
        if ((preInit.access & Opcodes.ACC_STATIC) != 0) {
            throw multiblockedBootstrapDrift("preInit()V is unexpectedly static");
        }

        MethodInsnNode eagerInit = null;
        int nextExpectedCall = 0;
        for (AbstractInsnNode instruction = preInit.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (nextExpectedCall >= MULTIBLOCKED_CLIENT_PREINIT_CALLS.length) {
                throw multiblockedBootstrapDrift(
                        "preInit()V contains an unexpected extra call " + formatCall(call)
                );
            }

            ExactInvocationShape expected =
                    MULTIBLOCKED_CLIENT_PREINIT_CALLS[nextExpectedCall];
            if (call.getOpcode() != expected.opcode || call.itf ||
                    !expected.owner.equals(call.owner) ||
                    !expected.name.equals(call.name) ||
                    !expected.descriptor.equals(call.desc)) {
                throw multiblockedBootstrapDrift(
                        "preInit()V call " + (nextExpectedCall + 1) + " has shape " +
                                formatCall(call) + "; expected opcode=" + expected.opcode +
                                " " + expected.owner + "." + expected.name +
                                expected.descriptor
                );
            }
            if (nextExpectedCall == 1) {
                eagerInit = call;
            }
            nextExpectedCall++;
        }

        if (nextExpectedCall != MULTIBLOCKED_CLIENT_PREINIT_CALLS.length) {
            ExactInvocationShape missing =
                    MULTIBLOCKED_CLIENT_PREINIT_CALLS[nextExpectedCall];
            throw multiblockedBootstrapDrift(
                    "preInit()V exposed " + nextExpectedCall + " of " +
                            MULTIBLOCKED_CLIENT_PREINIT_CALLS.length +
                            " expected calls; next expected " + missing.owner + "." +
                            missing.name + missing.descriptor
            );
        }
        if (eagerInit == null) {
            throw multiblockedBootstrapDrift(
                    "validated call sequence did not identify Shaders.init()V"
            );
        }

        preInit.instructions.remove(eagerInit);

        System.out.println(
                "[jeiexport] WARNING: Disabled only multiblocked-0.8.0's eager built-in " +
                        "shader bootstrap for this export launch because Apple GL 2.1 returns " +
                        "shader object 0. Native Minecraft item/model rendering and HEI recipe " +
                        "layout capture remain enabled; any deferred Multiblocked shader " +
                        "creation still uses the explicit fail-closed bridge."
        );
        return writeClass(classNode);
    }

    private static byte[] patchMultiblockedScissorCall(byte[] basicClass) {
        ClassNode classNode = readClass(basicClass);
        validateExactMultiblockedRenderUtilsClass(classNode);
        MethodNode target = findOnlyMethod(
                classNode, "applyScissor", "(IIII)V",
                "Multiblocked RenderUtils.applyScissor"
        );
        validateExactMultiblockedApplyScissor(target);

        MethodInsnNode rawScissor = null;
        for (AbstractInsnNode instruction = target.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (GL11_INTERNAL.equals(call.owner) && "glScissor".equals(call.name) &&
                        "(IIII)V".equals(call.desc)) {
                    rawScissor = call;
                    break;
                }
            }
        }
        if (rawScissor == null) {
            throw multiblockedScissorDrift(
                    "validated applyScissor(IIII)V did not expose its sole GL11.glScissor call"
            );
        }
        rawScissor.owner = MULTIBLOCKED_SCISSOR_BRIDGE_CLASS;
        rawScissor.name = "glScissor";
        rawScissor.itf = false;
        rejectUnexpectedMultiblockedScissorCalls(classNode);

        System.out.println(
                "[jeiexport] Patched the sole bytecode-validated multiblocked-0.8.0 " +
                        "RenderUtils.applyScissor GL11 call through the export-scoped " +
                        "framebuffer coordinate bridge; inactive calls retain their four raw " +
                        "arguments unchanged."
        );
        return writeClass(classNode);
    }

    private static void validateExactMultiblockedRenderUtilsClass(ClassNode classNode) {
        if (!MULTIBLOCKED_RENDER_UTILS_INTERNAL.equals(classNode.name)) {
            throw multiblockedScissorDrift(
                    "received class bytes for " + classNode.name + " while transforming " +
                            MULTIBLOCKED_RENDER_UTILS_INTERNAL
            );
        }
        if (classNode.version != Opcodes.V1_8 ||
                classNode.access != (Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER) ||
                !"java/lang/Object".equals(classNode.superName) ||
                !classNode.interfaces.isEmpty() ||
                !"RenderUtils.java".equals(classNode.sourceFile)) {
            throw multiblockedScissorDrift(
                    "class identity changed: version=" + classNode.version +
                            ", access=" + classNode.access +
                            ", super=" + classNode.superName +
                            ", interfaces=" + classNode.interfaces +
                            ", source=" + classNode.sourceFile
            );
        }
        if (classNode.fields.size() != 1) {
            throw multiblockedScissorDrift(
                    "RenderUtils field count changed from 1 to " + classNode.fields.size()
            );
        }
        FieldNode field = classNode.fields.get(0);
        if (field.access != (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL) ||
                !"scissorFrameStack".equals(field.name) ||
                !"Ljava/util/Stack;".equals(field.desc) ||
                !"Ljava/util/Stack<[I>;".equals(field.signature) || field.value != null) {
            throw multiblockedScissorDrift(
                    "RenderUtils.scissorFrameStack field identity changed"
            );
        }
        if (classNode.methods.size() != MULTIBLOCKED_RENDER_UTILS_METHODS.length) {
            throw multiblockedScissorDrift(
                    "RenderUtils method count changed from " +
                            MULTIBLOCKED_RENDER_UTILS_METHODS.length + " to " +
                            classNode.methods.size()
            );
        }
        for (int index = 0; index < MULTIBLOCKED_RENDER_UTILS_METHODS.length; index++) {
            MethodNode method = classNode.methods.get(index);
            ExactMethodShape expected = MULTIBLOCKED_RENDER_UTILS_METHODS[index];
            if (method.access != expected.access ||
                    !expected.name.equals(method.name) ||
                    !expected.descriptor.equals(method.desc)) {
                throw multiblockedScissorDrift(
                        "RenderUtils method " + (index + 1) + " changed from access=" +
                                expected.access + " " + expected.name + expected.descriptor +
                                " to access=" + method.access + " " +
                                method.name + method.desc
                );
            }
        }
    }

    private static void validateExactMultiblockedApplyScissor(MethodNode target) {
        if (!target.tryCatchBlocks.isEmpty() || target.maxStack != 5 || target.maxLocals != 7) {
            throw multiblockedScissorDrift(
                    "applyScissor(IIII)V metadata changed: tryCatchBlocks=" +
                            target.tryCatchBlocks.size() + ", maxStack=" + target.maxStack +
                            ", maxLocals=" + target.maxLocals
            );
        }
        List<AbstractInsnNode> executable = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode instruction = target.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() >= 0) {
                executable.add(instruction);
            }
        }
        if (executable.size() != MULTIBLOCKED_APPLY_SCISSOR_OPCODES.length) {
            throw multiblockedScissorDrift(
                    "applyScissor(IIII)V exposed " + executable.size() +
                            " executable instructions; expected exactly " +
                            MULTIBLOCKED_APPLY_SCISSOR_OPCODES.length
            );
        }
        for (int index = 0; index < MULTIBLOCKED_APPLY_SCISSOR_OPCODES.length; index++) {
            int actual = executable.get(index).getOpcode();
            int expected = MULTIBLOCKED_APPLY_SCISSOR_OPCODES[index];
            if (actual != expected) {
                throw multiblockedScissorDrift(
                        "applyScissor(IIII)V instruction " + (index + 1) +
                                " opcode=" + actual + "; expected " + expected
                );
            }
        }

        requireScissorCall(executable, 0, MINECRAFT_INTERNAL, "func_71410_x",
                "()Lnet/minecraft/client/Minecraft;");
        requireScissorField(executable, 1, MINECRAFT_INTERNAL, "field_71456_v",
                "Lnet/minecraft/client/gui/GuiIngame;");
        requireScissorType(executable, 2, "net/minecraftforge/client/GuiIngameForge");
        requireScissorCall(executable, 3, "net/minecraftforge/client/GuiIngameForge",
                "getResolution", "()Lnet/minecraft/client/gui/ScaledResolution;");
        requireScissorVar(executable, 4, 4);
        requireScissorVar(executable, 5, 4);
        requireScissorCall(executable, 6, "net/minecraft/client/gui/ScaledResolution",
                "func_78325_e", "()I");
        requireScissorVar(executable, 7, 5);
        requireScissorVar(executable, 8, 4);
        requireScissorCall(executable, 9, "net/minecraft/client/gui/ScaledResolution",
                "func_78328_b", "()I");
        requireScissorVar(executable, 10, 1);
        requireScissorVar(executable, 12, 3);
        requireScissorVar(executable, 14, 6);
        requireScissorVar(executable, 15, 0);
        requireScissorVar(executable, 16, 5);
        requireScissorVar(executable, 18, 6);
        requireScissorVar(executable, 19, 5);
        requireScissorVar(executable, 21, 2);
        requireScissorVar(executable, 22, 5);
        requireScissorVar(executable, 24, 3);
        requireScissorVar(executable, 25, 5);
        requireScissorCall(executable, 27, GL11_INTERNAL, "glScissor", "(IIII)V");
    }

    private static void requireScissorVar(List<AbstractInsnNode> executable, int index,
                                          int expectedVariable) {
        AbstractInsnNode instruction = executable.get(index);
        if (!(instruction instanceof VarInsnNode) ||
                ((VarInsnNode) instruction).var != expectedVariable) {
            throw multiblockedScissorDrift(
                    "applyScissor(IIII)V instruction " + (index + 1) +
                            " changed local variable from " + expectedVariable
            );
        }
    }

    private static void requireScissorCall(List<AbstractInsnNode> executable, int index,
                                           String owner, String name, String descriptor) {
        AbstractInsnNode instruction = executable.get(index);
        if (!(instruction instanceof MethodInsnNode)) {
            throw multiblockedScissorDrift(
                    "applyScissor(IIII)V instruction " + (index + 1) +
                            " is not the expected call"
            );
        }
        MethodInsnNode call = (MethodInsnNode) instruction;
        if (call.itf || !owner.equals(call.owner) || !name.equals(call.name) ||
                !descriptor.equals(call.desc)) {
            throw multiblockedScissorDrift(
                    "applyScissor(IIII)V instruction " + (index + 1) +
                            " has call shape " + formatCall(call) + "; expected " +
                            owner + "." + name + descriptor
            );
        }
    }

    private static void requireScissorField(List<AbstractInsnNode> executable, int index,
                                            String owner, String name, String descriptor) {
        AbstractInsnNode instruction = executable.get(index);
        if (!(instruction instanceof FieldInsnNode)) {
            throw multiblockedScissorDrift(
                    "applyScissor(IIII)V instruction " + (index + 1) +
                            " is not the expected field read"
            );
        }
        FieldInsnNode field = (FieldInsnNode) instruction;
        if (!owner.equals(field.owner) || !name.equals(field.name) ||
                !descriptor.equals(field.desc)) {
            throw multiblockedScissorDrift(
                    "applyScissor(IIII)V instruction " + (index + 1) +
                            " field changed to " + field.owner + "." +
                            field.name + field.desc
            );
        }
    }

    private static void requireScissorType(List<AbstractInsnNode> executable, int index,
                                           String descriptor) {
        AbstractInsnNode instruction = executable.get(index);
        if (!(instruction instanceof TypeInsnNode) ||
                !descriptor.equals(((TypeInsnNode) instruction).desc)) {
            throw multiblockedScissorDrift(
                    "applyScissor(IIII)V instruction " + (index + 1) +
                            " changed expected type " + descriptor
            );
        }
    }

    private static void rejectUnexpectedMultiblockedScissorCalls(ClassNode classNode) {
        int bridgeCalls = 0;
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (GL11_INTERNAL.equals(call.owner) && "glScissor".equals(call.name)) {
                    throw multiblockedScissorDrift(
                            "direct GL11.glScissor call remained in " +
                                    method.name + method.desc
                    );
                }
                if (MULTIBLOCKED_SCISSOR_BRIDGE_CLASS.equals(call.owner) &&
                        "glScissor".equals(call.name) && "(IIII)V".equals(call.desc)) {
                    bridgeCalls++;
                }
            }
        }
        if (bridgeCalls != 1) {
            throw multiblockedScissorDrift(
                    "expected exactly one bridge call after patching, found " + bridgeCalls
            );
        }
    }

    private static byte[] disableEarlyRandomPatchesWindowReload(byte[] basicClass) {
        ClassNode classNode = readClass(basicClass);
        if (!RANDOMPATCHES_WINDOW_INTERNAL.equals(classNode.name)) {
            throw randomPatchesDrift(
                    "received class bytes for " + classNode.name + " while transforming " +
                            RANDOMPATCHES_WINDOW_INTERNAL
            );
        }

        MethodNode target = null;
        for (MethodNode candidate : classNode.methods) {
            if (!"onReloadClient".equals(candidate.name) || !"()V".equals(candidate.desc)) {
                continue;
            }
            if (target != null) {
                throw randomPatchesDrift("found duplicate onReloadClient()V methods");
            }
            target = candidate;
        }
        if (target == null) {
            throw randomPatchesDrift("could not locate onReloadClient()V");
        }
        if ((target.access & Opcodes.ACC_STATIC) == 0) {
            throw randomPatchesDrift("onReloadClient()V is no longer static");
        }

        validateExactCallSequence(
                target,
                "RandomPatches RPConfig$Window.onReloadClient()V",
                RANDOMPATCHES_WINDOW_RELOAD_CALLS
        );
        target.instructions = new InsnList();
        target.instructions.add(new InsnNode(Opcodes.RETURN));
        resetMethodMetadata(target, 0, 0);

        System.out.println(
                "[jeiexport] Disabled only RandomPatches 1.22.1.10's cosmetic window " +
                        "title/icon reload for this export launch before normal LWJGL Display " +
                        "creation; Minecraft class patching remains disabled by pack config."
        );
        return writeClass(classNode);
    }

    private static void validateExactCallSequence(MethodNode method, String label,
                                                  ExactCallShape[] expectedCalls) {
        int nextExpectedCall = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (nextExpectedCall >= expectedCalls.length) {
                throw randomPatchesDrift(
                        label + " contains an unexpected extra call " + formatCall(call)
                );
            }

            ExactCallShape expected = expectedCalls[nextExpectedCall];
            if (call.getOpcode() != Opcodes.INVOKESTATIC || call.itf ||
                    !expected.owner.equals(call.owner) ||
                    !expected.name.equals(call.name) ||
                    !expected.descriptor.equals(call.desc)) {
                throw randomPatchesDrift(
                        label + " call " + (nextExpectedCall + 1) + " has shape " +
                                formatCall(call) + "; expected INVOKESTATIC " +
                                expected.owner + "." + expected.name + expected.descriptor
                );
            }
            nextExpectedCall++;
        }

        if (nextExpectedCall != expectedCalls.length) {
            ExactCallShape missing = expectedCalls[nextExpectedCall];
            throw randomPatchesDrift(
                    label + " exposed " + nextExpectedCall + " of " + expectedCalls.length +
                            " expected calls; next expected " + missing.owner + "." +
                            missing.name + missing.descriptor
            );
        }
    }

    private static MethodNode findOnlyMethod(ClassNode classNode, String name,
                                             String descriptor, String label) {
        MethodNode match = null;
        for (MethodNode candidate : classNode.methods) {
            if (!name.equals(candidate.name) || !descriptor.equals(candidate.desc)) {
                continue;
            }
            if (match != null) {
                throw graphicsDrift("found duplicate " + label + " methods");
            }
            match = candidate;
        }
        if (match == null) {
            throw graphicsDrift("could not locate " + label + descriptor);
        }
        return match;
    }

    private static void patchExactGl20Sequence(MethodNode method, String label,
                                                ShaderCallPatch[] expectedCalls) {
        int nextExpectedCall = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (!GL20_INTERNAL.equals(call.owner)) {
                continue;
            }
            if (nextExpectedCall >= expectedCalls.length) {
                throw graphicsDrift(
                        label + " contains an unexpected extra direct GL20 call " +
                                formatCall(call)
                );
            }

            ShaderCallPatch expected = expectedCalls[nextExpectedCall];
            if (call.getOpcode() != Opcodes.INVOKESTATIC || call.itf ||
                    !expected.gl20Name.equals(call.name) ||
                    !expected.descriptor.equals(call.desc)) {
                throw graphicsDrift(
                        label + " GL20 call " + (nextExpectedCall + 1) + " has shape " +
                                formatCall(call) + "; expected INVOKESTATIC " +
                                GL20_INTERNAL + "." + expected.gl20Name +
                                expected.descriptor
                );
            }

            call.owner = MULTIBLOCKED_SHADER_BRIDGE_CLASS;
            call.name = expected.bridgeName;
            call.itf = false;
            nextExpectedCall++;
        }

        if (nextExpectedCall != expectedCalls.length) {
            ShaderCallPatch missing = expectedCalls[nextExpectedCall];
            throw graphicsDrift(
                    label + " exposed " + nextExpectedCall + " of " +
                            expectedCalls.length + " expected direct GL20 calls; next expected " +
                            missing.gl20Name + missing.descriptor
            );
        }
    }

    private static void rejectRemainingDirectGl20Calls(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (GL20_INTERNAL.equals(call.owner)) {
                        throw graphicsDrift(
                                "direct GL20 call remained in " + method.name + method.desc +
                                        ": " + formatCall(call)
                        );
                    }
                }
            }
        }
    }

    private static String formatCall(MethodInsnNode call) {
        return call.owner + "." + call.name + call.desc +
                " (opcode=" + call.getOpcode() + ", interface=" + call.itf + ")";
    }

    private static ClassNode readClass(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        return classNode;
    }

    private static MethodNode findMethod(ClassNode classNode, String name, String descriptor) {
        ListIterator<MethodNode> methods = classNode.methods.listIterator();
        while (methods.hasNext()) {
            MethodNode candidate = methods.next();
            if (name.equals(candidate.name) && descriptor.equals(candidate.desc)) {
                return candidate;
            }
        }
        return null;
    }

    private static void resetMethodMetadata(MethodNode target, int maxStack, int maxLocals) {
        target.tryCatchBlocks.clear();
        if (target.localVariables != null) {
            target.localVariables.clear();
        }
        target.maxStack = maxStack;
        target.maxLocals = maxLocals;
    }

    private static byte[] writeClass(ClassNode classNode) {
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static IllegalStateException missingMethod(String method) {
        return new IllegalStateException(
                "[jeiexport] Could not locate " + method +
                        "; refusing a silent graphics compatibility fallback."
        );
    }

    private static IllegalStateException graphicsDrift(String detail) {
        return new IllegalStateException(
                "[jeiexport] multiblocked-0.8.0 Shader bytecode contract drift: " + detail +
                        "; refusing a silent GL20 compatibility fallback."
        );
    }

    private static IllegalStateException displayListDrift(String detail) {
        return new IllegalStateException(
                "[jeiexport] Minecraft 1.12.2 GLAllocation.generateDisplayLists bytecode " +
                        "contract drift: " + detail +
                        "; refusing to replace an unaudited display-list allocator."
        );
    }

    private static IllegalStateException multiblockedBootstrapDrift(String detail) {
        return new IllegalStateException(
                "[jeiexport] multiblocked-0.8.0 ClientProxy.preInit shader-bootstrap " +
                        "bytecode contract drift: " + detail +
                        "; refusing a silent shader-initialization fallback."
        );
    }

    private static IllegalStateException multiblockedScissorDrift(String detail) {
        return new IllegalStateException(
                "[jeiexport] multiblocked-0.8.0 RenderUtils.applyScissor bytecode " +
                        "contract drift: " + detail +
                        "; refusing an unaudited framebuffer-scissor correction."
        );
    }

    private static IllegalStateException randomPatchesDrift(String detail) {
        return new IllegalStateException(
                "[jeiexport] RandomPatches 1.22.1.10 window reload bytecode contract drift: " +
                        detail + "; refusing a silent early-AWT compatibility fallback."
        );
    }

    private static IllegalStateException textureSizeDrift(String detail) {
        return new IllegalStateException(
                "[jeiexport] Forge 1.12.2 Minecraft maximum-texture delegation bytecode " +
                        "contract drift: " + detail +
                        "; refusing to replace or bypass Forge's texture-size cache."
        );
    }

    private static IllegalStateException rendererBootstrapDrift(String detail) {
        return new IllegalStateException(
                "[jeiexport] Forge 1.12.2 Minecraft.init renderer-bootstrap bytecode " +
                        "contract drift: " + detail +
                        "; refusing an unaudited Display ownership operation before renderer " +
                        "texture allocation/upload."
        );
    }

    private enum RendererBootstrapMapping {
        AUDITED_DEVELOPMENT(
                "audited-development",
                "init",
                "defaultResourcePacks",
                "resourceManager",
                "metadataSerializer"
        ),
        FORGE_RUNTIME_SRG(
                "forge-runtime-srg",
                "func_71384_a",
                "field_110449_ao",
                "field_110451_am",
                "field_110452_an"
        );

        private final String logName;
        private final String initMethodName;
        private final String defaultResourcePacksFieldName;
        private final String resourceManagerFieldName;
        private final String metadataSerializerFieldName;

        RendererBootstrapMapping(String logName, String initMethodName,
                                 String defaultResourcePacksFieldName,
                                 String resourceManagerFieldName,
                                 String metadataSerializerFieldName) {
            this.logName = logName;
            this.initMethodName = initMethodName;
            this.defaultResourcePacksFieldName = defaultResourcePacksFieldName;
            this.resourceManagerFieldName = resourceManagerFieldName;
            this.metadataSerializerFieldName = metadataSerializerFieldName;
        }

        private static RendererBootstrapMapping forInitMethodName(String name) {
            for (RendererBootstrapMapping mapping : values()) {
                if (mapping.initMethodName.equals(name)) {
                    return mapping;
                }
            }
            return null;
        }
    }

    private enum DisplayListCallMapping {
        AUDITED_DEVELOPMENT(
                "audited-development",
                "glGenLists",
                "glGetError"
        ),
        FORGE_RUNTIME_SRG(
                "forge-runtime-srg",
                "func_187442_t",
                "func_187434_L"
        );

        private final String logName;
        private final String allocationMethodName;
        private final String errorMethodName;

        DisplayListCallMapping(String logName, String allocationMethodName,
                               String errorMethodName) {
            this.logName = logName;
            this.allocationMethodName = allocationMethodName;
            this.errorMethodName = errorMethodName;
        }

        private static DisplayListCallMapping forAllocationMethodName(String name) {
            for (DisplayListCallMapping mapping : values()) {
                if (mapping.allocationMethodName.equals(name)) {
                    return mapping;
                }
            }
            return null;
        }
    }

    private static final class ExactCallShape {
        private final String owner;
        private final String name;
        private final String descriptor;

        private ExactCallShape(String owner, String name, String descriptor) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    private static final class ExactInvocationShape {
        private final int opcode;
        private final String owner;
        private final String name;
        private final String descriptor;

        private ExactInvocationShape(int opcode, String owner, String name,
                                     String descriptor) {
            this.opcode = opcode;
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    private static final class ExactMethodShape {
        private final int access;
        private final String name;
        private final String descriptor;

        private ExactMethodShape(int access, String name, String descriptor) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    private static final class ShaderCallPatch {
        private final String gl20Name;
        private final String descriptor;
        private final String bridgeName;

        private ShaderCallPatch(String gl20Name, String descriptor, String bridgeName) {
            this.gl20Name = gl20Name;
            this.descriptor = descriptor;
            this.bridgeName = bridgeName;
        }
    }
}
