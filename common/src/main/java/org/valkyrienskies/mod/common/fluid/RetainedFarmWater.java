package org.valkyrienskies.mod.common.fluid;

import java.util.function.IntPredicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.core.internal.world.VsiShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;

public final class RetainedFarmWater {
    private RetainedFarmWater() { }

    public static boolean hydrates(final LevelReader reader, final BlockPos farm) {
        final var body = bodyAt(reader, farm);
        if (body == null) return false;
        final var water = waterPredicate(reader);
        for (final BlockPos pos : BlockPos.betweenClosed(farm.offset(-4, 0, -4), farm.offset(4, 1, 4))) {
            if (containsWater(body, pos, water)) return true;
        }
        return false;
    }

    public static boolean supportsCane(final LevelReader reader, final BlockPos substrate) {
        final var body = bodyAt(reader, substrate);
        if (body == null) return false;
        final var water = waterPredicate(reader);
        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            if (containsWater(body, substrate.relative(direction), water)) return true;
        }
        return false;
    }

    private static @Nullable ShipFluidInteraction.CachedBody bodyAt(final LevelReader reader, final BlockPos pos) {
        if (!(reader instanceof Level level)) return null;
        final VsiShipWorld world = VSGameUtilsKt.getShipWorldNullable(level);
        if (world == null) return null;
        final var ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        return ship == null ? null : ShipFluidInteraction.bodyFor(world, ship.getId());
    }

    private static IntPredicate waterPredicate(final LevelReader reader) {
        final boolean client = reader instanceof Level level
            && VSGameUtilsKt.getShipWorldNullable(level) instanceof VsiClientShipWorld;
        return id -> {
            final var fluid = MassDatapackResolver.INSTANCE.getFlowingFluid(id, client);
            return fluid != null && fluid.defaultFluidState().is(FluidTags.WATER);
        };
    }

    private static boolean containsWater(final ShipFluidInteraction.CachedBody body, final BlockPos pos,
        final IntPredicate water) {
        final var voxel = body.getFloodedVoxel(pos.getX(), pos.getY(), pos.getZ());
        return voxel != null && RetainedFluidContact.volume(voxel, water) > 0;
    }
}
