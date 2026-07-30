package com.recipetree.reiexport118.compat;

/** Exact KubeJS contract for its unsynchronized static item-tooltip initialization seam. */
public final class KubeJsTooltipConcurrencyContract {
    public static final String MINECRAFT_VERSION = "1.18.2";
    public static final String KUBEJS_VERSION = "1802.5.5-build.569";

    public static final String KUBEJS_JAR_SHA256 =
            "a3fdde9433d75ac1f5ae72d3f5b74d2dfb3a59d8b28bad18a5c526cb32f814d0";
    public static final String TARGET_CLASS =
            "dev.latvian.mods.kubejs.client.KubeJSClientEventHandler";
    public static final String TARGET_RESOURCE =
            "dev/latvian/mods/kubejs/client/KubeJSClientEventHandler.class";
    public static final String TARGET_SHA256 =
            "102e91b3de4613ef648f90a082dc8168c690d90492f435ecc1313b5785a2a22b";
    public static final String TARGET_FIELD = "staticItemTooltips";
    public static final String TARGET_METHOD = "itemTooltip";
    public static final String TARGET_METHOD_DESCRIPTOR =
            "(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;"
                    + "Lnet/minecraft/world/item/TooltipFlag;)V";
    public static final String TARGET_METHOD_SELECTOR =
            TARGET_METHOD + TARGET_METHOD_DESCRIPTOR;
    public static final String HANDLER_INIT_METHOD_SELECTOR = "init()V";

    public static final String RELOAD_TARGET_CLASS =
            "dev.latvian.mods.kubejs.client.KubeJSClient";
    public static final String RELOAD_TARGET_RESOURCE =
            "dev/latvian/mods/kubejs/client/KubeJSClient.class";
    public static final String RELOAD_TARGET_SHA256 =
            "59d20510db7829ea7b2ead82cf1dbfd2a0c5f4893d3e76f120de637e10395b0b";
    public static final String RELOAD_METHOD = "reloadClientScripts";
    public static final String RELOAD_METHOD_DESCRIPTOR = "()V";
    public static final String RELOAD_METHOD_SELECTOR =
            RELOAD_METHOD + RELOAD_METHOD_DESCRIPTOR;

    public static final String ARCHITECTURY_EVENT_REGISTER_TARGET =
            "Ldev/architectury/event/Event;register(Ljava/lang/Object;)V";
    public static final int TOOLTIP_REGISTER_ORDINAL = 3;

    public static final String TOOLTIP_EVENT_CLASS =
            "dev.latvian.mods.kubejs.item.ItemTooltipEventJS";
    public static final String TOOLTIP_EVENT_CONSTRUCTOR_DESCRIPTOR = "(Ljava/util/Map;)V";
    public static final String TOOLTIP_EVENT_NAME = "item.tooltip";

    private KubeJsTooltipConcurrencyContract() {
    }

    public static boolean isApplicable(String minecraftVersion, String kubeJsVersion) {
        return MINECRAFT_VERSION.equals(minecraftVersion)
                && KUBEJS_VERSION.equals(kubeJsVersion);
    }
}
