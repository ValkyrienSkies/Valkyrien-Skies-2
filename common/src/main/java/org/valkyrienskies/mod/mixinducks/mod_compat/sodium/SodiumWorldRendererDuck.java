package org.valkyrienskies.mod.mixinducks.mod_compat.sodium;

import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.mixinducks.client.render.NestedShipView;

public interface SodiumWorldRendererDuck extends NestedShipView {

    void vs$markShipRenderListsDirty();

    void vs$invalidateShipSectionCache(ClientShip ship);
}
