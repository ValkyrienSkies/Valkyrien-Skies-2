package org.valkyrienskies.mod.mixin.entity;

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
import org.spongepowered.asm.mixin.Mixin;
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
        return EntityShipCollisionUtils.INSTANCE
            .adjustMovementForCollisionsAndShipCollisions(entity, movement, entityBoundingBox, world, context,
                collisions);
    }

}
