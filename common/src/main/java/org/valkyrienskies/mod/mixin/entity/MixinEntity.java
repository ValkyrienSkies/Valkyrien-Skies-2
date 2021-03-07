package org.valkyrienskies.mod.mixin.entity;

import com.google.common.collect.Iterators;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.collection.ReusableStream;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.collision.CollisionRange;
import org.valkyrienskies.core.collision.CollisionRangec;
import org.valkyrienskies.core.collision.CollisionResult;
import org.valkyrienskies.core.collision.ConvexPolygon;
import org.valkyrienskies.core.collision.ConvexPolygonc;
import org.valkyrienskies.core.collision.SATConvexPolygonCollider;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(Entity.class)
public abstract class MixinEntity {

    private static final Vector3dc[] UNIT_NORMALS =
        new Vector3dc[] {new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(1, 0, 0)};

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

    @Redirect(
        method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;adjustMovementForCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Lnet/minecraft/world/World;Lnet/minecraft/block/ShapeContext;Lnet/minecraft/util/collection/ReusableStream;)Lnet/minecraft/util/math/Vec3d;"
        )
    )
    private Vec3d adjustMovementForCollisionsAndShipCollisions(@Nullable final Entity entity,
        final Vec3d movement,
        final Box entityBoundingBox, final World world, final ShapeContext context,
        final ReusableStream<VoxelShape> collisions) {
        final List<ConvexPolygonc> collidingPolygons =
            vs$getShipPolygonsCollidingWithEntity(entity, movement, entityBoundingBox, world);
        if (!collidingPolygons.isEmpty()) {
            final Vector3d newMovement = VectorConversionsMCKt.toJOML(movement);

            final ConvexPolygonc entityPolygon =
                ConvexPolygon.Companion
                    .createFromAABB(VectorConversionsMCKt.toJOML(entityBoundingBox.stretch(movement)), null);

            final CollisionResult collisionResult = CollisionResult.Companion.create();

            // region declare temp objects
            final CollisionRange temp1 = CollisionRange.Companion.create();
            final CollisionRange temp2 = CollisionRange.Companion.create();
            final CollisionRange temp3 = CollisionRange.Companion.create();
            // endregion

            for (final ConvexPolygonc shipPolygon : collidingPolygons) {
                SATConvexPolygonCollider.INSTANCE.checkIfColliding(
                    entityPolygon,
                    shipPolygon,
                    Iterators.concat(Arrays.stream(UNIT_NORMALS).iterator(), shipPolygon.getNormals().iterator()),
                    collisionResult,
                    temp1,
                    temp2,
                    temp3
                );

                if (collisionResult.getColliding()) {
                    final Vector3dc axis = collisionResult.getCollisionAxis();
                    final CollisionRangec collisionRangec = collisionResult.getCollisionRange();

                    // TODO: WRONG, but kind of works :O
                    newMovement.add(axis.mul(collisionRangec.getRangeLength(), new Vector3d()));
                }
            }

            return Entity
                .adjustMovementForCollisions(entity, VectorConversionsMCKt.toVec3d(newMovement), entityBoundingBox,
                    world, context, collisions);
        }
        return Entity.adjustMovementForCollisions(entity, movement, entityBoundingBox, world, context, collisions);
    }

    private List<ConvexPolygonc> vs$getShipPolygonsCollidingWithEntity(@Nullable final Entity entity,
        final Vec3d movement, final Box entityBoundingBox, final World world) {
        final Box entityBoxWithMovement = entityBoundingBox.stretch(movement);

        final List<ConvexPolygonc> collidingPolygons = new ArrayList<>();

        for (final ShipObject shipObject : VSGameUtilsKt.getShipObjectWorld(world).getShipObjects().values()) {
            final ShipTransform shipTransform = shipObject.getShipData().getShipTransform();

            final ConvexPolygonc entityPolyInShipCoordinates =
                ConvexPolygon.Companion.createFromAABB(VectorConversionsMCKt.toJOML(entityBoxWithMovement),
                    shipTransform.getWorldToShipMatrix());
            final AABBdc enclosingBB = entityPolyInShipCoordinates.getEnclosingAABB(new AABBd());
            final Box enclosingBBAsBox = VectorConversionsMCKt.toMinecraft(enclosingBB);

            final Stream<VoxelShape> stream2 =
                world.getBlockCollisions(entity, enclosingBBAsBox);

            stream2.forEach(voxelShape -> {
                final ConvexPolygonc shipPolygon =
                    ConvexPolygon.Companion.createFromAABB(VectorConversionsMCKt.toJOML(voxelShape.getBoundingBox()),
                        shipTransform.getShipToWorldMatrix());
                collidingPolygons.add(shipPolygon);
            });
        }
        return collidingPolygons;
    }

}
