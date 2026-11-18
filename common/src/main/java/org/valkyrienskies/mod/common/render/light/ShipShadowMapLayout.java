package org.valkyrienskies.mod.common.render.light;

/** Stable, nested maps for downward sky shadows. All positions are relative to the map origin. */
public final class ShipShadowMapLayout {
    public static final int CASCADES = 3;
    public static final int RESOLUTION = 1024;
    public final float[] cascades = new float[CASCADES * 4];
    public double originX;
    public double originY;
    public double originZ;
    public float top;
    public float range;
    public int changedLayers;

    public boolean update(final double cameraX, final double cameraY, final double cameraZ,
        final double minY, final double maxY, final int distanceChunks) {
        final double x = Math.floor(cameraX / 16.0) * 16.0;
        final double y = Math.floor(cameraY / 16.0) * 16.0;
        final double z = Math.floor(cameraZ / 16.0) * 16.0;
        final float nextTop = (float) (Math.ceil(Math.max(maxY, cameraY) / 16.0) * 16.0 + 16.0 - y);
        final float bottom = (float) (Math.floor(Math.min(minY, cameraY) / 16.0) * 16.0 - 16.0 - y);
        boolean changed = originX != x || originY != y || originZ != z
            || top != nextTop || range != nextTop - bottom;
        changedLayers = changed ? (1 << CASCADES) - 1 : 0;
        originX = x;
        originY = y;
        originZ = z;
        top = nextTop;
        range = nextTop - bottom;
        for (int layer = 0; layer < CASCADES; layer++) {
            // The last map includes the terrain render distance and the filter border.
            final float radius = layer == 0 ? 32 : layer == 1 ? 128 : Math.max(256, distanceChunks * 16 + 32);
            final float texel = radius * 2 / RESOLUTION;
            final float cx = (float) (Math.floor(cameraX / texel) * texel - x);
            final float cz = (float) (Math.floor(cameraZ / texel) * texel - z);
            final int i = layer * 4;
            final boolean layerChanged = cascades[i] != cx || cascades[i + 1] != cz || cascades[i + 2] != radius;
            changed |= layerChanged;
            if (layerChanged) changedLayers |= 1 << layer;
            cascades[i] = cx;
            cascades[i + 1] = cz;
            cascades[i + 2] = radius;
            cascades[i + 3] = texel;
        }
        return changed;
    }

}
