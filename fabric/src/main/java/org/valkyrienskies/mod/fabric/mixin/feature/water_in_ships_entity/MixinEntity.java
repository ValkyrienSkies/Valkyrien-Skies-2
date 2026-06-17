package org.valkyrienskies.mod.fabric.mixin.feature.water_in_ships_entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(Entity.class)
public abstract class MixinEntity {

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

    /**
     * Correctness relies on vanilla querying getFluidState before
     * getHeight/getFlow within each scanned cell: getFluidState sets these, the
     * other two read them. Null = current cell is world fluid, not ship fluid.
     */
    @Unique
    private BlockPos valkyrienskies$pushShipPos = null;
    @Unique
    private Ship valkyrienskies$pushShip = null;

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z"
    )
    private FluidState valkyrienskies$pushFluidState(final Level lvl, final BlockPos pos,
        final Operation<FluidState> original) {
        valkyrienskies$pushShipPos = null;
        valkyrienskies$pushShip = null;

        final FluidState world = original.call(lvl, pos);
        if (!world.isEmpty()) {
            return world;
        }
        if (((IEntityDraggingInformationProvider) this).vs$isInSealedArea()) {
            return world;
        }

        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(this.level, new AABB(pos))) {
            final Vector3d sp = ship.getWorldToShip().transformPosition(
                new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            final BlockPos shipPos = BlockPos.containing(sp.x, sp.y, sp.z);
            final FluidState shipFluid = original.call(lvl, shipPos);
            if (!shipFluid.isEmpty()) {
                valkyrienskies$pushShipPos = shipPos;
                valkyrienskies$pushShip = ship;
                return shipFluid;
            }
        }
        return world;
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"),
        method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z"
    )
    private float valkyrienskies$pushHeight(final FluidState instance, final BlockGetter bg, final BlockPos pos,
        final Operation<Float> original) {
        if (valkyrienskies$pushShipPos != null) {
            return original.call(instance, this.level, valkyrienskies$pushShipPos);
        }
        return original.call(instance, bg, pos);
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getFlow(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"),
        method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z"
    )
    private Vec3 valkyrienskies$pushFlow(final FluidState instance, final BlockGetter bg, final BlockPos pos,
        final Operation<Vec3> original) {
        if (valkyrienskies$pushShipPos == null || valkyrienskies$pushShip == null) {
            return original.call(instance, bg, pos);
        }
        final Vec3 shipFlow = original.call(instance, this.level, valkyrienskies$pushShipPos);
        final Quaterniondc shipToWorldRot = valkyrienskies$pushShip.getTransform().getShipToWorldRotation();
        final Vector3d rotated = shipToWorldRot.transform(new Vector3d(shipFlow.x, shipFlow.y, shipFlow.z));
        return new Vec3(rotated.x, rotated.y, rotated.z);
    }


    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "updateFluidOnEyes"
    )
    private FluidState getFluidStateRedirect(final Level level, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        final FluidState[] fluidState = {getFluidState.call(level, blockPos)};
        isShipWater = false;
        if (fluidState[0].isEmpty()) {

            final double d = this.getEyeY() - 0.1111111119389534;

            final double origX = this.getX();
            final double origY = d;
            final double origZ = this.getZ();

            VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, origX, origY, origZ, this.bb.getSize(),
                (x, y, z) -> {
                    final BlockPos shipPos = BlockPos.containing(x, y, z);
                    final FluidState fs = getFluidState.call(level, shipPos);
                    if (!fs.isEmpty() && y < shipPos.getY() + fs.getHeight(level, shipPos)) {
                        fluidState[0] = fs;
                    }
                });
            isShipWater = !fluidState[0].isEmpty();
        }
        return fluidState[0];
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"),
        method = "updateFluidOnEyes"
    )
    private float fluidHeightOverride(final FluidState instance, final BlockGetter arg, final BlockPos arg2,
        final Operation<Float> getHeight) {
        if (!instance.isEmpty() && this.level instanceof Level) {

            if (isShipWater) {
                if (instance.isSource()) {
                    return 1;
                }
            }

        }
        return getHeight.call(instance, arg, arg2);
    }

}
