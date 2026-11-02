package org.valkyrienskies.mod.compat;

/*
import com.jozufozu.flywheel.backend.instancing.InstanceWorld;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class FlywheelEvents {
    static {
        registerEvents();
    }

    private static final Set<InstanceWorld> weakLoadedInstanceWorlds =
        Collections.newSetFromMap(
            new WeakHashMap<>()
        );

    private static synchronized void registerEvents() {
        // TODO api fix
//        ShipUnloadEventClient.Companion.on(event -> {
//            for (final InstanceWorld instanceWorld : weakLoadedInstanceWorlds) {
//                ((MixinBlockEntityInstanceManagerDuck) instanceWorld.getBlockEntityInstanceManager()).vs$removeShipManager(event.getShip());
//            }
//        });
    }

    public static void onInstanceWorldLoad(final InstanceWorld instanceWorld) {
        weakLoadedInstanceWorlds.add(instanceWorld);
    }

    public static void onInstanceWorldUnload(final InstanceWorld instanceWorld) {
        weakLoadedInstanceWorlds.remove(instanceWorld);
    }
}
 */
