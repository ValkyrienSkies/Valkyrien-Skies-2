package org.valkyrienskies.mod.common.fluid;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3d;

final class FluidSolidFaces {
    private static final Direction[] DIRECTIONS = {
        Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH
    };

    private FluidSolidFaces() { }

    static int mask(final BlockGetter level, final int x, final int y, final int z) {
        int result = 0;
        final var position = new BlockPos.MutableBlockPos();
        for (int i = 0; i < DIRECTIONS.length; i++) {
            final var direction = DIRECTIONS[i];
            position.set(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
            final var state = level.getBlockState(position);
            if (state.canOcclude() && Block.isFaceFull(state.getOcclusionShape(level, position), direction.getOpposite())) {
                result |= 1 << i;
            }
        }
        return result;
    }

    static boolean covered(final List<Vector3d> face, final int mask) {
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if ((mask & (1 << i)) == 0) continue;
            final int axis = i / 2;
            final double plane = i % 2;
            boolean covered = true;
            for (final var point : face) {
                if (Math.abs(point.get(axis) - plane) > 1e-9) {
                    covered = false;
                    break;
                }
            }
            if (covered) return true;
        }
        return false;
    }
}
