package org.valkyrienskies.mod.compat.sodium.light;

import org.lwjgl.system.MemoryUtil;

/** Bound groups without changing the emitter entries or their order. */
public final class ShipEmitterBounds {
    public static final int GROUP_SIZE = 8;
    public static final int GROUPS = 16;
    public static final int HEADER_OFFSET = 1024 * 32;
    public static final int GLOBAL_OFFSET = HEADER_OFFSET + GROUPS * 32;
    public static final int HEADER_BYTES = GROUPS * 32 + 32;
    public static final int MODE_DISABLED = 0;
    public static final int MODE_GLOBAL = 1;
    public static final int MODE_GROUPED = 2;

    private ShipEmitterBounds() { }

    public static void write(final long data, final int count) {
        final int renderCount = Math.min(Math.max(count, 0), 128);
        double globalMinX = Double.POSITIVE_INFINITY, globalMinY = globalMinX, globalMinZ = globalMinX;
        double globalMaxX = Double.NEGATIVE_INFINITY, globalMaxY = globalMaxX, globalMaxZ = globalMaxX;
        double groupVolume = 0;
        boolean globalBounded = renderCount > 0;
        for (int group = 0; group < GROUPS; group++) {
            double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
            double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            boolean bounded = true;
            final int first = group * GROUP_SIZE;
            final int end = Math.min(renderCount, first + GROUP_SIZE);
            for (int i = first; i < end; i++) {
                final long entry = data + i * 32L;
                final float x = MemoryUtil.memGetFloat(entry), y = MemoryUtil.memGetFloat(entry + 4);
                final float z = MemoryUtil.memGetFloat(entry + 8), light = MemoryUtil.memGetFloat(entry + 12);
                double norm = 0;
                for (int axis = 0; axis < 4; axis++) {
                    final double q = MemoryUtil.memGetFloat(entry + 16 + axis * 4L);
                    norm += q * q;
                }
                final double error = Math.abs(norm - 1.0);
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || !Float.isFinite(light) || !Double.isFinite(error) || error > .001) {
                    bounded = false;
                    break;
                }
                // Cover Euclidean light and the small scale error of a packed unit quaternion.
                final double radius = Math.max(0, light) / (1.0 - 4.0 * error) + .0001;
                final double rx = radius + Math.ulp(x) * 2.0;
                final double ry = radius + Math.ulp(y) * 2.0;
                final double rz = radius + Math.ulp(z) * 2.0;
                minX = Math.min(minX, x - rx); minY = Math.min(minY, y - ry); minZ = Math.min(minZ, z - rz);
                maxX = Math.max(maxX, x + rx); maxY = Math.max(maxY, y + ry); maxZ = Math.max(maxZ, z + rz);
            }
            final long header = data + HEADER_OFFSET + group * 32L;
            bounded &= first < end;
            MemoryUtil.memPutFloat(header, bounded ? Math.nextDown((float) minX) : 0);
            MemoryUtil.memPutFloat(header + 4, bounded ? Math.nextDown((float) minY) : 0);
            MemoryUtil.memPutFloat(header + 8, bounded ? Math.nextDown((float) minZ) : 0);
            MemoryUtil.memPutFloat(header + 12, bounded ? 1 : 0);
            MemoryUtil.memPutFloat(header + 16, bounded ? Math.nextUp((float) maxX) : 0);
            MemoryUtil.memPutFloat(header + 20, bounded ? Math.nextUp((float) maxY) : 0);
            MemoryUtil.memPutFloat(header + 24, bounded ? Math.nextUp((float) maxZ) : 0);
            MemoryUtil.memPutFloat(header + 28, 0);
            if (first < end) {
                globalBounded &= bounded;
                if (bounded) {
                    final double lowerX = MemoryUtil.memGetFloat(header);
                    final double lowerY = MemoryUtil.memGetFloat(header + 4);
                    final double lowerZ = MemoryUtil.memGetFloat(header + 8);
                    final double upperX = MemoryUtil.memGetFloat(header + 16);
                    final double upperY = MemoryUtil.memGetFloat(header + 20);
                    final double upperZ = MemoryUtil.memGetFloat(header + 24);
                    globalMinX = Math.min(globalMinX, lowerX); globalMinY = Math.min(globalMinY, lowerY);
                    globalMinZ = Math.min(globalMinZ, lowerZ);
                    globalMaxX = Math.max(globalMaxX, upperX); globalMaxY = Math.max(globalMaxY, upperY);
                    globalMaxZ = Math.max(globalMaxZ, upperZ);
                    groupVolume += (upperX - lowerX) * (upperY - lowerY) * (upperZ - lowerZ);
                }
            }
        }
        final double globalVolume = (globalMaxX - globalMinX) * (globalMaxY - globalMinY)
            * (globalMaxZ - globalMinZ);
        globalBounded &= Double.isFinite(globalVolume) && globalVolume > 0;
        // Use a flat scan when most group bounds overlap. Keep the global rejection.
        final int mode = !globalBounded ? MODE_DISABLED
            : renderCount > GROUP_SIZE && groupVolume <= globalVolume * 1.5 ? MODE_GROUPED : MODE_GLOBAL;
        final long global = data + GLOBAL_OFFSET;
        MemoryUtil.memPutFloat(global, globalBounded ? (float) globalMinX : 0);
        MemoryUtil.memPutFloat(global + 4, globalBounded ? (float) globalMinY : 0);
        MemoryUtil.memPutFloat(global + 8, globalBounded ? (float) globalMinZ : 0);
        MemoryUtil.memPutFloat(global + 12, mode);
        MemoryUtil.memPutFloat(global + 16, globalBounded ? (float) globalMaxX : 0);
        MemoryUtil.memPutFloat(global + 20, globalBounded ? (float) globalMaxY : 0);
        MemoryUtil.memPutFloat(global + 24, globalBounded ? (float) globalMaxZ : 0);
        MemoryUtil.memPutFloat(global + 28, renderCount);
    }
}
