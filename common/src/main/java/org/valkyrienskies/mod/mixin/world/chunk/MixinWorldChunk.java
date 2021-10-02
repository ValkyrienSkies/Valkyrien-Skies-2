package org.valkyrienskies.mod.mixin.world.chunk;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.BlockStateInfoProvider;

@Mixin(WorldChunk.class)
public class MixinWorldChunk {
    @Shadow
    @Final
    private World world;

    @Inject(method = "setBlockState", at = @At("TAIL"))
    public void postSetBlockState(final BlockPos pos, final BlockState state, final boolean moved,
        final CallbackInfoReturnable<BlockState> cir) {
        final BlockState prevState = cir.getReturnValue();
        BlockStateInfoProvider.INSTANCE.onSetBlock(world, pos, prevState, state);
    }
}
