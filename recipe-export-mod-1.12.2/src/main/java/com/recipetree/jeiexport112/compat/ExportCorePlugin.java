package com.recipetree.jeiexport112.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("Recipe Tree exporter compatibility guard")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
public final class ExportCorePlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        List<String> transformers = new ArrayList<String>(4);
        if (ExportGraphicsConfiguration.isEnabled()) {
            System.out.println(
                    "[jeiexport] Export graphics guard enabled: stencil requests are disabled; GLAllocation is replaced only after exact owner/class-version/method/access/opcode/call validation, accepting and logging only the coherent audited-development or Forge-runtime-SRG GlStateManager call-name pair; display-list recovery is limited to the observed glGenLists(1)=0, GL-error-0, Display-not-current signature; same-Display reacquisition additionally requires the launcher policy, exact Forge 14.23.5.2860 SplashProgress.class SHA-256, and uninitialized private state d=null/thread=null, while active-splash pause/resume verifies both drawable ownership postconditions; both recovery paths perform an explicitly logged bounded drain of the validated current context's preexisting GL errors immediately before their sole allocation retry and require the immediate post-retry error sample to be zero; Minecraft's exact Forge SplashProgress maximum-texture delegation/cache is preserved and its returned integer is validated without an exporter GL query or context operation; multiblocked-0.8.0's eager built-in shader bootstrap is disabled while deferred shader calls retain the validated fail-closed OpenGlHelper bridge; and RandomPatches cosmetic title/icon reload is deferred past coremod initialization."
            );
            transformers.add(ExportGraphicsTransformer.class.getName());
        }

        if (TaaccAspectSubtypeConfiguration.isEnabled()) {
            System.out.println(
                    "[jeiexport] Exact TAACC 0.0.3 missing-Aspect compatibility repair enabled; " +
                            "class/method/opcode drift is fail-closed and every null-compound " +
                            "normalization is counted and logged."
            );
            transformers.add(TaaccAspectSubtypeTransformer.class.getName());
        }

        if (TinkersComplementFluidBlacklistConfiguration.isEnabled()) {
            System.out.println(
                    "[jeiexport] Exact Tinkers' Complement 1.12.2-0.4.3 unbound-fluid " +
                            "blacklist repair enabled; artifact/class/method/opcode drift is " +
                            "fail-closed and every impossible registry-name skip is counted and logged."
            );
            transformers.add(TinkersComplementFluidBlacklistTransformer.class.getName());
        }

        if (WorldStartupConfiguration.isEnabled()) {
            System.out.println(
                    "[jeiexport] Export world-start optimization enabled: MinecraftServer and IntegratedServer startup dimension enumeration will use dimension 0 plus registered DimensionTypes whose shouldLoadSpawn policy is true."
            );
            transformers.add(ExportWorldStartupTransformer.class.getName());
        }

        if (transformers.isEmpty()) {
            System.out.println(
                    "[jeiexport] Generic JEI/HEI compatibility mode active: no optional " +
                            "bytecode transformers were requested; pack classes remain untouched."
            );
        }
        return transformers.toArray(new String[transformers.size()]);
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // No runtime data is needed; the launch property is intentionally explicit.
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
