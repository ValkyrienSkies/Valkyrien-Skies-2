package org.valkyrienskies.mod.mixin.feature.ship_interactions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ButtonBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ButtonBlock.class)
public interface ButtonAccessor {
    @Invoker("playSound")
    void vs$playSound(Player player, LevelAccessor level, BlockPos pos, boolean pressed);
}
