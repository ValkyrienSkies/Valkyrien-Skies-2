package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import com.simibubi.create.content.contraptions.chassis.StickerBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.IMixinStickerTileEntity;

@Mixin(StickerBlock.class)
public abstract class MixinStickerBlock extends WrenchableDirectionalBlock implements IBE<StickerBlockEntity> {

    public MixinStickerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
        IBE.onRemove(state, world, pos, newState);
    }

    @Inject(method = "neighborChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;", ordinal = 0), cancellable = true)
    private void injectNeighbourChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving, CallbackInfo ci) {
        StickerBlockEntity ste = getBlockEntity(worldIn, pos);
        if (ste != null && ((IMixinStickerTileEntity) ste).isAlreadyPowered(false)) {
            if (!worldIn.hasNeighborSignal(pos)) {
                ci.cancel();
            } else {
                ((IMixinStickerTileEntity) ste).isAlreadyPowered(true);
            }
        }
    }
}
