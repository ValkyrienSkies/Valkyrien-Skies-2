package org.valkyrienskies.mod.mixinducks.mod_compat.sodium;

import java.util.WeakHashMap;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import org.valkyrienskies.core.api.ships.ClientShip;

public interface RenderSectionManagerDuck {

    WeakHashMap<ClientShip, SortedRenderLists> vs_getShipRenderLists();

}
