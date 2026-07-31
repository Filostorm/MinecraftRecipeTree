package com.recipetree.reiexport118.mixin;

import com.mojang.logging.LogUtils;
import com.recipetree.reiexport118.compat.LowDragFboViewportContract;
import com.recipetree.reiexport118.compat.KubeJsTooltipConcurrencyContract;
import com.recipetree.reiexport118.compat.IndustrialForegoingOreTagOrderContract;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2BlockAtlasCanonicalizationContract;
import com.recipetree.reiexport118.compat.Mm2ExportRequestScope;
import com.recipetree.reiexport118.compat.Mm2OffscreenGlintClockContract;
import com.recipetree.reiexport118.compat.Mm2LightmapReadinessContract;
import com.recipetree.reiexport118.compat.Mm2SpiritShaderGameTimeContract;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Applies compatibility mixins only to their byte-for-byte audited target classes. */
public final class ReiExportMixinConfigPlugin implements IMixinConfigPlugin {
    private record AuditedTarget(
            String className,
            String resource,
            String sha256,
            boolean requiredTarget,
            String resourceStage
    ) {
        private AuditedTarget(
                String className,
                String resource,
                String sha256,
                boolean requiredTarget
        ) {
            this(className, resource, sha256, requiredTarget, null);
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Path runtimeExactMm2GameDirectory;
    private static final ExactRuntimeSelection RELICS_STAT_RANDOM_RUNTIME_SELECTION =
            new ExactRuntimeSelection(
                    "Exact pinned Relics concrete randomizeStat override selection");
    private static final ExactRuntimeSelection PROJECT_RED_REGISTRATION_RUNTIME_SELECTION =
            new ExactRuntimeSelection(
                    "Exact pinned ProjectRed Integration fabricated-gate registration selection");

    private final Path gameDirectory;
    private final boolean publishRuntimeSelection;
    private Mm2ExportRequestScope.Inspection requestScope;
    private boolean disabledLogged;

    private static final String LOW_DRAG_FBO_MIXIN =
            "com.recipetree.reiexport118.mixin.LowDragFboViewportMixin";
    private static final String LOW_DRAG_IMMEDIATE_MIXIN =
            "com.recipetree.reiexport118.mixin.LowDragImmediateViewportMixin";
    private static final String LOW_DRAG_IMMEDIATE_RECT_MIXIN =
            "com.recipetree.reiexport118.mixin.LowDragImmediateRectMixin";
    private static final String LOW_DRAG_SCISSOR_MIXIN =
            "com.recipetree.reiexport118.mixin.LowDragScissorMixin";
    private static final String KUBEJS_TOOLTIP_MIXIN =
            "com.recipetree.reiexport118.mixin.KubeJsClientTooltipMixin";
    private static final String KUBEJS_RELOAD_MIXIN =
            "com.recipetree.reiexport118.mixin.KubeJsClientReloadMixin";
    private static final String AE2_COLOR_APPLICATOR_MIXIN =
            "com.recipetree.reiexport118.mixin.Ae2ColorApplicatorCreativeMixin";
    private static final String TOMBSTONE_FAMILIAR_MIXIN =
            "com.recipetree.reiexport118.mixin.TombstoneFamiliarCreativeMixin";
    private static final String IF_BACKPACK_NBT_MIXIN =
            "com.recipetree.reiexport118.mixin.IndustrialForegoingBackpackNbtMixin";
    private static final String IF_ORE_TAG_ORDER_MIXIN =
            "com.recipetree.reiexport118.mixin.IndustrialForegoingOreTagOrderMixin";
    private static final String SPIRIT_JEI_ENTITY_RENDERER_MIXIN =
            "com.recipetree.reiexport118.mixin.SpiritJeiEntityRendererMixin";
    private static final String RELICS_STAT_RANDOM_MIXIN =
            "com.recipetree.reiexport118.mixin.RelicsStatRandomMixin";
    private static final String BOTANIA_TWIG_WAND_MIXIN =
            "com.recipetree.reiexport118.mixin.BotaniaTwigWandCreativeMixin";
    private static final String BUFFER_UPLOADER_SPIRIT_SHADER_CLOCK_MIXIN =
            "com.recipetree.reiexport118.mixin.BufferUploaderSpiritShaderClockMixin";
    private static final String ELEMENTAL_ITEMS_TAGS_MIXIN =
            "com.recipetree.reiexport118.mixin.ElementalCraftItemsTagMixin";
    private static final String ELEMENTAL_PURE_ORE_LOADER_MIXIN =
            "com.recipetree.reiexport118.mixin.ElementalCraftPureOreLoaderMixin";
    private static final String ELEMENTAL_PURE_ORE_MANAGER_MIXIN =
            "com.recipetree.reiexport118.mixin.ElementalCraftPureOreManagerMixin";
    private static final String IMMERSIVE_ENGINEERING_TAG_CACHE_MIXIN =
            "com.recipetree.reiexport118.mixin.ImmersiveEngineeringTagCacheMixin";
    private static final String IMMERSIVE_ENGINEERING_POTION_BUCKET_ORDER_MIXIN =
            "com.recipetree.reiexport118.mixin.ImmersiveEngineeringPotionBucketOrderMixin";
    private static final String LOW_DRAG_CYCLE_ITEM_STACK_HANDLER_MIXIN =
            "com.recipetree.reiexport118.mixin.LowDragCycleItemStackHandlerMixin";
    private static final String LOW_DRAG_PROGRESS_WIDGET_MIXIN =
            "com.recipetree.reiexport118.mixin.LowDragProgressWidgetMixin";
    private static final String MULTIBLOCKED_CYCLE_BLOCK_STATE_RENDERER_MIXIN =
            "com.recipetree.reiexport118.mixin.MultiblockedCycleBlockStateRendererMixin";
    private static final String CREATE_ANIMATION_TICK_HOLDER_MIXIN =
            "com.recipetree.reiexport118.mixin.CreateAnimationTickHolderMixin";
    private static final String JEI_COMPAT_TICK_TIMER_MIXIN =
            "com.recipetree.reiexport118.mixin.JeiCompatTickTimerMixin";
    private static final String REI_NATIVE_RECIPE_RELOAD_MIXIN =
            "com.recipetree.reiexport118.mixin.ReiNativeRecipeReloadMixin";
    private static final String REI_RELOAD_LIFECYCLE_MIXIN =
            "com.recipetree.reiexport118.mixin.ReiReloadLifecycleMixin";
    private static final String REI_PLUGIN_ERROR_LEDGER_MIXIN =
            "com.recipetree.reiexport118.mixin.ReiPluginErrorLedgerMixin";
    private static final String JEI_PLUGIN_DETECTOR_TYPE_CACHE_MIXIN =
            "com.recipetree.reiexport118.mixin.JeiPluginDetectorTypeCacheMixin";
    private static final String JEI_PLUGIN_WRAPPER_DEFERRED_TASKS_MIXIN =
            "com.recipetree.reiexport118.mixin.JeiPluginWrapperDeferredTasksMixin";
    private static final String JEI_RECIPE_REGISTRATION_PIGMENT_MIXIN =
            "com.recipetree.reiexport118.mixin.JeiRecipeRegistrationPigmentMixin";
    private static final String PROJECT_RED_INTEGRATION_PARTS_MIXIN =
            "com.recipetree.reiexport118.mixin.ProjectRedIntegrationPartsMixin";
    private static final String TEXTURE_ATLAS_ANIMATION_MIXIN =
            "com.recipetree.reiexport118.mixin.TextureAtlasAnimationMixin";
    private static final String TEXTURE_ATLAS_SPRITES_ACCESSOR =
            "com.recipetree.reiexport118.mixin.TextureAtlasSpritesAccessor";
    private static final String RENDER_STATE_SHARD_GLINT_CLOCK_MIXIN =
            "com.recipetree.reiexport118.mixin.RenderStateShardGlintClockMixin";
    private static final String LIGHT_TEXTURE_PIXELS_ACCESSOR =
            "com.recipetree.reiexport118.mixin.LightTexturePixelsAccessor";
    private static final String LIGHT_TEXTURE_UPDATE_MIXIN =
            "com.recipetree.reiexport118.mixin.LightTextureUpdateMixin";

    /** Mixin's runtime entry point; Forge has resolved the authoritative game path by this phase. */
    public ReiExportMixinConfigPlugin() {
        this(requireForgeGameDirectory(), true);
    }

    /** Explicit path seam for deterministic contract tests; it never publishes runtime state. */
    public ReiExportMixinConfigPlugin(Path gameDirectory) {
        this(gameDirectory, false);
    }

    private ReiExportMixinConfigPlugin(Path gameDirectory, boolean publishRuntimeSelection) {
        if (gameDirectory == null) {
            throw new IllegalStateException(
                    "Forge game directory is unavailable during MM2 mixin request scoping");
        }
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.publishRuntimeSelection = publishRuntimeSelection;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        Mm2ExportRequestScope.Inspection scope = requestScope();
        if (!scope.isExactMm2()) {
            if (!disabledLogged) {
                disabledLogged = true;
                LOGGER.info(
                        "[reiexport] Exact MM2 compatibility mixins DISABLED: no exporter "
                                + "request exists at {}; target bytecode will not be inspected "
                                + "and no gameplay, creative, lifecycle, or rendering repair "
                                + "mixin will be applied",
                        scope.requestPath());
            }
            return false;
        }
        AuditedTarget target = auditedTarget(mixinClassName);
        if (target == null) {
            return true;
        }
        if (!target.className().equals(targetClassName)) {
            throw new IllegalStateException("Audited compatibility mixin target drift: expected="
                    + target.className() + ", actual=" + targetClassName);
        }
        try (InputStream input = ReiExportMixinConfigPlugin.class.getClassLoader()
                .getResourceAsStream(target.resource())) {
            if (input == null) {
                if (target.requiredTarget()) {
                    throw new IllegalStateException(
                            "Required audited mixin target class resource is absent: "
                                    + target.resource());
                }
                LOGGER.info(
                        "[reiexport] Exact audited compatibility mixin is explicitly not "
                                + "applicable: optional audited target class resource is absent ({})",
                        target.resource());
                return false;
            }
            String actual = sha256(input);
            if (!target.sha256().equals(actual)) {
                throw new IllegalStateException(
                        "Audited MM2 mixin target classpath-resource drift: resource="
                                + target.resource() + ", expectedSha256=" + target.sha256()
                                + ", actualSha256=" + actual + describeResourceStage(target)
                );
            }
            if (target.resourceStage() != null) {
                LOGGER.info(
                        "[reiexport] Accepted exact audited MM2 mixin target: mixin={}, "
                                + "resource={}, resourceStage={}, sha256={}",
                        mixinClassName, target.resource(), target.resourceStage(), actual);
            }
            publishAuditedRuntimeTargetSelection(mixinClassName);
            return true;
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Inspecting an audited target for mixin application failed",
                    exception
            );
        }
    }

    /** Ensures an exact request was present when Mixin made its irreversible apply decisions. */
    public static void requireExactMm2RequestSelection(Path gameDirectory) {
        Path expected = gameDirectory.toAbsolutePath().normalize();
        Path selected = runtimeExactMm2GameDirectory;
        if (!expected.equals(selected)) {
            throw new IllegalStateException(
                    "Exact MM2 export request reached FML setup without an exact early mixin "
                            + "request-scope selection: expectedGameDirectory=" + expected
                            + ", selectedGameDirectory=" + selected);
        }
    }

    /** Required by Relics stack construction, which can precede the Forge mod constructor. */
    public static void requireExactRelicsStatRandomSelection(Path gameDirectory) {
        RELICS_STAT_RANDOM_RUNTIME_SELECTION.require(gameDirectory);
    }

    /** Required during parallel ProjectRed mod construction, before the normal MM2 arm. */
    public static void requireExactProjectRedRegistrationSelection(Path gameDirectory) {
        PROJECT_RED_REGISTRATION_RUNTIME_SELECTION.require(gameDirectory);
    }

    private void publishAuditedRuntimeTargetSelection(String mixinClassName) {
        if (publishRuntimeSelection && RELICS_STAT_RANDOM_MIXIN.equals(mixinClassName)
                && RELICS_STAT_RANDOM_RUNTIME_SELECTION.publish(gameDirectory)) {
            LOGGER.info(
                    "[reiexport] Published exact pinned Relics concrete randomizeStat Mixin "
                            + "override selection "
                    + "before parallel Forge mod construction");
        }
        if (publishRuntimeSelection && PROJECT_RED_INTEGRATION_PARTS_MIXIN.equals(mixinClassName)
                && PROJECT_RED_REGISTRATION_RUNTIME_SELECTION.publish(gameDirectory)) {
            LOGGER.info(
                    "[reiexport] Published exact pinned ProjectRed Integration registration "
                            + "Mixin selection before parallel Forge mod construction");
        }
    }

    private Mm2ExportRequestScope.Inspection requestScope() {
        Mm2ExportRequestScope.Inspection current = requestScope;
        if (current != null) {
            return current;
        }
        current = Mm2ExportRequestScope.inspect(gameDirectory);
        requestScope = current;
        if (publishRuntimeSelection && current.isExactMm2()) {
            synchronized (ReiExportMixinConfigPlugin.class) {
                Path prior = runtimeExactMm2GameDirectory;
                if (prior != null && !prior.equals(gameDirectory)) {
                    throw new IllegalStateException(
                            "MM2 mixin request scope changed between plugin instances: prior="
                                    + prior + ", current=" + gameDirectory);
                }
                runtimeExactMm2GameDirectory = gameDirectory;
            }
        }
        return current;
    }

    private static Path requireForgeGameDirectory() {
        Path gameDirectory = FMLLoader.getGamePath();
        if (gameDirectory == null) {
            throw new IllegalStateException(
                    "Forge did not resolve its authoritative game directory before MM2 mixin "
                            + "request scoping; refusing an ambiguous compatibility decision");
        }
        return gameDirectory;
    }

    private static AuditedTarget auditedTarget(String mixinClassName) {
        return switch (mixinClassName) {
            case LOW_DRAG_FBO_MIXIN -> new AuditedTarget(
                    LowDragFboViewportContract.FBO_RENDERER_CLASS,
                    LowDragFboViewportContract.FBO_RENDERER_RESOURCE,
                    LowDragFboViewportContract.FBO_RENDERER_SHA256,
                    false);
            case LOW_DRAG_IMMEDIATE_MIXIN -> new AuditedTarget(
                    LowDragFboViewportContract.WORLD_RENDERER_CLASS,
                    LowDragFboViewportContract.WORLD_RENDERER_RESOURCE,
                    LowDragFboViewportContract.WORLD_RENDERER_SHA256,
                    false);
            case LOW_DRAG_IMMEDIATE_RECT_MIXIN -> new AuditedTarget(
                    LowDragFboViewportContract.IMMEDIATE_RENDERER_CLASS,
                    LowDragFboViewportContract.IMMEDIATE_RENDERER_RESOURCE,
                    LowDragFboViewportContract.IMMEDIATE_RENDERER_SHA256,
                    false);
            case LOW_DRAG_SCISSOR_MIXIN -> new AuditedTarget(
                    LowDragFboViewportContract.RENDER_UTILS_CLASS,
                    LowDragFboViewportContract.RENDER_UTILS_RESOURCE,
                    LowDragFboViewportContract.RENDER_UTILS_SHA256,
                    false);
            case KUBEJS_TOOLTIP_MIXIN -> new AuditedTarget(
                    KubeJsTooltipConcurrencyContract.TARGET_CLASS,
                    KubeJsTooltipConcurrencyContract.TARGET_RESOURCE,
                    KubeJsTooltipConcurrencyContract.TARGET_SHA256,
                    true);
            case KUBEJS_RELOAD_MIXIN -> new AuditedTarget(
                    KubeJsTooltipConcurrencyContract.RELOAD_TARGET_CLASS,
                    KubeJsTooltipConcurrencyContract.RELOAD_TARGET_RESOURCE,
                    KubeJsTooltipConcurrencyContract.RELOAD_TARGET_SHA256,
                    true);
            case AE2_COLOR_APPLICATOR_MIXIN -> new AuditedTarget(
                    Mm2DeterminismContract.AE2_COLOR_APPLICATOR.className(),
                    Mm2DeterminismContract.AE2_COLOR_APPLICATOR.resource(),
                    Mm2DeterminismContract.AE2_COLOR_APPLICATOR.sha256(),
                    true);
            case TOMBSTONE_FAMILIAR_MIXIN -> new AuditedTarget(
                    Mm2DeterminismContract.TOMBSTONE_RECEPTACLE.className(),
                    Mm2DeterminismContract.TOMBSTONE_RECEPTACLE.resource(),
                    Mm2DeterminismContract.TOMBSTONE_RECEPTACLE.sha256(),
                    true);
            case IF_BACKPACK_NBT_MIXIN -> new AuditedTarget(
                    Mm2DeterminismContract.INFINITY_BACKPACK.className(),
                    Mm2DeterminismContract.INFINITY_BACKPACK.resource(),
                    Mm2DeterminismContract.INFINITY_BACKPACK.sha256(),
                    true);
            case IF_ORE_TAG_ORDER_MIXIN -> new AuditedTarget(
                    IndustrialForegoingOreTagOrderContract.TARGET_CLASS,
                    IndustrialForegoingOreTagOrderContract.TARGET_RESOURCE,
                    IndustrialForegoingOreTagOrderContract.TARGET_CLASS_SHA256,
                    true);
            case SPIRIT_JEI_ENTITY_RENDERER_MIXIN -> audited(
                    Mm2DeterminismContract.SPIRIT_JEI_ENTITY_RENDERER);
            case RELICS_STAT_RANDOM_MIXIN -> audited(
                    Mm2DeterminismContract.RELIC_ITEM);
            case BOTANIA_TWIG_WAND_MIXIN -> audited(
                    Mm2DeterminismContract.BOTANIA_TWIG_WAND);
            case ELEMENTAL_ITEMS_TAGS_MIXIN -> audited(
                    Mm2DeterminismContract.ELEMENTAL_ITEMS_TAGS);
            case ELEMENTAL_PURE_ORE_LOADER_MIXIN -> audited(
                    Mm2DeterminismContract.ELEMENTAL_PURE_ORE_LOADER);
            case ELEMENTAL_PURE_ORE_MANAGER_MIXIN -> audited(
                    Mm2DeterminismContract.ELEMENTAL_PURE_ORE_MANAGER);
            case IMMERSIVE_ENGINEERING_TAG_CACHE_MIXIN -> audited(
                    Mm2DeterminismContract.IMMERSIVE_ENGINEERING_IE_API);
            case IMMERSIVE_ENGINEERING_POTION_BUCKET_ORDER_MIXIN -> audited(
                    Mm2DeterminismContract.IMMERSIVE_ENGINEERING_POTION_BUCKET);
            case LOW_DRAG_CYCLE_ITEM_STACK_HANDLER_MIXIN -> audited(
                    Mm2DeterminismContract.LOW_DRAG_CYCLE_ITEM_STACK_HANDLER);
            case LOW_DRAG_PROGRESS_WIDGET_MIXIN -> audited(
                    Mm2DeterminismContract.LOW_DRAG_PROGRESS_WIDGET);
            case MULTIBLOCKED_CYCLE_BLOCK_STATE_RENDERER_MIXIN -> audited(
                    Mm2DeterminismContract.MULTIBLOCKED_CYCLE_BLOCK_STATE_RENDERER);
            case CREATE_ANIMATION_TICK_HOLDER_MIXIN -> audited(
                    Mm2DeterminismContract.CREATE_ANIMATION_TICK_HOLDER);
            case JEI_COMPAT_TICK_TIMER_MIXIN -> audited(
                    Mm2DeterminismContract.JEI_GUI_HELPER_TICK_TIMER);
            case REI_NATIVE_RECIPE_RELOAD_MIXIN -> audited(
                    Mm2DeterminismContract.REI_CORE_CLIENT);
            case REI_RELOAD_LIFECYCLE_MIXIN -> audited(
                    Mm2DeterminismContract.REI_RELOAD_MANAGER);
            case REI_PLUGIN_ERROR_LEDGER_MIXIN -> audited(
                    Mm2DeterminismContract.REI_PLUGIN_MANAGER);
            case JEI_PLUGIN_DETECTOR_TYPE_CACHE_MIXIN -> audited(
                    Mm2DeterminismContract.JEI_PLUGIN_DETECTOR);
            case JEI_PLUGIN_WRAPPER_DEFERRED_TASKS_MIXIN -> audited(
                    Mm2DeterminismContract.JEI_PLUGIN_WRAPPER);
            case JEI_RECIPE_REGISTRATION_PIGMENT_MIXIN -> audited(
                    Mm2DeterminismContract.JEI_RECIPE_REGISTRATION);
            case PROJECT_RED_INTEGRATION_PARTS_MIXIN -> audited(
                    Mm2DeterminismContract.PROJECT_RED_INTEGRATION_PARTS);
            case TEXTURE_ATLAS_ANIMATION_MIXIN, TEXTURE_ATLAS_SPRITES_ACCESSOR ->
                    auditedCore(Mm2BlockAtlasCanonicalizationContract.TEXTURE_ATLAS);
            case RENDER_STATE_SHARD_GLINT_CLOCK_MIXIN -> {
                Mm2OffscreenGlintClockContract.CoreClassPin pin =
                        Mm2OffscreenGlintClockContract.RENDER_STATE_SHARD;
                yield new AuditedTarget(
                        pin.className(),
                        pin.resource(),
                        pin.sha256(),
                        true,
                        pin.resourceStage());
            }
            case BUFFER_UPLOADER_SPIRIT_SHADER_CLOCK_MIXIN -> {
                Mm2SpiritShaderGameTimeContract.CoreClassPin pin =
                        Mm2SpiritShaderGameTimeContract.BUFFER_UPLOADER;
                yield new AuditedTarget(
                        pin.className(),
                        pin.resource(),
                        pin.sha256(),
                        true,
                        pin.resourceStage());
            }
            case LIGHT_TEXTURE_PIXELS_ACCESSOR, LIGHT_TEXTURE_UPDATE_MIXIN ->
                    new AuditedTarget(
                            Mm2LightmapReadinessContract.LIGHT_TEXTURE_CLASS,
                            Mm2LightmapReadinessContract.LIGHT_TEXTURE_RESOURCE,
                            Mm2LightmapReadinessContract.LIGHT_TEXTURE_SHA256,
                            true,
                            Mm2LightmapReadinessContract.PRODUCTION_RESOURCE_STAGE);
            default -> null;
        };
    }

    private static AuditedTarget audited(Mm2DeterminismContract.ClassPin pin) {
        return new AuditedTarget(pin.className(), pin.resource(), pin.sha256(), true);
    }

    private static AuditedTarget auditedCore(
            Mm2BlockAtlasCanonicalizationContract.CoreClassPin pin
    ) {
        return new AuditedTarget(
                pin.className(),
                pin.resource(),
                pin.sha256(),
                true,
                Mm2BlockAtlasCanonicalizationContract.PRODUCTION_RESOURCE_STAGE);
    }

    private static String describeResourceStage(AuditedTarget target) {
        return target.resourceStage() == null
                ? ""
                : ", resourceStage=" + target.resourceStage();
    }

    private static String sha256(InputStream input) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
    }
}
