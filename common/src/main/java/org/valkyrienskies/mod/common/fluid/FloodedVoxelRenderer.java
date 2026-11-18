package org.valkyrienskies.mod.common.fluid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;

public final class FloodedVoxelRenderer {
    private static final double INSET = 0.002;
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
        RetainedFluidRenderer.render(poseStack, shipWorld, cameraX, cameraY, cameraZ);
        FluidParcelRenderer.render(poseStack, bufferSource, cameraX, cameraY, cameraZ);
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
        renderExposedFaces(matrix, normalMatrix, consumer, occupied, voxel, renderOrigin,
            waterRed, waterGreen, waterBlue, packedLight, lava, stillSprite, flowSprite, 1.0F);
    }

    static void renderExposedFaces(
        final Matrix4f matrix, final Matrix3f normalMatrix, final VertexConsumer consumer,
        final FloodedFluidClientCache.CachedSnapshot occupied, final VsiFluidFloodedVoxel voxel,
        final Vector3dc renderOrigin, final float waterRed, final float waterGreen, final float waterBlue,
        final int packedLight, final boolean lava, final TextureAtlasSprite stillSprite,
        final TextureAtlasSprite flowSprite, final float alpha
    ) {
        if (!voxel.getRegions().isEmpty()) {
            FluidVoxelGeometry.forEachFlooded(voxel, (id, box) -> renderPartialBox(matrix, normalMatrix, consumer,
                box, renderOrigin, lava ? 1.0F : waterRed, lava ? 1.0F : waterGreen, lava ? 1.0F : waterBlue,
                alpha, packedLight, stillSprite, flowSprite));
            return;
        }
        final int x = voxel.getPositionX();
        final int y = voxel.getPositionY();
        final int z = voxel.getPositionZ();
        final float red = lava ? 1.0F : waterRed;
        final float green = lava ? 1.0F : waterGreen;
        final float blue = lava ? 1.0F : waterBlue;
        final float fillHeight = Math.min(255, voxel.getFillAmount()) / 255.0F;
        final float verticalInset = (float) Math.min(INSET, fillHeight * 0.25);

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
            x0, y0, y1, z0, z1, renderOrigin.y(), y0 - verticalInset, 0, 8, false);
        renderSideX(matrix, normalMatrix, consumer, occupied.get(x + 1, y, z), voxel.getFluidId(),
            red, green, blue, alpha, packedLight, flowSprite,
            x1, y0, y1, z0, z1, renderOrigin.y(), y0 - verticalInset, 0, 8, true);
        final VsiFluidFloodedVoxel below = occupied.get(x, y - 1, z);
        if (below == null || !below.getRegions().isEmpty() || below.getFluidId() != voxel.getFluidId() || below.getFillAmount() < 255) {
            texturedQuad(matrix, normalMatrix, consumer, red, green, blue, alpha, packedLight,
                x0, y0, z1, stillSprite.getU0(), stillSprite.getV1(),
                x0, y0, z0, stillSprite.getU0(), stillSprite.getV0(),
                x1, y0, z0, stillSprite.getU1(), stillSprite.getV0(),
                x1, y0, z1, stillSprite.getU1(), stillSprite.getV1(),
                0.0F, -1.0F, 0.0F);
        }
        final VsiFluidFloodedVoxel above = occupied.get(x, y + 1, z);
        if (voxel.getFillAmount() < 255 || above == null || !above.getRegions().isEmpty() ||
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
            z0, y0, y1, x0, x1, renderOrigin.y(), y0 - verticalInset, 0, 8, false);
        renderSideZ(matrix, normalMatrix, consumer, occupied.get(x, y, z + 1), voxel.getFluidId(),
            red, green, blue, alpha, packedLight, flowSprite,
            z1, y0, y1, x0, x1, renderOrigin.y(), y0 - verticalInset, 0, 8, true);
    }

    static void renderPartialBox(final Matrix4f matrix, final Matrix3f normalMatrix,
        final VertexConsumer consumer, final net.minecraft.world.phys.AABB box, final Vector3dc origin,
        final float red, final float green, final float blue, final float alpha, final int light,
        final TextureAtlasSprite still, final TextureAtlasSprite flow) {
        final double inset = Math.min(INSET, (box.maxY - box.minY) * 0.25);
        final float localX0 = (float) (box.minX - Math.floor(box.minX));
        final float localX1 = localX0 + (float) box.getXsize();
        final float localZ0 = (float) (box.minZ - Math.floor(box.minZ));
        final float localZ1 = localZ0 + (float) box.getZsize();
        final float textureBaseY = (float) (Math.floor(box.minY) - origin.y());
        final float u0 = still.getU(localX0 * 16), u1 = still.getU(localX1 * 16);
        final float v0 = still.getV(localZ0 * 16), v1 = still.getV(localZ1 * 16);
        final float x0 = (float) (box.minX - origin.x() + INSET), x1 = (float) (box.maxX - origin.x() - INSET);
        final float y0 = (float) (box.minY - origin.y() + inset), y1 = (float) (box.maxY - origin.y() - inset);
        final float z0 = (float) (box.minZ - origin.z() + INSET), z1 = (float) (box.maxZ - origin.z() - INSET);
        renderSideX(matrix, normalMatrix, consumer, null, 0, red, green, blue, alpha, light, flow,
            x0, y0, y1, z0, z1, origin.y(), textureBaseY, localZ0 * 8, localZ1 * 8, false);
        renderSideX(matrix, normalMatrix, consumer, null, 0, red, green, blue, alpha, light, flow,
            x1, y0, y1, z0, z1, origin.y(), textureBaseY, localZ0 * 8, localZ1 * 8, true);
        renderSideZ(matrix, normalMatrix, consumer, null, 0, red, green, blue, alpha, light, flow,
            z0, y0, y1, x0, x1, origin.y(), textureBaseY, localX0 * 8, localX1 * 8, false);
        renderSideZ(matrix, normalMatrix, consumer, null, 0, red, green, blue, alpha, light, flow,
            z1, y0, y1, x0, x1, origin.y(), textureBaseY, localX0 * 8, localX1 * 8, true);
        texturedQuad(matrix, normalMatrix, consumer, red, green, blue, alpha, light,
            x0, y0, z1, u0, v1, x0, y0, z0, u0, v0,
            x1, y0, z0, u1, v0, x1, y0, z1, u1, v1, 0, -1, 0);
        texturedQuad(matrix, normalMatrix, consumer, red, green, blue, alpha, light,
            x0, y1, z0, u0, v0, x0, y1, z1, u0, v1,
            x1, y1, z1, u1, v1, x1, y1, z0, u1, v0, 0, 1, 0);
    }

    private static float exposedSideBottom(
        final VsiFluidFloodedVoxel neighbor,
        final int fluidId,
        final float y0,
        final float y1,
        final double renderOriginY
    ) {
        if (neighbor == null || !neighbor.getRegions().isEmpty() ||
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
        final float blockBaseY,
        final float textureU0,
        final float textureU1,
        final boolean positive
    ) {
        final float exposedY0 =
            exposedSideBottom(neighbor, fluidId, y0, y1, renderOriginY);
        if (exposedY0 >= y1) return;
        final float topHeight = Mth.clamp(y1 - blockBaseY, 0.0F, 1.0F);
        final float bottomHeight =
            Mth.clamp(exposedY0 - blockBaseY, 0.0F, topHeight);
        final float u0 = sprite.getU(textureU0);
        final float u1 = sprite.getU(textureU1);
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
        final float blockBaseY,
        final float textureU0,
        final float textureU1,
        final boolean positive
    ) {
        final float exposedY0 =
            exposedSideBottom(neighbor, fluidId, y0, y1, renderOriginY);
        if (exposedY0 >= y1) return;
        final float topHeight = Mth.clamp(y1 - blockBaseY, 0.0F, 1.0F);
        final float bottomHeight =
            Mth.clamp(exposedY0 - blockBaseY, 0.0F, topHeight);
        final float u0 = sprite.getU(textureU0);
        final float u1 = sprite.getU(textureU1);
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
            .overlayCoords(0, 0)
            .uv2(packedLight)
            .normal(normalX, normalY, normalZ)
            .endVertex();
    }
}
