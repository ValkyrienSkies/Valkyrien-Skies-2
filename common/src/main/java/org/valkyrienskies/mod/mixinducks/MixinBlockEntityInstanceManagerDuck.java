package org.valkyrienskies.mod.mixinducks;

import dev.engine_room.flywheel.api.instance.InstancerProvider;
import java.util.WeakHashMap;
import org.valkyrienskies.core.api.ships.ClientShip;

public interface MixinBlockEntityInstanceManagerDuck {

    WeakHashMap<ClientShip, InstancerProvider> vs$getShipMaterialManagers();

    void vs$removeShipManager(ClientShip clientShip);
}
