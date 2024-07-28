package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.flywheel.FlywheelCompat;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Shadow
    private ClientLevel level;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void vs_flywheel$beginRender(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        FlywheelCompat.viewProjection = new Matrix4f(projectionMatrix).mul(poseStack.last().pose());
    }

    @Inject(method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("TAIL"))
    private void vs_flywheel$checkUpdate(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (!FlywheelCompat.INSTANCE.isFlywheelInstalled()) return;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return;

        if (oldState != newState) {
            FlywheelCompat.INSTANCE.removeBlockEntity(blockEntity);
            FlywheelCompat.INSTANCE.addBlockEntity(blockEntity);
        } else {
            FlywheelCompat.INSTANCE.updateBlockEntity(blockEntity);
        }
    }

}
