package org.valkyrienskies.mod.mixin.mod_compat.create;

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
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.DefaultShipyardEntityHandler;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.IExtendedAirCurrentSource;


@Mixin(value = AirCurrent.class)
public abstract class MixinAirCurrent {

    @Shadow
    @Final
    public IAirCurrentSource source;

    @Shadow
    public Direction direction;

    @Unique
    double vs_scalingFactor = 1.0;

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

            Vector3dc scaling = ship.getTransform().getShipToWorldScaling();
            Matrix4d rescaledId = new Matrix4d().scale(1 / scaling.x(), 1 / scaling.y(), 1 / scaling.z());
            double vs_scalingFactor = VectorConversionsMCKt.transformDirection(
                rescaledId,
                facing
            ).length();

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
            cir.setReturnValue((float) (result.getLocation().distanceTo(mcStart) * vs_scalingFactor));
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

    @Inject(method = "rebuild", at = @At("TAIL"), remap = false)
    private void calcScaling(CallbackInfo ci) {
        Ship ship = getShip();
        if (ship != null) {
            Vector3dc scaling = ship.getTransform().getShipToWorldScaling();
            Matrix4d rescaledId = new Matrix4d().scale(1 / scaling.x(), 1 / scaling.y(), 1 / scaling.z());
            vs_scalingFactor = VectorConversionsMCKt.transformDirection(
                rescaledId,
                direction // Guaranteed to be non-null as this mixin only fires after direction is initialized.
            ).length();
        }
    }

    /**
     * Raycasting from ships works in shipspace coordinates, so range is lower for miniships or higher for jumboships.
     * Changing maxDistance to account for that is undesired as this value is also used for same-space processing
     * such as depots and belts. Instead we modify whatever is necessary specifically for entity interactions.
     */
    @WrapOperation(method = "rebuild", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 rescaleEntityInteractionAABB(Vec3 instance, double d, Operation<Vec3> original) {
        Vec3 result = original.call(instance, d);

        Ship ship = getShip();
        if (ship != null) {
            result.scale(vs_scalingFactor);
        }
        return result;
    }

    /**
     * On scaled ships we move the entity position closer to the current source, so that subsequently called distance
     * calculations that might or might not be Create-specific give a value accounted for ship-to-world scaling.
     */
    @WrapOperation(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 transformEntityPos(Entity instance, Operation<Vec3> original) {
        Vec3 result = original.call(instance);

        Ship ship = getShip();
        if (ship != null && !(VSEntityManager.INSTANCE.getHandler(instance) instanceof DefaultShipyardEntityHandler)) {
            Vector3dc sourcePos = VectorConversionsMCKt.toJOML(source.getAirCurrentPos().getCenter());
            Vector3dc naiveEntityPos = ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(result));

            Vector3dc distanceFromSource = VectorConversionsMCKt.toJOML(source.getAirCurrentPos().getCenter()).sub(naiveEntityPos);
            Vector3dc adjustedEntityPos = sourcePos.sub(
                distanceFromSource.mul(1 / vs_scalingFactor, new Vector3d()),
                new Vector3d()
            );
            return VectorConversionsMCKt.toMinecraft(adjustedEntityPos);
        } else return result;
    }

    /**
     * Our fake entity position is really useful for all ship- and scale-aware of distance calculations, particles
     * should be spawned where the entity actually is.
     */
    @ModifyArg(
        method = "tickAffectedEntities",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;spawnProcessingParticles(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;)V"),
        index = 1
    )
    private Vec3 useRealEntityPosition(Vec3 pos, @Local Entity entity) {
        return entity.position();
    }

    @Redirect(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;intersects(Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean redirectIntersects(AABB entityAABB, AABB boundsAABB) {
        Ship ship = getShip();
        if (ship != null) {
            // AABB of item entities is small so it can be transformed to ship even if rotated. Transforming ship AABB
            // to world would make it way too large, producing false positives for "should item be processed" checks.
            AABBd entityInShipAABB = VectorConversionsMCKt.toJOML(entityAABB).transform(ship.getWorldToShip());
            return boundsAABB
                // Making the AABB just a bit longer for some wiggle room. Solves items not being processed when reaching the end of airflow.
                .intersects(
                    entityInShipAABB.minX, entityInShipAABB.minY, entityInShipAABB.minZ,
                    entityInShipAABB.maxX, entityInShipAABB.maxY, entityInShipAABB.maxZ
                );
        } else return entityAABB.intersects(boundsAABB);
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

        if (ship != null && !(VSEntityManager.INSTANCE.getHandler(instance) instanceof DefaultShipyardEntityHandler)) {
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
