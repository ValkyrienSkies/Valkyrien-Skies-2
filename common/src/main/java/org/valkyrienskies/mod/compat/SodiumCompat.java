package org.valkyrienskies.mod.compat;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkStatus;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;

import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.hooks.VSGameEvents;
import org.valkyrienskies.mod.common.hooks.VSGameEvents.ShipRenderEventSodium;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixin.mod_compat.sodium.RenderSectionManagerAccessor;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.RenderSectionManagerDuck;

import com.mojang.blaze3d.systems.RenderSystem;

public class SodiumCompat {

    public static void onChunkAdded(final ClientLevel level, final int x, final int z) {
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
            ChunkTrackerHolder.get(level).onChunkStatusAdded(x, z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
        }
    }

    public static void onChunkRemoved(final ClientLevel level, final int x, final int z) {
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
            ChunkTrackerHolder.get(level).onChunkStatusRemoved(x, z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
        }
    }

    public static void vsRenderLayer(RenderSectionManager renderSectionManager, ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z,
            CommandList commandList) {

        VSGameEvents.INSTANCE.getShipsStartRenderingSodium().emit(new VSGameEvents.ShipStartRenderEventSodium(
            pass, matrices, x, y, z
        ));

        ((RenderSectionManagerDuck) renderSectionManager).vs_getShipRenderLists().forEach((ship, renderList) -> {
            VSGameEvents.INSTANCE.getRenderShipSodium().emit(new ShipRenderEventSodium(pass, matrices, x, y, z, ship, renderList));
            final ShipTransform shipTransform = ship.getRenderTransform();

            final float distanceScaling = 1 / (float) shipTransform.getShipToWorldScaling().x();
            final float initialFogStart = RenderSystem.getShaderFogStart();
            final float initialFogEnd = RenderSystem.getShaderFogEnd();

            if (distanceScaling != 1f) {
                RenderSystem.setShaderFogStart(initialFogStart * distanceScaling);
                RenderSystem.setShaderFogEnd(initialFogEnd * distanceScaling);
            }

            final Vector3dc cameraShipSpace = shipTransform.getWorldToShip().transformPosition(new Vector3d(x, y, z));
            final Matrix4dc s = ship.getRenderTransform().getShipToWorld();
            final Matrix4d newModelView = new Matrix4d(matrices.modelView())
                .translate(-x, -y, -z)
                .mul(s)
                .translate(cameraShipSpace);

            final ChunkRenderMatrices newMatrices =
                new ChunkRenderMatrices(matrices.projection(), new Matrix4f(newModelView));
            ((RenderSectionManagerAccessor) renderSectionManager).getChunkRenderer().render(newMatrices, commandList, renderList, pass,
                new CameraTransform(cameraShipSpace.x(), cameraShipSpace.y(), cameraShipSpace.z()));
            commandList.close();

             if (distanceScaling != 1f) {
                RenderSystem.setShaderFogStart(initialFogStart);
                RenderSystem.setShaderFogEnd(initialFogEnd);
            }

            VSGameEvents.INSTANCE.getPostRenderShipSodium().emit(new ShipRenderEventSodium(pass, matrices, x, y, z, ship, renderList));
        });
    }


    public static void renderShips(RenderSectionManager renderSectionManager, RenderType renderLayer, ChunkRenderMatrices matrices, double x, double y, double z) {
        RenderDevice device = RenderDevice.INSTANCE;
        CommandList commandList = device.createCommandList();

        if (renderLayer == RenderType.solid()) {
            vsRenderLayer(renderSectionManager, matrices, DefaultTerrainRenderPasses.SOLID, x, y, z, commandList);
            vsRenderLayer(renderSectionManager, matrices, DefaultTerrainRenderPasses.CUTOUT, x, y, z, commandList);
        } else if (renderLayer == RenderType.translucent()) {
            vsRenderLayer(renderSectionManager, matrices, DefaultTerrainRenderPasses.TRANSLUCENT, x, y, z, commandList);
        }

        commandList.close();
    }
}
