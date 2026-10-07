package org.valkyrienskies.mod.mixin.feature.ship_interactions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BasePressurePlateBlock.class)
public interface PressurePlateAccessor {
    @Invoker("checkPressed")
    void vs$checkPressed(Entity entity, Level level, BlockPos pos, BlockState state, int signal);

    @Invoker("getSignalForState")
    int vs$getSignalForState(BlockState state);
}
