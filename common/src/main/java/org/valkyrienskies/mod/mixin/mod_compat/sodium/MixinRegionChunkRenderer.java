package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import java.util.List;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.RegionChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.RegionChunkRendererDuck;

@Mixin(value = RegionChunkRenderer.class, remap = false)
public class MixinRegionChunkRenderer implements RegionChunkRendererDuck {

    @Unique
    private final Vector3d camInWorld = new Vector3d();

    @Unique
    private final Vector3d camInShip = new Vector3d();

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;getBounds()Lme/jellysquid/mods/sodium/client/render/chunk/data/ChunkRenderBounds;"
        ),
        method = "buildDrawBatches"
    )
    private ChunkRenderBounds injectBuildDrawBatches(final RenderSection section, final List<RenderSection> sections,
        final BlockRenderPass pass, final ChunkCameraContext c) {

        final ClientShip ship = VSGameUtilsKt.getShipObjectManagingPos(Minecraft.getInstance().level,
            section.getChunkX(), section.getChunkZ());

        if (ship != null) {
            ship.getRenderTransform().getWorldToShip().transformPosition(camInWorld, camInShip);
            final ChunkRenderBounds originalBounds = section.getBounds();
            return new ChunkRenderBounds(originalBounds.x1 - 1.9f, originalBounds.y1 - 1.9f,
                originalBounds.z1 - 1.9f, originalBounds.x2 + 1.9f, originalBounds.y2 + 1.9f,
                originalBounds.z2 + 1.9f);
        } else {
            camInShip.set(camInWorld);
            return section.getBounds();
        }
    }

    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/RegionChunkRenderer;isBlockFaceCullingEnabled:Z"
        ),
        method = "buildDrawBatches"
    )
    private boolean redirectEnabledCulling(final RegionChunkRenderer instance) {
        return false;
    }

    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkCameraContext;posX:F"
        ),
        method = "buildDrawBatches"
    )
    private float redirectCameraPosX(final ChunkCameraContext instance) {
        return (float) camInShip.x;
    }

    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkCameraContext;posY:F"
        ),
        method = "buildDrawBatches"
    )
    private float redirectCameraPosY(final ChunkCameraContext instance) {
        return (float) camInShip.y;
    }

    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkCameraContext;posZ:F"
        ),
        method = "buildDrawBatches"
    )
    private float redirectCameraPosZ(final ChunkCameraContext instance) {
        return (float) camInShip.z;
    }

    @Override
    public void setCameraForCulling(final double x, final double y, final double z) {
        camInWorld.set(x, y, z);
    }
}
