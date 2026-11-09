package org.valkyrienskies.mod.common.fluid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        if (topology.getSnapshot().getEnabled() && topology.getSnapshot().getValid()
            && topology.getSnapshot().getVoxels().stream().anyMatch(voxel -> !voxel.getRegions().isEmpty()
                || flooding != null && flooding.get(voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ()) != null
                && (!flooding.get(voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ()).getRegions().isEmpty() || !flooding.get(voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ()).getSurfaceRegions().isEmpty()))) {
            return buildRegions(topology, flooding);
        }
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

    private static FluidOcclusionMaskData buildRegions(final FluidTopologyClientCache.CachedSnapshot topology,
        final @Nullable FloodedFluidClientCache.CachedSnapshot flooding) {
        final List<VsiFluidTopologyVoxel> cells = new ArrayList<>();
        final List<VsiFluidTopologyVoxel> complex = new ArrayList<>();
        for (final var cell : topology.getSnapshot().getVoxels()) {
            final var wet = flooding == null ? null : flooding.get(cell.getPositionX(), cell.getPositionY(), cell.getPositionZ());
            if (!cell.getRegions().isEmpty() || wet != null && (!wet.getRegions().isEmpty() || !wet.getSurfaceRegions().isEmpty())) {
                cells.add(cell); complex.add(cell);
            } else if (wet == null || wet.getFillAmount() < 255) {
                cells.add(cell);
                if (wet != null && wet.getFillAmount() > 0) complex.add(cell);
            }
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        long payloadWords = 0;
        for (final var cell : cells) {
            minX = Math.min(minX, cell.getPositionX()); maxX = Math.max(maxX, cell.getPositionX());
            minY = Math.min(minY, cell.getPositionY()); maxY = Math.max(maxY, cell.getPositionY());
            minZ = Math.min(minZ, cell.getPositionZ()); maxZ = Math.max(maxZ, cell.getPositionZ());
            final var wet = flooding == null ? null : flooding.get(cell.getPositionX(), cell.getPositionY(), cell.getPositionZ());
        }
        for (final var cell : complex) {
            final var wet = flooding == null ? null : flooding.get(cell.getPositionX(), cell.getPositionY(), cell.getPositionZ());
            payloadWords += Math.max(1, cell.getRegions().size());
            if (wet != null) payloadWords += 6L * (wet.getSurfaceRegions().isEmpty() ? Math.max(1, wet.getRegions().size()) : wet.getSurfaceRegions().size());
        }
        final long sx = (long) maxX - minX + 1, sy = (long) maxY - minY + 1, sz = (long) maxZ - minZ + 1;
        if (sx > MAX_VOLUME || sy > MAX_VOLUME / sx || sz > MAX_VOLUME / (sx * sy)) {
            throw new IllegalArgumentException("Fluid mask bounds exceed " + MAX_VOLUME + " cells");
        }
        final int sizeX = (int) sx, sizeY = (int) sy, originX = minX, originY = minY, originZ = minZ;
        final int denseWords = ((int) (sx * sy * sz) + 15) >>> 4;
        final long words = denseWords + 4L * complex.size() + payloadWords;
        if (words > MAX_VOLUME) throw new IllegalArgumentException("Fluid region mask exceeds storage limit");
        final int height = (int) ((words + TEXTURE_WIDTH - 1) / TEXTURE_WIDTH);
        final ByteBuffer bytes = BufferUtils.createByteBuffer(TEXTURE_WIDTH * height * 4);
        complex.sort(Comparator.comparingInt(cell -> cell.getPositionX() - originX + sizeX *
            (cell.getPositionY() - originY + sizeY * (cell.getPositionZ() - originZ))));
        for (final var cell : cells) {
            final int index = cell.getPositionX() - minX + sizeX *
                (cell.getPositionY() - minY + sizeY * (cell.getPositionZ() - minZ));
            final int bit = index * 2;
            bytes.put(bit >>> 3, (byte) (bytes.get(bit >>> 3) | (1 << (bit & 7))));
        }
        int payload = denseWords + complex.size() * 4;
        for (int i = 0; i < complex.size(); i++) {
            final var cell = complex.get(i);
            final int index = cell.getPositionX() - minX + sizeX *
                (cell.getPositionY() - minY + sizeY * (cell.getPositionZ() - minZ));
            final int bit = index * 2;
            bytes.put(bit >>> 3, (byte) (bytes.get(bit >>> 3) | (3 << (bit & 7))));
            final var wet = flooding == null ? null : flooding.get(cell.getPositionX(), cell.getPositionY(), cell.getPositionZ());
            final int domains = Math.max(1, cell.getRegions().size());
            final int liquids = wet == null ? 0 : wet.getSurfaceRegions().isEmpty() ? Math.max(1, wet.getRegions().size()) : wet.getSurfaceRegions().size();
            final int record = denseWords + i * 4;
            putWord(bytes, record, index);
            putWord(bytes, record + 1, domains);
            putWord(bytes, record + 2, liquids);
            putWord(bytes, record + 3, payload);
            if (cell.getRegions().isEmpty()) putWord(bytes, payload++, fullBounds());
            else for (final long region : cell.getRegions()) putWord(bytes, payload++, (int) region);
            if (wet != null) {
                if (!wet.getSurfaceRegions().isEmpty()) {
                    for (final var region : wet.getSurfaceRegions()) {
                        putWord(bytes, payload++, region.getBounds());
                        putPlane(bytes, payload, region.getNormalX(), region.getNormalY(), region.getNormalZ(), region.getLowerProjection(), region.getUpperProjection());
                        payload += 5;
                    }
                } else if (wet.getRegions().isEmpty()) {
                    putWord(bytes, payload++, fullBounds());
                    putPlane(bytes, payload, 0, 1, 0, 0, Math.min(255, wet.getFillAmount()) / 255.0); payload += 5;
                } else for (final var region : wet.getRegions()) {
                    putWord(bytes, payload++, region.getBounds());
                    putPlane(bytes, payload, 0, 1, 0, 0, region.getHeight()); payload += 5;
                }
            }
        }
        return new FluidOcclusionMaskData(minX, minY, minZ, sizeX, sizeY, (int) sz, -complex.size(), bytes);
    }

    private static int fullBounds() { return (16 << 15) | (16 << 20) | (16 << 25); }

    private static void putPlane(final ByteBuffer bytes, final int offset, final double nx, final double ny,
        final double nz, final double lower, final double upper) {
        final double[] values = {nx, ny, nz, lower, upper};
        for (int i = 0; i < values.length; i++) putWord(bytes, offset + i, (int) Math.round((values[i] + 2.0) * 4194304.0));
    }

    private static void putWord(final ByteBuffer bytes, final int index, final int value) {
        for (int b = 0; b < 4; b++) bytes.put(index * 4 + b, (byte) (value >>> (b * 8)));
    }
}
