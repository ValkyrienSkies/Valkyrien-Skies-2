package org.valkyrienskies.mod.mixinducks.world.entity;

import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;

public interface PlayerDuck {

    MinecraftPlayer vs_getPlayer();

    Vec3 vs_getQueuedPositionUpdate();
    void vs_setQueuedPositionUpdate(Vec3 queuedPositionUpdate);

    boolean vs_handledMovePacket();
    void vs_setHandledMovePacket(boolean handledMovePacket);
}
