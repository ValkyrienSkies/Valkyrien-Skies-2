package org.valkyrienskies.mod.mixin.feature.entity_collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntityDraggingInformationProvider {

    // region collision

    /**
     * Cancel movement of entities that are colliding with unloaded ships
     */
    @Inject(
        at = @At("HEAD"),
        method = "move",
        cancellable = true
    )
    private void beforeMove(final MoverType type, final Vec3 pos, final CallbackInfo ci) {
        if (EntityShipCollisionUtils.isCollidingWithUnloadedShips(Entity.class.cast(this))) {
            ci.cancel();
        }
    }

    /**
     * Allows entities to collide with ships by modifying the movement vector.
     */
    @WrapOperation(
        method = "move",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    public Vec3 collideWithShips(final Entity entity, Vec3 movement, final Operation<Vec3> collide) {
        final AABB box = this.getBoundingBox();
        movement = EntityShipCollisionUtils.INSTANCE
            .adjustEntityMovementForShipCollisions(entity, movement, box, this.level);
        final Vec3 collisionResultWithWorld = collide.call(entity, movement);

        if (collisionResultWithWorld.distanceToSqr(movement) > 1e-12) {
            // We collided with the world? Set the dragging ship to null.
            final EntityDraggingInformation entityDraggingInformation = getDraggingInformation();
            entityDraggingInformation.setLastShipStoodOn(null);
            entityDraggingInformation.setAddedMovementLastTick(new Vector3d());
            entityDraggingInformation.setAddedYawRotLastTick(0.0);
        }

        return collisionResultWithWorld;
    }

    /**
     * This mixin replaces the following code in Entity.move().
     *
     * <p>if (movement.x != vec3d.x) { this.setVelocity(0.0D, vec3d2.y, vec3d2.z); } </p>
     *
     * <p>if (movement.z != vec3d.z) { this.setVelocity(vec3d2.x, vec3d2.y, 0.0D); } </p>
     *
     * <p>This code makes accurate collision with non axis-aligned surfaces impossible, so this mixin replaces it. </p>
     */
    @Inject(method = "move", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(DDD)V"),
        locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true
    )
    private void redirectSetVelocity(final MoverType moverType, final Vec3 movement, final CallbackInfo callbackInfo,
        final Vec3 movementAdjustedForCollisions) {

        // Compute the collision response horizontal
        final Vector3dc collisionResponseHorizontal =
            new Vector3d(movementAdjustedForCollisions.x - movement.x, 0.0,
                movementAdjustedForCollisions.z - movement.z);

        // Remove the component of [movementAdjustedForCollisions] that is parallel to [collisionResponseHorizontal]
        if (collisionResponseHorizontal.lengthSquared() > 1e-6) {
            final Vec3 deltaMovement = getDeltaMovement();

            final Vector3dc collisionResponseHorizontalNormal = collisionResponseHorizontal.normalize(new Vector3d());
            final double parallelHorizontalVelocityComponent =
                collisionResponseHorizontalNormal
                    .dot(deltaMovement.x, 0.0, deltaMovement.z);

            setDeltaMovement(
                deltaMovement.x
                    - collisionResponseHorizontalNormal.x() * parallelHorizontalVelocityComponent,
                deltaMovement.y,
                deltaMovement.z
                    - collisionResponseHorizontalNormal.z() * parallelHorizontalVelocityComponent
            );
        }
        // Cancel the original invocation of Entity.setVelocity(DDD)V to remove vanilla behavior
        callbackInfo.cancel();
    }

    // endregion

    // region Block standing on friction and sprinting particles mixins
    @Unique
    private BlockPos getPosStandingOnFromShips(final Vector3dc blockPosInGlobal) {
        final double radius = 0.5;
        final AABBdc testAABB = new AABBd(
            blockPosInGlobal.x() - radius, blockPosInGlobal.y() - radius, blockPosInGlobal.z() - radius,
            blockPosInGlobal.x() + radius, blockPosInGlobal.y() + radius, blockPosInGlobal.z() + radius
        );
        final Iterable<Ship> intersectingShips = VSGameUtilsKt.getShipsIntersecting(level, testAABB);
        for (final Ship ship : intersectingShips) {
            final Vector3dc blockPosInLocal =
                ship.getTransform().getWorldToShip().transformPosition(blockPosInGlobal, new Vector3d());
            final BlockPos blockPos = BlockPos.containing(
                Math.floor(blockPosInLocal.x()), Math.floor(blockPosInLocal.y()), Math.floor(blockPosInLocal.z())
            );
            final BlockState blockState = level.getBlockState(blockPos);
            if (!blockState.isAir()) {
                return blockPos;
            } else {
                // Check the block below as well, in the cases of fences
                final Vector3dc blockPosInLocal2 = ship.getTransform().getWorldToShip()
                    .transformPosition(
                        new Vector3d(blockPosInGlobal.x(), blockPosInGlobal.y() - 1.0, blockPosInGlobal.z()));
                final BlockPos blockPos2 = BlockPos.containing(
                    Math.round(blockPosInLocal2.x()), Math.round(blockPosInLocal2.y()), Math.round(blockPosInLocal2.z())
                );
                final BlockState blockState2 = level.getBlockState(blockPos2);
                if (!blockState2.isAir()) {
                    return blockPos2;
                }
            }
        }
        return null;
    }

    @Inject(method = "getBlockPosBelowThatAffectsMyMovement", at = @At("HEAD"), cancellable = true)
    private void preGetBlockPosBelowThatAffectsMyMovement(final CallbackInfoReturnable<BlockPos> cir) {
        final Vector3dc blockPosInGlobal = new Vector3d(
            position.x,
            getBoundingBox().minY - 0.5,
            position.z
        );
        final BlockPos blockPosStandingOnFromShip = getPosStandingOnFromShips(blockPosInGlobal);
        if (blockPosStandingOnFromShip != null) {
            cir.setReturnValue(blockPosStandingOnFromShip);
        }
    }

    /**
     * @author tri0de
     * @reason Allows ship blocks to spawn landing particles, running particles, and play step sounds
     */
    @Inject(method = "getOnPos", at = @At("HEAD"), cancellable = true)
    private void preGetOnPos(final CallbackInfoReturnable<BlockPos> cir) {
        final Vector3dc blockPosInGlobal = new Vector3d(
            position.x,
            position.y - 0.2,
            position.z
        );
        final BlockPos blockPosStandingOnFromShip = getPosStandingOnFromShips(blockPosInGlobal);
        if (blockPosStandingOnFromShip != null) {
            cir.setReturnValue(blockPosStandingOnFromShip);
        }
    }

    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void preSpawnSprintParticle(final CallbackInfo ci) {
        final Vector3dc blockPosInGlobal = new Vector3d(
            position.x,
            position.y - 0.2,
            position.z
        );
        final BlockPos blockPosStandingOnFromShip = getPosStandingOnFromShips(blockPosInGlobal);
        if (blockPosStandingOnFromShip != null) {
            final BlockState blockState = this.level.getBlockState(blockPosStandingOnFromShip);
            if (blockState.getRenderShape() != RenderShape.INVISIBLE) {
                final Vec3 vec3 = this.getDeltaMovement();
                this.level.addParticle(
                    new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                    this.getX() + (this.random.nextDouble() - 0.5) * (double) this.dimensions.width,
                    this.getY() + 0.1,
                    this.getZ() + (this.random.nextDouble() - 0.5) * (double) this.dimensions.width,
                    vec3.x * -4.0,
                    1.5,
                    vec3.z * -4.0
                );
                ci.cancel();
            }
        }
    }
    // endregion

    // region shadow functions and fields
    @Shadow
    public Level level;

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract void setDeltaMovement(double x, double y, double z);

    @Shadow
    protected abstract Vec3 collide(Vec3 vec3d);

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract double getZ();

    @Shadow
    public abstract double getY();

    @Shadow
    public abstract double getX();

    @Shadow
    private Vec3 position;

    @Shadow
    @Final
    protected RandomSource random;

    @Shadow
    private EntityDimensions dimensions;
    // endregion
}
