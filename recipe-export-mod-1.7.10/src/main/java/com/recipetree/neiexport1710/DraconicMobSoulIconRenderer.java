package com.recipetree.neiexport1710;

import net.minecraft.client.gui.Gui;

/** Deterministic catalog glyph for Draconic Evolution's entity-backed Mob Soul item. */
final class DraconicMobSoulIconRenderer {
    static final String CONTRACT =
            "draconic-evolution-1.4.27-mob-soul-semantic-glyph-v1";
    static final int EXPECTED_CATALOG_IDENTITIES = 363;
    static final int EXPECTED_MOBSINFO_IDENTITIES = 362;
    private static final String REGISTRY_ID = "DraconicEvolution:mobSoul";
    private static final String ITEM_CLASS =
            "com.brandon3055.draconicevolution.common.items.MobSoul";

    private DraconicMobSoulIconRenderer() {}

    static void requireCompleteCoverage(
            int catalogIdentities, int mobsInfoIdentities) throws ExportFailure {
        if (catalogIdentities != EXPECTED_CATALOG_IDENTITIES
                || mobsInfoIdentities != EXPECTED_MOBSINFO_IDENTITIES) {
            throw new ExportFailure("HANDLER_UNLOADED",
                    "Draconic Mob Soul coverage drifted; expected catalog="
                            + EXPECTED_CATALOG_IDENTITIES + ", MobsInfo="
                            + EXPECTED_MOBSINFO_IDENTITIES + ", observed catalog="
                            + catalogIdentities + ", MobsInfo=" + mobsInfoIdentities);
        }
    }

    static boolean isPinnedTarget(StackIdentity identity) {
        if (identity == null || !"item".equals(identity.type)
                || !REGISTRY_ID.equals(identity.registryId)
                || identity.metadata != 0 || identity.stack == null
                || identity.stack.getItem() == null
                || !identity.stack.hasTagCompound()) {
            return false;
        }
        if (!ITEM_CLASS.equals(identity.stack.getItem().getClass().getName())) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: " + REGISTRY_ID + " runtime class drifted");
        }
        return true;
    }

    static void draw(StackIdentity identity) {
        if (!isPinnedTarget(identity)) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: unpinned Draconic Mob Soul glyph target");
        }
        int accent = 0xff000000 | (identity.key.hashCode() & 0x005f5fff) | 0x00606080;
        Gui.drawRect(4, 0, 12, 2, 0xff2a123f);
        Gui.drawRect(2, 2, 14, 13, 0xff2a123f);
        Gui.drawRect(4, 13, 12, 16, 0xff2a123f);
        Gui.drawRect(4, 3, 12, 12, accent);
        Gui.drawRect(5, 5, 7, 8, 0xffd8ffff);
        Gui.drawRect(9, 5, 11, 8, 0xffd8ffff);
        Gui.drawRect(6, 10, 10, 12, 0xff17111f);
    }
}
