package org.valkyrienskies.mod.mixinducks;

import com.jozufozu.flywheel.api.MaterialManager;
import java.util.WeakHashMap;
import org.valkyrienskies.core.api.ships.ClientShip;

public interface MixinBlockEntityInstanceManagerDuck {

    WeakHashMap<ClientShip, MaterialManager> vs$getShipMaterialManagers();

    void vs$removeShipManager(ClientShip clientShip);
}
