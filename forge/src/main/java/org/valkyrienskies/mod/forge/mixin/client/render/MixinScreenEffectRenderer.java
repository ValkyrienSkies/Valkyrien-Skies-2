package org.valkyrienskies.mod.forge.mixin.client.render;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import org.apache.commons.lang3.tuple.Pair;
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
    private static void viewBlockingOnShip(Player player, CallbackInfoReturnable<Pair<BlockState, BlockPos>> cir){
        Level level = player.level();
        float s = player.getDimensions(player.getPose()).width * 0.8f;
        AABB eyeHitbox = AABB.ofSize(player.getEyePosition(), s, 1.0E-6, s);
        VSGameUtilsKt.transformFromWorldToNearbyShipsAndWorld(level, eyeHitbox, arg -> {
            BlockPos.betweenClosedStream(arg).forEach(
                blockPos -> {
                    BlockState blockState = level.getBlockState(blockPos);
                    if(!blockState.isAir()
                        && blockState.isSuffocating(level, blockPos)
                        && Shapes.joinIsNotEmpty(blockState.getCollisionShape(level, blockPos).move(blockPos.getX(), blockPos.getY(), blockPos.getZ()), Shapes.create(arg), BooleanOp.AND)
                    ) {
                        cir.setReturnValue(Pair.of(blockState, blockPos));
                    }
                }
            );
        });
    }
}
