package org.valkyrienskies.mod.forge.mixin.compat.cc_tweaked;

import dan200.computercraft.shared.turtle.blocks.TileTurtle;
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
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(TurtleBrain.class)
public abstract class MixinTurtleBrain {
    @Shadow
    public abstract TileTurtle getOwner();

    @Shadow
    public abstract void setOwner(TileTurtle owner);

    @Shadow
    public abstract Level getLevel();

    @ModifyVariable(
        method = "teleportTo",
        at = @At(value = "HEAD"),
        index = 2,
        remap = false
    )
    private BlockPos ValkyrienSkies2$teleportToBlockPos(final BlockPos pos) {
        final TileTurtle currentOwner = getOwner();
        final BlockPos oldPos = currentOwner.getBlockPos();
        final Level world = getLevel();

        final Ship ship = ValkyrienSkies.getShipManagingBlock(world, oldPos);
        if (ship != null) {
            // THERE IS A SHIP
            final Vector3d transformedDirection = ship.getShipToWorld().transformDirection(
                ValkyrienSkies.toJOMLd(currentOwner.getDirection().getNormal())
            );
            if (!ship.getShipAABB().containsPoint(VectorConversionsMCKt.toJOML(pos))) {
                // POSITION IS OUTSIDE THE SHIP'S AABB

                currentOwner.setDirection(
                    Direction.getNearest(transformedDirection.x, transformedDirection.y, transformedDirection.z));
                setOwner(currentOwner);

                if (ship.getTransform().getShipToWorldScaling().equals(1.000E+0, 1.000E+0, 1.000E+0) &&
                    VSGameConfig.SERVER.getComputerCraft().getCanTurtlesLeaveScaledShips()) {
                    // SHIP IS SCALED AND TURTLES CAN LEAVE SCALED SHIPS

                    return new BlockPos(ValkyrienSkies.positionToWorld(ship, Vec3.atCenterOf(pos)));
                }
            }
        }
        return pos;
    }
}
