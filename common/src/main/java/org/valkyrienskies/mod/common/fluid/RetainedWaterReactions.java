package org.valkyrienskies.mod.common.fluid;

import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.mod.mixin.feature.fluid_escaping_ship_config.ConcretePowderBlockAccessor;

final class RetainedWaterReactions {
    private RetainedWaterReactions() { }

    static boolean acceptsWater(final BlockState state) {
        final Block block = state.getBlock();
        return block instanceof ConcretePowderBlock ||
            ((block instanceof CampfireBlock || block instanceof AbstractCandleBlock)
                && state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT));
    }

    static boolean touches(final ServerLevel level, final BlockPos target, final BlockState state,
        final BlockPos waterPos, final VsiFluidFloodedVoxel voxel, final IntPredicate water) {
        if (state.getBlock() instanceof ConcretePowderBlock && waterPos.getY() < target.getY()) return false;
        final int x = target.getX() - waterPos.getX();
        final int y = target.getY() - waterPos.getY();
        final int z = target.getZ() - waterPos.getZ();
        for (final var box : state.getShape(level, target).toAabbs()) {
            if (RetainedFluidContact.touches(voxel, box.move(x, y, z), water)) return true;
        }
        return false;
    }

    static boolean apply(final ServerLevel level, final BlockPos pos, final BlockState state) {
        if (state.getBlock() instanceof ConcretePowderBlock powder) {
            return level.setBlock(pos, ((ConcretePowderBlockAccessor) powder).vs$getConcrete(), Block.UPDATE_ALL);
        }
        if (state.getBlock() instanceof CampfireBlock) {
            if (!level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL)) return false;
            CampfireBlock.dowse(null, level, pos, state);
            level.levelEvent(1009, pos, 0);
            return true;
        }
        AbstractCandleBlock.extinguish(null, state, level, pos);
        return !level.getBlockState(pos).equals(state);
    }
}
