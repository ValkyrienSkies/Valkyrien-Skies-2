package org.valkyrienskies.mod.fabric.mixin.compat.cc_restitched;

import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.turtle.core.TurtleMoveCommand;
import dan200.computercraft.shared.turtle.core.TurtlePlayer;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(TurtleMoveCommand.class)
public abstract class MixinTurtleMoveCommand {
    @Inject(method = "canEnter", at = @At("RETURN"), cancellable = true)
    private static void ValkyrienSkies2$canEnter(TurtlePlayer turtlePlayer, Level world, @Nonnull BlockPos position, CallbackInfoReturnable<TurtleCommandResult> cir) {
        if (((TurtleCommandResult) cir.getReturnValue()).isSuccess()) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(world, position);
            if (ship == null) {
                Ship iShip = VSGameUtilsKt.getShipManagingPos(world, getShipPosFromWorldPos(world, position));
                if (iShip != null) {
                    cir.setReturnValue(TurtleCommandResult.failure("ship"));
                }
            } else {
                ChunkPos chunk = world.getChunkAt(position).getPos();
                if (!ship.getShipActiveChunksSet().containsChunkPos(chunk.x, chunk.z)) {
                    cir.setReturnValue(TurtleCommandResult.failure("out of ship"));
                }
            }
        }
    }

    //CUSTOM METHODS
    @Unique
    private static Vector3d getShipPosFromWorldPos(Level world, BlockPos position) {
        List<Vector3d> detectedShips = VSGameUtilsKt.transformToNearbyShipsAndWorld(world, position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5, 0.1);
        for (Vector3d vec : detectedShips) {
            if (vec != null) {
                return vec;
            }
        }
        return new Vector3d(position.getX(), position.getY(), position.getZ());
    }
    @Unique
    private static boolean isShipScaled(Ship ship) {
        Vector3dc scale = ship.getShipTransform().getShipCoordinatesToWorldCoordinatesScaling();
        Vector3dc normalScale = new Vector3d(1.000E+0, 1.000E+0, 1.000E+0);
        return !scale.equals(normalScale);
    }
    @Unique
    private static boolean doesShipContainPoint(Ship ship, BlockPos pos) {
        AABBic shipAABB = ship.getShipVoxelAABB();

        AABB t = new AABB(shipAABB.maxX(), shipAABB.maxY(), shipAABB.maxZ(), shipAABB.minX(), shipAABB.minY(), shipAABB.minZ());
        boolean test = t.intersects(new AABB(pos));
        return test;
    }
}
