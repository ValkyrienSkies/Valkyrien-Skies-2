package org.valkyrienskies.mod.mixin.feature.air_pockets;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;

@Mixin(LevelReader.class)
public interface MixinLevelReader {

    @Inject(
        method = "isWaterAt(Lnet/minecraft/core/BlockPos;)Z",
        at = @At("RETURN"),
        cancellable = true
    )
    private void valkyrienair$ignoreWorldWaterInShipAirPockets(final BlockPos pos,
        final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return;
        if (!(((Object) this) instanceof final Level level)) return;
        if (VSGameUtilsKt.isBlockInShipyard(level, pos)) return;

        if (ShipWaterPocketManager.isWorldPosInShipWorldFluidSuppressionZone(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
