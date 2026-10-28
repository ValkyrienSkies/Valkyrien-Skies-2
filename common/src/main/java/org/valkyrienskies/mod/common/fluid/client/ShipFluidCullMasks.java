package org.valkyrienskies.mod.common.fluid.client;

import java.util.BitSet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Packs per-cell occupancy into the flat {@code int[]} bit words the cull renderer uploads.
 *
 * <p>Kept free of any Minecraft world access so it can run on {@link ShipFluidAsyncRuntime}: callers
 * snapshot the block shapes on the render thread and hand the arrays over.</p>
 */
public final class ShipFluidCullMasks {

    private ShipFluidCullMasks() {
    }

    /**
     * Rasterizes each cell's blocking shape into a {@code sub}<sup>3</sup> sub-cell bit mask.
     *
     * @param shapes per-cell blocking shapes, indexed {@code lx + sizeX * (ly + sizeY * lz)}
     * @param sub    sub-cell resolution per axis; {@code sub^3} must be a multiple of 32
     */
    public static int[] buildOccMaskWords(final @Nullable VoxelShape[] shapes, final int sizeX, final int sizeY,
        final int sizeZ, final int sub) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || sub <= 0) return new int[0];

        final int subCount = sub * sub * sub;
        final int occWordsPerVoxel = subCount >>> 5;
        final int volume = sizeX * sizeY * sizeZ;
        final int[] words = new int[volume * occWordsPerVoxel];

        for (int lz = 0; lz < sizeZ; lz++) {
            for (int ly = 0; ly < sizeY; ly++) {
                for (int lx = 0; lx < sizeX; lx++) {
                    final int voxelIdx = lx + sizeX * (ly + sizeY * lz);
                    if (voxelIdx >= shapes.length) continue;
                    final VoxelShape shape = shapes[voxelIdx];
                    if (shape == null || shape.isEmpty()) continue;

                    final int wordBase = voxelIdx * occWordsPerVoxel;
                    for (final AABB box : shape.toAabbs()) {
                        final int x0 = Mth.clamp(Mth.floor(box.minX * sub), 0, sub);
                        final int x1 = Mth.clamp(Mth.ceil(box.maxX * sub), 0, sub);
                        final int y0 = Mth.clamp(Mth.floor(box.minY * sub), 0, sub);
                        final int y1 = Mth.clamp(Mth.ceil(box.maxY * sub), 0, sub);
                        final int z0 = Mth.clamp(Mth.floor(box.minZ * sub), 0, sub);
                        final int z1 = Mth.clamp(Mth.ceil(box.maxZ * sub), 0, sub);

                        for (int sz = z0; sz < z1; sz++) {
                            for (int sy = y0; sy < y1; sy++) {
                                for (int sx = x0; sx < x1; sx++) {
                                    final int subIdx = sx + sub * (sy + sub * sz);
                                    words[wordBase + (subIdx >>> 5)] |= 1 << (subIdx & 31);
                                }
                            }
                        }
                    }
                }
            }
        }

        return words;
    }

    /** Packs a per-cell {@link BitSet} into one bit per cell. */
    public static int[] buildAirMaskWords(final @Nullable BitSet cells, final int volume) {
        if (volume <= 0) return new int[0];
        final int[] words = new int[(volume + 31) >>> 5];
        if (cells == null || cells.isEmpty()) return words;

        for (int idx = cells.nextSetBit(0); idx >= 0 && idx < volume; idx = cells.nextSetBit(idx + 1)) {
            words[idx >>> 5] |= 1 << (idx & 31);
        }
        return words;
    }
}
