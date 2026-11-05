package org.valkyrienskies.mod.mixinducks.world;

import org.valkyrienskies.core.api.ships.Ship;

public interface OfShip {

    Ship getShip();

    void setShip(Ship ship);

    default void setShip(OfShip ship) {
        this.setShip(ship.getShip());
    }

}
