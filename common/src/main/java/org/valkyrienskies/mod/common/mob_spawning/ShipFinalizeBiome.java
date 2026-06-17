package org.valkyrienskies.mod.common.mob_spawning;

import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;

public final class ShipFinalizeBiome {

    private ShipFinalizeBiome() {
    }

    public static BlockPos project(final BlockPos pos) {
        final Ship ship = ShipSpawnFinalizeContext.current();
        if (ship == null) {
            return pos;
        }
        final Vector3d world = ship.getTransform().getShipToWorld().transformPosition(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new Vector3d()
        );
        return BlockPos.containing(world.x, world.y, world.z);
    }
}
