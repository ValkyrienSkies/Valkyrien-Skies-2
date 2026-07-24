package org.valkyrienskies.mod.air_pockets.client;

/**
 * Render-thread state for the ship currently being rendered.
 */
public final class ShipWaterPocketCurrentShipRenderContext {

    private ShipWaterPocketCurrentShipRenderContext() {}

    private static final int MAX_STACK_DEPTH = 8;
    private static final long[] SHIP_ID_STACK = new long[MAX_STACK_DEPTH];
    private static final boolean[] SHIP_SPACE_STACK = new boolean[MAX_STACK_DEPTH];

    private static int depth = 0;
    private static long shipId = 0L;
    private static boolean shipSpaceCoords = false;

    public static void push(final long newShipId, final boolean newShipSpaceCoords) {
        if (depth < MAX_STACK_DEPTH) {
            SHIP_ID_STACK[depth] = shipId;
            SHIP_SPACE_STACK[depth] = shipSpaceCoords;
        }
        depth++;
        shipId = newShipId;
        shipSpaceCoords = newShipSpaceCoords;
    }

    public static void pop() {
        depth--;
        if (depth <= 0) {
            depth = 0;
            shipId = 0L;
            shipSpaceCoords = false;
            return;
        }
        if (depth < MAX_STACK_DEPTH) {
            shipId = SHIP_ID_STACK[depth];
            shipSpaceCoords = SHIP_SPACE_STACK[depth];
        } else {
            shipId = 0L;
            shipSpaceCoords = false;
        }
    }

    public static boolean isActive() {
        return depth > 0;
    }

    public static long getShipId() {
        return shipId;
    }

    public static boolean isShipSpaceCoords() {
        return shipSpaceCoords;
    }

    public static void clear() {
        depth = 0;
        shipId = 0L;
        shipSpaceCoords = false;
    }
}
