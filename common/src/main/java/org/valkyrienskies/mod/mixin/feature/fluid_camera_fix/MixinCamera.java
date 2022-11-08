package org.valkyrienskies.mod.mixin.feature.fluid_camera_fix;

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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow
    public abstract Vec3 getPosition();

    @Shadow
    private BlockGetter level;

    @Unique
    private boolean isShipWater = false;

    @Redirect(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "getFluidInCamera"
    )
    private FluidState getFluidInCamera(final BlockGetter instance, final BlockPos blockPos) {
        final FluidState[] fluidState = {instance.getFluidState(blockPos)};
        isShipWater = false;
        if (fluidState[0].isEmpty() && instance instanceof Level) {

            final double origX = this.getPosition().x;
            final double origY = this.getPosition().y;
            final double origZ = this.getPosition().z;

            VSGameUtilsKt.transformToNearbyShipsAndWorld((Level) instance, origX, origY, origZ, 1,
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

    @Redirect(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"),
        method = "getFluidInCamera"
    )
    private float fluidHeightOverride(final FluidState instance, final BlockGetter arg, final BlockPos arg2) {
        if (!instance.isEmpty() && this.level instanceof Level) {

            if (isShipWater) {
                if (instance.isSource()) {
                    return 1;
                }
            }

        }
        return instance.getHeight(arg, arg2);
    }
}
