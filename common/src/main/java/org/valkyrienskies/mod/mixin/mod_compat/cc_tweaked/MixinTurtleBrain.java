package org.valkyrienskies.mod.mixin.mod_compat.cc_tweaked;

import dan200.computercraft.shared.turtle.blocks.TurtleBlockEntity;
import dan200.computercraft.shared.turtle.core.TurtleBrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(TurtleBrain.class)
public abstract class MixinTurtleBrain {
    @Shadow
    public abstract TurtleBlockEntity getOwner();

    @Shadow
    public abstract Level getLevel();

    @ModifyVariable(
       method = "Ldan200/computercraft/shared/turtle/core/TurtleBrain;teleportTo(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
       at = @At(value = "HEAD"),
       index = 2
    )
    private BlockPos ValkyrienSkies2$teleportToBlockPos(final BlockPos pos) {
        final TurtleBlockEntity currentOwner = getOwner();
        final BlockPos oldPos = currentOwner.getBlockPos();
        final Level world = getLevel();

        final Ship ship = VSGameUtilsKt.getShipManagingPos(world, oldPos);
        if (ship == null) {
            return pos;
        }
        final AABBic box = ship.getShipAABB();
        if (box.minX() <= pos.getX() && pos.getX() < box.maxX() && box.minY() <= pos.getY() && pos.getY() < box.maxY() && box.minZ() <= pos.getZ() && pos.getZ() < box.maxZ()) {
            return pos;
        }
        
        currentOwner.setDirection(getNewDirection(ship, currentOwner.getDirection()));
        if (!ship.getTransform().getShipToWorldScaling().equals(1, 1, 1) && !VSGameConfig.SERVER.getComputerCraft().getCanTurtlesLeaveScaledShips()) {
            return pos;
        }
        final Vector3d worldPos = VSGameUtilsKt.toWorldCoordinates(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
    }

    // CUSTOM METHODS

    @Unique
    private static Direction getNewDirection(final Ship ship, final Direction direction) {
        final Vec3i dirVec = direction.getNormal();
        final Vector3d directionVec = ship.getShipToWorld().transformDirection(dirVec.getX(), dirVec.getY(), dirVec.getZ(), new Vector3d());
        return Direction.getNearest(directionVec.x, directionVec.y, directionVec.z);
    }
}
