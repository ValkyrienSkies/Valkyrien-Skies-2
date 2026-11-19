package org.valkyrienskies.mod.mixinducks.world.entity;

import org.valkyrienskies.mod.common.util.MinecraftPlayer;

public interface PlayerDuck {

    MinecraftPlayer vs_getPlayer();

    /** Install the existing connection's wrapper when its player entity is replaced by respawn. */
    void vs_setPlayer(MinecraftPlayer player);

}
