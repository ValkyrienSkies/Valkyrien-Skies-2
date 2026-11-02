package org.valkyrienskies.mod.mixin.feature.air_pockets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import java.util.Iterator;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.air_pockets.ShipGravityDownDirCache;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(FlowingFluid.class)
public abstract class MixinFlowingFluid {

    @Unique
    private static final ThreadLocal<ShipGravityDownDirCache> valkyrienair$shipDownCache =
        ThreadLocal.withInitial(ShipGravityDownDirCache::new);

    @Unique
    private static final ThreadLocal<Vector3d> valkyrienair$tmpRotVec = ThreadLocal.withInitial(Vector3d::new);

    @Unique
    private static final class ValkyrienAirDirIter implements Iterator<Direction> {
        private final Direction[] dirs;
        private int idx = 0;

        private ValkyrienAirDirIter(final Direction[] dirs) {
            this.dirs = dirs;
        }

        @Override
        public boolean hasNext() {
            return idx < dirs.length;
        }

        @Override
        public Direction next() {
            return dirs[idx++];
        }
    }

    @Unique
    private static final Direction[][] valkyrienair$LATERAL_DIRS = new Direction[Direction.values().length][];

    static {
        for (final Direction down : Direction.values()) {
            if (down.getAxis() == Direction.Axis.Y) continue;
            final Direction[] arr = new Direction[4];
            int j = 0;
            for (final Direction d : Direction.values()) {
                if (d == down || d == down.getOpposite()) continue;
                arr[j++] = d;
            }
            valkyrienair$LATERAL_DIRS[down.ordinal()] = arr;
        }
    }

    @Unique
    private static Direction valkyrienair$getShipGravityDown(final Level level, final BlockPos pos) {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return null;
        if (!VSGameUtilsKt.isBlockInShipyard(level, pos)) return null;

        final ShipGravityDownDirCache cache = valkyrienair$shipDownCache.get();
        final long now = level.getGameTime();
        final long posLong = pos.asLong();
        if (cache.lastLevel == level && cache.lastGameTime == now && cache.lastPosLong == posLong) {
            return cache.lastDown;
        }

        cache.lastLevel = level;
        cache.lastGameTime = now;
        cache.lastPosLong = posLong;

        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) {
            cache.lastDown = null;
            return null;
        }

        final ShipTransform transform = ship.getTransform();
        final var rot = transform.getShipToWorldRotation();

        // Determine which ship-local axis direction points most downward in world-space.
        // We only need the world-space Y components of the rotated ship basis vectors.
        final Vector3d v = valkyrienair$tmpRotVec.get();

        v.set(0.0, 1.0, 0.0);
        rot.transform(v);
        final double yY = v.y; // local +Y (UP)

        v.set(1.0, 0.0, 0.0);
        rot.transform(v);
        final double yX = v.y; // local +X (EAST)

        v.set(0.0, 0.0, 1.0);
        rot.transform(v);
        final double yZ = v.y; // local +Z (SOUTH)

        Direction best = Direction.DOWN;
        double bestY = -yY; // local -Y

        if (yY < bestY) {
            bestY = yY;
            best = Direction.UP;
        }
        if (yX < bestY) {
            bestY = yX;
            best = Direction.EAST;
        }
        if (-yX < bestY) {
            bestY = -yX;
            best = Direction.WEST;
        }
        if (yZ < bestY) {
            bestY = yZ;
            best = Direction.SOUTH;
        }
        if (-yZ < bestY) {
            bestY = -yZ;
            best = Direction.NORTH;
        }

        cache.lastDown = best;
        return best;
    }

    @Unique
    private static Iterator<Direction> valkyrienair$getLateralIterator(final Direction.Plane plane, final Direction down) {
        if (down == null) return plane.iterator();
        if (down.getAxis() == Direction.Axis.Y) return plane.iterator();
        final Direction[] dirs = valkyrienair$LATERAL_DIRS[down.ordinal()];
        if (dirs == null) return plane.iterator();
        return new ValkyrienAirDirIter(dirs);
    }

    @Redirect(
        method = "spread",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;below()Lnet/minecraft/core/BlockPos;"),
        require = 0
    )
    private BlockPos valkyrienair$spreadBelow(final BlockPos instance, final Level level, final BlockPos pos,
        final FluidState state) {
        final Direction down = valkyrienair$getShipGravityDown(level, pos);
        return down != null ? instance.relative(down) : instance.below();
    }

    @Redirect(
        method = "spread",
        at = @At(value = "FIELD", target = "Lnet/minecraft/core/Direction;DOWN:Lnet/minecraft/core/Direction;"),
        require = 0
    )
    private Direction valkyrienair$spreadDownDirection(final Level level, final BlockPos pos, final FluidState state) {
        final Direction down = valkyrienair$getShipGravityDown(level, pos);
        return down != null ? down : Direction.DOWN;
    }

    @Redirect(
        method = "spread",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction$Plane;iterator()Ljava/util/Iterator;"),
        require = 0
    )
    private Iterator<Direction> valkyrienair$spreadHorizontalIterator(final Direction.Plane instance, final Level level,
        final BlockPos pos, final FluidState state) {
        final Direction down = valkyrienair$getShipGravityDown(level, pos);
        return valkyrienair$getLateralIterator(instance, down);
    }

    @Redirect(
        method = "getNewLiquid",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;below()Lnet/minecraft/core/BlockPos;"),
        require = 0
    )
    private BlockPos valkyrienair$getNewLiquidBelow(final BlockPos instance, final Level level, final BlockPos pos,
        final BlockState state) {
        final Direction down = valkyrienair$getShipGravityDown(level, pos);
        return down != null ? instance.relative(down) : instance.below();
    }

    @Redirect(
        method = "getNewLiquid",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;above()Lnet/minecraft/core/BlockPos;"),
        require = 0
    )
    private BlockPos valkyrienair$getNewLiquidAbove(final BlockPos instance, final Level level, final BlockPos pos,
        final BlockState state) {
        final Direction down = valkyrienair$getShipGravityDown(level, pos);
        if (down == null) return instance.above();
        return instance.relative(down.getOpposite());
    }

    @Redirect(
        method = "getNewLiquid",
        at = @At(value = "FIELD", target = "Lnet/minecraft/core/Direction;UP:Lnet/minecraft/core/Direction;"),
        require = 0
    )
    private Direction valkyrienair$getNewLiquidUpDirection(final Level level, final BlockPos pos, final BlockState state) {
        final Direction down = valkyrienair$getShipGravityDown(level, pos);
        return down != null ? down.getOpposite() : Direction.UP;
    }

    @Redirect(
        method = "getNewLiquid",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction$Plane;iterator()Ljava/util/Iterator;"),
        require = 0
    )
    private Iterator<Direction> valkyrienair$getNewLiquidHorizontalIterator(final Direction.Plane instance, final Level level,
        final BlockPos pos, final BlockState state) {
        final Direction down = valkyrienair$getShipGravityDown(level, pos);
        return valkyrienair$getLateralIterator(instance, down);
    }

    @Redirect(
        method = "isWaterHole",
        at = @At(value = "FIELD", target = "Lnet/minecraft/core/Direction;DOWN:Lnet/minecraft/core/Direction;"),
        require = 0
    )
    private Direction valkyrienair$isWaterHoleDownDirection(final BlockGetter getter, final Fluid fluid, final BlockPos pos,
        final BlockState state, final BlockPos otherPos, final BlockState otherState) {
        if (!(getter instanceof final Level level)) return Direction.DOWN;
        final Direction down = valkyrienair$getShipGravityDown(level, pos);
        return down != null ? down : Direction.DOWN;
    }

    @Redirect(
        method = "getFlow",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;below()Lnet/minecraft/core/BlockPos;"),
        require = 0
    )
    private BlockPos valkyrienair$getFlowBelow(final BlockPos.MutableBlockPos instance, final BlockGetter getter,
        final BlockPos pos, final FluidState state) {
        if (!(getter instanceof final Level level)) return instance.below();
        final Direction down = valkyrienair$getShipGravityDown(level, pos);
        return down != null ? instance.move(down) : instance.below();
    }

    @Redirect(
        method = "getFlow",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;above()Lnet/minecraft/core/BlockPos;"),
        require = 0
    )
    private BlockPos valkyrienair$getFlowAbove(final BlockPos.MutableBlockPos instance, final BlockGetter getter,
        final BlockPos pos, final FluidState state) {
        if (!(getter instanceof final Level level)) return instance.above();
        final Direction down = valkyrienair$getShipGravityDown(level, pos);
        if (down == null) return instance.above();
        return instance.move(down.getOpposite());
    }

    @Redirect(
        method = "getFlow",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction$Plane;iterator()Ljava/util/Iterator;"),
        require = 0
    )
    private Iterator<Direction> valkyrienair$getFlowHorizontalIterator(final Direction.Plane instance, final BlockGetter getter,
        final BlockPos pos, final FluidState state) {
        if (!(getter instanceof final Level level)) return instance.iterator();
        final Direction down = valkyrienair$getShipGravityDown(level, pos);
        return valkyrienair$getLateralIterator(instance, down);
    }
}
