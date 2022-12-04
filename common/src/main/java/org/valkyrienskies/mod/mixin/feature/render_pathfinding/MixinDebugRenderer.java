package org.valkyrienskies.mod.mixin.feature.render_pathfinding;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.debug.PathfindingRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    @Shadow
    @Final
    public PathfindingRenderer pathfindingRenderer;

    @Inject(method = "render", at = @At("HEAD"))
    void render(
        final PoseStack matrixStack,
        final BufferSource buffer,
        final double camX, final double camY, final double camZ,
        final CallbackInfo ci) {
        if (VSGameConfig.COMMON.ADVANCED.getRenderPathfinding()) {
            pathfindingRenderer.render(matrixStack, buffer, camX, camY, camZ);
        }
    }

}
