package com.recipetree.jeiexport112.compat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Runtime half of the exact TAACC 0.0.3 subtype repair. */
public final class TaaccAspectSubtypeGuard {
    private static final Logger LOGGER = LogManager.getLogger("jeiexport");
    private static final String EXPECTED_MC = "1.12.2";
    private static final String EXPECTED_FORGE = "14.23.5.2860";
    private static final String EXPECTED_TAACC = "0.0.3";
    private static final String EXPECTED_HEI = "4.25.0";
    private static final AtomicInteger NULL_COMPOUND_NORMALIZATIONS = new AtomicInteger();
    private static volatile boolean runtimeValidated;

    private TaaccAspectSubtypeGuard() {
    }

    /**
     * Same operand/return shape as NBTTagCompound.getString. Null is normalized to TAACC's
     * intended empty subtype; every present compound delegates to the native method unchanged.
     */
    public static String getStringOrEmpty(NBTTagCompound compound, String key) {
        ensureRuntimeValidated();
        return getStringOrEmptyAfterRuntimeValidation(compound, key);
    }

    static String getStringOrEmptyAfterRuntimeValidation(NBTTagCompound compound, String key) {
        if (!"Aspect".equals(key)) {
            throw new IllegalStateException(
                    "[jeiexport] TAACC compatibility guard received unexpected NBT key " + key +
                            "; expected exact key Aspect. Refusing semantic drift."
            );
        }
        if (compound != null) {
            return compound.getString(key);
        }

        int count = NULL_COMPOUND_NORMALIZATIONS.incrementAndGet();
        LOGGER.warn(
                "[jeiexport] TAACC missing-Aspect normalization #{}: subtype stack had no NBT " +
                        "compound; returning the plugin's intended empty subtype without mutating " +
                        "or fabricating NBT, ingredients, recipes, images, or export data.",
                count
        );
        return "";
    }

    /** Fail-closed readiness gate invoked before an export job can observe the HEI registry. */
    public static void assertReadyForExport() {
        if (!TaaccAspectSubtypeConfiguration.isEnabled()) {
            return;
        }
        TaaccAspectSubtypeTransformer.assertAppliedExactlyOnce();
        ensureRuntimeValidated();
        LOGGER.info(
                "[jeiexport] TAACC compatibility readiness gate passed: transformApplications=1, " +
                        "missingAspectNormalizations={}. " +
                        "Present compounds use native NBTTagCompound.getString(\"Aspect\") exactly; " +
                        "no fallback registry or fabricated data is permitted.",
                NULL_COMPOUND_NORMALIZATIONS.get()
        );
    }

    /** Final count/invariant gate after HEI traversal and before transactional publication. */
    public static void assertReadyForPublication() {
        if (!TaaccAspectSubtypeConfiguration.isEnabled()) {
            return;
        }
        TaaccAspectSubtypeTransformer.assertAppliedExactlyOnce();
        ensureRuntimeValidated();
        LOGGER.info(
                "[jeiexport] TAACC compatibility publication gate passed: " +
                        "transformApplications=1, finalMissingAspectNormalizations={}. " +
                        "Present compounds remained direct native getString delegates. No degraded-registry " +
                        "fallback or fabricated data was published.",
                NULL_COMPOUND_NORMALIZATIONS.get()
        );
    }

    static void validateRuntimeVersions(String minecraft, String forge, String taacc, String hei) {
        requireVersion("Minecraft", EXPECTED_MC, minecraft);
        requireVersion("Forge", EXPECTED_FORGE, forge);
        requireVersion("thaumicadditionsagricraftcompat", EXPECTED_TAACC, taacc);
        requireVersion("jei (HadEnoughItems)", EXPECTED_HEI, hei);
    }

    static int missingAspectNormalizationCount() {
        return NULL_COMPOUND_NORMALIZATIONS.get();
    }

    static void resetForTests() {
        NULL_COMPOUND_NORMALIZATIONS.set(0);
        runtimeValidated = false;
    }

    private static void ensureRuntimeValidated() {
        if (runtimeValidated) {
            return;
        }
        synchronized (TaaccAspectSubtypeGuard.class) {
            if (runtimeValidated) {
                return;
            }
            if (!TaaccAspectSubtypeConfiguration.isEnabled()) {
                throw new IllegalStateException(
                        "[jeiexport] Patched TAACC subtype call executed without exact property " +
                                TaaccAspectSubtypeConfiguration.ENABLE_PROPERTY + "=true."
                );
            }
            Map<String, ModContainer> mods = Loader.instance().getIndexedModList();
            validateRuntimeVersions(
                    Loader.instance().getMinecraftModContainer().getVersion(),
                    ForgeVersion.getVersion(),
                    version(mods, "thaumicadditionsagricraftcompat"),
                    version(mods, "jei")
            );
            runtimeValidated = true;
            LOGGER.info(
                    "[jeiexport] TAACC compatibility runtime identity validated exactly: " +
                            "Minecraft {}, Forge {}, TAACC {}, HEI {}.",
                    EXPECTED_MC, EXPECTED_FORGE, EXPECTED_TAACC, EXPECTED_HEI
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
                    "[jeiexport] TAACC compatibility runtime gate rejected " + component +
                            " version " + actual + "; expected exactly " + expected +
                            ". Refusing to export with unverified subtype semantics."
            );
        }
    }
}
