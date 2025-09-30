package org.valkyrienskies.mod.compat;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import org.valkyrienskies.core.impl.hooks.VSEvents.ShipUnloadEventClient;
import org.valkyrienskies.mod.mixinducks.mod_compat.flywheel.MixinBlockEntityStorageDuck;

public class FlywheelEvents {
    static {
        registerEvents();
    }

    private static final Set<MixinBlockEntityStorageDuck> weakLoadedBlockEntityStorages =
        Collections.newSetFromMap(
            new WeakHashMap<>()
        );

    private static synchronized void registerEvents() {
        ShipUnloadEventClient.Companion.on(event -> {
            for (final MixinBlockEntityStorageDuck blockEntityStorage : weakLoadedBlockEntityStorages) {
                blockEntityStorage.vs$unloadShip(event.getShip());
            }
        });
    }

    public static void onBlockEntityStorageCreation(final MixinBlockEntityStorageDuck blockEntityStorage) {
        weakLoadedBlockEntityStorages.add(blockEntityStorage);
    }
}
