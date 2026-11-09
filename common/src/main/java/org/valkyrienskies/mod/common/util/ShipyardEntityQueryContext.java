package org.valkyrienskies.mod.common.util;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.world.phys.AABB;

public final class ShipyardEntityQueryContext {

    private static final ThreadLocal<Deque<AABB>> SHIP_SPACE_QUERIES = ThreadLocal.withInitial(ArrayDeque::new);

    private ShipyardEntityQueryContext() {
    }

    public static boolean isShipSpaceQuery(final AABB aabb) {
        for (final AABB shipSpaceAabb : SHIP_SPACE_QUERIES.get()) {
            if (shipSpaceAabb == aabb) {
                return true;
            }
        }
        return false;
    }

    public static void pushShipSpaceQuery(final AABB aabb) {
        SHIP_SPACE_QUERIES.get().push(aabb);
    }

    public static void popShipSpaceQuery() {
        final Deque<AABB> queries = SHIP_SPACE_QUERIES.get();
        queries.pop();
        if (queries.isEmpty()) {
            SHIP_SPACE_QUERIES.remove();
        }
    }
}
