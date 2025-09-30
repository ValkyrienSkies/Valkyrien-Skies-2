package org.valkyrienskies.mod.mixinducks.mod_compat.flywheel;

import org.valkyrienskies.core.api.ships.ClientShip;

public interface MixinBlockEntityStorageDuck {

    /*
        Updates every VisualEmbedding attached to a ship.
        This should be called manually to update the transformation, or it won't properly update current ship movement.
     */
    void vs$updateAllShips();

    /*
        Check the storage and remove every entry that involves the ship.
     */
    void vs$unloadShip(ClientShip ship);
}
