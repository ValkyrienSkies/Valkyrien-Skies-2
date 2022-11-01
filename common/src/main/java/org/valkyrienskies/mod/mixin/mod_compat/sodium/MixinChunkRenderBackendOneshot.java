package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.backends.oneshot.ChunkRenderBackendOneshot;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ChunkRenderBackendOneshot.class, remap = false)
public class MixinChunkRenderBackendOneshot {
//
//    @Redirect(at = @At(value = "INVOKE",
//        target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkCameraContext;getChunkModelOffset(IIF)F"),
//        method = "prepareDrawBatch")
//    private float redirectPrepareDrawBatch(final ChunkCameraContext instance, final int chunkBlockPos,
//        final int cameraBlockPos, final float cameraPos, final ChunkCameraContext camera,
//        final ChunkOneshotGraphicsState state) {
//        final ShipObjectClient ship =
//            VSGameUtilsKt.getShipObjectManagingPos(Minecraft.getInstance().level, state.getX() >> 4, state.getZ() >> 4);
//        if (ship != null) {
//            final Vector3d camInShip = ship.getRenderTransform().getWorldToShipMatrix().transformPosition(new Vector3d(
//                camera.blockOriginX + camera.originX, camera.blockOriginY + camera.originY,
//                camera.blockOriginZ + camera.originZ));
//
//            if (cameraBlockPos == camera.blockOriginX) {
//                return (float) (state.getX() - camInShip.x);
//            } else if (cameraBlockPos == camera.blockOriginY) {
//                return (float) (state.getY() - camInShip.y);
//            } else {
//                return (float) (state.getZ() - camInShip.z);
//            }
//        }
//
//        return instance.getChunkModelOffset(chunkBlockPos, cameraBlockPos, cameraPos);
//    }

}
