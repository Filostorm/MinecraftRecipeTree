package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2PigmentRecipeRegistrationGate;
import com.recipetree.reiexport118.compat.Mm2ReiLifecycleGate;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** Audits the exact pinned pigment recipe collection from queueing through execution. */
@Pseudo
@Mixin(targets = "me.shedaniel.rei.jeicompat.wrap.JEIRecipeRegistration", remap = false)
public abstract class JeiRecipeRegistrationPigmentMixin {
    @Unique
    private static final String ADD_RECIPES =
            "addRecipes(Ljava/util/Collection;Lnet/minecraft/resources/ResourceLocation;)V";
    @Unique
    private static final String ADD_RECIPES_ZERO =
            "addRecipes0(Ljava/util/Collection;Lnet/minecraft/resources/ResourceLocation;)V";
    @Unique
    private static final String CAN_RECIPES_BE_MULTITHREADED =
            "canRecipesBeMultithreaded(Ljava/util/Collection;"
                    + "Lme/shedaniel/rei/api/common/category/CategoryIdentifier;)Z";
    @Unique
    private static final String ADD_RECIPES_OPTIMIZED =
            "addRecipesOptimized(Ljava/util/List;"
                    + "Lme/shedaniel/rei/api/common/category/CategoryIdentifier;"
                    + "Lme/shedaniel/rei/api/client/registry/display/DisplayRegistry;"
                    + "Ljava/util/function/Function;)V";

    @ModifyVariable(
            method = ADD_RECIPES,
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 1,
            remap = false)
    private Collection<?> reiexport$canonicalizeQueuedPigmentRecipes(
            Collection<?> modifiedRecipes,
            Collection<?> originalRecipes,
            ResourceLocation categoryId
    ) {
        if (modifiedRecipes != originalRecipes) {
            throw new IllegalStateException(
                    "Pigment queue @ModifyVariable HEAD contract changed: modified and "
                            + "original recipe collections are not the same instance");
        }
        return Mm2PigmentRecipeRegistrationGate.canonicalizeQueued(
                this, modifiedRecipes, categoryId);
    }

    @Inject(method = ADD_RECIPES_ZERO, at = @At("HEAD"), require = 1, remap = false)
    private static void reiexport$beginPigmentRecipeExecution(
            Collection<?> recipes,
            ResourceLocation categoryId,
            CallbackInfo callback
    ) {
        Mm2PigmentRecipeRegistrationGate.beginExecution(recipes, categoryId);
    }

    @Inject(method = ADD_RECIPES_ZERO, at = @At("RETURN"), require = 1, remap = false)
    private static void reiexport$finishPigmentRecipeExecution(
            Collection<?> recipes,
            ResourceLocation categoryId,
            CallbackInfo callback
    ) {
        Mm2PigmentRecipeRegistrationGate.finishExecution(recipes, categoryId);
    }

    @Inject(
            method = CAN_RECIPES_BE_MULTITHREADED,
            at = @At("RETURN"),
            cancellable = true,
            require = 1,
            remap = false)
    private static void reiexport$forceSerialPigmentRecipeExecution(
            Collection<?> recipes,
            CategoryIdentifier<? extends Display> categoryId,
            CallbackInfoReturnable<Boolean> callback
    ) {
        callback.setReturnValue(Mm2PigmentRecipeRegistrationGate.forceSerialExecution(
                recipes, categoryId, callback.getReturnValueZ()));
    }

    @Inject(method = ADD_RECIPES_OPTIMIZED, at = @At("HEAD"), require = 1, remap = false)
    private static void reiexport$rejectOptimizedPigmentRecipes(
            List<?> recipes,
            CategoryIdentifier<? extends Display> categoryId,
            DisplayRegistry registry,
            Function<Object, Collection<Display>> filler,
            CallbackInfo callback
    ) {
        Mm2PigmentRecipeRegistrationGate.beginOptimized(recipes, categoryId);
    }

    @Inject(method = ADD_RECIPES_OPTIMIZED, at = @At("RETURN"), require = 1, remap = false)
    private static void reiexport$finishOptimizedPigmentRecipes(
            List<?> recipes,
            CategoryIdentifier<? extends Display> categoryId,
            DisplayRegistry registry,
            Function<Object, Collection<Display>> filler,
            CallbackInfo callback
    ) {
        Mm2PigmentRecipeRegistrationGate.finishOptimized(recipes, categoryId);
    }

    @Redirect(
            method = ADD_RECIPES_OPTIMIZED,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Exception;printStackTrace()V"),
            require = 1,
            remap = false)
    private static void reiexport$rejectSwallowedOptimizedFailure(Exception failure) {
        failure.printStackTrace();
        Mm2ReiLifecycleGate.rejectSwallowedPluginFailure(
                "JEIRecipeRegistration.addRecipesOptimized", failure);
    }
}
