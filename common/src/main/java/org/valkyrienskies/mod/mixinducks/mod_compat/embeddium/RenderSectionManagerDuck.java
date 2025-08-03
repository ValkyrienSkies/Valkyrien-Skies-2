package org.valkyrienskies.mod.mixinducks.mod_compat.embeddium;

import org.embeddedt.embeddium.impl.render.chunk.lists.SortedRenderLists;
import org.valkyrienskies.core.api.ships.ClientShip;

import java.util.WeakHashMap;

public interface RenderSectionManagerDuck {

    WeakHashMap<ClientShip, SortedRenderLists> vs_getShipRenderLists();

}
