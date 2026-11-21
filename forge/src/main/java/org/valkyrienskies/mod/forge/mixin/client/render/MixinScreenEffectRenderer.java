package org.valkyrienskies.mod.forge.mixin.client.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {
    @Inject(
        method = "getOverlayBlock",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
        cancellable = true
    )
    private static void viewBlockingOnShip(Player player, CallbackInfoReturnable<Pair<BlockState, BlockPos>> cir,
        @Local(ordinal = 0) double d, @Local(ordinal = 1) double e, @Local(ordinal = 2) double f){
        if (player.level() instanceof ClientLevel level) {
            final BlockPos blockPos = VSClientGameUtils.viewBlockingShipBlock(level, d, e, f);
            if (blockPos != null) cir.setReturnValue(Pair.of(level.getBlockState(blockPos), blockPos));
        }
    }
}
