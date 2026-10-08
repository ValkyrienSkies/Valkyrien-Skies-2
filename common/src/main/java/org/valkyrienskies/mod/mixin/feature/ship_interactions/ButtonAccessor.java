package org.valkyrienskies.mod.mixin.feature.ship_interactions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ButtonBlock.class)
public interface ButtonAccessor {
    @Invoker("playSound")
    void vs$playSound(Player player, LevelAccessor level, BlockPos pos, boolean pressed);

    @Invoker("checkPressed")
    void vs$checkPressed(BlockState state, Level level, BlockPos pos);
}
