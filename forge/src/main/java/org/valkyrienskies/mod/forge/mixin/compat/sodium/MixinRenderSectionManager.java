package org.valkyrienskies.mod.forge.mixin.compat.sodium;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.RenderSectionManagerDuck;

@Mixin(value = RenderSectionManager.class, remap = false)
public class MixinRenderSectionManager {

    @Shadow
    @Final
    private ChunkRenderer chunkRenderer;

    @Redirect(at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/gl/device/CommandList;flush()V"),
        method = "renderLayer")
    private void redirectRenderLayer(final CommandList list, final ChunkRenderMatrices matrices,
        final TerrainRenderPass pass, final double camX, final double camY, final double camZ) {

        ((RenderSectionManagerDuck) this).vs_getShipRenderLists().forEach((ship, renderList) -> {
            final Vector3dc center = ship.getRenderTransform().getPositionInShip();
            final org.joml.Matrix4dc s = ship.getRenderTransform().getShipToWorld();
            final Matrix4d newModelView = new Matrix4d(matrices.modelView())
                .translate(-camX, -camY, -camZ)
                .mul(s.m00(), s.m01(), s.m02(), s.m03(), s.m10(), s.m11(), s.m12(), s.m13(), s.m20(),
                    s.m21(), s.m22(), s.m23(), s.m30(), s.m31(), s.m32(), s.m33())
                .translate(center.x(), center.y(), center.z());

            final ChunkRenderMatrices newMatrices =
                new ChunkRenderMatrices(matrices.projection(), new Matrix4f(newModelView));
            chunkRenderer.render(newMatrices, list, renderList, pass,
                new CameraTransform(center.x(), center.y(), center.z()));
            list.close();
        });
    }
}
