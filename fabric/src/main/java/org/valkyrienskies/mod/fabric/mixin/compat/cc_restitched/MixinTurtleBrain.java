package org.valkyrienskies.mod.fabric.mixin.compat.cc_restitched;

import dan200.computercraft.shared.turtle.blocks.TileTurtle;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
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
        method = "Ldan200/computercraft/shared/turtle/core/TurtleBrain;teleportTo(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "HEAD"),
        index = 2
    )
    private BlockPos ValkyrienSkies2$teleportToBlockPos(final BlockPos pos) {
        final TileTurtle currentOwner = getOwner();
        final BlockPos oldPos = currentOwner.getBlockPos();
        final Level world = getLevel();

        final Ship ship = VSGameUtilsKt.getShipManagingPos(world, oldPos);
        if (ship != null) {
            // THERE IS A SHIP
            final Direction d = getNewDirection(ship, currentOwner.getDirection());
            if (!doesShipContainPoint(ship, pos)) {
                // POSITION IS OUTSIDE THE SHIP'S AABB

                currentOwner.setDirection(d);
                setOwner(currentOwner);

                if (!isShipScaled(ship)) {
                    // SHIP IS NOT SCALED

                    return getWorldPosFromShipPos(ship, pos);
                } else if (turtlesLeaveScaledShips()) {
                    // SHIP IS SCALED AND TURTLES CAN LEAVE SCALED SHIPS

                    return getWorldPosFromShipPos(ship, pos);
                }
            }
        }
        return pos;
    }

    // CUSTOM METHODS

    @Unique
    private static Direction getNewDirection(final Ship ship, final Direction direction) {
        final Matrix4dc matrix = ship.getShipToWorld();
        final Vec3i turtleDirectionVector = direction.getNormal();
        final Vector3d directionVec =
            matrix.transformDirection(turtleDirectionVector.getX(), turtleDirectionVector.getY(),
                turtleDirectionVector.getZ(), new Vector3d());
        final Direction dir = Direction.getNearest(directionVec.x, directionVec.y, directionVec.z);

        return dir;
    }

    @Unique
    private static boolean turtlesLeaveScaledShips() {
        return VSGameConfig.SERVER.getComputerCraft().getCanTurtlesLeaveScaledShips();
    }

    @Unique
    private static boolean isShipScaled(final Ship ship) {
        final Vector3dc scale = ship.getTransform().getShipToWorldScaling();
        final Vector3dc normalScale = new Vector3d(1.000E+0, 1.000E+0, 1.000E+0);
        return !scale.equals(normalScale);
    }

    @Unique
    private static boolean doesShipContainPoint(final Ship ship, final BlockPos pos) {
        final AABBic shipAABB = ship.getShipAABB();

        final AABB t = new AABB(shipAABB.maxX(), shipAABB.maxY(), shipAABB.maxZ(), shipAABB.minX(), shipAABB.minY(),
            shipAABB.minZ());
        return t.intersects(new AABB(pos));
    }

    @Unique
    private static BlockPos getWorldPosFromShipPos(final Ship ship, final BlockPos pos) {
        final Vec3 tPos = VectorConversionsMCKt.toMinecraft(
            VSGameUtilsKt.toWorldCoordinates(ship, pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5));
        final BlockPos newPos = BlockPos.containing(tPos.x, tPos.y, tPos.z);
        return newPos;
    }
}
