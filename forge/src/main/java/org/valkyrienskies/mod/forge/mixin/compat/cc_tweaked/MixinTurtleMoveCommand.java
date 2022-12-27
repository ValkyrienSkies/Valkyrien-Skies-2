package org.valkyrienskies.mod.forge.mixin.compat.cc_tweaked;

import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.turtle.core.TurtleMoveCommand;
import dan200.computercraft.shared.turtle.core.TurtlePlayer;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(TurtleMoveCommand.class)
public abstract class MixinTurtleMoveCommand {
    @Inject(method = "canEnter", at = @At("RETURN"), remap = false, cancellable = true)
    private static void ValkyrienSkies2$canEnter(
        final TurtlePlayer turtlePlayer, final Level world, @Nonnull final BlockPos position,
        final CallbackInfoReturnable<TurtleCommandResult> cir) {
        if (cir.getReturnValue().isSuccess()) {
            final Ship ship = VSGameUtilsKt.getShipManagingPos(world, position);
            if (ship == null) {
                final Ship iShip = VSGameUtilsKt.getShipManagingPos(world, getShipPosFromWorldPos(world, position));
                if (iShip != null) {
                    cir.setReturnValue(TurtleCommandResult.failure("ship"));
                }
            } else {
                final ChunkPos chunk = world.getChunkAt(position).getPos();
                if (!ship.getChunkClaim().contains(chunk.x, chunk.z)) {
                    cir.setReturnValue(TurtleCommandResult.failure("out of ship"));
                }
            }
        }
    }

    //CUSTOM METHODS
    @Unique
    private static Vector3d getShipPosFromWorldPos(final Level world, final BlockPos position) {
        final List<Vector3d> detectedShips =
            VSGameUtilsKt.transformToNearbyShipsAndWorld(world, position.getX() + 0.5, position.getY() + 0.5,
                position.getZ() + 0.5, 0.1);
        for (final Vector3d vec : detectedShips) {
            if (vec != null) {
                return vec;
            }
        }
        return new Vector3d(position.getX(), position.getY(), position.getZ());
    }
}
