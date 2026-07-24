package org.valkyrienskies.mod.mixin.feature.entity_collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import org.joml.Vector3d;
import org.joml.Vector3dc;
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
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.ShipPathfindingUtils;
import org.valkyrienskies.mod.mixinducks.world.entity.EntityShipGroundingDuck;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntityDraggingInformationProvider, EntityShipGroundingDuck {

    // region collision

    @Shadow
    public abstract BlockPos blockPosition();

    @Shadow
    public boolean hasImpulse;
    @Shadow
    protected boolean firstTick;
    @Shadow
    public int tickCount;

    @Shadow
    public abstract void setPos(Vec3 arg);

    @Shadow
    public abstract boolean is(Entity arg);

    @Shadow
    public abstract boolean isControlledByLocalInstance();

    @Shadow
    public abstract EntityType<?> getType();

    @Shadow
    public abstract Iterable<Entity> getIndirectPassengers();

    @Shadow
    public abstract BlockPos getOnPos();

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
            if (entityDraggingInformation.getIgnoreNextGroundStand()) {
                entityDraggingInformation.setIgnoreNextGroundStand(false);
                return collisionResultWithWorld;
            }
            entityDraggingInformation.setLastShipStoodOn(null);
            entityDraggingInformation.setAddedYawRotLastTick(0.0);

            for (Entity entityRiding : entity.getIndirectPassengers()) {
                final EntityDraggingInformation passengerDraggingInformation =
                    ((IEntityDraggingInformationProvider) entityRiding).getDraggingInformation();
                passengerDraggingInformation.setLastShipStoodOn(null);
                passengerDraggingInformation.setAddedYawRotLastTick(0.0);
            }
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
            final Vec3 deltaMovement = this.getDeltaMovement();

            final Vector3dc collisionResponseHorizontalNormal = collisionResponseHorizontal.normalize(new Vector3d());
            final double parallelHorizontalVelocityComponent =
                collisionResponseHorizontalNormal
                    .dot(deltaMovement.x, 0.0, deltaMovement.z);

            setDeltaMovement(
                deltaMovement.x - collisionResponseHorizontalNormal.x() * parallelHorizontalVelocityComponent,
                deltaMovement.y,
                deltaMovement.z - collisionResponseHorizontalNormal.z() * parallelHorizontalVelocityComponent
            );
        }
    }
    // endregion

    // This cancels the actual setDeltaMovement. We can't cancel it in the @Inject, that cancels the entire move function. Somehow this doesn't interfere with the @Inject, crazy.
    @Redirect(
        method = "move",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(DDD)V"
        )
    )
    private void cancelSetDeltaMovement(Entity instance, double d, double e, double f) {}


    // region Block standing on friction and sprinting particles mixins
    @Unique
    private int vs$shipSupportCacheTick = Integer.MIN_VALUE;
    @Unique
    private double vs$shipSupportCacheMinX;
    @Unique
    private double vs$shipSupportCacheMinY;
    @Unique
    private double vs$shipSupportCacheMinZ;
    @Unique
    private double vs$shipSupportCacheMaxX;
    @Unique
    private double vs$shipSupportCacheMaxY;
    @Unique
    private double vs$shipSupportCacheMaxZ;
    @Unique
    private BlockPos vs$shipSupportCacheResult;

    @Unique
    private BlockPos getPosStandingOnFromShips() {
        final AABB box = getBoundingBox();
        if (vs$isShipSupportCacheValid(box)) {
            return vs$shipSupportCacheResult;
        }

        final BlockPos result = ShipPathfindingUtils.findSupportingShipBlock(level, Entity.class.cast(this), box);
        vs$shipSupportCacheTick = tickCount;
        vs$shipSupportCacheMinX = box.minX;
        vs$shipSupportCacheMinY = box.minY;
        vs$shipSupportCacheMinZ = box.minZ;
        vs$shipSupportCacheMaxX = box.maxX;
        vs$shipSupportCacheMaxY = box.maxY;
        vs$shipSupportCacheMaxZ = box.maxZ;
        vs$shipSupportCacheResult = result;
        return result;
    }

    @Unique
    private boolean vs$isShipSupportCacheValid(final AABB box) {
        return vs$shipSupportCacheTick == tickCount
            && vs$shipSupportCacheMinX == box.minX
            && vs$shipSupportCacheMinY == box.minY
            && vs$shipSupportCacheMinZ == box.minZ
            && vs$shipSupportCacheMaxX == box.maxX
            && vs$shipSupportCacheMaxY == box.maxY
            && vs$shipSupportCacheMaxZ == box.maxZ;
    }

    @Inject(method = "getBlockPosBelowThatAffectsMyMovement", at = @At("HEAD"), cancellable = true)
    private void preGetBlockPosBelowThatAffectsMyMovement(final CallbackInfoReturnable<BlockPos> cir) {
        final BlockPos blockPosStandingOnFromShip = getPosStandingOnFromShips();
        if (blockPosStandingOnFromShip != null) {
            cir.setReturnValue(blockPosStandingOnFromShip);
        }
    }


    /**
     * @author tri0de
     * @reason Allows ship blocks to spawn landing particles, running particles, and play step sounds
     */
    @Inject(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true)
    private void preGetOnPos(final CallbackInfoReturnable<BlockPos> cir) {
        final BlockPos blockPosStandingOnFromShip = getPosStandingOnFromShips();
        if (blockPosStandingOnFromShip != null) {
            cir.setReturnValue(blockPosStandingOnFromShip);
        }
    }

    @WrapOperation(
        method = "move",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setOnGroundWithKnownMovement(ZLnet/minecraft/world/phys/Vec3;)V"
        )
    )
    private void addShipSupportToOnGroundState(final Entity entity, final boolean onGround, final Vec3 movement,
        final Operation<Void> original) {
        final BlockPos shipSupportPos =
            onGround ? null : getPosStandingOnFromShips();
        final boolean shouldBeGrounded = onGround || shipSupportPos != null;
        original.call(entity, shouldBeGrounded, movement);
        if (shipSupportPos != null) {
            vs$setShipSupportingBlock(shipSupportPos);
        }
    }

    @Override
    public void vs$setShipSupportingBlock(final BlockPos blockPos) {
        this.mainSupportingBlockPos = Optional.of(blockPos.immutable());
        this.onGroundNoBlocks = false;
    }

    @WrapOperation(method = "spawnSprintParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos skipBlockPosition(final Entity entity, final Operation<BlockPos> original, @Local final BlockPos posOn) {
        if (VSGameUtilsKt.isBlockInShipyard(level, posOn)) return posOn;
        return original.call(entity);
    }

    /**
     * This will set the entity impulsed if it is dragged by a ship on its first tick.
     * Marking impulse forces the sync over server-client, so this will also sync dragging information.
     */
    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void markImpulsedFirstTick(CallbackInfo ci) {
        if (firstTick && getDraggingInformation().isEntityBeingDraggedByAShip() && !level.isClientSide) {
            hasImpulse = true;
        }
    }

    @Inject(
        method = "baseTick",
        at = @At("TAIL")
    )
    private void postBaseTick(final CallbackInfo ci) {
        final EntityDraggingInformation entityDraggingInformation = getDraggingInformation();
        final Entity self = Entity.class.cast(this);

        if (level != null && level.isClientSide && tickCount > 1) { //baseTick sets the firstTick false, use tickCount instead.
//            if (!(self.isControlledByLocalInstance() || (self instanceof final Player player && player.isLocalPlayer()))) {
//                entityDraggingInformation.setMountedToEntity(self.getVehicle() != null);
//                return;
//            }
            final BlockPos onPos = getOnPos();
            final Ship ship = VSGameUtilsKt.getLoadedShipManagingPos(level, onPos);
            if (ship != null) {
//                if (entityDraggingInformation.getLastShipStoodOnServerWriteOnly() == null) {
//                    return;
//                }
                entityDraggingInformation.setLastShipStoodOn(ship.getId());
                getIndirectPassengers().forEach(entity -> {
                    final EntityDraggingInformation passengerDraggingInformation =
                        ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                    passengerDraggingInformation.setLastShipStoodOn(ship.getId());
                });
            } else {
                if (!level.getBlockState(onPos).isAir()) {
                    if (entityDraggingInformation.getIgnoreNextGroundStand()) {
                        entityDraggingInformation.setIgnoreNextGroundStand(false);
                    } else {
//                        if (entityDraggingInformation.getLastShipStoodOnServerWriteOnly() != null) {
//                            return;
//                        }
                        entityDraggingInformation.setLastShipStoodOn(null);
                        getIndirectPassengers().forEach(entity -> {
                            final EntityDraggingInformation passengerDraggingInformation =
                                ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                            passengerDraggingInformation.setLastShipStoodOn(null);
                        });
                    }

                }
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

    @Shadow
    public Optional<BlockPos> mainSupportingBlockPos;

    @Shadow
    private boolean onGroundNoBlocks;
    // endregion
}
