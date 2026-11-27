package org.valkyrienskies.mod.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public final class MechanicalArmCompat {
    private MechanicalArmCompat() {
    }

    public static boolean isWithinRange(Level level, BlockPos armPos, BlockPos targetPos, double range) {
        // Measure in the arm's frame, so scaled ships retain the arm's local reach.
        Vec3 target = toArmSpace(level, armPos, targetPos, Vec3.atCenterOf(targetPos));
        return target.distanceToSqr(Vec3.atCenterOf(armPos)) < range * range;
    }

    public static Vec3 toArmSpace(Level level, BlockPos armPos, BlockPos targetPos, Vec3 position) {
        Ship armShip = VSGameUtilsKt.getShipManagingPos(level, armPos);
        Ship targetShip = VSGameUtilsKt.getShipManagingPos(level, targetPos);
        if (armShip == targetShip) {
            return position;
        }
        return CompatUtil.INSTANCE.toSameSpaceAs(level, position, armShip, targetShip);
    }

    public static Direction toArmSpace(Level level, BlockPos armPos, BlockPos targetPos, Direction direction) {
        Ship armShip = VSGameUtilsKt.getShipManagingPos(level, armPos);
        Ship targetShip = VSGameUtilsKt.getShipManagingPos(level, targetPos);
        if (armShip == targetShip) {
            return direction;
        }

        Vector3d normal = new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        if (targetShip != null) {
            targetShip.getShipToWorld().transformDirection(normal);
        }
        if (armShip != null) {
            armShip.getWorldToShip().transformDirection(normal);
        }
        // Create's angle solver accepts only the six block-facing directions.
        return Direction.getNearest(normal.x, normal.y, normal.z);
    }
}
