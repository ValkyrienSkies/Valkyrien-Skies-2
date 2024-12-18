package org.valkyrienskies.mod.mixinducks.world.entity;

import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;

public interface PlayerDuck {

    MinecraftPlayer vs_getPlayer();

    Vec3 getQueuedPositionUpdate();
    void setQueuedPositionUpdate(Vec3 queuedPositionUpdate);

    boolean handledMovePacket();
    void setHandledMovePacket(boolean handledMovePacket);
}
