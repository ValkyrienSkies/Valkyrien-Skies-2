package org.valkyrienskies.mod.mixin.mod_compat.cc_tweaked;

import dan200.computercraft.shared.turtle.blocks.TurtleBlockEntity;
import dan200.computercraft.shared.turtle.core.TurtleBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Pseudo
@Mixin(TurtleBrain.class)
public abstract class MixinTurtleBrain {
    @Shadow(remap = false)
    public abstract TurtleBlockEntity getOwner();

    @Shadow(remap = false)
    public abstract void setOwner(TurtleBlockEntity owner);

    @Shadow
    public abstract Level getLevel();

    @ModifyVariable(
            method = "teleportTo(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(value = "HEAD"),
            index = 2,
            argsOnly = true,
            remap = false
    )
    private BlockPos ValkyrienSkies2$teleportToBlockPos(final BlockPos pos) {
        final TurtleBlockEntity currentOwner = getOwner();
        final BlockPos oldPos = currentOwner.getBlockPos();
        final Level world = getLevel();

        final Ship ship = ValkyrienSkies.getShipManagingBlock(world, oldPos);
        if (ship != null) {
            // THERE IS A SHIP

            final Vector3d transformedDirection = ship.getShipToWorld().transformDirection(
                ValkyrienSkies.toJOMLd(currentOwner.getDirection().getNormal())
            );
            if (!ship.getShipAABB().containsPoint(ValkyrienSkies.toJOML(pos))) {
                // POSITION IS OUTSIDE THE SHIP'S AABB

                currentOwner.setDirection(
                    Direction.getNearest(transformedDirection.x, transformedDirection.y, transformedDirection.z));
                setOwner(currentOwner);

                final boolean isShipScaled = !ship.getTransform().getShipToWorldScaling().equals(1.000E+0, 1.000E+0, 1.000E+0);

                if (isShipScaled) {
                    // SHIP IS SCALED

                    if (VSGameConfig.SERVER.getComputerCraft().getCanTurtlesLeaveScaledShips()) {
                        // TURTLES CAN LEAVE SCALED SHIPS

                        return BlockPos.containing(ValkyrienSkies.positionToWorld(ship, Vec3.atCenterOf(pos)));
                    }
                } else {
                    // SHIP ISNT SCALED

                    return BlockPos.containing(ValkyrienSkies.positionToWorld(ship, Vec3.atCenterOf(pos)));
                }
            }
        }
        return pos;
    }
}
