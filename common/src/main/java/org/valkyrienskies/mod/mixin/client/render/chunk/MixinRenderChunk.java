package org.valkyrienskies.mod.mixin.client.render.chunk;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ChunkRenderDispatcher.RenderChunk.class)
public class MixinRenderChunk {

    @Shadow
    public AABB bb;
    @Shadow
    @Final
    private BlockPos.MutableBlockPos origin;

    /**
     * This mixin fixes chunk render sorting. Vanilla MC behavior is to render the chunks closest to the player first,
     * however ship chunks are extremely far away, so they always get rendered last.
     *
     * <p>By injecting here we fix the calculation that determines the distance between the player and a chunk, which
     * makes the ship chunks render in the correct order.
     */
    @Inject(method = "getDistToPlayerSqr", at = @At("HEAD"), cancellable = true)
    private void preGetSquaredCameraDistance(final CallbackInfoReturnable<Double> cir) {
        final ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }

        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos(world, origin);
        if (shipObject != null) {
            final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            final Vector3dc chunkPosInWorld = shipObject.getRenderTransform().getShipToWorldMatrix().transformPosition(
                new Vector3d(bb.minX + 8.0, bb.minY + 8.0, bb.minZ + 8.0)
            );
            final double relDistanceSq =
                chunkPosInWorld.distanceSquared(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
            cir.setReturnValue(relDistanceSq);
        }
    }
}
