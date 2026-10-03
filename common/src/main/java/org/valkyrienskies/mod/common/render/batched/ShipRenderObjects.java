package org.valkyrienskies.mod.common.render.batched;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.valkyrienskies.core.api.ships.ClientShip;

/** Render-thread ownership; IDs can be reused by a new client ship between frames. */
final class ShipRenderObjects extends Long2ObjectOpenHashMap<ShipRenderObject> {
    ShipRenderObject getOrCreate(final ClientShip ship) {
        ShipRenderObject object = get(ship.getId());
        if (object == null || object.ship != ship) {
            if (object != null) object.close();
            object = new ShipRenderObject(ship);
            put(ship.getId(), object);
        }
        return object;
    }

    ShipRenderObject removeInstance(final ClientShip ship) {
        final ShipRenderObject object = get(ship.getId());
        return object != null && object.ship == ship ? remove(ship.getId()) : null;
    }
}
