package org.valkyrienskies.mod.mixin.mod_compat.cc_tweaked;

import com.google.common.collect.Streams;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.turtle.core.TurtleMoveCommand;
import dan200.computercraft.shared.turtle.core.TurtlePlayer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Pseudo
@Mixin(TurtleMoveCommand.class)
public abstract class MixinTurtleMoveCommand {
    @Inject(method = "canEnter", at = @At("RETURN"), remap = false, cancellable = true)
    private static void ValkyrienSkies2$canEnter(
        TurtlePlayer turtlePlayer, ServerLevel world, BlockPos position,
        CallbackInfoReturnable<TurtleCommandResult> cir) {
        if (cir.getReturnValue().isSuccess()) {
            final Ship ship = ValkyrienSkies.getShipManagingBlock(world, position);
            if (ship == null) {
                final List<Vector3d> nearbyShips = Streams.stream(ValkyrienSkies.positionToNearbyShips(world,
                    position.getCenter().x, position.getCenter().y, position.getCenter().z, 0.1)).toList();

                final boolean inAir = nearbyShips.isEmpty() || nearbyShips
                    .stream()
                    .map(ValkyrienSkies::toMinecraft)
                    .map(BlockPos::containing)
                    .map(world::getBlockState)
                    .anyMatch(BlockState::isAir);

                if (!inAir) {
                    cir.setReturnValue(TurtleCommandResult.failure("Movement obstructed by ship"));
                }
            } else {
                final ChunkPos chunk = world.getChunkAt(position).getPos();
                if (!ship.getChunkClaim().contains(chunk.x, chunk.z)) {
                    cir.setReturnValue(TurtleCommandResult.failure("Out of ship chunk"));
                }
            }
        }
    }
}
