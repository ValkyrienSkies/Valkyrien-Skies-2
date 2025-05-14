package org.valkyrienskies.mod.mixin.mod_compat.cc_tweaked;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.turtle.core.TurtleMoveCommand;
import dan200.computercraft.shared.turtle.core.TurtlePlayer;
import dan200.computercraft.shared.util.WorldUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Vector3d;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

@Pseudo
@Mixin(TurtleMoveCommand.class)
public abstract class MixinTurtleMoveCommand {
    @ModifyVariable(method = "execute", at = @At("STORE"), ordinal = 1, remap = false)
    public BlockPos execute$newPosition(final BlockPos newPosition, final ITurtleAccess turtle) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(turtle.getLevel(), turtle.getPosition());
        if (ship == null) {
            return newPosition;
        }
        final AABBic box = ship.getShipAABB();;
        if (box.minX() <= newPosition.getX() && newPosition.getX() <= box.maxX() && box.minY() <= newPosition.getY() && newPosition.getY() <= box.maxY() && box.minZ() <= newPosition.getZ() && newPosition.getZ() <= box.maxZ()) {
            return newPosition;
        }
        if (!ship.getTransform().getShipToWorldScaling().equals(1, 1, 1) && !VSGameConfig.SERVER.getComputerCraft().getCanTurtlesLeaveScaledShips()) {
            return newPosition;
        }
        final Vector3d worldPos = ship.getTransform().getShipToWorld().transformPosition(new Vector3d(newPosition.getX() + 0.5, newPosition.getY() + 0.5, newPosition.getZ() + 0.5));
        return BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
    }

    @Inject(method = "canEnter", at = @At("HEAD"), remap = false, cancellable = true)
    private static void canEnter(
        final TurtlePlayer turtlePlayer, final ServerLevel world, final BlockPos position,
        final CallbackInfoReturnable<TurtleCommandResult> cir
    ) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(world, position);
        final Vector3d testPos = new Vector3d(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);

        if (ship != null) {
            final ChunkPos chunk = world.getChunkAt(position).getPos();
            if (!ship.getChunkClaim().contains(chunk.x, chunk.z)) {
                cir.setReturnValue(TurtleCommandResult.failure("Out of ship chunk"));
                return;
            }
            VSGameUtilsKt.toWorldCoordinates(world, testPos);
        }

        final boolean isAir = VSGameUtilsKt.transformToNearbyShipsAndWorld(world, testPos.x, testPos.y, testPos.z, 0.1)
            .stream()
            .map(VectorConversionsMCKt::toMinecraft)
            .map(BlockPos::containing)
            .filter(pos -> VSGameUtilsKt.getShipManagingPos(world, pos) != ship)
            .map(world::getBlockState)
            .allMatch(((Predicate<BlockState>) WorldUtil::isEmptyBlock).or(BlockState::canBeReplaced));

        if (!isAir) {
            cir.setReturnValue(TurtleCommandResult.failure("Movement obstructed by ship"));
        }
    }
}
