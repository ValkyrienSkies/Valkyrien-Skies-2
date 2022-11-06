package org.valkyrienskies.mod.mixin.feature.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionSpliterator;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CollisionSpliterator.class)
public class CollisionSpliteratorMixin {
    @Redirect(method = "collisionCheck",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    BlockState collisionCheckInShips(final BlockGetter instance, final BlockPos blockPos) {
        final BlockState[] blockState = {instance.getBlockState(blockPos)};
//        if (blockState[0].isAir()) {
//            if ((instance instanceof LevelChunk)) {
//                VSGameUtilsKt.transformToNearbyShipsAndWorld(((LevelChunk) instance).getLevel(), blockPos.getX(),
//                    blockPos.getY(), blockPos.getZ(), 1, (x, y, z) -> {
//                        blockState[0] = instance.getBlockState(new BlockPos(x, y, z));
//                    });
//            }
//        }
        return blockState[0];
    }
}
