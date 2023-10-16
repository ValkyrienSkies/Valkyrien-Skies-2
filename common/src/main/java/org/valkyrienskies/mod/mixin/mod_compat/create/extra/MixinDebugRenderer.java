package org.valkyrienskies.mod.mixin.mod_compat.create.extra;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.CreateCompat;
import org.valkyrienskies.mod.compat.CreateCompat.HarvesterBlockEntity;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    /**
     * This mixin renders harvesting machines (drill, etc) bounding boxes.
     *
     * <p>They get rendered in the same pass as entities.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void postRender(final PoseStack matricesIgnore, final MultiBufferSource.BufferSource vertexConsumersIgnore,
        final double cameraX, final double cameraY, final double cameraZ, final CallbackInfo ci) {
        // Ignore the matrix/buffer inputs to this, we're really just using this mixin as a place to run our render code
        final PoseStack matrices = new PoseStack();
        final MultiBufferSource.BufferSource bufferSource =
            MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        final ClientLevel world = Minecraft.getInstance().level;

        if (CreateCompat.shouldRenderHarvesterBoxes()) {
            for (final long harvesterLong : CreateCompat.getClientHarvesters()) {
                final BlockPos harvesterPos = BlockPos.of(harvesterLong);
                // Check if chunk is loaded
                if (world.getChunk(harvesterPos.getX() >> 4, harvesterPos.getZ() >> 4, ChunkStatus.FULL, false) == null) {
                    CreateCompat.getClientHarvesters().remove(harvesterLong);
                    continue;
                }

                final BlockEntity be =  world.getBlockEntity(harvesterPos);

                // Check if harvester exists and is a harvester, this happens when the block is broken
                if (be instanceof final HarvesterBlockEntity harvester) {
                    LevelRenderer
                        .renderLineBox(matrices, bufferSource.getBuffer(RenderType.lines()),
                            harvester.getHitAABB().move(-cameraX, -cameraY, -cameraZ),
                            20.0F / 255.0F, 20.0F / 255.0F, 250.0F / 255.0F, 1.0F);
                } else {
                    CreateCompat.getClientHarvesters().remove(harvesterLong);
                }
            }
        }

        bufferSource.endBatch();
    }
}
