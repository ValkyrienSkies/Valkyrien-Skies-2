package org.valkyrienskies.mod.mixin.feature.render_ship_debug_bb;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.debug.ShipDebugRenderer;
import org.valkyrienskies.mod.client.debug.ShipHitboxRenderer;
import org.valkyrienskies.mod.client.render.ShipRenderViews;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.fluid.FloodedVoxelRenderer;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {
    @Inject(method = "render", at = @At("HEAD"))
    private void postRender(final PoseStack matrices, final MultiBufferSource.BufferSource bufferSource,
        final double cameraX, final double cameraY, final double cameraZ, final CallbackInfo ci) {
        final var minecraft = Minecraft.getInstance();
        final var view = ShipRenderViews.getCurrent();
        final var world = VSGameUtilsKt.getShipObjectWorld(view == null ? minecraft.level : view.getLevel());
        ShipDebugRenderer.render(matrices, bufferSource, cameraX, cameraY, cameraZ);
        FloodedVoxelRenderer.render(matrices, bufferSource, world, cameraX, cameraY, cameraZ);
        if (view != null && minecraft.player != null && minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()
            && !minecraft.showOnlyReducedInfo()) {
            ShipHitboxRenderer.renderStandaloneBodies(view, world, matrices, bufferSource);
        }
        bufferSource.endBatch();
    }
}
