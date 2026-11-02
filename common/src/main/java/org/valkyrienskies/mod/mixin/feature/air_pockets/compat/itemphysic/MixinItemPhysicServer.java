package org.valkyrienskies.mod.mixin.feature.air_pockets.compat.itemphysic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;

@Pseudo
@Mixin(targets = "team.creative.itemphysic.server.ItemPhysicServer", remap = false)
public abstract class MixinItemPhysicServer {

    @WrapOperation(
        method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/tags/TagKey;D)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        ),
        require = 0
    )
    private static FluidState valkyrienair$overrideItemPhysicPushingFluidQuery(final Level level, final BlockPos pos,
        final Operation<FluidState> getFluidState) {
        final FluidState original = getFluidState.call(level, pos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return original;
        return ShipWaterPocketManager.overrideWaterFluidState(level, pos, original);
    }
}
