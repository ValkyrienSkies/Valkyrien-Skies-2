package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Animal.class)
public abstract class MixinAnimalShipSpawn {

    @WrapOperation(
        method = "isBrightEnoughToSpawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockAndTintGetter;getRawBrightness(Lnet/minecraft/core/BlockPos;I)I"
        )
    )
    private static int vs$shipAnimalBrightness(
        final BlockAndTintGetter getter, final BlockPos pos, final int skyDarken,
        final Operation<Integer> original
    ) {
        return VSGameUtilsKt.shipAwareCombinedBrightness(
            getter, pos, skyDarken, original.call(getter, pos, skyDarken)
        );
    }
}
