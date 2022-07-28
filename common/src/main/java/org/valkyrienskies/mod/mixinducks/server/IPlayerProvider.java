package org.valkyrienskies.mod.mixinducks.server;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.valkyrienskies.core.game.IPlayer;

public interface IPlayerProvider {
    IPlayer getPlayer(UUID uuid);

    IPlayer getOrCreatePlayer(ServerPlayer player);
}
