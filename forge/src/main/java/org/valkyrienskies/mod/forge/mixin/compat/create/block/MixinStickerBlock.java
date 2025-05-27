package org.valkyrienskies.mod.forge.mixin.compat.create.block;

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
import org.valkyrienskies.mod.forge.mixinducks.mod_compat.create.IMixinStickerTileEntity;

@Mixin(StickerBlock.class)
public abstract class MixinStickerBlock extends WrenchableDirectionalBlock implements IBE<StickerBlockEntity> {

    public MixinStickerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public void onRemove(
        @NotNull final BlockState state,
        @NotNull final Level world,
        @NotNull final BlockPos pos,
        @NotNull final BlockState newState,
        final boolean isMoving
    ) {
        IBE.onRemove(state, world, pos, newState);
    }

    @Inject(
        method = "neighborChanged",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;", ordinal = 0),
        cancellable = true
    )
    private void injectNeighbourChanged(
        final BlockState state,
        final Level worldIn,
        final BlockPos pos,
        final Block blockIn,
        final BlockPos fromPos,
        final boolean isMoving,
        final CallbackInfo ci
    ) {
        final StickerBlockEntity ste = getBlockEntity(worldIn, pos);
        // By checking `instanceof IMixinStickerTileEntity` we only run this code if Clockwork is installed
        if (ste instanceof final IMixinStickerTileEntity iMixinStickerTileEntity && iMixinStickerTileEntity.isAlreadyPowered(false)) {
            if (!worldIn.hasNeighborSignal(pos)) {
                ci.cancel();
            } else {
                iMixinStickerTileEntity.isAlreadyPowered(true);
            }
        }
    }
}
