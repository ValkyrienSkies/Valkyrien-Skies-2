package org.valkyrienskies.mod.mixin.entity;

import java.util.stream.Stream;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.collection.ReusableStream;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
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
     * @author Tri0de
     */
    @Overwrite
    private Vec3d adjustMovementForCollisions(final Vec3d movement) {
        final Entity thisAsEntity = Entity.class.cast(this);
        final Box box = this.getBoundingBox();
        final ShapeContext shapeContext = ShapeContext.of(thisAsEntity);
        final VoxelShape voxelShape = this.world.getWorldBorder().asVoxelShape();
        final Stream<VoxelShape> stream = VoxelShapes
            .matchesAnywhere(voxelShape, VoxelShapes.cuboid(box.contract(1.0E-7D)), BooleanBiFunction.AND) ?
            Stream.empty() : Stream.of(voxelShape);
        final Stream<VoxelShape> stream2 =
            this.world.getEntityCollisions(thisAsEntity, box.stretch(movement), (entity) -> true);
        final ReusableStream<VoxelShape> reusableStream = new ReusableStream<>(Stream.concat(stream2, stream));
        final Vec3d vec3d = movement.lengthSquared() == 0.0D ? movement :
            EntityShipCollisionUtils.INSTANCE
                .adjustMovementForCollisionsAndShipCollisions(thisAsEntity, movement, box, world, shapeContext,
                    reusableStream, false);
        final boolean bl = movement.x != vec3d.x;
        final boolean bl2 = movement.y != vec3d.y;
        final boolean bl3 = movement.z != vec3d.z;
        final boolean bl4 = onGround || bl2 && movement.y < 0.0D;
        if (stepHeight > 0.0F && bl4 && (bl || bl3)) {
            Vec3d vec3d2 =
                EntityShipCollisionUtils.INSTANCE.adjustMovementForCollisionsAndShipCollisions(thisAsEntity,
                    new Vec3d(movement.x, stepHeight, movement.z), box,
                    world, shapeContext, reusableStream, false);
            final Vec3d vec3d3 = EntityShipCollisionUtils.INSTANCE
                .adjustMovementForCollisionsAndShipCollisions(thisAsEntity, new Vec3d(0.0D, stepHeight, 0.0D),
                    box.stretch(movement.x, 0.0D, movement.z), world, shapeContext, reusableStream, true);
            if (vec3d3.y < (double) stepHeight) {
                final Vec3d vec3d4 =
                    EntityShipCollisionUtils.INSTANCE.adjustMovementForCollisionsAndShipCollisions(thisAsEntity,
                        new Vec3d(movement.x, 0.0D, movement.z),
                        box.offset(vec3d3),
                        world, shapeContext, reusableStream, false).add(vec3d3);
                if (squaredHorizontalLength(vec3d4) > squaredHorizontalLength(vec3d2)) {
                    vec3d2 = vec3d4;
                }
            }

            if (squaredHorizontalLength(vec3d2) > squaredHorizontalLength(vec3d)) {
                return vec3d2.add(
                    EntityShipCollisionUtils.INSTANCE.adjustMovementForCollisionsAndShipCollisions(thisAsEntity,
                        new Vec3d(0.0D, -vec3d2.y + movement.y, 0.0D), box.offset(vec3d2),
                        world, shapeContext, reusableStream, true));
            }
        }

        return vec3d;
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
    public static double squaredHorizontalLength(final Vec3d vector) {
        throw new AssertionError("Mixin failed to apply");
    }
    // endregion

}
