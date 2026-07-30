package com.recipetree.jeiexport112.compat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Runtime half of the exact Tinkers' Complement 1.12.2-0.4.3 JEI repair. */
public final class TinkersComplementFluidBlacklistGuard {
    private static final Logger LOGGER = LogManager.getLogger("jeiexport");
    private static final String EXPECTED_MC = "1.12.2";
    private static final String EXPECTED_FORGE = "14.23.5.2860";
    private static final String EXPECTED_TCOMPLEMENT = "1.12.2-0.4.3";
    private static final String EXPECTED_HEI = "4.25.0";
    private static final AtomicInteger SKIPPED_UNBOUND_FLUIDS = new AtomicInteger();
    private static volatile boolean runtimeValidated;

    private TinkersComplementFluidBlacklistGuard() {
    }

    /**
     * Returns true only when Tinkers' Complement's original blacklistFluid body can run.
     *
     * <p>Forge's FluidStack constructor accepts a late alternate fluid when its declared name is
     * registered, but FluidUtil.getFilledBucket later serializes the concrete fluid through
     * FluidRegistry.getFluidName. That inverse lookup can be null. The native code then passes the
     * null value to NBTTagCompound.setString and aborts the entire HEI plugin registration. We skip
     * only that impossible entry; every fluid with a non-null inverse registry name executes the
     * original method body unchanged.</p>
     */
    public static boolean shouldRunNativeBlacklist(Fluid fluid) {
        ensureRuntimeValidated();
        if (fluid == null) {
            return shouldRunNativeBlacklistAfterRuntimeValidation(null, null, null);
        }

        // Reproduce FluidStack's exact delegate resolution without mutating a registry or HEI.
        // Constructor/delegate failures deliberately propagate just as the native method would.
        FluidStack probe = new FluidStack(fluid, Fluid.BUCKET_VOLUME);
        Fluid resolvedFluid = probe.getFluid();
        return shouldRunNativeBlacklistAfterRuntimeValidation(
                fluid, resolvedFluid, FluidRegistry.getFluidName(resolvedFluid)
        );
    }

    static boolean shouldRunNativeBlacklistAfterRuntimeValidation(
            Fluid suppliedFluid, Fluid resolvedFluid, String inverseRegistryName
    ) {
        if (suppliedFluid != null && resolvedFluid != null && inverseRegistryName != null) {
            return true;
        }
        if ((suppliedFluid == null || resolvedFluid == null) && inverseRegistryName != null) {
            throw new IllegalStateException(
                    "[jeiexport] Tinkers' Complement fluid blacklist guard received an impossible " +
                            "test/runtime state: null supplied/resolved Fluid with non-null " +
                            "inverse registry name " + inverseRegistryName + "."
            );
        }

        int count = SKIPPED_UNBOUND_FLUIDS.incrementAndGet();
        String declaredName = suppliedFluid == null
                ? "<null-fluid-object>" : String.valueOf(suppliedFluid.getName());
        String suppliedClass = suppliedFluid == null
                ? "<null-fluid-object>" : suppliedFluid.getClass().getName();
        String resolvedName = resolvedFluid == null
                ? "<null-resolved-fluid>" : String.valueOf(resolvedFluid.getName());
        String resolvedClass = resolvedFluid == null
                ? "<null-resolved-fluid>" : resolvedFluid.getClass().getName();
        LOGGER.warn(
                "[jeiexport] Tinkers' Complement impossible fluid blacklist skip #{}: " +
                        "JEIPlugin.blacklistFluid supplied fluid={} (class {}), FluidStack delegate " +
                        "resolved fluid={} (class {}), and " +
                        "FluidRegistry.getFluidName(resolvedFluid) returned null. No FluidStack, " +
                        "bucket, or ingredient is submitted to the HEI blacklist; plugin " +
                        "registration continues without fabricated data.",
                count, declaredName, suppliedClass, resolvedName, resolvedClass
        );
        return false;
    }

    /** Fail-closed readiness gate invoked before an export job can observe the HEI registry. */
    public static void assertReadyForExport() {
        if (!TinkersComplementFluidBlacklistConfiguration.isEnabled()) {
            return;
        }
        TinkersComplementFluidBlacklistTransformer.assertAppliedExactlyOnce();
        ensureRuntimeValidated();
        LOGGER.info(
                "[jeiexport] Tinkers' Complement compatibility readiness gate passed: " +
                        "transformApplications=1, skippedUnboundBlacklistFluids={}. Every fluid " +
                        "with a non-null inverse registry name retained the original blacklistFluid " +
                        "implementation; no fallback ingredient or bucket is permitted.",
                SKIPPED_UNBOUND_FLUIDS.get()
        );
    }

    /** Final count/invariant gate after HEI traversal and before transactional publication. */
    public static void assertReadyForPublication() {
        if (!TinkersComplementFluidBlacklistConfiguration.isEnabled()) {
            return;
        }
        TinkersComplementFluidBlacklistTransformer.assertAppliedExactlyOnce();
        ensureRuntimeValidated();
        LOGGER.info(
                "[jeiexport] Tinkers' Complement compatibility publication gate passed: " +
                        "transformApplications=1, finalSkippedUnboundBlacklistFluids={}. No " +
                        "malformed FluidStack, fabricated bucket, fallback ingredient, or degraded " +
                        "plugin-registration path was published.",
                SKIPPED_UNBOUND_FLUIDS.get()
        );
    }

    static void validateRuntimeVersions(
            String minecraft, String forge, String tcomplement, String hei
    ) {
        requireVersion("Minecraft", EXPECTED_MC, minecraft);
        requireVersion("Forge", EXPECTED_FORGE, forge);
        requireVersion("tcomplement", EXPECTED_TCOMPLEMENT, tcomplement);
        requireVersion("jei (HadEnoughItems)", EXPECTED_HEI, hei);
    }

    static int skippedUnboundFluidCount() {
        return SKIPPED_UNBOUND_FLUIDS.get();
    }

    static void resetForTests() {
        SKIPPED_UNBOUND_FLUIDS.set(0);
        runtimeValidated = false;
    }

    private static void ensureRuntimeValidated() {
        if (runtimeValidated) {
            return;
        }
        synchronized (TinkersComplementFluidBlacklistGuard.class) {
            if (runtimeValidated) {
                return;
            }
            if (!TinkersComplementFluidBlacklistConfiguration.isEnabled()) {
                throw new IllegalStateException(
                        "[jeiexport] Patched Tinkers' Complement blacklist call executed without " +
                                "exact property " +
                                TinkersComplementFluidBlacklistConfiguration.ENABLE_PROPERTY +
                                "=true."
                );
            }
            Map<String, ModContainer> mods = Loader.instance().getIndexedModList();
            validateRuntimeVersions(
                    Loader.instance().getMinecraftModContainer().getVersion(),
                    ForgeVersion.getVersion(),
                    version(mods, "tcomplement"),
                    version(mods, "jei")
            );
            runtimeValidated = true;
            LOGGER.info(
                    "[jeiexport] Tinkers' Complement compatibility runtime identity validated " +
                            "exactly: Minecraft {}, Forge {}, Tinkers' Complement {}, HEI {}.",
                    EXPECTED_MC, EXPECTED_FORGE, EXPECTED_TCOMPLEMENT, EXPECTED_HEI
            );
        }
    }

    private static String version(Map<String, ModContainer> mods, String modId) {
        ModContainer container = mods.get(modId);
        return container == null ? "<missing>" : container.getVersion();
    }

    private static void requireVersion(String component, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "[jeiexport] Tinkers' Complement compatibility runtime gate rejected " +
                            component + " version " + actual + "; expected exactly " + expected +
                            ". Refusing to export with unverified HEI blacklist semantics."
            );
        }
    }
}
