package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import static org.valkyrienskies.mod.client.McClientMathUtilKt.transformRenderWithShip;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.SortedSet;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Camera;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SodiumWorldRenderer.class)
public class MixinSodiumWorldRenderer {

    @Shadow
    private ClientLevel world;

    @Redirect(method = "renderTileEntities", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private void renderShipChunkBlockEntity(final BlockEntityRenderDispatcher instance, final BlockEntity blockEntity,
        final float partialTicks, final PoseStack matrixStack, final MultiBufferSource buffer, final PoseStack matrices,
        final RenderBuffers bufferBuilders,
        final Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions, final Camera camera,
        final float tickDelta) {

        final BlockPos blockEntityPos = blockEntity.getBlockPos();
        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos(world, blockEntityPos);
        if (shipObject != null) {
            final Vec3 cam = camera.getPosition();
            matrixStack.popPose();
            matrixStack.pushPose();
            transformRenderWithShip(shipObject.getRenderTransform(), matrixStack, blockEntityPos, cam.x(), cam.y(),
                cam.z());
        }
        instance.render(blockEntity, tickDelta, matrixStack, buffer);
    }

}
