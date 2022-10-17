package org.valkyrienskies.mod.mixin.entity;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;

import java.util.Random;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntityDraggingInformationProvider {

    @Unique
    private final EntityDraggingInformation draggingInformation = new EntityDraggingInformation();

    @Redirect(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
        )
    )
    public BlockHitResult addShipsToRaycast(final Level receiver, final ClipContext ctx) {
        return RaycastUtilsKt.clipIncludeShipsClient((ClientLevel) receiver, ctx);
    }

    @Inject(
        at = @At("TAIL"),
        method = "checkInsideBlocks"
    )
    private void afterCheckInside(final CallbackInfo ci) {
        final AABBd boundingBox = toJOML(getBoundingBox());
        final AABBd temp = new AABBd();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, boundingBox)) {
            final AABBd inShipBB = boundingBox.transform(ship.getShipTransform().getWorldToShipMatrix(), temp);
            originalCheckInside(inShipBB);
        }
    }

    @Unique
    private void originalCheckInside(final AABBd aABB) {
        final Entity self = Entity.class.cast(this);
        final BlockPos blockPos = new BlockPos(aABB.minX + 0.001, aABB.minY + 0.001, aABB.minZ + 0.001);
        final BlockPos blockPos2 = new BlockPos(aABB.maxX - 0.001, aABB.maxY - 0.001, aABB.maxZ - 0.001);
        final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        if (this.level.hasChunksAt(blockPos, blockPos2)) {
            for (int i = blockPos.getX(); i <= blockPos2.getX(); ++i) {
                for (int j = blockPos.getY(); j <= blockPos2.getY(); ++j) {
                    for (int k = blockPos.getZ(); k <= blockPos2.getZ(); ++k) {
                        mutableBlockPos.set(i, j, k);
                        final BlockState blockState = this.level.getBlockState(mutableBlockPos);

                        try {
                            blockState.entityInside(this.level, mutableBlockPos, self);
                            this.onInsideBlock(blockState);
                        } catch (final Throwable var12) {
                            final CrashReport crashReport =
                                CrashReport.forThrowable(var12, "Colliding entity with block");
                            final CrashReportCategory crashReportCategory =
                                crashReport.addCategory("Block being collided with");
                            CrashReportCategory.populateBlockDetails(crashReportCategory, mutableBlockPos, blockState);
                            throw new ReportedException(crashReport);
                        }
                    }
                }
            }
        }
    }

    /**
     * @reason Needed for players to pick blocks correctly when mounted to a ship
     */
    @Inject(method = "getEyePosition", at = @At("HEAD"), cancellable = true)
    private void preGetEyePosition(final float partialTicks, final CallbackInfoReturnable<Vec3> cir) {
        final ShipObject shipMountedTo =
            VSGameUtilsKt.getShipObjectEntityMountedTo(level, Entity.class.cast(this));
        if (shipMountedTo == null) {
            return;
        }

        final ShipObject shipObject = shipMountedTo;
        final ShipTransform shipTransform;
        if (shipObject instanceof ShipObjectClient) {
            shipTransform = ((ShipObjectClient) shipObject).getRenderTransform();
        } else {
            shipTransform = shipObject.getShipData().getShipTransform();
        }
        final Vector3dc basePos = shipTransform.getShipToWorldMatrix()
            .transformPosition(VSGameUtilsKt.getPassengerPos(this.vehicle, partialTicks), new Vector3d());
        final Vector3dc eyeRelativePos = shipTransform.getShipCoordinatesToWorldCoordinatesRotation().transform(
            new Vector3d(0.0, getEyeHeight(), 0.0)
        );
        final Vec3 newEyePos = VectorConversionsMCKt.toMinecraft(basePos.add(eyeRelativePos, new Vector3d()));
        cir.setReturnValue(newEyePos);
    }

    /**
     * @reason Needed for players to pick blocks correctly when mounted to a ship
     */
    @Inject(method = "calculateViewVector", at = @At("HEAD"), cancellable = true)
    private void preCalculateViewVector(final float xRot, final float yRot, final CallbackInfoReturnable<Vec3> cir) {
        final ShipObject shipMountedTo = VSGameUtilsKt.getShipObjectEntityMountedTo(level, Entity.class.cast(this));
        if (shipMountedTo == null) {
            return;
        }
        final float f = xRot * (float) (Math.PI / 180.0);
        final float g = -yRot * (float) (Math.PI / 180.0);
        final float h = Mth.cos(g);
        final float i = Mth.sin(g);
        final float j = Mth.cos(f);
        final float k = Mth.sin(f);
        final Vector3dc originalViewVector = new Vector3d(i * j, -k, h * j);

        final ShipObject shipObject = shipMountedTo;
        final ShipTransform shipTransform;
        if (shipObject instanceof ShipObjectClient) {
            shipTransform = ((ShipObjectClient) shipObject).getRenderTransform();
        } else {
            shipTransform = shipObject.getShipData().getShipTransform();
        }
        final Vec3 newViewVector = VectorConversionsMCKt.toMinecraft(
            shipTransform.getShipCoordinatesToWorldCoordinatesRotation().transform(originalViewVector, new Vector3d()));
        cir.setReturnValue(newViewVector);
    }

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
    @Redirect(
        method = "move",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    public Vec3 collideWithShips(final Entity entity, Vec3 movement) {
        final AABB box = this.getBoundingBox();
        movement = EntityShipCollisionUtils.INSTANCE
            .adjustEntityMovementForShipCollisions(entity, movement, box, this.level);
        final Vec3 collisionResultWithWorld = collide(movement);

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
    private void redirectSetVelocity(final MoverType type, final Vec3 movement, final CallbackInfo callbackInfo,
        final Vec3 movementAdjustedForCollisions, final BlockPos landingPos, final BlockState landingBlockState,
        final Vec3 currentVelocity) {

        // Compute the collision response horizontal
        final Vector3dc collisionResponseHorizontal =
            new Vector3d(movementAdjustedForCollisions.x - movement.x, 0.0,
                movementAdjustedForCollisions.z - movement.z);

        // Remove the component of [movementAdjustedForCollisions] that is parallel to [collisionResponseHorizontal]
        if (collisionResponseHorizontal.lengthSquared() > 1e-6) {
            final Vector3dc collisionResponseHorizontalNormal = collisionResponseHorizontal.normalize(new Vector3d());
            final double parallelHorizontalVelocityComponent =
                collisionResponseHorizontalNormal
                    .dot(movementAdjustedForCollisions.x, 0.0, movementAdjustedForCollisions.z);

            setDeltaMovement(
                movementAdjustedForCollisions.x
                    - collisionResponseHorizontalNormal.x() * parallelHorizontalVelocityComponent,
                movementAdjustedForCollisions.y,
                movementAdjustedForCollisions.z
                    - collisionResponseHorizontalNormal.z() * parallelHorizontalVelocityComponent
            );
        }
        // Cancel the original invocation of Entity.setVelocity(DDD)V to remove vanilla behavior
        callbackInfo.cancel();
    }

    /**
     * @author ewoudje
     * @reason use vs2 handler to handle this method
     */
    @Redirect(method = "positionRider(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V"))
    public void positionRider(final Entity instance, final Entity passengerI, final Entity.MoveFunction callback) {
        this.positionRider(passengerI,
            (passenger, x, y, z) -> VSEntityManager.INSTANCE.getHandler(passenger.getType())
                .positionSetFromVehicle(passenger, Entity.class.cast(this), x, y, z));
    }

    // region Block standing on friction and particles mixins
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
                ship.getShipTransform().getWorldToShipMatrix().transformPosition(blockPosInGlobal, new Vector3d());
            final BlockPos blockPos = new BlockPos(
                Math.round(blockPosInLocal.x()), Math.round(blockPosInLocal.y()), Math.round(blockPosInLocal.z())
            );
            final BlockState blockState = level.getBlockState(blockPos);
            if (!blockState.isAir()) {
                return blockPos;
            } else {
                // Check the block below as well, in the cases of fences
                final Vector3dc blockPosInLocal2 = ship.getShipTransform().getWorldToShipMatrix()
                    .transformPosition(
                        new Vector3d(blockPosInGlobal.x(), blockPosInGlobal.y() - 1.0, blockPosInGlobal.z()));
                final BlockPos blockPos2 = new BlockPos(
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
            Math.floor(position.x) + 0.5,
            Math.floor(getBoundingBox().minY - 0.5) + 0.5,
            Math.floor(position.z) + 0.5
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
            Math.floor(position.x) + 0.5,
            Math.floor(position.y - 0.2) + 0.5,
            Math.floor(position.z) + 0.5
        );
        final BlockPos blockPosStandingOnFromShip = getPosStandingOnFromShips(blockPosInGlobal);
        if (blockPosStandingOnFromShip != null) {
            cir.setReturnValue(blockPosStandingOnFromShip);
        }
    }

    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void preSpawnSprintParticle(final CallbackInfo ci) {
        final Vector3dc blockPosInGlobal = new Vector3d(
            Math.floor(position.x) + 0.5,
            Math.floor(position.y - 0.2) + 0.5,
            Math.floor(position.z) + 0.5
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
    protected abstract void positionRider(Entity passenger, Entity.MoveFunction callback);

    @Shadow
    protected abstract void onInsideBlock(BlockState state);

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract void setDeltaMovement(Vec3 motion);

    @Shadow
    public abstract double getZ();

    @Shadow
    public abstract double getY();

    @Shadow
    public abstract double getX();

    @Shadow
    private @Nullable Entity vehicle;

    @Shadow
    private Vec3 position;

    @Shadow
    @Final
    protected Random random;

    @Shadow
    public abstract float getEyeHeight();

    @Shadow
    private EntityDimensions dimensions;
    // endregion

    @Override
    @NotNull
    public EntityDraggingInformation getDraggingInformation() {
        return draggingInformation;
    }
}
