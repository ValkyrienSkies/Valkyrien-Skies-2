package org.valkyrienskies.mod.forge.mixinducks;

import com.jozufozu.flywheel.backend.instancing.instancing.InstancingEngine;
import com.jozufozu.flywheel.core.shader.WorldProgram;
import java.util.WeakHashMap;
import org.valkyrienskies.core.api.ships.ClientShip;

public interface MixinTileInstanceManagerDuck {

    WeakHashMap<ClientShip, InstancingEngine<WorldProgram>> getShipInstancingEngines();

}
