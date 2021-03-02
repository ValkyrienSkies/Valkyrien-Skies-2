package org.valkyrienskies.mod.mixin.client.render.chunk;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.mod.common.VSGameUtils;

@Mixin(ChunkBuilder.BuiltChunk.class)
public class MixinChunkBuilderBuiltChunk {

    @Shadow
    public Box boundingBox;
    @Shadow
    @Final
    private BlockPos.Mutable origin;

    /**
     * This mixin fixes chunk render sorting. Vanilla MC behavior is to render the chunks closest to the player first,
     * however ship chunks are extremely far away, so they always get rendered last.
     *
     * <p>By injecting here we fix the calculation that determines the distance between the player and a chunk, which
     * makes the ship chunks render in the correct order.
     */
    @Inject(method = "getSquaredCameraDistance", at = @At("HEAD"), cancellable = true)
    private void preGetSquaredCameraDistance(CallbackInfoReturnable<Double> cir) {
        final World world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }

        final ShipObject shipObject = VSGameUtils.getShipObjectManagingPos(world, origin);
        if (shipObject != null) {
            final Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
            final Vector3dc chunkPosInWorld = shipObject.getRenderTransform().getShipToWorldMatrix().transformPosition(
                new Vector3d(boundingBox.minX + 8.0, boundingBox.minY + 8.0, boundingBox.minZ + 8.0)
            );
            final double relDistanceSq =
                chunkPosInWorld.distanceSquared(camera.getPos().x, camera.getPos().y, camera.getPos().z);
            cir.setReturnValue(relDistanceSq);
        }
    }
}
