package org.valkyrienskies.mod.common.render.light;

import java.nio.ByteBuffer;

/** Index shadow box groups by position. Keep the box data unchanged. */
public final class ShipShadowTree {
    public static final int GROUP_SIZE = 16;
    private static final int BOX_BYTES = 48;
    private static final int NODE_BYTES = 32;
    private static final int EXACT_FLOAT_INTEGER = 1 << 24;
    private float[] bounds = new float[0];
    private int[] order = new int[0];
    private int boxEnd;
    private int boxCount;
    private int[] nodeFirst = new int[0];
    private int[] nodeLast = new int[0];
    private int[] nodeEscape = new int[0];
    private int cachedCount = -1;
    private int cachedNodes;
    private int refitFrames;
    private double buildCost;

    public static int extraBytes(final int count) {
        if (count < 0) throw new IllegalArgumentException("Negative shadow box count");
        if (count < 32 || count > EXACT_FLOAT_INTEGER) return 0;
        final int groups = (count + GROUP_SIZE - 1) / GROUP_SIZE;
        return Math.multiplyExact(groups * 2 - 1, NODE_BYTES);
    }

    /** Append tree nodes and return their count. Zero uses the full box scan. */
    public int append(final ByteBuffer data, final int count) {
        if (count < 0 || data.position() != Math.multiplyExact(count, BOX_BYTES)) {
            throw new IllegalArgumentException("Invalid shadow box count or buffer position");
        }
        if (extraBytes(count) == 0) return 0;
        boxEnd = data.position();
        boxCount = count;
        final int groups = (count + GROUP_SIZE - 1) / GROUP_SIZE;
        if (order.length < groups) {
            final int capacity = Math.max(groups, order.length * 2);
            order = new int[capacity];
            bounds = new float[capacity * 5];
        }
        for (int group = 0; group < groups; group++) {
            order[group] = group;
            double minX = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (int i = group * GROUP_SIZE; i < Math.min((group + 1) * GROUP_SIZE, count); i++) {
                final int offset = i * BOX_BYTES;
                final float x = data.getFloat(offset);
                final float y = data.getFloat(offset + 4);
                final float z = data.getFloat(offset + 8);
                final float hx = data.getFloat(offset + 16);
                final float hy = data.getFloat(offset + 20);
                final float hz = data.getFloat(offset + 24);
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || !Float.isFinite(hx) || !Float.isFinite(hy) || !Float.isFinite(hz)) return 0;
                final double squared = (double) hx * hx + (double) hy * hy + (double) hz * hz;
                // Keep the full scan if the GPU radius calculation can overflow.
                if (squared >= Float.MAX_VALUE * 0.99) return 0;
                final double radius = Math.sqrt(squared);
                final double padding = 0.001 + radius * 0.0001
                    + 4.0 * ((double) Math.ulp(x) + Math.ulp(y) + Math.ulp(z));
                final double extent = radius + padding;
                minX = Math.min(minX, x - extent);
                minZ = Math.min(minZ, z - extent);
                maxX = Math.max(maxX, x + extent);
                maxY = Math.max(maxY, y + extent + 0.35);
                maxZ = Math.max(maxZ, z + extent);
            }
            final int offset = group * 5;
            bounds[offset] = Math.nextDown((float) minX);
            bounds[offset + 1] = Math.nextDown((float) minZ);
            bounds[offset + 2] = Math.nextUp((float) maxX);
            bounds[offset + 3] = Math.nextUp((float) maxY);
            bounds[offset + 4] = Math.nextUp((float) maxZ);
            for (int i = 0; i < 5; i++) if (!Float.isFinite(bounds[offset + i])) return 0;
        }
        if (cachedCount == count && cachedNodes > 1 && refitFrames++ < 32) {
            refit(data);
            // Rebuild if motion or a new box order makes the old layout expensive.
            if (treeCost(data, cachedNodes) <= buildCost * 1.5) return cachedNodes;
            data.position(boxEnd);
        }
        appendNode(data, 0, groups);
        cachedNodes = (data.position() - boxEnd) / NODE_BYTES;
        cachedCount = count;
        refitFrames = 0;
        if (nodeFirst.length < cachedNodes) {
            nodeFirst = new int[cachedNodes];
            nodeLast = new int[cachedNodes];
            nodeEscape = new int[cachedNodes];
        }
        for (int node = 0; node < cachedNodes; node++) {
            final int offset = boxEnd + node * NODE_BYTES;
            nodeFirst[node] = (int) data.getFloat(offset + 12);
            nodeLast[node] = (int) data.getFloat(offset + 4);
            nodeEscape[node] = (int) data.getFloat(offset + 28);
        }
        buildCost = treeCost(data, cachedNodes);
        return cachedNodes;
    }

    private void refit(final ByteBuffer data) {
        for (int node = cachedNodes - 1; node >= 0; node--) {
            final int offset = boxEnd + node * NODE_BYTES;
            if (nodeFirst[node] >= 0) {
                final int leaf = nodeFirst[node] / GROUP_SIZE * 5;
                writeNode(data, offset, bounds[leaf], bounds[leaf + 1], bounds[leaf + 2],
                    bounds[leaf + 3], bounds[leaf + 4], nodeFirst[node], nodeLast[node], nodeEscape[node]);
            } else {
                final int left = boxEnd + (node + 1) * NODE_BYTES;
                final int right = boxEnd + nodeEscape[node + 1] * NODE_BYTES;
                writeNode(data, offset, Math.min(data.getFloat(left), data.getFloat(right)),
                    Math.min(data.getFloat(left + 8), data.getFloat(right + 8)),
                    Math.max(data.getFloat(left + 16), data.getFloat(right + 16)),
                    Math.max(data.getFloat(left + 20), data.getFloat(right + 20)),
                    Math.max(data.getFloat(left + 24), data.getFloat(right + 24)),
                    -1, 0, nodeEscape[node]);
            }
        }
        data.position(boxEnd + cachedNodes * NODE_BYTES);
    }

    private double treeCost(final ByteBuffer data, final int nodes) {
        double area = 0.0;
        for (int node = 0; node < nodes; node++) {
            final int offset = boxEnd + node * NODE_BYTES;
            area += ((double) data.getFloat(offset + 16) - data.getFloat(offset))
                * ((double) data.getFloat(offset + 24) - data.getFloat(offset + 8));
        }
        final double rootArea = ((double) data.getFloat(boxEnd + 16) - data.getFloat(boxEnd))
            * ((double) data.getFloat(boxEnd + 24) - data.getFloat(boxEnd + 8));
        return area / Math.max(rootArea, Double.MIN_NORMAL);
    }

    private void appendNode(final ByteBuffer data, final int start, final int end) {
        float minX = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        double area = 0.0;
        for (int i = start; i < end; i++) {
            final int offset = order[i] * 5;
            minX = Math.min(minX, bounds[offset]);
            minZ = Math.min(minZ, bounds[offset + 1]);
            maxX = Math.max(maxX, bounds[offset + 2]);
            maxY = Math.max(maxY, bounds[offset + 3]);
            maxZ = Math.max(maxZ, bounds[offset + 4]);
            area += ((double) bounds[offset + 2] - bounds[offset])
                * ((double) bounds[offset + 4] - bounds[offset + 1]);
        }
        final int node = data.position();
        data.position(node + NODE_BYTES);
        int first = -1;
        int last = 0;
        if (end - start == 1) {
            first = order[start] * GROUP_SIZE;
            last = Math.min(first + GROUP_SIZE, boxCount);
        } else if (start == 0 && end * GROUP_SIZE >= boxCount
            && area >= ((double) maxX - minX) * ((double) maxZ - minZ) * end * 0.75) {
            // A single root is cheaper when the group bounds mostly overlap.
            first = 0;
            last = boxCount;
        } else {
            final int axis = (double) maxX - minX >= (double) maxZ - minZ ? 0 : 1;
            final int middle = (start + end) >>> 1;
            partition(start, end - 1, middle, axis);
            appendNode(data, start, middle);
            appendNode(data, middle, end);
        }
        final int escape = (data.position() - boxEnd) / NODE_BYTES;
        writeNode(data, node, minX, minZ, maxX, maxY, maxZ, first, last, escape);
    }

    private static void writeNode(final ByteBuffer data, final int node, final float minX, final float minZ,
        final float maxX, final float maxY, final float maxZ, final int first, final int last, final int escape) {
        data.putFloat(node, minX).putFloat(node + 4, last).putFloat(node + 8, minZ).putFloat(node + 12, first);
        data.putFloat(node + 16, maxX).putFloat(node + 20, maxY)
            .putFloat(node + 24, maxZ).putFloat(node + 28, escape);
    }

    private void partition(int left, int right, final int middle, final int axis) {
        while (left < right) {
            final double pivot = center(order[(left + right) >>> 1], axis);
            int low = left;
            int high = right;
            while (low <= high) {
                while (center(order[low], axis) < pivot) low++;
                while (center(order[high], axis) > pivot) high--;
                if (low <= high) {
                    final int swap = order[low];
                    order[low++] = order[high];
                    order[high--] = swap;
                }
            }
            if (middle <= high) right = high;
            else if (middle >= low) left = low;
            else return;
        }
    }

    private double center(final int group, final int axis) {
        final int offset = group * 5;
        return axis == 0 ? (double) bounds[offset] + bounds[offset + 2]
            : (double) bounds[offset + 1] + bounds[offset + 4];
    }

    public void clear() {
        bounds = new float[0];
        order = new int[0];
        nodeFirst = new int[0];
        nodeLast = new int[0];
        nodeEscape = new int[0];
        cachedCount = -1;
        cachedNodes = 0;
    }
}
