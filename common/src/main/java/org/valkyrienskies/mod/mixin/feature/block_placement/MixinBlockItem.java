package org.valkyrienskies.mod.mixin.feature.block_placement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Intersectiond;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.PlayerUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {
    @Unique
    private static final double VS_OBSTRUCTION_SHRINK = 1.0E-3;

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"
        ),
        method = "place"
    )
    private BlockState transformPlayerWhenPlacing(
        final BlockItem _instance, final BlockPlaceContext _ctx,
        final Operation<BlockState> original, final BlockPlaceContext ctx
    ) {
        if (ctx == null || ctx.getPlayer() == null) {
            return original.call(this, ctx);
        }
        return PlayerUtil.transformPlayerTemporarily(
            ctx.getPlayer(),
            ctx.getLevel(),
            ctx.getClickedPos(),
            () -> original.call(this, ctx)
        );
    }

    @WrapOperation(
        method = "canPlace",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isUnobstructed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Z"
        )
    )
    private boolean checkObstructions(
        final Level level, final BlockState blockState, final BlockPos blockPos, final CollisionContext ctx,
        Operation<Boolean> original
    ) {
        boolean result = original.call(level, blockState, blockPos, ctx);
        if (!result) {
            return false;
        }
        final boolean checkBlockObstructions = VSGameConfig.COMMON.BLOCK_PLACEMENT.getEnableBlockObstructionChecks();
        final boolean checkEntityObstructions = VSGameConfig.COMMON.BLOCK_PLACEMENT.getEnableEntityObstructionChecks();
        if (!checkBlockObstructions && !checkEntityObstructions) {
            return true;
        }

        final VoxelShape voxelShape = blockState.getCollisionShape(level, blockPos, ctx);
        if (voxelShape.isEmpty()) {
            return true;
        }

        final List<AABBd> placementBoxes = vs_getCollisionBoxes(voxelShape, blockPos);
        if (placementBoxes.isEmpty()) {
            return true;
        }

        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, blockPos);
        if (ship != null) {
            final Matrix4dc shipToWorld = ship.getShipToWorld();
            if (checkBlockObstructions && vs_hasObstructionWithWorldBlocks(level, placementBoxes, shipToWorld)) {
                return false;
            }
            if (checkEntityObstructions && vs_hasObstructionWithEntities(level, placementBoxes, shipToWorld)) {
                return false;
            }
            return true;
        }

        final AABBd worldBounds = VectorConversionsMCKt.toJOML(voxelShape.bounds().move(blockPos));
        if (checkBlockObstructions && vs_hasWorldPlacementObstructionWithShips(level, placementBoxes, worldBounds)) {
            return false;
        }

        return true;
    }

    @Unique
    private static boolean vs_hasObstructionWithWorldBlocks(
        final Level level, final List<AABBd> placementBoxes, final Matrix4dc shipToWorld
    ) {
        for (final AABBd placementBox : placementBoxes) {
            if (vs_hasObstructionWithNonReplaceableBlocks(level, placementBox, shipToWorld)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean vs_hasObstructionWithEntities(
        final Level level, final List<AABBd> placementBoxes, final Matrix4dc shipToWorld
    ) {
        final AABBd worldBounds = placementBoxes.get(0).transform(shipToWorld, new AABBd());
        for (int i = 1; i < placementBoxes.size(); i++) {
            worldBounds.union(placementBoxes.get(i).transform(shipToWorld, new AABBd()));
        }

        for (final Entity entity : level.getEntities((Entity) null, VectorConversionsMCKt.toMinecraft(worldBounds))) {
            if (entity.isRemoved() || !entity.blocksBuilding) {
                continue;
            }
            final AABBd entityAABB = VectorConversionsMCKt.toJOML(entity.getBoundingBox().deflate(1.0E-7));
            for (final AABBd placementBox : placementBoxes) {
                if (vs_testObAab(placementBox, shipToWorld, entityAABB)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Unique
    private static boolean vs_hasWorldPlacementObstructionWithShips(
        final Level level, final List<AABBd> placementBoxes, final AABBd worldBounds
    ) {
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, worldBounds)) {
            final Matrix4dc worldToShip = ship.getWorldToShip();
            for (final AABBd placementBox : placementBoxes) {
                if (vs_hasObstructionWithNonReplaceableBlocks(level, placementBox, worldToShip)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Unique
    private static boolean vs_hasObstructionWithNonReplaceableBlocks(
        final Level level, final AABBd placementBox, final Matrix4dc placementToBlockSpace
    ) {
        final AABBd shrunkenPlacementBox = vs_deflateAabb(placementBox, VS_OBSTRUCTION_SHRINK);
        if (shrunkenPlacementBox == null) {
            return false;
        }
        final AABBd placementInBlockSpaceBounds = shrunkenPlacementBox.transform(placementToBlockSpace, new AABBd());
        final int minX = Mth.floor(placementInBlockSpaceBounds.minX());
        final int minY = Mth.floor(placementInBlockSpaceBounds.minY());
        final int minZ = Mth.floor(placementInBlockSpaceBounds.minZ());
        final int maxX = Mth.floor(placementInBlockSpaceBounds.maxX());
        final int maxY = Mth.floor(placementInBlockSpaceBounds.maxY());
        final int maxZ = Mth.floor(placementInBlockSpaceBounds.maxZ());

        final MutableBlockPos mutablePos = new MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    final BlockState blockState = level.getBlockState(mutablePos);
                    if (blockState.canBeReplaced()) {
                        continue;
                    }

                    final VoxelShape blockShape = blockState.getCollisionShape(level, mutablePos);
                    if (blockShape.isEmpty()) {
                        continue;
                    }

                    for (final AABB blockBox : blockShape.toAabbs()) {
                        final AABBd blockBoxInBlockSpace =
                            VectorConversionsMCKt.toJOML(blockBox.move(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ()));
                        if (vs_testObAab(shrunkenPlacementBox, placementToBlockSpace, blockBoxInBlockSpace)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Unique
    private static AABBd vs_deflateAabb(final AABBd source, final double amount) {
        final double minX = source.minX() + amount;
        final double minY = source.minY() + amount;
        final double minZ = source.minZ() + amount;
        final double maxX = source.maxX() - amount;
        final double maxY = source.maxY() - amount;
        final double maxZ = source.maxZ() - amount;

        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            return null;
        }

        return new AABBd(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Unique
    private static List<AABBd> vs_getCollisionBoxes(final VoxelShape voxelShape, final BlockPos blockPos) {
        final List<AABBd> boxes = new ArrayList<>();
        for (final AABB box : voxelShape.toAabbs()) {
            boxes.add(VectorConversionsMCKt.toJOML(box.move(blockPos)));
        }
        return boxes;
    }

    @Unique
    private static boolean vs_testObAab(AABBd aabb, Matrix4dc transform, AABBd other) {
        // Axes of OBB. Not scaled on purpose.
        Vector3d tX = new Vector3d(transform.m00(), transform.m01(), transform.m02()).normalize();
        Vector3d tY = new Vector3d(transform.m10(), transform.m11(), transform.m12()).normalize();
        Vector3d tZ = new Vector3d(transform.m20(), transform.m21(), transform.m22()).normalize();
        Vector3d transformedCenter = transform.transformPosition(aabb.center(new Vector3d()));
        // Axes of an AABB are world axes by definition.
        final Vector3d wX = new Vector3d(1, 0, 0);
        final Vector3d wY = new Vector3d(0, 1, 0);
        final Vector3d wZ = new Vector3d(0, 0, 1);

        return Intersectiond.testObOb(
            transformedCenter,
            tX, tY, tZ, aabb.getSize(new Vector3d()).mul(0.5),
            other.center(new Vector3d()),
            wX, wY, wZ, other.getSize(new Vector3d()).mul(0.5)
        );
    }

}
