package org.valkyrienskies.mod.mixin.world.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.BlockStateInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.hooks.VSEvents;

@Mixin(LevelChunk.class)
public class MixinLevelChunk {
    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private ChunkPos chunkPos;

    @Inject(method = "setBlockState", at = @At("TAIL"))
    public void postSetBlockState(final BlockPos pos, final BlockState state, final boolean moved,
        final CallbackInfoReturnable<BlockState> cir) {
        final BlockState prevState = cir.getReturnValue();
        final Ship ship = VSGameUtilsKt.getShipManagingPos(this.level, this.chunkPos);
        if (ship != null) {
            BlockStateInfo.INSTANCE.onSetBlock(level, pos, prevState, state);
            VSEvents.INSTANCE.getShipBlockChangeEvent$valkyrienskies_116().emit(new VSEvents.ShipBlockChangeEvent(
                ship, pos, prevState, state, level
            ));
        }
    }
}
