package org.valkyrienskies.mod.common.air_pockets

import net.minecraft.util.Mth
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.Arrays
import java.util.BitSet

internal object ShipWaterPocketAsyncCull {
    @JvmStatic
    fun buildOccMaskWords(
        shapes: Array<VoxelShape?>,
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
        sub: Int,
    ): IntArray {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || sub <= 0) return IntArray(0)

        val subCount = sub * sub * sub
        val occWordsPerVoxel = subCount ushr 5
        val volume = sizeX * sizeY * sizeZ
        val words = IntArray(volume * occWordsPerVoxel)

        for (lz in 0 until sizeZ) {
            for (ly in 0 until sizeY) {
                for (lx in 0 until sizeX) {
                    val voxelIdx = lx + sizeX * (ly + sizeY * lz)
                    val shape = shapes.getOrNull(voxelIdx) ?: continue
                    if (shape.isEmpty) continue

                    val wordBase = voxelIdx * occWordsPerVoxel
                    for (box in shape.toAabbs()) {
                        val x0 = Mth.clamp(Mth.floor(box.minX * sub.toDouble()), 0, sub)
                        val x1 = Mth.clamp(Mth.ceil(box.maxX * sub.toDouble()), 0, sub)
                        val y0 = Mth.clamp(Mth.floor(box.minY * sub.toDouble()), 0, sub)
                        val y1 = Mth.clamp(Mth.ceil(box.maxY * sub.toDouble()), 0, sub)
                        val z0 = Mth.clamp(Mth.floor(box.minZ * sub.toDouble()), 0, sub)
                        val z1 = Mth.clamp(Mth.ceil(box.maxZ * sub.toDouble()), 0, sub)

                        for (sz in z0 until z1) {
                            for (sy in y0 until y1) {
                                for (sx in x0 until x1) {
                                    val subIdx = sx + sub * (sy + sub * sz)
                                    val wordIdx = wordBase + (subIdx ushr 5)
                                    val bit = subIdx and 31
                                    words[wordIdx] = words[wordIdx] or (1 shl bit)
                                }
                            }
                        }
                    }
                }
            }
        }

        return words
    }

    @JvmStatic
    fun buildAirMaskWords(
        interior: BitSet?,
        volume: Int,
    ): IntArray {
        if (volume <= 0) return IntArray(0)
        val words = IntArray((volume + 31) ushr 5)
        if (interior == null || interior.isEmpty) return words

        var idx = interior.nextSetBit(0)
        while (idx >= 0 && idx < volume) {
            val wordIdx = idx ushr 5
            val bit = idx and 31
            words[wordIdx] = words[wordIdx] or (1 shl bit)
            idx = interior.nextSetBit(idx + 1)
        }
        return words
    }

    @JvmStatic
    fun paintFluidMask(
        width: Int,
        height: Int,
        rects: IntArray,
    ): ByteArray {
        if (width <= 0 || height <= 0) return ByteArray(0)
        val out = ByteArray(width * height)
        if (rects.isEmpty()) return out

        var i = 0
        while (i + 3 < rects.size) {
            val x0 = rects[i]
            val y0 = rects[i + 1]
            val x1 = rects[i + 2]
            val y1 = rects[i + 3]
            i += 4

            if (x1 <= x0 || y1 <= y0) continue
            val clampedX0 = x0.coerceIn(0, width)
            val clampedX1 = x1.coerceIn(0, width)
            val clampedY0 = y0.coerceIn(0, height)
            val clampedY1 = y1.coerceIn(0, height)
            if (clampedX1 <= clampedX0 || clampedY1 <= clampedY0) continue

            for (y in clampedY0 until clampedY1) {
                val rowBase = y * width
                Arrays.fill(out, rowBase + clampedX0, rowBase + clampedX1, 0xFF.toByte())
            }
        }

        return out
    }
}
