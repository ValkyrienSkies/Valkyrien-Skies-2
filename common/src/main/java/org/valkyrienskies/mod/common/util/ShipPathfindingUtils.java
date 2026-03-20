package org.valkyrienskies.mod.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixin.feature.ai.node_evaluator.PathNavigationRegionAccessor;

public final class ShipPathfindingUtils {
    private static final double SHIP_SUPPORT_CHECK_DISTANCE = 0.15;
    private static final double SHIP_SUPPORT_MAX_Y_OFFSET = 1.0E-6;

    private ShipPathfindingUtils() {
    }

    public static Level getLevel(final BlockGetter blockGetter) {
        if (blockGetter instanceof PathNavigationRegion region) {
            return ((PathNavigationRegionAccessor) region).getLevel();
        }
        if (blockGetter instanceof Level level) {
            return level;
        }
        return null;
    }

    public static BlockPos findSupportingShipBlock(final Level level, final AABB aabb) {
        final double sampleY = aabb.minY - SHIP_SUPPORT_CHECK_DISTANCE;
        final double centerX = (aabb.minX + aabb.maxX) * 0.5;
        final double centerZ = (aabb.minZ + aabb.maxZ) * 0.5;
        final double insetX = Math.min((aabb.maxX - aabb.minX) * 0.25, 0.2);
        final double insetZ = Math.min((aabb.maxZ - aabb.minZ) * 0.25, 0.2);
        final double minX = Math.min(aabb.minX + insetX, centerX);
        final double maxX = Math.max(aabb.maxX - insetX, centerX);
        final double minZ = Math.min(aabb.minZ + insetZ, centerZ);
        final double maxZ = Math.max(aabb.maxZ - insetZ, centerZ);

        final double[][] samplePoints = {
            {centerX, centerZ},
            {minX, minZ},
            {minX, maxZ},
            {maxX, minZ},
            {maxX, maxZ}
        };

        for (final double[] samplePoint : samplePoints) {
            final BlockPos supportPos = findSupportingShipBlock(level, samplePoint[0], sampleY, samplePoint[1]);
            if (supportPos != null) {
                return supportPos;
            }
        }

        return null;
    }

    public static boolean hasShipSupport(final Level level, final Entity entity, final AABB aabb) {
        return hasShipCollision(level, entity, getShipSupportCheckAabb(aabb));
    }

    public static BlockPos findSupportingShipBlock(final Level level, final Entity entity, final AABB aabb) {
        if (!hasShipSupport(level, entity, aabb)) {
            return null;
        }
        return findSupportingShipBlock(level, aabb);
    }

    public static BlockPos findSupportingShipBlock(final Level level, final double sampleX, final double sampleY,
        final double sampleZ) {
        final double radius = 0.5;
        final AABBdc testAABB = new AABBd(
            sampleX - radius, sampleY - radius, sampleZ - radius,
            sampleX + radius, sampleY + radius, sampleZ + radius
        );
        final Iterable<Ship> intersectingShips = VSGameUtilsKt.getShipsIntersecting(level, testAABB);
        for (final Ship ship : intersectingShips) {
            final Vector3d samplePosInGlobal = new Vector3d(sampleX, sampleY, sampleZ);
            final Vector3d blockPosInLocal =
                ship.getTransform().getWorldToShip().transformPosition(samplePosInGlobal, new Vector3d());
            final BlockPos blockPos = BlockPos.containing(blockPosInLocal.x(), blockPosInLocal.y(), blockPosInLocal.z());
            final BlockState blockState = level.getBlockState(blockPos);
            if (!blockState.isAir()) {
                return blockPos;
            }
        }

        return null;
    }

    public static BlockPos findExactShipBlock(final Level level, final BlockPos blockPos) {
        return findExactShipBlock(level, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
    }

    public static BlockPos findExactShipBlock(final Level level, final double sampleX, final double sampleY,
        final double sampleZ) {
        final BlockPos[] result = {null};
        VSGameUtilsKt.transformToNearbyShipsAndWorld(level, sampleX, sampleY, sampleZ, 0.5, (x, y, z) -> {
            if (result[0] != null) {
                return;
            }
            final BlockPos candidatePos = BlockPos.containing(x, y, z);
            if (!level.getBlockState(candidatePos).isAir()) {
                result[0] = candidatePos.immutable();
            }
        });
        return result[0];
    }

    public static FluidState findExactShipFluid(final Level level, final BlockPos blockPos) {
        return findExactShipFluid(level, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
    }

    public static FluidState findExactShipFluid(final Level level, final double sampleX, final double sampleY,
        final double sampleZ) {
        final FluidState[] result = {null};
        VSGameUtilsKt.transformToNearbyShipsAndWorld(level, sampleX, sampleY, sampleZ, 0.5, (x, y, z) -> {
            if (result[0] != null && !result[0].isEmpty()) {
                return;
            }
            final FluidState candidateState = level.getFluidState(BlockPos.containing(x, y, z));
            if (!candidateState.isEmpty()) {
                result[0] = candidateState;
            }
        });
        return result[0];
    }

    public static boolean hasShipCollision(final Level level, final Entity entity, final AABB aabb) {
        return !EntityShipCollisionUtils.INSTANCE
            .getShipPolygonsCollidingWithEntity(entity, Vec3.ZERO, aabb, level)
            .isEmpty();
    }

    private static AABB getShipSupportCheckAabb(final AABB aabb) {
        return new AABB(
            aabb.minX,
            aabb.minY - SHIP_SUPPORT_CHECK_DISTANCE,
            aabb.minZ,
            aabb.maxX,
            aabb.minY + SHIP_SUPPORT_MAX_Y_OFFSET,
            aabb.maxZ
        );
    }
}
