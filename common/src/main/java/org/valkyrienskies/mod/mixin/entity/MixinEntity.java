package org.valkyrienskies.mod.mixin.entity;

import java.util.stream.Stream;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.collection.ReusableStream;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Redirect(
        method = "raycast",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;raycast(Lnet/minecraft/world/RaycastContext;)Lnet/minecraft/util/hit/BlockHitResult;"
        )
    )
    public BlockHitResult addShipsToRaycast(final World receiver, final RaycastContext ctx) {
        return RaycastUtilsKt.raycastIncludeShips((ClientWorld) receiver, ctx);
    }

    /**
     * Allow entities to collide with ships by replacing [Entity.adjustMovementForCollisions] with
     * [EntityShipCollisionUtils.adjustMovementForCollisionsAndShipCollisions].
     *
     * @reason Allows entities to collide with ships
     * @author Tri0de
     */
    @Overwrite
    private Vec3d adjustMovementForCollisions(Vec3d movement) {
        final Entity thisAsEntity = Entity.class.cast(this);
        final Box box = this.getBoundingBox();
        final ShapeContext shapeContext = ShapeContext.of(thisAsEntity);
        final VoxelShape voxelShape = this.world.getWorldBorder().asVoxelShape();
        final Stream<VoxelShape> stream =
            VoxelShapes.matchesAnywhere(voxelShape, VoxelShapes.cuboid(box.contract(1.0E-7D)), BooleanBiFunction.AND)
                ? Stream.empty() : Stream.of(voxelShape);
        final Stream<VoxelShape> stream2 =
            this.world.getEntityCollisions(thisAsEntity, box.stretch(movement), (entity) -> true);
        final ReusableStream<VoxelShape> reusableStream = new ReusableStream<>(Stream.concat(stream2, stream));

        // vs code start
        final Vec3d originalMovement = movement;
        movement = EntityShipCollisionUtils.INSTANCE
            .adjustEntityMovementForShipCollisions(thisAsEntity, movement, box, this.world);

        final Vec3d vec3d = movement.lengthSquared() == 0.0D ? movement :
            adjustMovementForCollisions(thisAsEntity, movement, box, this.world, shapeContext, reusableStream);
        // vs code end

        final boolean bl = movement.x != vec3d.x;
        final boolean bl2 = movement.y != vec3d.y;
        final boolean bl3 = movement.z != vec3d.z;
        final boolean bl4 = this.onGround || bl2 && originalMovement.y < 0.0D;
        if (this.stepHeight > 0.0F && bl4 && (bl || bl3)) {
            Vec3d vec3d2 =
                adjustMovementForCollisions(thisAsEntity, new Vec3d(movement.x, this.stepHeight, movement.z),
                    box, this.world, shapeContext, reusableStream);
            final Vec3d vec3d3 = adjustMovementForCollisions(thisAsEntity, new Vec3d(0.0D, this.stepHeight, 0.0D),
                box.stretch(movement.x, 0.0D, movement.z), this.world, shapeContext, reusableStream);
            if (vec3d3.y < (double) this.stepHeight) {
                final Vec3d vec3d4 = adjustMovementForCollisions(thisAsEntity, new Vec3d(movement.x, 0.0D, movement.z),
                    box.offset(vec3d3), this.world, shapeContext, reusableStream).add(vec3d3);
                if (squaredHorizontalLength(vec3d4) > squaredHorizontalLength(vec3d2)) {
                    vec3d2 = vec3d4;
                }
            }

            if (squaredHorizontalLength(vec3d2) > squaredHorizontalLength(vec3d)) {
                return vec3d2.add(
                    adjustMovementForCollisions(thisAsEntity, new Vec3d(0.0D, -vec3d2.y + movement.y, 0.0D),
                        box.offset(vec3d2), this.world, shapeContext, reusableStream));
            }
        }

        return vec3d;
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
    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setVelocity(DDD)V"),
        locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void redirectSetVelocity(final MovementType type, final Vec3d movement, final CallbackInfo callbackInfo,
        final Vec3d movementAdjustedForCollisions, final BlockPos landingPos, final BlockState landingBlockState,
        final Vec3d currentVelocity) {

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

            setVelocity(
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

    // region shadow functions and fields
    @Shadow
    public World world;
    @Shadow
    protected boolean onGround;
    @Shadow
    public float stepHeight;

    @Shadow
    public abstract Box getBoundingBox();

    @Shadow
    public abstract void setVelocity(double x, double y, double z);

    @Shadow
    public static double squaredHorizontalLength(final Vec3d vector) {
        throw new AssertionError("Mixin failed to apply");
    }

    @Shadow
    public static Vec3d adjustMovementForCollisions(final Entity thisAsEntity, final Vec3d movement, final Box box,
        final World world,
        final ShapeContext shapeContext, final ReusableStream<VoxelShape> reusableStream) {
        return null;
    }
    // endregion

}
