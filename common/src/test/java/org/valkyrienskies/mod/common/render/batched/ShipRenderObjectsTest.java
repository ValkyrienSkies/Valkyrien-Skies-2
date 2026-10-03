package org.valkyrienskies.mod.common.render.batched;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.valkyrienskies.core.api.ships.ClientShip;
import static org.junit.jupiter.api.Assertions.*;

class ShipRenderObjectsTest {
    @Test
    void sameIdReloadReplacesObsoleteClientInstance() {
        final ShipRenderObjects objects = new ShipRenderObjects();
        final ClientShip first = ship(42);
        final ShipRenderObject firstRender = objects.getOrCreate(first);
        assertSame(firstRender, objects.getOrCreate(first));
        final ClientShip reloaded = ship(42);
        final ShipRenderObject secondRender = objects.getOrCreate(reloaded);
        assertNotSame(firstRender, secondRender);
        assertSame(reloaded, secondRender.ship);
        assertEquals(1, objects.size());
        // A delayed unload of the old instance must not evict the replacement.
        assertNull(objects.removeInstance(first));
        assertSame(secondRender, objects.getOrCreate(reloaded));
        assertSame(secondRender, objects.removeInstance(reloaded));
        assertTrue(objects.isEmpty());
    }

    private static ClientShip ship(final long id) {
        return (ClientShip) Proxy.newProxyInstance(ClientShip.class.getClassLoader(), new Class<?>[] {ClientShip.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getId")) return id;
                throw new UnsupportedOperationException(method.getName());
            });
    }
}
