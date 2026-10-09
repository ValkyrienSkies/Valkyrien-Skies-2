package org.valkyrienskies.mod.mixin.client.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.render.light.VsDynamicLight;

@Mixin(LevelChunk.class)
public abstract class MixinClientLevelChunk {
    @Shadow @Final private Level level;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void vs$invalidateShipGeometry(final BlockPos pos, final BlockState state,
        final boolean moved, final CallbackInfoReturnable<BlockState> cir) {
        // Also handle block changes that do not send a render update.
        if (!level.isClientSide || cir.getReturnValue() == null || cir.getReturnValue() == state) return;
        final var ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship != null) VsDynamicLight.invalidateShipEmitters(ship.getId());
    }
}
