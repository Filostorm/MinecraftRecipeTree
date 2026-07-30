package com.recipetree.jeiexport112.compat;

import net.minecraft.world.DimensionType;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLLog;

/** Forge-facing replacement for the two exact server-startup dimension enumerations. */
public final class ExportWorldStartupDimensions {
    private static volatile SelectionSnapshot lastSelection;

    private ExportWorldStartupDimensions() {
    }

    /**
     * Has the same descriptor as DimensionManager.getStaticDimensionIDs so the call-site patch
     * does not change stack-map frames. This method is never called when the property is disabled.
     */
    public static Integer[] getStaticDimensionIDs() {
        if (!WorldStartupConfiguration.isEnabled()) {
            throw new IllegalStateException(
                    "[jeiexport] A patched world-start call site ran while " +
                            WorldStartupConfiguration.ENABLE_PROPERTY +
                            " is disabled; refusing inconsistent partial activation."
            );
        }

        Integer[] original = DimensionManager.getStaticDimensionIDs();
        WorldStartupDimensionPolicy.Selection selection = WorldStartupDimensionPolicy.select(
                original,
                new WorldStartupDimensionPolicy.LoadSpawnLookup() {
                    @Override
                    public boolean shouldLoadSpawn(int dimensionId) {
                        if (!DimensionManager.isDimensionRegistered(dimensionId)) {
                            throw new IllegalStateException(
                                    "[jeiexport] Static dimension ID " + dimensionId +
                                            " is not registered; refusing world-start optimization."
                            );
                        }
                        DimensionType type = DimensionManager.getProviderType(dimensionId);
                        if (type == null) {
                            throw new IllegalStateException(
                                    "[jeiexport] Registered dimension ID " + dimensionId +
                                            " has a null DimensionType; refusing world-start optimization."
                            );
                        }
                        return type.shouldLoadSpawn();
                    }
                }
        );

        SelectionSnapshot snapshot = new SelectionSnapshot(selection);
        lastSelection = snapshot;
        FMLLog.log.info(
                "[jeiexport] World-start dimension policy {}: original={} selected={} skipped={}",
                WorldStartupDimensionPolicy.POLICY_NAME,
                selection.originalIds(),
                selection.selectedIds(),
                selection.skippedIds()
        );
        return selection.selectedCopy();
    }

    public static boolean isEnabled() {
        return WorldStartupConfiguration.isEnabled();
    }

    public static String policyName() {
        return WorldStartupDimensionPolicy.POLICY_NAME;
    }

    public static SelectionSnapshot lastSelection() {
        return lastSelection;
    }

    public static final class SelectionSnapshot {
        private final int originalCount;
        private final int selectedCount;
        private final int skippedCount;

        private SelectionSnapshot(WorldStartupDimensionPolicy.Selection selection) {
            originalCount = selection.originalCount();
            selectedCount = selection.selectedCount();
            skippedCount = selection.skippedCount();
        }

        public int originalCount() {
            return originalCount;
        }

        public int selectedCount() {
            return selectedCount;
        }

        public int skippedCount() {
            return skippedCount;
        }
    }
}
