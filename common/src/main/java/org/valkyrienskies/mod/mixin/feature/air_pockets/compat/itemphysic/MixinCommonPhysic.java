package org.valkyrienskies.mod.mixin.feature.air_pockets.compat.itemphysic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Pseudo
@Mixin(targets = "team.creative.itemphysic.common.CommonPhysic", remap = false)
public abstract class MixinCommonPhysic {

    @WrapOperation(
        method = "getFluid(Lnet/minecraft/world/entity/item/ItemEntity;Z)Lnet/minecraft/world/level/material/Fluid;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        ),
        require = 0
    )
    private static FluidState valkyrienair$overrideItemPhysicFluidQuery(final Level level, final BlockPos pos,
        final Operation<FluidState> getFluidState) {
        final FluidState original = getFluidState.call(level, pos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return original;
        return ShipWaterPocketManager.overrideWaterFluidState(level, pos, original);
    }
}
