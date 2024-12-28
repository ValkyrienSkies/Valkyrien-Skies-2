package org.valkyrienskies.mod.mixin.feature.debug_sparse_voxel_rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.SparseVoxelRenderer;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {
    @Unique
    @Final
    SparseVoxelRenderer sparseVoxelRenderer = new SparseVoxelRenderer();

    @Inject(method = "render", at = @At("HEAD"))
    void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double d, double e, double f, final CallbackInfo ci) {
        //sparseVoxelRenderer.render(poseStack, bufferSource, d, e, f);
    }
}
