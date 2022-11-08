package org.valkyrienskies.mod.mixin.feature.water_in_ships_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    public abstract void setPos(double x, double y, double z);

    @Shadow
    public abstract Vec3 position();

    @Shadow
    public abstract boolean updateFluidHeightAndDoFluidPushing(Tag<Fluid> fluidTag, double motionScale);

    @Unique
    private boolean isModifyingWaterState = false;

    @Shadow
    public Level level;
    @Shadow
    private AABB bb;

    @Shadow
    public abstract double getEyeY();

    @Shadow
    public abstract double getX();

    @Shadow
    public abstract double getZ();

    @Unique
    private boolean isShipWater = false;

    @Inject(
        at = @At("TAIL"),
        method = "updateFluidHeightAndDoFluidPushing",
        cancellable = true
    )
    private void afterFluidStateUpdate(final Tag<Fluid> fluidTag, final double motionScale,
        final CallbackInfoReturnable<Boolean> cir) {
        if (isModifyingWaterState || cir.getReturnValue()) {
            return;
        }

        isModifyingWaterState = true;

        final Vec3 pos = this.position();

        final double origX = pos.x;
        final double origY = pos.y;
        final double origZ = pos.z;

        VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, origX, origY, origZ, this.bb.getSize(), (x, y, z) -> {
            this.setPos(x, y, z);
            cir.setReturnValue(this.updateFluidHeightAndDoFluidPushing(fluidTag, motionScale));
            this.setPos(origX, origY, origZ);
        });

        isModifyingWaterState = false;
    }

    @Redirect(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "updateFluidOnEyes"
    )
    private FluidState getFluidStateRedirect(final Level level, final BlockPos blockPos) {
        final FluidState[] fluidState = {level.getFluidState(blockPos)};
        isShipWater = false;
        if (fluidState[0].isEmpty()) {

            final double d = this.getEyeY() - 0.1111111119389534;

            final double origX = this.getX();
            final double origY = d;
            final double origZ = this.getZ();

            VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, origX, origY, origZ, this.bb.getSize(),
                (x, y, z) -> {
                    fluidState[0] = level.getFluidState(new BlockPos(x, y, z));
                });
            isShipWater = true;
        }
        return fluidState[0];
    }

    @Redirect(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"),
        method = "updateFluidOnEyes"
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
