package org.valkyrienskies.mod.forge.mixinducks;

import com.jozufozu.flywheel.backend.material.MaterialManager;
import com.jozufozu.flywheel.core.shader.WorldProgram;
import java.util.WeakHashMap;
import org.valkyrienskies.core.game.ships.ShipObjectClient;

public interface MixinTileInstanceManagerDuck {

    WeakHashMap<ShipObjectClient, MaterialManager<WorldProgram>> getShipMaterialManagers();

}
