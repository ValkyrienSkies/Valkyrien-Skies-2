package org.valkyrienskies.mod.mixin.client.render;

import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ShipObject;
import org.valkyrienskies.mod.VSGameUtils;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    @Shadow
    @Final
    private ObjectList<WorldRenderer.ChunkInfo> visibleChunks;
    @Shadow
    private ClientWorld world;
    @Shadow
    private BuiltChunkStorage chunks;

    private final WorldRenderer vs$thisAsWorldRenderer = WorldRenderer.class.cast(this);

    @Inject(method = "setupTerrain", at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ObjectList;iterator()Lit/unimi/dsi/fastutil/objects/ObjectListIterator;"
    ))
    private void addShipVisibleChunks(Camera camera, Frustum frustum, boolean hasForcedFrustum, int frame, boolean spectator, CallbackInfo ci) {
        final BlockPos.Mutable tempPos = new BlockPos.Mutable();
        for (ShipObject shipObject : VSGameUtils.INSTANCE.getShipObjectWorldFromWorld(world).getUuidToShipObjectMap().values()) {
            shipObject.getShipData().getShipActiveChunksSet().iterateChunkPos((x, z) -> {
                for (int y = 0; y < 16; y++) {
                    tempPos.set(x << 4, y << 4, z << 4);
                    final ChunkBuilder.BuiltChunk renderChunk = chunks.getRenderedChunk(tempPos);
                    if (renderChunk != null) {
                        final WorldRenderer.ChunkInfo newChunkInfo = MixinWorldRendererChunkInfo.invoker$new(vs$thisAsWorldRenderer, renderChunk, null, 0);
                        visibleChunks.add(newChunkInfo);
                    }
                }
                return null;
            });
        }
    }
}
