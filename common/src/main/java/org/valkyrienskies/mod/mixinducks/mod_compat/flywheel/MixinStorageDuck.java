package org.valkyrienskies.mod.mixinducks.mod_compat.flywheel;

import org.valkyrienskies.core.api.ships.ClientShip;

public interface MixinStorageDuck<T> {

    /*
        Updates every VisualEmbedding attached to a ship.
        This should be called manually to update the transformation, or it won't properly update current ship movement.
     */
    void vs$updateAllShips();
    /*
        Removes ship from the storage.
        This will delete the embedding create for the ship.
     */
    void vs$unloadShip(ClientShip ship);
}
