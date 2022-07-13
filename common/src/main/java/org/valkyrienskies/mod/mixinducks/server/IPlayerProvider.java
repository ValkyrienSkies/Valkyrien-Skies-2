package org.valkyrienskies.mod.mixinducks.server;

import java.util.UUID;
import org.valkyrienskies.core.game.IPlayer;

public interface IPlayerProvider {
    IPlayer getPlayer(UUID uuid);
}
