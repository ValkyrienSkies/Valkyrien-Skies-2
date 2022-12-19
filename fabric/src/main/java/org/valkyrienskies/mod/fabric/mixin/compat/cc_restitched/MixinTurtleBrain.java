package org.valkyrienskies.mod.fabric.mixin.compat.cc_restitched;

import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import dan200.computercraft.shared.turtle.core.TurtleBrain;
import javax.annotation.Nonnull;
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
import org.valkyrienskies.core.api.Ship;
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
    @Nonnull
    public abstract Level getWorld();

    @ModifyVariable(
        method = "Ldan200/computercraft/shared/turtle/core/TurtleBrain;teleportTo(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "HEAD"),
        index = 2
    )
    private BlockPos ValkyrienSkies2$teleportToBlockPos(BlockPos pos) {
        TileTurtle currentOwner = getOwner();
        BlockPos oldPos = currentOwner.getBlockPos();
        Level world = getWorld();

        Ship ship = VSGameUtilsKt.getShipManagingPos(world, oldPos);
        if (ship != null) {
            // THERE IS A SHIP
            Direction d = getNewDirection(ship, currentOwner.getDirection());
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
    private static Direction getNewDirection(Ship ship, Direction direction) {
        Matrix4dc matrix = ship.getShipToWorld();
        Vec3i turtleDirectionVector = direction.getNormal();
        Vector3d directionVec = matrix.transformDirection(turtleDirectionVector.getX(), turtleDirectionVector.getY(), turtleDirectionVector.getZ(), new Vector3d());
        Direction dir = Direction.getNearest(directionVec.x, directionVec.y, directionVec.z);

        return dir;
    }
    @Unique
    private static boolean turtlesLeaveScaledShips() {
        return VSGameConfig.SERVER.getComputerCraft().getTurtlesCanLeaveScaledShips();
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
    @Unique
    private static BlockPos getWorldPosFromShipPos(Ship ship, BlockPos pos) {
        Vec3 tPos = VectorConversionsMCKt.toMinecraft(
            VSGameUtilsKt.toWorldCoordinates(ship, pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5));
        BlockPos newPos = new BlockPos(tPos.x, tPos.y, tPos.z);
        return newPos;
    }
}
