package org.valkyrienskies.mod.mixinducks.world.entity;

import net.minecraft.world.entity.player.Player;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;

public interface PlayerDuck {

    MinecraftPlayer vs_getPlayer();

    /** Takes over {@code previous}'s wrapper: this entity replaces it for the same connection (respawn). */
    void vs_takeOverPlayer(Player previous);

}
