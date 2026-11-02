package org.valkyrienskies.mod.common.fluid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel;

/** CPU layout consumed by vs_shouldCullFluid in rendertype_translucent.fsh. */
final class FluidOcclusionMaskData {
    static final int TEXTURE_WIDTH = 4096;
    static final int MAX_VOLUME = 16 * 1024 * 1024;
    final int minX, minY, minZ, sizeX, sizeY, sizeZ, partialCount, textureHeight;
    final ByteBuffer bytes;

    private FluidOcclusionMaskData(final int minX, final int minY, final int minZ,
        final int sizeX, final int sizeY, final int sizeZ, final int partialCount, final ByteBuffer bytes) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.partialCount = partialCount;
        this.bytes = bytes;
        textureHeight = bytes.capacity() / (TEXTURE_WIDTH * 4);
    }

    /** Geometry is part of the mask: equal bytes at a different origin are not interchangeable. */
    boolean sameContent(final @Nullable FluidOcclusionMaskData other) {
        return other != null && minX == other.minX && minY == other.minY && minZ == other.minZ &&
            sizeX == other.sizeX && sizeY == other.sizeY && sizeZ == other.sizeZ &&
            partialCount == other.partialCount && bytes.equals(other.bytes);
    }

    /** Null means no dry cells. Oversized bounds are rejected before allocating. */
    static @Nullable FluidOcclusionMaskData build(final FluidTopologyClientCache.CachedSnapshot topology,
        final @Nullable FloodedFluidClientCache.CachedSnapshot flooding) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int partialCount = 0;
        for (final VsiFluidTopologyVoxel voxel : topology.getSnapshot().getVoxels()) {
            final int fill = topology.getDryCellFillAmount(
                voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ(), flooding);
            if (fill < 0) continue;
            if (fill > 0) partialCount++;
            minX = Math.min(minX, voxel.getPositionX());
            minY = Math.min(minY, voxel.getPositionY());
            minZ = Math.min(minZ, voxel.getPositionZ());
            maxX = Math.max(maxX, voxel.getPositionX());
            maxY = Math.max(maxY, voxel.getPositionY());
            maxZ = Math.max(maxZ, voxel.getPositionZ());
        }
        if (maxX < minX) return null;
        final long sx = (long) maxX - minX + 1, sy = (long) maxY - minY + 1, sz = (long) maxZ - minZ + 1;
        if (sx > MAX_VOLUME || sy > MAX_VOLUME / sx || sz > MAX_VOLUME / (sx * sy)) {
            throw new IllegalArgumentException("Fluid mask bounds exceed " + MAX_VOLUME + " cells");
        }
        final int volume = (int) (sx * sy * sz);
        final int bitsPerCell = partialCount == 0 ? 1 : 2;
        final int denseWords = (volume * bitsPerCell + 31) >>> 5;
        final int textureHeight = (denseWords + partialCount + TEXTURE_WIDTH - 1) / TEXTURE_WIDTH;
        final ByteBuffer bytes = BufferUtils.createByteBuffer(TEXTURE_WIDTH * textureHeight * 4);
        final long[] partials = new long[partialCount];
        int partial = 0;
        for (final VsiFluidTopologyVoxel voxel : topology.getSnapshot().getVoxels()) {
            final int fill = topology.getDryCellFillAmount(
                voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ(), flooding);
            if (fill < 0) continue;
            final int index = (voxel.getPositionX() - minX) + (int) sx *
                ((voxel.getPositionY() - minY) + (int) sy * (voxel.getPositionZ() - minZ));
            final int bit = index * bitsPerCell;
            final int state = fill == 0 ? 1 : 2; // 0 = outside/full; 1 = all dry; 2 = partial.
            bytes.put(bit >>> 3, (byte) (bytes.get(bit >>> 3) | (state << (bit & 7))));
            if (fill > 0) partials[partial++] = ((long) index << 8) | fill;
        }
        Arrays.sort(partials); // Unsigned 32-bit records: high 24 bits index, low 8 bits fill.
        for (int i = 0; i < partialCount; i++) {
            final int offset = (denseWords + i) * 4;
            final long record = partials[i];
            for (int b = 0; b < 4; b++) bytes.put(offset + b, (byte) (record >>> (8 * b)));
        }
        return new FluidOcclusionMaskData(minX, minY, minZ, (int) sx, (int) sy, (int) sz, partialCount, bytes);
    }
}
