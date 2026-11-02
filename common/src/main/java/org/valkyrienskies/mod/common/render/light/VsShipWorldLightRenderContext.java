package org.valkyrienskies.mod.common.render.light;

public final class VsShipWorldLightRenderContext {

    private VsShipWorldLightRenderContext() {
    }

    private static int depth = 0;
    private static double camX;
    private static double camY;
    private static double camZ;

    public static void beginWorldTerrainLayer(final double x, final double y, final double z) {
        depth++;
        camX = x;
        camY = y;
        camZ = z;
    }

    public static void endWorldTerrainLayer() {
        if (depth > 0) {
            depth--;
        }
    }

    public static boolean isInWorldTerrainLayer() {
        return depth > 0;
    }

    public static double getCamX() {
        return camX;
    }

    public static double getCamY() {
        return camY;
    }

    public static double getCamZ() {
        return camZ;
    }
}
