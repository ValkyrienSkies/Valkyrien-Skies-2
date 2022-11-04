package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import static org.valkyrienskies.mod.client.McClientMathUtilKt.transformRenderWithShip;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LevelRenderer.class)
public class MixinLevelRendererVanilla {
    @Shadow
    private ClientLevel level;

    /**
     * Fix the distance to render chunks, so that MC doesn't think ship chunks are too far away
     */
    @Redirect(
        method = "compileChunks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"
        )
    )
    private double includeShipChunksInNearChunks(final BlockPos b, final Vec3i v) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(
            level, b.getX(), b.getY(), b.getZ(), v.getX(), v.getY(), v.getZ()
        );
    }

    /**
     * This mixin tells the game where to render chunks; which allows us to render ship chunks in arbitrary positions.
     */
    @Inject(
        method = "renderChunkLayer",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;upload()V"),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    @SuppressWarnings("InvalidInjectorMethodSignature")
    private void renderShipChunk(final RenderType renderLayer, final PoseStack matrixStack,
        final double playerCameraX,
        final double playerCameraY, final double playerCameraZ, final Matrix4f matrix4f, final CallbackInfo ci,
        final boolean bl, final ObjectListIterator<?> objectListIterator,
        final VertexFormat format, final ShaderInstance shader,
        final Uniform uniform, final boolean bl2, final LevelRenderer.RenderChunkInfo info,
        final ChunkRenderDispatcher.RenderChunk builtChunk, final VertexBuffer vertexBuffer) {

        final int playerChunkX = ((int) playerCameraX) >> 4;
        final int playerChunkZ = ((int) playerCameraZ) >> 4;
        // Don't apply the ship render transform if the player is in the shipyard
        final boolean isPlayerInShipyard = ChunkAllocator.isChunkInShipyard(playerChunkX, playerChunkZ);

        final BlockPos renderChunkOrigin = builtChunk.getOrigin();
        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos(level, renderChunkOrigin);
        if (!isPlayerInShipyard && shipObject != null) {
            // matrixStack.pop(); so while checking for bugs this seems unusual?
            // matrixStack.push(); but it doesn't fix sadly the bug im searching for
//            matrixStack.pushPose();
            transformRenderWithShip(shipObject.getRenderTransform(), matrixStack, renderChunkOrigin,
                playerCameraX, playerCameraY, playerCameraZ);
            if (shader.PROJECTION_MATRIX != null) {
                shader.PROJECTION_MATRIX.set(matrixStack.last().pose());
            }
//            matrixStack.popPose();
        } else {
            // Restore MC default behavior (that was removed by cancelDefaultTransform())
            uniform.set((float) (renderChunkOrigin.getX() - playerCameraX),
                (float) (renderChunkOrigin.getY() - playerCameraY),
                (float) (renderChunkOrigin.getZ() - playerCameraZ));
        }
    }

    /**
     * This mixin removes the vanilla code that determines where each chunk renders.
     */
    @Redirect(
        method = "renderChunkLayer",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;set(FFF)V")
    )
    private void cancelDefaultTransform(final Uniform instance, final float f, final float g, final float h) {
        // Do nothing
    }
}
