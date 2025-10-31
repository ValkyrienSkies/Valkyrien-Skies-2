package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
    private double shipScale = 1.0;

    @Shadow
    private static boolean shouldAlwaysPass(BlockState state) {
        return false;
    }

    @Unique
    private Ship getShip() {
        if (source instanceof IExtendedAirCurrentSource se) {
            return se.getShip();
        }
        if (source.getAirCurrentWorld() != null) {
            return VSGameUtilsKt.getShipManagingPos(source.getAirCurrentWorld(), source.getAirCurrentPos());
        }
        return null;
    }

    @Inject(method = "getFlowLimit", at = @At("RETURN"), cancellable = true, remap = false)
    private static void clipFlowLimit(Level level, BlockPos start, float originalMax, Direction facing, CallbackInfoReturnable<Float> cir) {
        // First let Create do its job at finding block obstructions that will cap max length, then use this value as ship search range.
        final float flowLimit = cir.getReturnValue();

        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, start);
        if (ship != null) {
            final Vector3d startVec = new Vector3d(start.getX(), start.getY(), start.getZ());
            final Vector3d direction = VectorConversionsMCKt.toJOMLD(facing.getNormal());
            startVec.add(0.5, 0.5, 0.5).add(direction.mul(0.5, new Vector3d()));
            ship.getTransform().getShipToWorld().transformPosition(startVec);
            ship.getTransform().getShipToWorld().transformDirection(direction);

            final Vector3dc scaling = ship.getTransform().getShipToWorldScaling();
            final double shipScale = facing.getAxis().choose(scaling.x(), scaling.y(), scaling.z());

            direction.mul(flowLimit);
            final Vec3 startPos = VectorConversionsMCKt.toMinecraft(startVec);
            final Vec3 endPos = VectorConversionsMCKt.toMinecraft(startVec.add(direction.x, direction.y, direction.z));
            final BlockHitResult result = level.clip(new AirFlowClipContext(startPos, endPos, start));

            // Distance from start to end but, its not squared so, slow -_-
            cir.setReturnValue((float) (result.getLocation().distanceTo(startPos) * shipScale));
        } else {
            final BlockPos end = start.relative(facing, (int) (Math.ceil(flowLimit)));
            if (
                VSGameUtilsKt.getShipsIntersecting(
                    level,
                    new AABB(start.getX(), start.getY(), start.getZ(), end.getX() + 1, end.getY() + 1, end.getZ() + 1)
                )
                    .iterator()
                    .hasNext()
            ) {
                final Vec3 startPos = Vec3.atCenterOf(start).add(facing.getStepX() * 0.5, facing.getStepY() * 0.5, facing.getStepZ() * 0.5);
                final BlockHitResult result = level.clip(new AirFlowClipContext(startPos, Vec3.atCenterOf(end), start));
                // Distance from start to end but, its not squared so, slow -_-
                cir.setReturnValue(Math.min((float) (result.getLocation().distanceTo(startPos)), flowLimit));
            }
        }
    }

    @Inject(method = "rebuild", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/fan/IAirCurrentSource;getAirCurrentWorld()Lnet/minecraft/world/level/Level;"), remap = false)
    private void calcScaling(CallbackInfo ci) {
        Ship ship = this.getShip();
        if (ship != null) {
            final Vector3dc scaling = ship.getTransform().getShipToWorldScaling();
            this.shipScale = this.direction.getAxis().choose(scaling.x(), scaling.y(), scaling.z());
        }
    }

    /**
     * Raycasting from ships works in shipspace coordinates, so range is lower for miniships or higher for jumboships.
     * Changing maxDistance to account for that is undesired as this value is also used for same-space processing
     * such as depots and belts. Instead we modify whatever is necessary specifically for entity interactions.
     */
    @WrapOperation(method = "rebuild", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 rescaleEntityInteractionAABB(Vec3 instance, double scale, Operation<Vec3> original) {
        Ship ship = this.getShip();
        if (ship != null) {
            scale *= this.shipScale;
        }
        return original.call(instance, scale);
    }

    /**
     * On scaled ships we move the entity position closer to the current source, so that subsequently called distance
     * calculations that might or might not be Create-specific give a value accounted for ship-to-world scaling.
     */
    @WrapOperation(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 transformEntityPos(Entity instance, Operation<Vec3> original) {
        Vec3 result = original.call(instance);

        Ship ship = this.getShip();
        if (ship == null || VSEntityManager.INSTANCE.getHandler(instance) instanceof DefaultShipyardEntityHandler) {
            return result;
        }
        Vector3dc sourcePos = VectorConversionsMCKt.toJOML(source.getAirCurrentPos().getCenter());
        Vector3dc naiveEntityPos = ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(result));

        Vector3dc distanceFromSource = VectorConversionsMCKt.toJOML(source.getAirCurrentPos().getCenter()).sub(naiveEntityPos);
        Vector3dc adjustedEntityPos = sourcePos.sub(
            distanceFromSource.mul(1 / this.shipScale, new Vector3d()),
            new Vector3d()
        );
        return VectorConversionsMCKt.toMinecraft(adjustedEntityPos);
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
        Ship ship = this.getShip();
        if (ship == null) {
            return entityAABB.intersects(boundsAABB);
        }
        AABBd entityInShipAABB = VectorConversionsMCKt.toJOML(entityAABB).transform(ship.getWorldToShip());
        AABBd boundsInWorldAABB = VectorConversionsMCKt.toJOML(boundsAABB).transform(ship.getShipToWorld());
        return
            boundsAABB.intersects(
                entityInShipAABB.minX, entityInShipAABB.minY, entityInShipAABB.minZ,
                entityInShipAABB.maxX, entityInShipAABB.maxY, entityInShipAABB.maxZ
            ) &&
            entityAABB.intersects(
                boundsInWorldAABB.minX, boundsInWorldAABB.minY, boundsInWorldAABB.minZ,
                boundsInWorldAABB.maxX, boundsInWorldAABB.maxY, boundsInWorldAABB.maxZ
            );
    }

    // Ordinals used here are correct both for v0.5.1 and v6 and in fact are stable all the way from Create v0.3.
    @WrapOperation(method = "tickAffectedEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V")
    )
    private void redirectSetDeltaMovement(Entity instance, Vec3 motion,
        Operation<Void> original,
        @Local(ordinal = 2) float acceleration, @Local(ordinal = 3) float maxAcceleration, @Local(ordinal = 0) Vec3i flow
    ) {
        Ship ship = this.getShip();

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

    private static final class AirFlowClipContext extends ClipContext {
        private final BlockPos source;

        public AirFlowClipContext(final Vec3 from, final Vec3 to, final BlockPos source) {
            super(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
            this.source = source;
        }

        @Override
        public VoxelShape getBlockShape(final BlockState state, final BlockGetter level, final BlockPos pos) {
            if (pos.equals(this.source)) {
                return Shapes.empty();
            }
            final BlockState copycat = CopycatBlock.getMaterial(level, pos);
            if (shouldAlwaysPass(copycat.isAir() ? state : copycat)) {
                return Shapes.empty();
            }
            return super.getBlockShape(state, level, pos);
        }
    }
}
