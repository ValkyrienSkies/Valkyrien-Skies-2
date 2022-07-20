package org.valkyrienskies.mod.mixin.entity;

import kotlin.Pair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RewindableStream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntityDraggingInformationProvider {

    @Unique
    private final EntityDraggingInformation draggingInformation = new EntityDraggingInformation();

    @Shadow
    public abstract double getZ();

    @Shadow
    public abstract double getY();

    @Shadow
    public abstract double getX();

    @Redirect(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
        )
    )
    public BlockHitResult addShipsToRaycast(final Level receiver, final ClipContext ctx) {
        return RaycastUtilsKt.clipIncludeShips((ClientLevel) receiver, ctx);
    }

    @Shadow
    public double xo;
    @Shadow
    public double yo;
    @Shadow
    public double zo;

    @Shadow
    public abstract float getEyeHeight();

    /**
     * @reason Needed for players to pick blocks correctly when mounted to a ship
     */
    @Inject(method = "getEyePosition", at = @At("HEAD"), cancellable = true)
    private void preGetEyePosition(final float partialTicks, final CallbackInfoReturnable<Vec3> cir) {
        final Pair<ShipObject, Vector3dc> shipMountedTo =
            VSGameUtilsKt.getShipObjectEntityMountedTo(level, Entity.class.cast(this));
        if (shipMountedTo == null) {
            return;
        }

        final ShipObject shipObject = shipMountedTo.getFirst();
        final ShipTransform shipTransform;
        if (shipObject instanceof ShipObjectClient) {
            shipTransform = ((ShipObjectClient) shipObject).getRenderTransform();
        } else {
            shipTransform = shipObject.getShipData().getShipTransform();
        }
        final Vector3dc basePos = shipTransform.getShipToWorldMatrix().transformPosition(
            shipMountedTo.getSecond(), new Vector3d()
        );
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
        final Pair<ShipObject, Vector3dc> shipMountedTo =
            VSGameUtilsKt.getShipObjectEntityMountedTo(level, Entity.class.cast(this));
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

        final ShipObject shipObject = shipMountedTo.getFirst();
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

    // This whole part changes distanceTo(sqrt) to use ship locations if needed.
    // and unjank mojank

    /**
     * @author ewoudje
     * @reason unjank mojank, we need to modify distanceTo's so while were at it unjank it
     */
    @Overwrite
    public float distanceTo(final Entity entity) {
        return Mth.sqrt(distanceToSqr(entity));
    }

    /**
     * @author ewoudje
     * @reason unjank mojank, we need to modify distanceTo's so while were at it unjank it
     */
    @Overwrite
    public double distanceToSqr(final Vec3 vec) {
        return distanceToSqr(vec.x, vec.y, vec.z);
    }

    /**
     * @author ewoudje
     * @reason it fixes general issues when checking for distance between in world player and ship things
     */
    @Overwrite
    public double distanceToSqr(final double x, final double y, final double z) {
        return VSGameUtilsKt.squaredDistanceToInclShips(Entity.class.cast(this), x, y, z);
    }

    // region shadow functions and fields
    @Shadow
    public Level level;
    @Shadow
    protected boolean onGround;
    @Shadow
    public float maxUpStep;

    @Shadow
    public abstract void setDeltaMovement(Vec3 motion);

    @Shadow
    public abstract double distanceToSqr(final Entity entity);

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract void setDeltaMovement(double x, double y, double z);

    @Shadow
    public static double getHorizontalDistanceSqr(final Vec3 vector) {
        throw new AssertionError("Mixin failed to apply");
    }

    @Shadow
    public static Vec3 collideBoundingBoxHeuristically(final Entity thisAsEntity, final Vec3 movement, final AABB box,
        final Level world,
        final CollisionContext shapeContext, final RewindableStream<VoxelShape> reusableStream) {
        return null;
    }

    @Shadow
    protected abstract Vec3 collide(Vec3 vec3d);
    // endregion

    @Override
    @NotNull
    public EntityDraggingInformation getDraggingInformation() {
        return draggingInformation;
    }
}
