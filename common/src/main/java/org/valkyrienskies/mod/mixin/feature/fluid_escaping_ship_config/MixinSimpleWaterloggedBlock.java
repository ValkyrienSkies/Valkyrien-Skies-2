package org.valkyrienskies.mod.mixin.feature.fluid_escaping_ship_config;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.valkyrienskies.mod.common.fluid.FractionalFluidSources;

@Mixin(SimpleWaterloggedBlock.class)
public interface MixinSimpleWaterloggedBlock {
    @Overwrite
    default ItemStack pickupBlock(final LevelAccessor level, final BlockPos pos, final BlockState state) {
        if (FractionalFluidSources.owns(level, pos) || !state.getValue(BlockStateProperties.WATERLOGGED)) {
            return ItemStack.EMPTY;
        }
        level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
        if (!state.canSurvive(level, pos)) level.destroyBlock(pos, true);
        return new ItemStack(Items.WATER_BUCKET);
    }
}
