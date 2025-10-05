package org.valkyrienskies.mod.compat.flywheel;

import org.valkyrienskies.core.impl.hooks.VSEvents.ShipUnloadEventClient;
import org.valkyrienskies.core.impl.hooks.VSEvents.StartUpdateRenderTransformsEvent;

public class FlywheelEvents {
    static {
        registerEvents();
    }

    private static synchronized void registerEvents() {
        ShipUnloadEventClient.Companion.on(event -> {
            ShipEmbeddingManager.unloadShip(event.getShip());
        });
        StartUpdateRenderTransformsEvent.Companion.on(event -> {
            ShipEmbeddingManager.updateAllShips();
        });
    }
}
