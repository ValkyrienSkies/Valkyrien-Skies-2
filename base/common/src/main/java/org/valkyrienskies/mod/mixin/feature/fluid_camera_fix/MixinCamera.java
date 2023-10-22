package org.valkyrienskies.mod.mixin.feature.fluid_camera_fix;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow
    public abstract Vec3 getPosition();

    @Unique
    private boolean isShipWater = false;

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "getFluidInCamera"
    )
    private FluidState getFluidInCamera(final BlockGetter instance, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        final FluidState[] fluidState = {getFluidState.call(instance, blockPos)};
        isShipWater = false;
        if (fluidState[0].isEmpty() && instance instanceof final Level level) {

            final double origX = this.getPosition().x;
            final double origY = this.getPosition().y;
            final double origZ = this.getPosition().z;

            VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX, origY, origZ, 1,
                (x, y, z) -> {
                    fluidState[0] = instance.getBlockState(new BlockPos(x, y, z))
                        .getFluidState();
                    if (!fluidState[0].isEmpty()) {
                        isShipWater = true;
                    }
                });

        }
        return fluidState[0];
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"),
        method = "getFluidInCamera"
    )
    private float fluidHeightOverride(final FluidState instance, final BlockGetter arg, final BlockPos arg2,
        final Operation<Float> getHeight) {
        if (!instance.isEmpty()) {
            if (isShipWater) {
                if (instance.isSource()) {
                    return 1;
                }
            }
        }
        return getHeight.call(instance, arg, arg2);
    }
}
