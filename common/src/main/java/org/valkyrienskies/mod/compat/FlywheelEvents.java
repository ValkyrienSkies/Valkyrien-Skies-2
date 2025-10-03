package org.valkyrienskies.mod.compat;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import org.valkyrienskies.core.impl.hooks.VSEvents.ShipUnloadEventClient;
import org.valkyrienskies.core.impl.hooks.VSEvents.StartUpdateRenderTransformsEvent;
import org.valkyrienskies.mod.mixinducks.mod_compat.flywheel.MixinStorageDuck;

public class FlywheelEvents {
    static {
        registerEvents();
    }

    private static final Set<MixinStorageDuck<?>> weakStorageDucks =
        Collections.newSetFromMap(
            new WeakHashMap<>()
        );

    private static synchronized void registerEvents() {
        ShipUnloadEventClient.Companion.on(event -> {
            for (final MixinStorageDuck<?> storageDuck : weakStorageDucks) {
                storageDuck.vs$unloadShip(event.getShip());
            }
        });
        StartUpdateRenderTransformsEvent.Companion.on(event -> {
            for (final MixinStorageDuck<?> storageDuck : weakStorageDucks) {
                storageDuck.vs$updateAllShips();
            }
        });
    }

    public static void onVisualizationManagerCreation(final MixinStorageDuck<?> storageDuck) {
        weakStorageDucks.add(storageDuck);
    }
}
