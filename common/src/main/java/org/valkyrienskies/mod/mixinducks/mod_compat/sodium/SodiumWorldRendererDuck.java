package org.valkyrienskies.mod.mixinducks.mod_compat.sodium;

import org.valkyrienskies.core.api.ships.ClientShip;

public interface SodiumWorldRendererDuck {

    void vs$markShipRenderListsDirty();

    void vs$invalidateShipSectionCache(ClientShip ship);
}
