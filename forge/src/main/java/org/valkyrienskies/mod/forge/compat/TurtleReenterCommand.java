package org.valkyrienskies.mod.forge.compat;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.turtle.core.InteractDirection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

public class TurtleReenterCommand implements ITurtleCommand {
    private InteractDirection direction;

    public TurtleReenterCommand(InteractDirection inputDirection) {
        direction = inputDirection;
    }

    @NotNull
    @Override
    public TurtleCommandResult execute(@NotNull ITurtleAccess iTurtleAccess) {
        if (VSGameConfig.SERVER.getCOMPUTERCRAFT().getTurtlesCanReenterShip()) {
            BlockPos currentPos = iTurtleAccess.getPosition();
            BlockPos potentShipPos = currentPos.relative(direction.toWorldDir(iTurtleAccess));
            Level world = iTurtleAccess.getWorld();

            Vector3d shipPos = getShipPosFromWorldPos(world, potentShipPos);
            Ship ship = getShip(world, shipPos);
            if (ship != null) {
                if (iTurtleAccess.teleportTo(world, new BlockPos(shipPos.x + 0.5, shipPos.y + 0.5, shipPos.z + 0.5))) {
                    Object[] results = new Object[1];
                    results[0] = "reentered";
                    return TurtleCommandResult.success(results);
                }
                return TurtleCommandResult.failure("obstructed");
            }
            return TurtleCommandResult.failure("no ship");
        }
        return TurtleCommandResult.failure("disabled");
    }

    private static Vector3d getShipPosFromWorldPos(Level world, BlockPos position) {
        List<Vector3d>
            detectedShips = VSGameUtilsKt.transformToNearbyShipsAndWorld(world, position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5, 0.1);
        for (Vector3d vec : detectedShips) {
            if (vec != null) {
                return vec;
            }
        }
        return new Vector3d(position.getX(), position.getY(), position.getZ());
    }

    private static Ship getShip(Level level, Vector3d pos) {
        return VSGameUtilsKt.getShipManagingPos(level, pos);
    }
}
