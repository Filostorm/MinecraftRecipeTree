package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.IndustrialForegoingOreTagOrderCompatibility;
import com.recipetree.reiexport118.compat.IndustrialForegoingOreTagOrderContract;
import com.recipetree.reiexport118.compat.IndustrialForegoingRecipeListOrderCompatibility;
import com.recipetree.reiexport118.compat.IndustrialForegoingRecipeListOrderContract;
import com.hrznstudio.titanium.util.RecipeUtil;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.tags.ITagManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.stream.Stream;

/** Canonicalizes the one item-tag stream used to generate Industrial Foregoing ore recipes. */
@Pseudo
@Mixin(
        targets = IndustrialForegoingOreTagOrderContract.TARGET_CLASS,
        remap = false)
public abstract class IndustrialForegoingOreTagOrderMixin {
    @Redirect(
            method = IndustrialForegoingOreTagOrderContract.REGISTER_RECIPES,
            at = @At(
                    value = "INVOKE",
                    target = IndustrialForegoingOreTagOrderContract.GET_TAG_NAMES_TARGET),
            require = 1,
            remap = false)
    private Stream<TagKey<Item>> reiexport$canonicalOreRecipeTagOrder(
            ITagManager<Item> tagManager
    ) {
        return IndustrialForegoingOreTagOrderCompatibility.canonicalTagNames(tagManager);
    }

    @Redirect(
            method = IndustrialForegoingRecipeListOrderContract.REGISTER_RECIPES,
            at = @At(
                    value = "INVOKE",
                    target = IndustrialForegoingRecipeListOrderContract.GET_RECIPES_TARGET),
            require = IndustrialForegoingRecipeListOrderContract.EXPECTED_GET_RECIPES_CALLS,
            remap = false)
    private <T extends Recipe<?>> List<T> reiexport$canonicalRecipeOrder(
            Level level,
            RecipeType<T> recipeType
    ) {
        List<T> source = RecipeUtil.getRecipes(level, recipeType);
        return IndustrialForegoingRecipeListOrderCompatibility.canonicalRecipes(
                recipeType,
                source);
    }
}
