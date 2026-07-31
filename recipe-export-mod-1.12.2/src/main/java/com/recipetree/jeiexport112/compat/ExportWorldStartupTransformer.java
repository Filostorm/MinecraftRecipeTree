package com.recipetree.jeiexport112.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Export-only, fail-closed patch for Forge's duplicated startup-dimension loop.
 *
 * IntegratedServer owns the actual client-export path. MinecraftServer contains the equivalent
 * base/dedicated implementation, so both owners are patched and each must contain exactly one
 * matching method and one exact DimensionManager call site.
 */
public final class ExportWorldStartupTransformer implements IClassTransformer {
    private static final String MINECRAFT_SERVER = "net.minecraft.server.MinecraftServer";
    private static final String INTEGRATED_SERVER =
            "net.minecraft.server.integrated.IntegratedServer";
    private static final String METHOD_DESCRIPTOR =
            "(Ljava/lang/String;Ljava/lang/String;JLnet/minecraft/world/WorldType;Ljava/lang/String;)V";
    private static final String DEOBFUSCATED_METHOD = "loadAllWorlds";
    private static final String SRG_METHOD = "func_71247_a";
    private static final String DIMENSION_MANAGER =
            "net/minecraftforge/common/DimensionManager";
    private static final String REPLACEMENT_OWNER =
            "com/recipetree/jeiexport112/compat/ExportWorldStartupDimensions";
    private static final String ENUMERATION_METHOD = "getStaticDimensionIDs";
    private static final String ENUMERATION_DESCRIPTOR = "()[Ljava/lang/Integer;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        String expectedInternalName = expectedInternalName(transformedName);
        if (expectedInternalName == null) {
            return basicClass;
        }
        if (basicClass == null) {
            throw invalid(transformedName, "LaunchWrapper supplied null class bytes");
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        if (!expectedInternalName.equals(classNode.name)) {
            throw invalid(
                    transformedName,
                    "class bytes declare owner " + classNode.name + " instead of " + expectedInternalName
            );
        }

        List<MethodNode> targetMethods = findTargetMethods(classNode);
        if (targetMethods.size() != 1) {
            throw invalid(
                    transformedName,
                    "expected exactly one loadAllWorlds/func_71247_a method with descriptor " +
                            METHOD_DESCRIPTOR + " but found " + targetMethods.size()
            );
        }

        MethodNode method = targetMethods.get(0);
        List<MethodInsnNode> callSites = findEnumerationCalls(method);
        if (callSites.size() != 1) {
            throw invalid(
                    transformedName,
                    "expected exactly one INVOKESTATIC " + DIMENSION_MANAGER + "." +
                            ENUMERATION_METHOD + ENUMERATION_DESCRIPTOR + " inside " + method.name +
                            " but found " + callSites.size()
            );
        }

        MethodInsnNode call = callSites.get(0);
        call.owner = REPLACEMENT_OWNER;
        call.name = ENUMERATION_METHOD;
        call.desc = ENUMERATION_DESCRIPTOR;
        call.itf = false;

        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        System.out.println(
                "[jeiexport] Patched exact startup-dimension call site in " + transformedName + "." +
                        method.name + "; silent all-dimension fallback is disabled."
        );
        return writer.toByteArray();
    }

    private static String expectedInternalName(String transformedName) {
        if (MINECRAFT_SERVER.equals(transformedName) || INTEGRATED_SERVER.equals(transformedName)) {
            return transformedName.replace('.', '/');
        }
        return null;
    }

    private static List<MethodNode> findTargetMethods(ClassNode classNode) {
        List<MethodNode> matches = new ArrayList<MethodNode>(1);
        ListIterator<MethodNode> methods = classNode.methods.listIterator();
        while (methods.hasNext()) {
            MethodNode method = methods.next();
            if (METHOD_DESCRIPTOR.equals(method.desc) &&
                    (DEOBFUSCATED_METHOD.equals(method.name) || SRG_METHOD.equals(method.name))) {
                matches.add(method);
            }
        }
        return matches;
    }

    private static List<MethodInsnNode> findEnumerationCalls(MethodNode method) {
        List<MethodInsnNode> matches = new ArrayList<MethodInsnNode>(1);
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() == Opcodes.INVOKESTATIC &&
                    DIMENSION_MANAGER.equals(call.owner) &&
                    ENUMERATION_METHOD.equals(call.name) &&
                    ENUMERATION_DESCRIPTOR.equals(call.desc) &&
                    !call.itf) {
                matches.add(call);
            }
        }
        return matches;
    }

    private static IllegalStateException invalid(String owner, String detail) {
        return new IllegalStateException(
                "[jeiexport] World-start transform invariant failed for " + owner + ": " + detail +
                        ". Refusing partial activation or an all-dimension fallback."
        );
    }
}
