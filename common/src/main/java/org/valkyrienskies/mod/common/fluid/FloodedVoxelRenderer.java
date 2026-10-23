package org.valkyrienskies.mod.common.fluid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodingSnapshot;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;
import org.valkyrienskies.mod.common.config.VSGameConfig;

public final class FloodedVoxelRenderer {
    private static final double INSET = 0.002;
    private static final ResourceLocation WATER_STILL =
        new ResourceLocation("minecraft", "block/water_still");
    private static final ResourceLocation WATER_FLOW =
        new ResourceLocation("minecraft", "block/water_flow");
    private static final ResourceLocation LAVA_STILL =
        new ResourceLocation("minecraft", "block/lava_still");
    private static final ResourceLocation LAVA_FLOW =
        new ResourceLocation("minecraft", "block/lava_flow");

    private FloodedVoxelRenderer() {
    }

    public static void render(
        final PoseStack poseStack,
        final MultiBufferSource.BufferSource bufferSource,
        final VsiClientShipWorld shipWorld,
        final double cameraX,
        final double cameraY,
        final double cameraZ
    ) {
        if (!VSGameConfig.CLIENT.getRenderFloodedVoxels()) return;
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        final WorldFluidCoverage worldCoverage = new WorldFluidCoverage(level);
        final Function<ResourceLocation, TextureAtlasSprite> atlas =
            Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        final TextureAtlasSprite waterStill = atlas.apply(WATER_STILL);
        final TextureAtlasSprite waterFlow = atlas.apply(WATER_FLOW);
        final TextureAtlasSprite lavaStill = atlas.apply(LAVA_STILL);
        final TextureAtlasSprite lavaFlow = atlas.apply(LAVA_FLOW);

        for (final ClientShip ship : shipWorld.getLoadedShips()) {
            final var pair = shipWorld.getFluidGameplaySnapshot(ship.getId());
            final FloodedFluidClientCache.CachedSnapshot cached =
                FloodedFluidClientCache.get(shipWorld, ship.getId(), pair);
            if (cached == null || cached.getSnapshot().getVoxels().isEmpty()) continue;
            final FluidTopologyClientCache.CachedSnapshot topology =
                FluidTopologyClientCache.get(shipWorld, ship.getId(), pair);

            final Vector3dc renderOrigin =
                ship.getRenderTransform().getPositionInShip();
            final Matrix4dc shipToWorld =
                ship.getRenderTransform().getShipToWorld();
            // The translucent shader consumes world normals, without the camera's view rotation.
            final Matrix3f normalMatrix = new Matrix3f().set(
                (float) shipToWorld.m00(), (float) shipToWorld.m01(), (float) shipToWorld.m02(),
                (float) shipToWorld.m10(), (float) shipToWorld.m11(), (float) shipToWorld.m12(),
                (float) shipToWorld.m20(), (float) shipToWorld.m21(), (float) shipToWorld.m22()
            ).invert().transpose();
            final Vector3d worldPosition = shipToWorld.transformPosition(
                renderOrigin,
                new Vector3d()
            );
            final BlockPos.MutableBlockPos worldBlock = new BlockPos.MutableBlockPos();
            worldBlock.set(
                Mth.floor(worldPosition.x),
                Mth.floor(worldPosition.y),
                Mth.floor(worldPosition.z)
            );
            final int waterColor =
                level.getBlockTint(worldBlock, BiomeColors.WATER_COLOR_RESOLVER);
            final float waterRed = ((waterColor >> 16) & 0xFF) / 255.0F;
            final float waterGreen = ((waterColor >> 8) & 0xFF) / 255.0F;
            final float waterBlue = (waterColor & 0xFF) / 255.0F;
            final Vector3d worldSurface = new Vector3d();
            poseStack.pushPose();
            VSClientGameUtils.transformRenderWithShip(
                ship.getRenderTransform(),
                poseStack,
                renderOrigin.x(), renderOrigin.y(), renderOrigin.z(),
                cameraX, cameraY, cameraZ
            );
            final VertexConsumer consumer =
                bufferSource.getBuffer(RenderType.translucent());
            final Matrix4f matrix = poseStack.last().pose();
            for (final VsiFluidFloodedVoxel voxel : cached.getSnapshot().getVoxels()) {
                if (voxel.getFillAmount() <= 0) continue;
                final FlowingFluid fluid =
                    MassDatapackResolver.INSTANCE.getFlowingFluid(voxel.getFluidId());
                if (!WorldFluidCoverage.hasProtectedBoundary(voxel, topology, cached)
                    && worldCoverage.covers(shipToWorld, voxel, fluid)) continue;
                shipToWorld.transformPosition(
                    voxel.getPositionX() + 0.5,
                    voxel.getPositionY() + Math.min(255, voxel.getFillAmount()) / 255.0,
                    voxel.getPositionZ() + 0.5, worldSurface
                );
                worldBlock.set(Mth.floor(worldSurface.x), Mth.floor(worldSurface.y), Mth.floor(worldSurface.z));
                final boolean lava =
                    fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA;
                final int packedLight =
                    lava
                    ? LightTexture.FULL_BRIGHT
                    : level.hasChunkAt(worldBlock)
                        ? LevelRenderer.getLightColor(level, worldBlock)
                        : LightTexture.FULL_BRIGHT;
                renderExposedFaces(
                    matrix,
                    normalMatrix,
                    consumer,
                    cached,
                    voxel,
                    renderOrigin,
                    waterRed,
                    waterGreen,
                    waterBlue,
                    packedLight,
                    lava,
                    lava ? lavaStill : waterStill,
                    lava ? lavaFlow : waterFlow
                );
            }
            poseStack.popPose();
        }
    }

    static void renderExposedFaces(
        final Matrix4f matrix,
        final Matrix3f normalMatrix,
        final VertexConsumer consumer,
        final FloodedFluidClientCache.CachedSnapshot occupied,
        final VsiFluidFloodedVoxel voxel,
        final Vector3dc renderOrigin,
        final float waterRed,
        final float waterGreen,
        final float waterBlue,
        final int packedLight,
        final boolean lava,
        final TextureAtlasSprite stillSprite,
        final TextureAtlasSprite flowSprite
    ) {
        final int x = voxel.getPositionX();
        final int y = voxel.getPositionY();
        final int z = voxel.getPositionZ();
        final float red = lava ? 1.0F : waterRed;
        final float green = lava ? 1.0F : waterGreen;
        final float blue = lava ? 1.0F : waterBlue;
        final float fillHeight = Math.min(255, voxel.getFillAmount()) / 255.0F;
        final float verticalInset = (float) Math.min(INSET, fillHeight * 0.25);
        final float alpha = 1.0F;
        final float x0 = (float) (x - renderOrigin.x() + INSET);
        final float y0 = (float) (y - renderOrigin.y() + verticalInset);
        final float z0 = (float) (z - renderOrigin.z() + INSET);
        final float x1 = (float) (x - renderOrigin.x() + 1.0 - INSET);
        final float y1 = (float) (
            y - renderOrigin.y() + fillHeight - verticalInset
        );
        final float z1 = (float) (z - renderOrigin.z() + 1.0 - INSET);

        renderSideX(matrix, normalMatrix, consumer, occupied.get(x - 1, y, z), voxel.getFluidId(),
            red, green, blue, alpha, packedLight, flowSprite,
            x0, y0, y1, z0, z1, renderOrigin.y(), verticalInset, false);
        renderSideX(matrix, normalMatrix, consumer, occupied.get(x + 1, y, z), voxel.getFluidId(),
            red, green, blue, alpha, packedLight, flowSprite,
            x1, y0, y1, z0, z1, renderOrigin.y(), verticalInset, true);
        final VsiFluidFloodedVoxel below = occupied.get(x, y - 1, z);
        if (below == null || below.getFluidId() != voxel.getFluidId() || below.getFillAmount() < 255) {
            texturedQuad(matrix, normalMatrix, consumer, red, green, blue, alpha, packedLight,
                x0, y0, z1, stillSprite.getU0(), stillSprite.getV1(),
                x0, y0, z0, stillSprite.getU0(), stillSprite.getV0(),
                x1, y0, z0, stillSprite.getU1(), stillSprite.getV0(),
                x1, y0, z1, stillSprite.getU1(), stillSprite.getV1(),
                0.0F, -1.0F, 0.0F);
        }
        final VsiFluidFloodedVoxel above = occupied.get(x, y + 1, z);
        if (voxel.getFillAmount() < 255 || above == null ||
            above.getFluidId() != voxel.getFluidId() || above.getFillAmount() <= 0) {
            texturedQuad(matrix, normalMatrix, consumer, red, green, blue, alpha, packedLight,
                x0, y1, z0, stillSprite.getU0(), stillSprite.getV0(),
                x0, y1, z1, stillSprite.getU0(), stillSprite.getV1(),
                x1, y1, z1, stillSprite.getU1(), stillSprite.getV1(),
                x1, y1, z0, stillSprite.getU1(), stillSprite.getV0(),
                0.0F, 1.0F, 0.0F);
        }
        renderSideZ(matrix, normalMatrix, consumer, occupied.get(x, y, z - 1), voxel.getFluidId(),
            red, green, blue, alpha, packedLight, flowSprite,
            z0, y0, y1, x0, x1, renderOrigin.y(), verticalInset, false);
        renderSideZ(matrix, normalMatrix, consumer, occupied.get(x, y, z + 1), voxel.getFluidId(),
            red, green, blue, alpha, packedLight, flowSprite,
            z1, y0, y1, x0, x1, renderOrigin.y(), verticalInset, true);
    }

    private static float exposedSideBottom(
        final VsiFluidFloodedVoxel neighbor,
        final int fluidId,
        final float y0,
        final float y1,
        final double renderOriginY
    ) {
        if (neighbor == null ||
            neighbor.getFillAmount() <= 0 ||
            neighbor.getFluidId() != fluidId) return y0;
        final double neighborHeight = Math.min(255, neighbor.getFillAmount()) / 255.0;
        final float neighborTop =
            (float) (
                neighbor.getPositionY() - renderOriginY + neighborHeight -
                    Math.min(INSET, neighborHeight * 0.25)
            );
        return Math.min(y1, Math.max(y0, neighborTop));
    }

    private static void renderSideX(
        final Matrix4f matrix,
        final Matrix3f normalMatrix,
        final VertexConsumer consumer,
        final VsiFluidFloodedVoxel neighbor,
        final int fluidId,
        final float red,
        final float green,
        final float blue,
        final float alpha,
        final int packedLight,
        final TextureAtlasSprite sprite,
        final float x,
        final float y0,
        final float y1,
        final float z0,
        final float z1,
        final double renderOriginY,
        final float verticalInset,
        final boolean positive
    ) {
        final float exposedY0 =
            exposedSideBottom(neighbor, fluidId, y0, y1, renderOriginY);
        if (exposedY0 >= y1) return;
        final float blockBaseY = y0 - verticalInset;
        final float topHeight = Mth.clamp(y1 - blockBaseY, 0.0F, 1.0F);
        final float bottomHeight =
            Mth.clamp(exposedY0 - blockBaseY, 0.0F, topHeight);
        final float u0 = sprite.getU(0.0F);
        final float u1 = sprite.getU(8.0F);
        final float vTop = sprite.getV((1.0F - topHeight) * 8.0F);
        final float vBottom = sprite.getV((1.0F - bottomHeight) * 8.0F);
        if (positive) {
            texturedQuad(matrix, normalMatrix, consumer, red, green, blue, alpha, packedLight,
                x, exposedY0, z1, u1, vBottom,
                x, exposedY0, z0, u0, vBottom,
                x, y1, z0, u0, vTop,
                x, y1, z1, u1, vTop,
                1.0F, 0.0F, 0.0F);
        } else {
            texturedQuad(matrix, normalMatrix, consumer, red, green, blue, alpha, packedLight,
                x, exposedY0, z0, u0, vBottom,
                x, exposedY0, z1, u1, vBottom,
                x, y1, z1, u1, vTop,
                x, y1, z0, u0, vTop,
                -1.0F, 0.0F, 0.0F);
        }
    }

    private static void renderSideZ(
        final Matrix4f matrix,
        final Matrix3f normalMatrix,
        final VertexConsumer consumer,
        final VsiFluidFloodedVoxel neighbor,
        final int fluidId,
        final float red,
        final float green,
        final float blue,
        final float alpha,
        final int packedLight,
        final TextureAtlasSprite sprite,
        final float z,
        final float y0,
        final float y1,
        final float x0,
        final float x1,
        final double renderOriginY,
        final float verticalInset,
        final boolean positive
    ) {
        final float exposedY0 =
            exposedSideBottom(neighbor, fluidId, y0, y1, renderOriginY);
        if (exposedY0 >= y1) return;
        final float blockBaseY = y0 - verticalInset;
        final float topHeight = Mth.clamp(y1 - blockBaseY, 0.0F, 1.0F);
        final float bottomHeight =
            Mth.clamp(exposedY0 - blockBaseY, 0.0F, topHeight);
        final float u0 = sprite.getU(0.0F);
        final float u1 = sprite.getU(8.0F);
        final float vTop = sprite.getV((1.0F - topHeight) * 8.0F);
        final float vBottom = sprite.getV((1.0F - bottomHeight) * 8.0F);
        if (positive) {
            texturedQuad(matrix, normalMatrix, consumer, red, green, blue, alpha, packedLight,
                x0, exposedY0, z, u0, vBottom,
                x1, exposedY0, z, u1, vBottom,
                x1, y1, z, u1, vTop,
                x0, y1, z, u0, vTop,
                0.0F, 0.0F, 1.0F);
        } else {
            texturedQuad(matrix, normalMatrix, consumer, red, green, blue, alpha, packedLight,
                x1, exposedY0, z, u1, vBottom,
                x0, exposedY0, z, u0, vBottom,
                x0, y1, z, u0, vTop,
                x1, y1, z, u1, vTop,
                0.0F, 0.0F, -1.0F);
        }
    }

    private static void texturedQuad(
        final Matrix4f matrix,
        final Matrix3f normalMatrix,
        final VertexConsumer consumer,
        final float red,
        final float green,
        final float blue,
        final float alpha,
        final int packedLight,
        final float x0, final float y0, final float z0, final float u0, final float v0,
        final float x1, final float y1, final float z1, final float u1, final float v1,
        final float x2, final float y2, final float z2, final float u2, final float v2,
        final float x3, final float y3, final float z3, final float u3, final float v3,
        final float normalX,
        final float normalY,
        final float normalZ
    ) {
        final float nx = normalMatrix.m00() * normalX + normalMatrix.m10() * normalY + normalMatrix.m20() * normalZ;
        final float ny = normalMatrix.m01() * normalX + normalMatrix.m11() * normalY + normalMatrix.m21() * normalZ;
        final float nz = normalMatrix.m02() * normalX + normalMatrix.m12() * normalY + normalMatrix.m22() * normalZ;
        final float inverseLength = (float) (1.0 / Math.sqrt(nx * nx + ny * ny + nz * nz));
        final float worldNormalX = nx * inverseLength;
        final float worldNormalY = ny * inverseLength;
        final float worldNormalZ = nz * inverseLength;
        vertex(matrix, consumer, red, green, blue, alpha, packedLight,
            x0, y0, z0, u0, v0, worldNormalX, worldNormalY, worldNormalZ);
        vertex(matrix, consumer, red, green, blue, alpha, packedLight,
            x1, y1, z1, u1, v1, worldNormalX, worldNormalY, worldNormalZ);
        vertex(matrix, consumer, red, green, blue, alpha, packedLight,
            x2, y2, z2, u2, v2, worldNormalX, worldNormalY, worldNormalZ);
        vertex(matrix, consumer, red, green, blue, alpha, packedLight,
            x3, y3, z3, u3, v3, worldNormalX, worldNormalY, worldNormalZ);
    }

    private static void vertex(
        final Matrix4f matrix,
        final VertexConsumer consumer,
        final float red,
        final float green,
        final float blue,
        final float alpha,
        final int packedLight,
        final float x,
        final float y,
        final float z,
        final float u,
        final float v,
        final float normalX,
        final float normalY,
        final float normalZ
    ) {
        consumer.vertex(matrix, x, y, z)
            .color(red, green, blue, alpha)
            .uv(u, v)
            .uv2(packedLight)
            .normal(normalX, normalY, normalZ)
            .endVertex();
    }
}
