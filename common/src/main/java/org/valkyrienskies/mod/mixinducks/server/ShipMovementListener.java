package org.valkyrienskies.mod.mixinducks.server;

import net.minecraft.world.entity.Entity;
import org.joml.Vector3dc;

public interface ShipMovementListener {
    void vs$onShipDrag(Entity entity, Vector3dc movement);
}
