package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.foundation.utility.VecHelper;
import java.util.Iterator;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.IExtendedAirCurrentSource;

@Mixin(AirCurrent.class)
public abstract class MixinAirCurrent {

    @Unique
    private final float maxAcceleration = 5;
    @Shadow
    @Final
    public IAirCurrentSource source;
    @Unique
    private Vec3 transformedFlow = Vec3.ZERO;
    @Unique
    private float acceleration;

    @Unique
    private Ship getShip() {
        if (source instanceof IExtendedAirCurrentSource se)
            return se.getShip();
        else if (source.getAirCurrentWorld() != null)
            return VSGameUtilsKt.getShipManagingPos(source.getAirCurrentWorld(), source.getAirCurrentPos());
        else
            return null;
    }

    @Inject(method = "getFlowLimit", at = @At("HEAD"), cancellable = true)
    private static void clipFlowLimit(Level level, BlockPos start, float max, Direction facing, CallbackInfoReturnable<Float> cir) {
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
                            null));

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

    @Redirect(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;intersects(Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean redirectIntersects(AABB instance, AABB other) {
        Ship ship = getShip();
        if (ship != null) {
            AABBd thisAABB = VectorConversionsMCKt.toJOML(instance);
            thisAABB.transform(ship.getWorldToShip());
            return other.intersects(thisAABB.minX, thisAABB.minY, thisAABB.minZ, thisAABB.maxX, thisAABB.maxY, thisAABB.maxZ);
        } else return instance.intersects(other);
    }

    @Inject(
            method = "tickAffectedEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void harvester(Level world, Direction facing, CallbackInfo ci, Iterator<Entity> iterator, Entity entity, Vec3 center, Vec3i flow, float sneakModifier, float speed, double entityDistance, float acceleration) {
        Ship ship = getShip();
        if (ship != null) {
            Vector3d tempVec = new Vector3d();
            ship.getTransform().getShipToWorld().transformDirection(flow.getX(), flow.getY(), flow.getZ(), tempVec);
            transformedFlow = VectorConversionsMCKt.toMinecraft(tempVec);
        }
        this.acceleration = acceleration;
    }

    @Redirect(method = "tickAffectedEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V")
    )
    private void redirectSetDeltaMovement(Entity instance, Vec3 motion) {
        Ship ship = getShip();
        if (ship != null) {
            Vec3 previousMotion = instance.getDeltaMovement();
            double xIn = Mth.clamp(transformedFlow.x * acceleration - previousMotion.x, -maxAcceleration, maxAcceleration);
            double yIn = Mth.clamp(transformedFlow.y * acceleration - previousMotion.y, -maxAcceleration, maxAcceleration);
            double zIn = Mth.clamp(transformedFlow.z * acceleration - previousMotion.z, -maxAcceleration, maxAcceleration);
            instance.setDeltaMovement(previousMotion.add(new Vec3(xIn, yIn, zIn).scale(1 / 8f)));
        } else {
            instance.setDeltaMovement(motion);
        }
    }

    @Redirect(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/utility/VecHelper;getCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"), allow = 1)
    private Vec3 redirectGetCenterOf(Vec3i pos) {
        Ship ship = getShip();
        Vec3 result = VecHelper.getCenterOf(pos);
        if (ship != null && this.source.getAirCurrentWorld() != null) {
            Vector3d tempVec = new Vector3d();
            ship.getTransform().getShipToWorld().transformPosition(result.x, result.y, result.z, tempVec);
            result = VectorConversionsMCKt.toMinecraft(tempVec);
        }
        return result;
    }
}
