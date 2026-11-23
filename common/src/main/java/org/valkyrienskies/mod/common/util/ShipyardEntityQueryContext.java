package org.valkyrienskies.mod.common.util;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.world.phys.AABB;

public final class ShipyardEntityQueryContext {

    private record Query(AABB box, long owner) {}
    private static final ThreadLocal<Deque<Query>> SHIP_SPACE_QUERIES = ThreadLocal.withInitial(ArrayDeque::new);

    private ShipyardEntityQueryContext() {
    }

    public static boolean isShipSpaceQuery(final AABB aabb) {
        return find(aabb) != null;
    }

    private static Query find(AABB box) {
        for (Query query : SHIP_SPACE_QUERIES.get()) if (query.box == box) return query;
        return null;
    }

    public static boolean acceptsSection(AABB box, Long sectionOwner) {
        Query query = find(box);
        return query == null || sectionOwner != null && query.owner == sectionOwner;
    }

    public static void pushShipSpaceQuery(final AABB aabb, long owner) {
        SHIP_SPACE_QUERIES.get().push(new Query(aabb, owner));
    }

    public static void popShipSpaceQuery() {
        final Deque<Query> queries = SHIP_SPACE_QUERIES.get();
        queries.pop();
        if (queries.isEmpty()) {
            SHIP_SPACE_QUERIES.remove();
        }
    }
}
