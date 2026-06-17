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
import net.minecraft.util.Mth;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;
import org.valkyrienskies.mod.mixin.feature.ai.node_evaluator.PathNavigationRegionAccessor;
import org.valkyrienskies.mod.util.FluidStateManager;

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

        final AABB supportCheckAabb = getShipSupportCheckAabb(aabb);
        final AABBd supportQueryAabb = new AABBd(
            supportCheckAabb.minX, supportCheckAabb.minY, supportCheckAabb.minZ,
            supportCheckAabb.maxX, supportCheckAabb.maxY, supportCheckAabb.maxZ
        );
        final AABBd localSupportQueryAabb = new AABBd();
        final Vector3d samplePosInGlobal = new Vector3d();
        final Vector3d samplePosInShip = new Vector3d();

        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, supportQueryAabb)) {
            supportQueryAabb.transform(ship.getWorldToShip(), localSupportQueryAabb);
            if (!EntityShipCollisionUtils.mayShipIntersectLocalAabb(ship, localSupportQueryAabb)) {
                continue;
            }

            final BlockPos supportPos = findSupportingShipBlock(
                level, ship, sampleY, centerX, centerZ, minX, minZ, maxX, maxZ, samplePosInGlobal, samplePosInShip
            );
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
        final AABBd localTestAABB = new AABBd();
        for (final Ship ship : intersectingShips) {
            testAABB.transform(ship.getWorldToShip(), localTestAABB);
            if (!EntityShipCollisionUtils.mayShipIntersectLocalAabb(ship, localTestAABB)) {
                continue;
            }
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

    private static BlockPos findSupportingShipBlock(final Level level, final Ship ship, final Vector3d samplePosInGlobal,
        final Vector3d samplePosInShip) {
        ship.getWorldToShip().transformPosition(samplePosInGlobal, samplePosInShip);
        final BlockPos blockPos = BlockPos.containing(samplePosInShip.x(), samplePosInShip.y(), samplePosInShip.z());
        final BlockState blockState = level.getBlockState(blockPos);
        return blockState.isAir() ? null : blockPos;
    }

    private static BlockPos findSupportingShipBlock(final Level level, final Ship ship, final double sampleY,
        final double centerX, final double centerZ, final double minX, final double minZ, final double maxX,
        final double maxZ, final Vector3d samplePosInGlobal, final Vector3d samplePosInShip) {
        BlockPos supportPos = findSupportingShipBlock(
            level, ship, samplePosInGlobal.set(centerX, sampleY, centerZ), samplePosInShip);
        if (supportPos != null) {
            return supportPos;
        }

        supportPos = findSupportingShipBlock(level, ship, samplePosInGlobal.set(minX, sampleY, minZ), samplePosInShip);
        if (supportPos != null) {
            return supportPos;
        }

        supportPos = findSupportingShipBlock(level, ship, samplePosInGlobal.set(minX, sampleY, maxZ), samplePosInShip);
        if (supportPos != null) {
            return supportPos;
        }

        supportPos = findSupportingShipBlock(level, ship, samplePosInGlobal.set(maxX, sampleY, minZ), samplePosInShip);
        if (supportPos != null) {
            return supportPos;
        }

        return findSupportingShipBlock(level, ship, samplePosInGlobal.set(maxX, sampleY, maxZ), samplePosInShip);
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
        final BlockPos.MutableBlockPos candidatePos = new BlockPos.MutableBlockPos();
        final FluidStateManager.QueryCache queryCache = new FluidStateManager.QueryCache();
        VSGameUtilsKt.transformToNearbyShipsAndWorld(level, sampleX, sampleY, sampleZ, 0.5, (x, y, z) -> {
            if (result[0] != null && !result[0].isEmpty()) {
                return;
            }
            candidatePos.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
            final FluidState candidateState = getShipAwareFluidState(level, candidatePos, queryCache);
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

    public static FluidState getShipAwareFluidState(final Level level, final BlockPos pos,
        final FluidStateManager.QueryCache queryCache) {
        final FluidState rawState = FluidStateManager.getFluidState(level, pos, queryCache);
        return ShipWaterPocketManager.overrideWaterFluidState(level, pos, rawState);
    }
}
