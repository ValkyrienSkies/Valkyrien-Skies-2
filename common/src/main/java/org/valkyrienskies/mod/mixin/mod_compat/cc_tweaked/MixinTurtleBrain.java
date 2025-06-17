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
@Mixin(value = TurtleBrain.class, priority = 2000)
public abstract class MixinTurtleBrain {
    @Shadow(remap = false)
    public abstract TurtleBlockEntity getOwner();

    @Shadow
    public abstract Level getLevel();

    @ModifyVariable(
       method = "Ldan200/computercraft/shared/turtle/core/TurtleBrain;teleportTo(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
       at = @At(value = "HEAD"),
       index = 2
    )
    private BlockPos teleportTo$modify$blockPos(final BlockPos pos) {
        final TurtleBlockEntity owner = getOwner();
        final BlockPos oldPos = owner.getBlockPos();
        final Level world = getLevel();

        final Ship ship = VSGameUtilsKt.getShipManagingPos(world, oldPos);
        if (ship == null) {
            return pos;
        }
        final AABBic box = ship.getShipAABB();
        if (box.minX() <= pos.getX() && pos.getX() < box.maxX() && box.minY() <= pos.getY() && pos.getY() < box.maxY() && box.minZ() <= pos.getZ() && pos.getZ() < box.maxZ()) {
            return pos;
        }

        final Vec3i dirVec = owner.getDirection().getNormal();
        final Vector3d directionVec = ship.getShipToWorld().transformDirection(dirVec.getX(), dirVec.getY(), dirVec.getZ(), new Vector3d());
        owner.setDirection(Direction.getNearest(directionVec.x, directionVec.y, directionVec.z));
        if (!ship.getTransform().getShipToWorldScaling().equals(1, 1, 1) && !VSGameConfig.SERVER.getComputerCraft().getCanTurtlesLeaveScaledShips()) {
            return pos;
        }
        final Vector3d worldPos = VSGameUtilsKt.toWorldCoordinates(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
    }
}
