package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import static org.valkyrienskies.mod.common.VSClientGameUtils.transformRenderWithShip;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.SortedSet;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SodiumWorldRenderer.class)
public class MixinSodiumWorldRenderer {

    @Shadow
    private ClientLevel world;

    @Shadow
    private RenderSectionManager renderSectionManager;

    @Unique
    private boolean vs$prevFrameHadShips = false;

    /**
     * @reason Fix ship ghosts when ships are deleted and camera hasn't moved, and ships not rendering when teleported
     * and camera hasn't moved
     */
    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void preUpdateChunks(final CallbackInfo callbackInfo) {
        final boolean curFrameHasShips =
            !VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance()).getLoadedShips().isEmpty();
        // Mark the graph dirty if ships were loaded this frame or the previous one
        if (vs$prevFrameHadShips || curFrameHasShips) {
            this.renderSectionManager.markGraphDirty();
        }
        vs$prevFrameHadShips = curFrameHasShips;
    }

    @Redirect(method = "renderTileEntities", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private void renderShipChunkBlockEntity(final BlockEntityRenderDispatcher instance, final BlockEntity blockEntity,
        final float partialTicks, final PoseStack matrixStack, final MultiBufferSource buffer, final PoseStack matrices,
        final RenderBuffers bufferBuilders,
        final Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions, final Camera camera,
        final float tickDelta) {

        final BlockPos blockEntityPos = blockEntity.getBlockPos();
        final ClientShip shipObject = VSGameUtilsKt.getShipObjectManagingPos(world, blockEntityPos);
        if (shipObject != null) {
            final Vec3 cam = camera.getPosition();
            matrixStack.popPose();
            matrixStack.pushPose();
            transformRenderWithShip(shipObject.getRenderTransform(), matrixStack, blockEntityPos, cam.x(), cam.y(),
                cam.z());
        }
        instance.render(blockEntity, tickDelta, matrixStack, buffer);
    }

    /**
     * Fix entities in ships not rendering when Sodium is installed
     */
    @Inject(method = "doesChunkHaveFlag", at = @At("HEAD"), cancellable = true, remap = false)
    private void preDoesChunkHaveFlag(final int x, final int z, final int status, final CallbackInfoReturnable<Boolean> cir) {
        if (VSGameUtilsKt.isChunkInShipyard(world, x, z)) {
            cir.setReturnValue(true);
        }
    }
}
