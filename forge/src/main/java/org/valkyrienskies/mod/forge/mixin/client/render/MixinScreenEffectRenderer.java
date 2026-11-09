package org.valkyrienskies.mod.forge.mixin.client.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

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
        VSGameUtilsKt.getShipsIntersecting(player.level(), player.getBoundingBox()).forEach(
            ship -> {
                Vector3d pos = ship.getWorldToShip().transformPosition(d, e, f, new Vector3d());
                BlockPos blockPos = BlockPos.containing(pos.x, pos.y, pos.z);
                BlockState state = player.level().getBlockState(blockPos);
                if(state.getRenderShape() != RenderShape.INVISIBLE && state.isViewBlocking(player.level(), blockPos)) cir.setReturnValue(Pair.of(state, blockPos));
            }
        );
    }
}
