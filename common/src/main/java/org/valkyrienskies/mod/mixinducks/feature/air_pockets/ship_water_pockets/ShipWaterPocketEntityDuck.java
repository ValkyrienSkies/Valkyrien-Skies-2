package org.valkyrienskies.mod.mixinducks.feature.air_pockets.ship_water_pockets;

/**
 * Exposes cached "is this entity inside a ship air pocket?" checks to other mixins without duplicating logic.
 */
public interface ShipWaterPocketEntityDuck {

    /**
     * @return True if world water should be treated as air for this entity (ship air pocket).
     */
    boolean vs$isInShipAirPocketForWorldWater();
}
