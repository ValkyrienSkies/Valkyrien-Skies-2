package org.valkyrienskies.mod.mixin.mod_compat.common_create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.IExtendedAirCurrentSource;

@Mixin(AirCurrent.class)
public abstract class MixinAirCurrent {

    @Shadow
    @Final
    public IAirCurrentSource source;

    @Unique
    private Ship getShip() {
        if (source instanceof IExtendedAirCurrentSource se)
            return se.getShip();
        else if (source.getAirCurrentWorld() != null)
            return VSGameUtilsKt.getShipManagingPos(source.getAirCurrentWorld(), source.getAirCurrentPos());
        else
            return null;
    }

    @Inject(method = "getFlowLimit", at = @At("RETURN"), cancellable = true, remap = false)
    private static void clipFlowLimit(Level level, BlockPos start, float originalMax, Direction facing, CallbackInfoReturnable<Float> cir) {
        // First let Create do its job at finding block obstructions that will cap max length, then use this value as ship search range.
        float max = cir.getReturnValue();

        Ship ship = VSGameUtilsKt.getShipManagingPos(level, start);
        if (ship != null) {
            Vector3d startVec = ship.getTransform().getShipToWorld().transformPosition(new Vector3d(start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5));
            Vector3d direction = ship.getTransform().getShipToWorld().transformDirection(VectorConversionsMCKt.toJOMLD(facing.getNormal()));
            startVec.add(direction.x, direction.y, direction.z);
            direction.mul(max);
            Vec3 mcStart = VectorConversionsMCKt.toMinecraft(startVec);
            BlockHitResult result = RaycastUtilsKt.clipIncludeShips(level,
                    new ClipContext(
                            mcStart,
                            VectorConversionsMCKt.toMinecraft(startVec.add(direction.x, direction.y, direction.z)),
                            ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE,
                            null), true,
                            // Skipping own ship as block obstructions were already calculated by Create. Besides,
                            // solid blocks like blaze burners or fan catalysts (from addons) can be used for processing
                            // instead of obstructing airflow. Not accounting for this used to cause this bug:
                            // https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/1161
                            ship.getId());

            // Distance from start to end but, its not squared so, slow -_-
            cir.setReturnValue((float) result.getLocation().distanceTo(mcStart));
        } else {
            BlockPos end = start.relative(facing, (int) max);
            if (VSGameUtilsKt.getShipsIntersecting(level,
                    new AABB(start.getX(), start.getY(), start.getZ(),
                            end.getX() + 1.0, end.getY() + 1.0, end.getZ() + 1.0)).iterator().hasNext()) {
                Vec3 centerStart = Vec3.atCenterOf(start);
                BlockHitResult result = RaycastUtilsKt.clipIncludeShips(level,
                        new ClipContext(
                                centerStart.add(facing.getStepX(), facing.getStepY(), facing.getStepZ()),
                                Vec3.atCenterOf(end),
                                ClipContext.Block.OUTLINE,
                                ClipContext.Fluid.NONE,
                                null));

                // Distance from start to end but, its not squared so, slow -_-
                cir.setReturnValue((float) result.getLocation().distanceTo(centerStart));
            }
        }
    }

    @Redirect(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;intersects(Lnet/minecraft/world/phys/AABB;)Z"), require = 0)
    private boolean redirectIntersects(AABB instance, AABB other) {
        Ship ship = getShip();
        if (ship != null) {
            AABBd thisAABB = VectorConversionsMCKt.toJOML(instance);
            thisAABB.transform(ship.getWorldToShip());
            return other.intersects(thisAABB.minX, thisAABB.minY, thisAABB.minZ, thisAABB.maxX, thisAABB.maxY, thisAABB.maxZ);
        } else return instance.intersects(other);
    }

    // While getCenter is unstable between versions, it is only used as an argument of distanceTo which is only called once.
    @ModifyArg(method = "tickAffectedEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D", ordinal = 0),
        index = 0
    )
    private Vec3 transformCenter(Vec3 center) {
        Ship ship = getShip();
        if (ship != null && this.source.getAirCurrentWorld() != null) {
            Vector3d tempVec = new Vector3d();
            ship.getTransform().getShipToWorld().transformPosition(center.x, center.y, center.z, tempVec);
            center = VectorConversionsMCKt.toMinecraft(tempVec);
        }
        return center;
    }

    // Ordinals used here are correct both for v0.5.1 and v6 and in fact are stable all the way from Create v0.3.
    @WrapOperation(method = "tickAffectedEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V")
    )
    private void redirectSetDeltaMovement(Entity instance, Vec3 motion,
        Operation<Void> original,
        @Local(ordinal = 2) float acceleration, @Local(ordinal = 3) float maxAcceleration, @Local(ordinal = 0) Vec3i flow
    ) {
        Ship ship = getShip();
        if (ship != null) {
            Vector3d tempVec = new Vector3d();
            ship.getTransform().getShipToWorld().transformDirection(flow.getX(), flow.getY(), flow.getZ(), tempVec);
            Vec3 transformedFlow = VectorConversionsMCKt.toMinecraft(tempVec);

            Vec3 previousMotion = instance.getDeltaMovement();
            double xIn = Mth.clamp(transformedFlow.x * acceleration - previousMotion.x, -maxAcceleration, maxAcceleration);
            double yIn = Mth.clamp(transformedFlow.y * acceleration - previousMotion.y, -maxAcceleration, maxAcceleration);
            double zIn = Mth.clamp(transformedFlow.z * acceleration - previousMotion.z, -maxAcceleration, maxAcceleration);
            motion = previousMotion.add(new Vec3(xIn, yIn, zIn).scale(1 / 8f));
        }
        original.call(instance, motion);
    }
}
