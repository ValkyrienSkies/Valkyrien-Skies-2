package org.valkyrienskies.mod.air_pockets.client;

/**
 * Render-thread state for ship water biome tinting.
 *
 * <p>Shipyard water vertices are baked once, so we apply the current world-biome tint at render time via shader. This
 * context stores the tint for the ship currently being rendered (supports nesting via a small stack).
 */
public final class ShipWaterPocketShipWaterTintRenderContext {

    private ShipWaterPocketShipWaterTintRenderContext() {}

    private static final int MAX_STACK_DEPTH = 8;
    private static final int[] TINT_STACK = new int[MAX_STACK_DEPTH];
    private static int depth = 0;
    private static int tintRgb = 0xFFFFFF;

    public static void pushShipWaterTintRgb(final int rgb) {
        if (depth < MAX_STACK_DEPTH) {
            TINT_STACK[depth] = tintRgb;
        }
        depth++;
        tintRgb = rgb;
    }

    public static void popShipWaterTintRgb() {
        depth--;
        if (depth <= 0) {
            depth = 0;
            tintRgb = 0xFFFFFF;
            return;
        }
        if (depth < MAX_STACK_DEPTH) {
            tintRgb = TINT_STACK[depth];
        } else {
            tintRgb = 0xFFFFFF;
        }
    }

    public static boolean isActive() {
        return depth > 0;
    }

    public static int getTintRgb() {
        return tintRgb;
    }

    public static void clear() {
        depth = 0;
        tintRgb = 0xFFFFFF;
    }
}

