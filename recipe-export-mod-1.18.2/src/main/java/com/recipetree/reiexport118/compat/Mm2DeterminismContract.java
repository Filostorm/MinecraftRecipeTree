package com.recipetree.reiexport118.compat;

import java.util.List;

/** Byte-for-byte contract for the Multiblock Madness 2 determinism repairs. */
public final class Mm2DeterminismContract {
    public record ModPin(String modId, String version, String jarSha256) {
    }

    public record ClassPin(String modId, String className, String resource, String sha256) {
    }

    public static final String BOTANIA_TWIG_WAND_CLASS =
            "vazkii.botania.common.item.ItemTwigWand";
    public static final ModPin BOTANIA = new ModPin(
            "botania", "1.18.2-435",
            "1300bc74d0cc1fad40261b0a888fc3c8b88594c43b9063b5f6a6b7456518ed1f");
    public static final ClassPin BOTANIA_TWIG_WAND = new ClassPin(
            BOTANIA.modId(), BOTANIA_TWIG_WAND_CLASS,
            "vazkii/botania/common/item/ItemTwigWand.class",
            "d028b8779376f39cb64c7727c996810567ccc89375b9c24b0b006f691e60bd56");

    public static final String ELEMENTAL_ITEMS_TAGS_CLASS =
            "sirttas.elementalcraft.tag.ECTags$Items";
    public static final String ELEMENTAL_PURE_ORE_LOADER_CLASS =
            "sirttas.elementalcraft.pureore.PureOreLoader";
    public static final String ELEMENTAL_PURE_ORE_MANAGER_CLASS =
            "sirttas.elementalcraft.pureore.PureOreManager";
    public static final ModPin ELEMENTAL_CRAFT = new ModPin(
            "elementalcraft", "1.18.2-4.4.30b",
            "a0770b5b8d06e7130bdd257dbeb38c81a81ae2da60ee43b75c15d2871b2d116b");
    public static final ClassPin ELEMENTAL_ITEMS_TAGS = new ClassPin(
            ELEMENTAL_CRAFT.modId(), ELEMENTAL_ITEMS_TAGS_CLASS,
            "sirttas/elementalcraft/tag/ECTags$Items.class",
            "ec516e7435173411cb6663fdf97c7fcd81d2de36b4699f18dfb09f4f461d212f");
    public static final ClassPin ELEMENTAL_PURE_ORE_LOADER = new ClassPin(
            ELEMENTAL_CRAFT.modId(), ELEMENTAL_PURE_ORE_LOADER_CLASS,
            "sirttas/elementalcraft/pureore/PureOreLoader.class",
            "2d66b1985c8c1a1e50b075d8c2c0a3ffcf7c8cc193801787025abc177dc70617");
    public static final ClassPin ELEMENTAL_PURE_ORE_MANAGER = new ClassPin(
            ELEMENTAL_CRAFT.modId(), ELEMENTAL_PURE_ORE_MANAGER_CLASS,
            "sirttas/elementalcraft/pureore/PureOreManager.class",
            "7896ef5f7f7a8c2009a6600399ac2f7fa1439b38d9b2d249396aea4530267ee1");

    public static final ModPin RELICS = new ModPin(
            "relics", "0.6.2.4",
            "5e7f5d373eee5fbc0a08f2fb767d54188807800b2e02a2b167aebe02862e8322");
    public static final String RELIC_INTERFACE_CLASS =
            "it.hurts.sskirillss.relics.items.relics.base.IRelicItem";
    public static final String RELIC_ITEM_CLASS =
            "it.hurts.sskirillss.relics.items.relics.base.RelicItem";
    public static final ClassPin RELIC_INTERFACE = new ClassPin(
            RELICS.modId(), RELIC_INTERFACE_CLASS,
            "it/hurts/sskirillss/relics/items/relics/base/IRelicItem.class",
            "03a21596d5250b2172de0220f0fc5c6df4cc8fdcb29de603d61bbb65f61a9a66");
    public static final ClassPin RELIC_ITEM = new ClassPin(
            RELICS.modId(), RELIC_ITEM_CLASS,
            "it/hurts/sskirillss/relics/items/relics/base/RelicItem.class",
            "b984e6edbd71928720a29ee451f2e095314cd4c7cfc5646f50714ff0af62626e");
    public static final ClassPin RELIC_DATA = new ClassPin(
            RELICS.modId(), "it.hurts.sskirillss.relics.items.relics.base.data.RelicData",
            "it/hurts/sskirillss/relics/items/relics/base/data/RelicData.class",
            "e60a8c4a01a5ef6eb7a4d8b39e96669e6d4d3fdaa17d066412429e2c63699437");
    public static final ClassPin RELIC_ABILITIES_DATA = new ClassPin(
            RELICS.modId(),
            "it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilitiesData",
            "it/hurts/sskirillss/relics/items/relics/base/data/leveling/AbilitiesData.class",
            "900edfa2e4d81e744855e6b7113dfc7ca4b88a9d7b639fc5894ed5270418517e");
    public static final ClassPin RELIC_ABILITY_DATA = new ClassPin(
            RELICS.modId(),
            "it.hurts.sskirillss.relics.items.relics.base.data.leveling.AbilityData",
            "it/hurts/sskirillss/relics/items/relics/base/data/leveling/AbilityData.class",
            "8595e3bdcfaaab0c2d1d9439c3b9d7aab3dac15bb6e5beb269ceec48952a1a52");
    public static final ClassPin RELIC_STAT_DATA = new ClassPin(
            RELICS.modId(),
            "it.hurts.sskirillss.relics.items.relics.base.data.leveling.StatData",
            "it/hurts/sskirillss/relics/items/relics/base/data/leveling/StatData.class",
            "1e7056485c311a24f66cee3366fdfa65dd74a0ea776dbce5e9abadd6f99f63d3");

    public static final String AE2_COLOR_APPLICATOR_CLASS =
            "appeng.items.tools.powered.ColorApplicatorItem";
    public static final ModPin AE2 = new ModPin(
            "ae2", "11.7.6",
            "86f06ffdd7b73848cbb82ff23cf6bba6b2949e0562ae8a5f68bf0eed86eba8d2");
    public static final ClassPin AE2_COLOR_APPLICATOR = new ClassPin(
            AE2.modId(), AE2_COLOR_APPLICATOR_CLASS,
            "appeng/items/tools/powered/ColorApplicatorItem.class",
            "b8f92fe08b89aec9243a65bda18eabd71e19afd746d5d2f5426738c2f4335be2");
    public static final ClassPin AE2_BASIC_CELL = new ClassPin(
            AE2.modId(), "appeng.me.cells.BasicCellInventory",
            "appeng/me/cells/BasicCellInventory.class",
            "dab16ed0db9081597fffec2154aaf5a50eabe81a2502130880f04c3847055163");

    public static final String TOMBSTONE_RECEPTACLE_CLASS =
            "ovh.corail.tombstone.item.ItemReceptacleOfFamiliar";
    public static final ModPin TOMBSTONE = new ModPin(
            "tombstone", "7.7.6",
            "4ee36be6ab4c33cffd8353ace283b1cceecf6bf21bd0b66476375df80f98c59c");
    public static final ClassPin TOMBSTONE_RECEPTACLE = new ClassPin(
            TOMBSTONE.modId(), TOMBSTONE_RECEPTACLE_CLASS,
            "ovh/corail/tombstone/item/ItemReceptacleOfFamiliar.class",
            "f5ef4e6a2ce0fc830cb1068ab7cc6bb04f5fcc366772d5caec89d9069572557e");
    public static final ClassPin TOMBSTONE_TAMABLE_TYPE = new ClassPin(
            TOMBSTONE.modId(), "ovh.corail.tombstone.helper.TamableType",
            "ovh/corail/tombstone/helper/TamableType.class",
            "5f9b811c9ef8bd0cfff76ea404e0cf9533fb8a4a1896bf7c46159404d631f479");

    public static final String INFINITY_BACKPACK_CLASS =
            "com.buuz135.industrial.item.infinity.item.ItemInfinityBackpack";
    public static final ModPin INDUSTRIAL_FOREGOING = new ModPin(
            "industrialforegoing", "3.3.1.7",
            "5ff7dce05c3c3233fae8213bb8a23b4b5957495ec6adbd07143f862f9ba7a35a");
    public static final ClassPin INFINITY_BACKPACK = new ClassPin(
            INDUSTRIAL_FOREGOING.modId(),
            INFINITY_BACKPACK_CLASS,
            "com/buuz135/industrial/item/infinity/item/ItemInfinityBackpack.class",
            "052672eccdf935667b56b83bd94a5f28c40fb359c366dd431fe50c094e18acb1");
    public static final ClassPin IF_FLUID_HANDLER = new ClassPin(
            INDUSTRIAL_FOREGOING.modId(),
            "com.buuz135.industrial.capability.MultipleFluidHandlerScreenProviderItemStack",
            "com/buuz135/industrial/capability/MultipleFluidHandlerScreenProviderItemStack.class",
            "fef32549dda1bed821b2dfd5ccabf2a3387c2b841df30c2920c53e6afd4becab");
    public static final ClassPin IF_JEI_CUSTOM_PLUGIN = new ClassPin(
            INDUSTRIAL_FOREGOING.modId(),
            IndustrialForegoingOreTagOrderContract.TARGET_CLASS,
            IndustrialForegoingOreTagOrderContract.TARGET_RESOURCE,
            IndustrialForegoingOreTagOrderContract.TARGET_CLASS_SHA256);

    public static final ModPin TITANIUM = new ModPin(
            "titanium", "3.5.11",
            "ba108e9745bc0f048eebae11f0c3fcbc1bf4eb40dfa88a8c3ba490a6a1989d70");
    public static final ClassPin TITANIUM_RECIPE_UTIL = new ClassPin(
            TITANIUM.modId(),
            "com.hrznstudio.titanium.util.RecipeUtil",
            "com/hrznstudio/titanium/util/RecipeUtil.class",
            "75a805b61cbda43a476356b69109374b3be2d85b6ede2f4b0c3568ce26e154f9");

    public static final ModPin SPIRIT = new ModPin(
            "spirit", "2.1.8",
            "1d3f14a37a492f1fa6babb83fc7527c99777acf500a6b14311f75ad2aa4c93d9");

    /**
     * The exact MM2 export resolves Spirit's entity ingredient through REI's JEI adapter
     * ({@code spirit:jei_jei_compat_entityingredient}), so the active renderer is Spirit's JEI
     * renderer. Spirit's separate native-REI renderer is not on this entry-type path.
     */
    public static final ClassPin SPIRIT_JEI_ENTITY_RENDERER = new ClassPin(
            SPIRIT.modId(),
            "me.codexadrian.spirit.compat.jei.ingredients.EntityRenderer",
            "me/codexadrian/spirit/compat/jei/ingredients/EntityRenderer.class",
            "f2ec2207c297fe7e064f481cad4de2aaf8890ee4456eac01a484aef974b9a0d7");

    public static final ClassPin SPIRIT_ENTITY_INGREDIENT = new ClassPin(
            SPIRIT.modId(),
            "me.codexadrian.spirit.compat.jei.ingredients.EntityIngredient",
            "me/codexadrian/spirit/compat/jei/ingredients/EntityIngredient.class",
            "3c5f56d7276af3bad1f23c8add311286e4277843405dd9bace1a9d5f626b5d27");

    public static final ModPin IMMERSIVE_ENGINEERING = new ModPin(
            "immersiveengineering", "1.18.2-8.4.0-161",
            "6e9b1ad1fc29d10863465bb48c65ad4d5df74042a5b0ee50c6289b687cd38a74");
    public static final ClassPin IMMERSIVE_ENGINEERING_IE_API = new ClassPin(
            IMMERSIVE_ENGINEERING.modId(),
            "blusunrize.immersiveengineering.api.IEApi",
            "blusunrize/immersiveengineering/api/IEApi.class",
            "38ddddae31ee2f0f1dd495188ee89b0ee6fc33d460e4911a746fad1a75810948");
    public static final ClassPin IMMERSIVE_ENGINEERING_POTION_BUCKET = new ClassPin(
            IMMERSIVE_ENGINEERING.modId(),
            "blusunrize.immersiveengineering.common.items.PotionBucketItem",
            "blusunrize/immersiveengineering/common/items/PotionBucketItem.class",
            "20da959c75c1ab6ab1d21febdcb6e2566cf747e3cfca397b0cc22a6d3b482dc2");

    public static final ModPin PROJECT_RED_INTEGRATION = new ModPin(
            "projectred_integration", "4.16.0",
            "d22aaf567b25ddd74cdb2e190349ec5a8c9a66b8412d3bbf1973b6fc9b39b3dd");
    public static final ClassPin PROJECT_RED_INTEGRATION_PARTS = new ClassPin(
            PROJECT_RED_INTEGRATION.modId(),
            "mrtjp.projectred.integration.init.IntegrationParts",
            "mrtjp/projectred/integration/init/IntegrationParts.class",
            "d53c647843bd57b565a0d760b5d3c8695820f9d7c0adf30e071f96135220ccbe");
    public static final ClassPin PROJECT_RED_GATE_TYPE = new ClassPin(
            PROJECT_RED_INTEGRATION.modId(),
            "mrtjp.projectred.integration.GateType",
            "mrtjp/projectred/integration/GateType.class",
            "bb548098c40ebe375d293bb5c1d933ed3e5698f3044849352c89c8b23c476be3");
    public static final ModPin PROJECT_RED_FABRICATION = new ModPin(
            "projectred_fabrication", "4.16.0",
            "f42edab2dabedbd6d7007b1131a38deb7feda36abb471487bf8c37771a460085");
    public static final ClassPin PROJECT_RED_FABRICATION_PARTS = new ClassPin(
            PROJECT_RED_FABRICATION.modId(),
            "mrtjp.projectred.fabrication.init.FabricationParts",
            "mrtjp/projectred/fabrication/init/FabricationParts.class",
            "d0feb8a311a8ba92dcecad120a62f9e4fafc21abdd01f85ac59290236e57b35f");

    public static final ModPin LOW_DRAG_LIB = new ModPin(
            "ldlib", "1.18.2-1.0.8",
            "dbf3032612be9e0c7448673bac8f6c14b1bab3e6927aff4e27182309de900b50");
    public static final ClassPin LOW_DRAG_CYCLE_ITEM_STACK_HANDLER = new ClassPin(
            LOW_DRAG_LIB.modId(),
            "com.lowdragmc.lowdraglib.utils.CycleItemStackHandler",
            "com/lowdragmc/lowdraglib/utils/CycleItemStackHandler.class",
            "eba18fd30984cf30b6a7fdd90c4cc6ea32ca4f27e9033e748a561739cc13ac2c");
    public static final ClassPin LOW_DRAG_PROGRESS_WIDGET = new ClassPin(
            LOW_DRAG_LIB.modId(),
            "com.lowdragmc.lowdraglib.gui.widget.ProgressWidget",
            "com/lowdragmc/lowdraglib/gui/widget/ProgressWidget.class",
            "549ac12477761e9ec67cf0d49a3ce026453ed7b3d9e45e176116b6bbcf6702e7");

    public static final ModPin CREATE = new ModPin(
            "create", "0.5.1.i",
            "5311dbc98d734d7cf0f38f3a9a149234136b912228cdf01b746fbe32d41d0fe2");
    public static final ClassPin CREATE_ANIMATION_TICK_HOLDER = new ClassPin(
            CREATE.modId(),
            "com.simibubi.create.foundation.utility.AnimationTickHolder",
            "com/simibubi/create/foundation/utility/AnimationTickHolder.class",
            "cbc42a6f90e882f1c324a1b8fc0344d46145d9843a0b11e83230424410e88a99");
    public static final ClassPin CREATE_ANIMATED_KINETICS = new ClassPin(
            CREATE.modId(),
            "com.simibubi.create.compat.jei.category.animations.AnimatedKinetics",
            "com/simibubi/create/compat/jei/category/animations/AnimatedKinetics.class",
            "af70ee8e5f9789149c5397abc1de845f00cde10cc5f5ec038fb00ae7b5f8d9b6");
    public static final ClassPin CREATE_ANIMATED_MIXER = new ClassPin(
            CREATE.modId(),
            "com.simibubi.create.compat.jei.category.animations.AnimatedMixer",
            "com/simibubi/create/compat/jei/category/animations/AnimatedMixer.class",
            "691147f074db0e09b01c039f3c6bdf2d12b00a13eec64ab935e3eb26ed3f7bae");
    public static final ClassPin CREATE_MIXING_CATEGORY = new ClassPin(
            CREATE.modId(),
            "com.simibubi.create.compat.jei.category.MixingCategory",
            "com/simibubi/create/compat/jei/category/MixingCategory.class",
            "4f8de60ae61ef666d16c0e66eedd676581f5c76c5a9568e5a5669de659b3557f");

    public static final ModPin MULTIBLOCKED = new ModPin(
            "multiblocked", "1.18.2-1.0.10",
            "45661399563a17d6c4c99fa7abe65e17039902874741ddda30cb99df68a7ec93");
    public static final ClassPin MULTIBLOCKED_RECIPE_DISPLAY = new ClassPin(
            MULTIBLOCKED.modId(),
            "com.lowdragmc.multiblocked.rei.recipepage.RecipeDisplay",
            "com/lowdragmc/multiblocked/rei/recipepage/RecipeDisplay.class",
            "fdab87b944f22644e736392df71a4701cff2653a8912582431a012da1a072ae2");
    public static final ClassPin MULTIBLOCKED_CYCLE_BLOCK_STATE_RENDERER = new ClassPin(
            MULTIBLOCKED.modId(),
            "com.lowdragmc.multiblocked.client.renderer.impl.CycleBlockStateRenderer",
            "com/lowdragmc/multiblocked/client/renderer/impl/CycleBlockStateRenderer.class",
            "cd6c1e871dd2e3d5fbb2cf4cdee29a1fd17a7dc9b8a57a4f1365c86a6c181087");

    public static final ModPin MEKANISM = new ModPin(
            "mekanism", "10.2.5",
            "ae5f3818940aa99cf3bf51786a9b66284327d14120924bd99f864e6b5457e523");
    public static final ClassPin MEKANISM_BASE_RECIPE_CATEGORY = new ClassPin(
            MEKANISM.modId(),
            "mekanism.client.jei.BaseRecipeCategory",
            "mekanism/client/jei/BaseRecipeCategory.class",
            "de02eb702f58f0e402e4378dc0360baea7da59b93b99b589d8280d3219b80f4d");
    public static final ClassPin MEKANISM_CHEMICAL_INJECTION_CATEGORY = new ClassPin(
            MEKANISM.modId(),
            "mekanism.client.jei.machine.ItemStackGasToItemStackRecipeCategory",
            "mekanism/client/jei/machine/ItemStackGasToItemStackRecipeCategory.class",
            "d3740463758945e8f2d8214a663132fb632f36189b1beae178e81090e75518e3");
    public static final int MEKANISM_PIGMENT_EXTRACTING_RECIPES = 177;
    public static final String MEKANISM_PIGMENT_EXTRACTING_IDS_SHA256 =
            "abdd514c2a7af320c27cdf8748338bbc772be3fef5dc52fc9bae0f288298a38a";
    public static final ModPin REI = new ModPin(
            "roughlyenoughitems", "8.4.778",
            "46bbddc0f99bf392c6cb32bc757f834aab5c0ba42a3e2e8f1e9d89d76fff6842");
    public static final ClassPin REI_RELOAD_MANAGER = new ClassPin(
            REI.modId(), "me.shedaniel.rei.impl.common.plugins.ReloadManagerImpl",
            "me/shedaniel/rei/impl/common/plugins/ReloadManagerImpl.class",
            "19144974ef4f6bafc826d4e62040b51279e40ccd8f6dcd2bd403a347e6ac0720");
    public static final ClassPin REI_PLUGIN_MANAGER = new ClassPin(
            REI.modId(), "me.shedaniel.rei.impl.common.plugins.PluginManagerImpl",
            "me/shedaniel/rei/impl/common/plugins/PluginManagerImpl.class",
            "009fe0d144cbd1390c7edbff5cb44eb1b1bb5b49e72c555dcfcd0ee6556404e1");
    public static final ClassPin REI_CORE_CLIENT = new ClassPin(
            REI.modId(), "me.shedaniel.rei.RoughlyEnoughItemsCoreClient",
            "me/shedaniel/rei/RoughlyEnoughItemsCoreClient.class",
            "848b06b48665e917500d920f40cfb03fb2870b20a2f0ab6cbbb44eee828ea1ac");
    public static final ClassPin REI_FORGE_CLIENT_PACKET_MIXIN = new ClassPin(
            REI.modId(), "me.shedaniel.rei.mixin.forge.MixinClientPacketListener",
            "me/shedaniel/rei/mixin/forge/MixinClientPacketListener.class",
            "7d892f186cbe2d27344b38753d5577cd48326cd7031c0b900dd0006938cf5265");
    public static final ClassPin REI_DISPLAYS_HOLDER = new ClassPin(
            REI.modId(), "me.shedaniel.rei.impl.client.registry.display.DisplaysHolderImpl",
            "me/shedaniel/rei/impl/client/registry/display/DisplaysHolderImpl.class",
            "7f60366ead0c13725f5f09ed5dcbf844e8c1e6766250ebc11a70743da48be586");

    public static final ModPin REI_PLUGIN_COMPAT = new ModPin(
            "rei_plugin_compatibilities", "8.0.89",
            "be4bca14846470ff0b686498d2ba43bd08b9689632ff5fd4c156863af2f98909");
    public static final ClassPin JEI_PLUGIN_DETECTOR = new ClassPin(
            REI_PLUGIN_COMPAT.modId(), "me.shedaniel.rei.jeicompat.JEIPluginDetector",
            "me/shedaniel/rei/jeicompat/JEIPluginDetector.class",
            "08b3ab220c4546d353824badd9de5eece94dbfdf6523eeb588a0f54caa467021");
    public static final ClassPin JEI_PLUGIN_WRAPPER = new ClassPin(
            REI_PLUGIN_COMPAT.modId(),
            "me.shedaniel.rei.jeicompat.JEIPluginDetector$JEIPluginWrapper",
            "me/shedaniel/rei/jeicompat/JEIPluginDetector$JEIPluginWrapper.class",
            "d43b1c6e7a2ae3ad2eeb7e543b2a22e9e912ff1de9d9b84f8e648d40a4cb1c75");
    public static final ClassPin JEI_RECIPE_REGISTRATION = new ClassPin(
            REI_PLUGIN_COMPAT.modId(),
            "me.shedaniel.rei.jeicompat.wrap.JEIRecipeRegistration",
            "me/shedaniel/rei/jeicompat/wrap/JEIRecipeRegistration.class",
            "7337781621f1d4cf5a2caed018ec6bae111c1de478c9b38c3ebf50b4432d752d");
    public static final ClassPin JEI_RECIPE_TRANSFER_REGISTRATION = new ClassPin(
            REI_PLUGIN_COMPAT.modId(),
            "me.shedaniel.rei.jeicompat.wrap.JEIRecipeTransferRegistration",
            "me/shedaniel/rei/jeicompat/wrap/JEIRecipeTransferRegistration.class",
            "cab70f4a183592d1778bcf7911bc87c0920dcf139518cad332a7dc822b72d02b");
    public static final ClassPin JEI_DRAWABLE_RENDERER = new ClassPin(
            REI_PLUGIN_COMPAT.modId(), "me.shedaniel.rei.jeicompat.JEIPluginDetector$2",
            "me/shedaniel/rei/jeicompat/JEIPluginDetector$2.class",
            "5bc93be73d7b03e4702665509e13dc451a8f3ca75dd2aff31ccb620b6843a6a6");
    public static final ClassPin JEI_GUI_HELPER_TICK_TIMER = new ClassPin(
            REI_PLUGIN_COMPAT.modId(),
            "me.shedaniel.rei.jeicompat.wrap.JEIGuiHelper$6",
            "me/shedaniel/rei/jeicompat/wrap/JEIGuiHelper$6.class",
            "04377889109bed4421d4dc46999e47c4f54f815cc494a1a09373b5759d9578d7");

    public static final List<ModPin> LIFECYCLE_SIGNATURE = List.of(
            BOTANIA, ELEMENTAL_CRAFT, RELICS, AE2, TOMBSTONE,
            INDUSTRIAL_FOREGOING, TITANIUM, SPIRIT, IMMERSIVE_ENGINEERING,
            PROJECT_RED_INTEGRATION, PROJECT_RED_FABRICATION, LOW_DRAG_LIB,
            CREATE, MULTIBLOCKED, MEKANISM, REI, REI_PLUGIN_COMPAT);

    public static final List<ClassPin> CLASS_PINS = List.of(
            BOTANIA_TWIG_WAND,
            ELEMENTAL_ITEMS_TAGS, ELEMENTAL_PURE_ORE_LOADER, ELEMENTAL_PURE_ORE_MANAGER,
            RELIC_INTERFACE, RELIC_ITEM, RELIC_DATA, RELIC_ABILITIES_DATA,
            RELIC_ABILITY_DATA, RELIC_STAT_DATA,
            AE2_COLOR_APPLICATOR, AE2_BASIC_CELL,
            TOMBSTONE_RECEPTACLE, TOMBSTONE_TAMABLE_TYPE,
            INFINITY_BACKPACK, IF_FLUID_HANDLER, IF_JEI_CUSTOM_PLUGIN,
            TITANIUM_RECIPE_UTIL,
            SPIRIT_JEI_ENTITY_RENDERER, SPIRIT_ENTITY_INGREDIENT,
            IMMERSIVE_ENGINEERING_IE_API, IMMERSIVE_ENGINEERING_POTION_BUCKET,
            PROJECT_RED_INTEGRATION_PARTS, PROJECT_RED_GATE_TYPE,
            PROJECT_RED_FABRICATION_PARTS,
            LOW_DRAG_CYCLE_ITEM_STACK_HANDLER, LOW_DRAG_PROGRESS_WIDGET,
            CREATE_ANIMATION_TICK_HOLDER, CREATE_ANIMATED_KINETICS,
            CREATE_ANIMATED_MIXER, CREATE_MIXING_CATEGORY,
            MULTIBLOCKED_RECIPE_DISPLAY, MULTIBLOCKED_CYCLE_BLOCK_STATE_RENDERER,
            MEKANISM_BASE_RECIPE_CATEGORY, MEKANISM_CHEMICAL_INJECTION_CATEGORY,
            REI_RELOAD_MANAGER, REI_PLUGIN_MANAGER, REI_CORE_CLIENT,
            REI_FORGE_CLIENT_PACKET_MIXIN, REI_DISPLAYS_HOLDER,
            JEI_PLUGIN_DETECTOR, JEI_PLUGIN_WRAPPER, JEI_RECIPE_REGISTRATION,
            JEI_RECIPE_TRANSFER_REGISTRATION,
            JEI_DRAWABLE_RENDERER, JEI_GUI_HELPER_TICK_TIMER);

    private Mm2DeterminismContract() {
    }
}
