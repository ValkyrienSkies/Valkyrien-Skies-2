package org.valkyrienskies.mod.mixinducks.feature.tickets;

import it.unimi.dsi.fastutil.longs.LongSet;

public interface PlayerKnownShipsDuck {

    void vs_addKnownShip(long shipId);

    void vs_removeKnownShip(long shipId);

    LongSet vs_getKnownShips();

    void vs_setKnownShips(LongSet ships);

    boolean vs_isKnownShip(long shipId);

}
