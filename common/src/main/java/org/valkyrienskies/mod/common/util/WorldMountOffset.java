package org.valkyrienskies.mod.common.util;

import net.minecraft.util.Mth;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Quaterniondc;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/** Vanilla has positioned the passengers before the dragger moves the vehicle at tick end.
 * Remember the actual offsets then: re-running positionRider at render time can turn a boat rider,
 * change a horse rider's body yaw, or trigger arbitrary modded vehicle logic. */
public record WorldMountOffset(int vehicleId, int tick, Vec3 previous, Vec3 current) {
    /** Called with vanilla's final MoveFunction point, before a shipyard seat is mapped to world. */
    public static void record(Entity vehicle, Entity rider, Vec3 point) {
        EntityDraggingInformation info = ((IEntityDraggingInformationProvider) rider).getDraggingInformation();
        Vec3 offset = point.subtract(vehicle.position()).yRot(heading(vehicle, 1) * Mth.DEG_TO_RAD);
        WorldMountOffset old = info.getWorldMountOffset();
        Vec3 previous = offset;
        if (old != null && old.vehicleId == vehicle.getId()) {
            if (old.tick == vehicle.tickCount - 1) previous = old.current;
            if (old.tick == vehicle.tickCount) previous = old.previous; // e.g. rearing horse calls move twice
        }
        info.setWorldMountOffset(new WorldMountOffset(vehicle.getId(), vehicle.tickCount, previous, offset));
    }

    public static WorldMountOffset attachment(Entity vehicle, Entity rider) {
        WorldMountOffset edge = ((IEntityDraggingInformationProvider) rider).getDraggingInformation().getWorldMountOffset();
        if (edge != null && edge.vehicleId == vehicle.getId() && Math.abs(vehicle.tickCount - edge.tick) <= 1) return edge;
        return null;
    }

    public static void captureTree(Entity vehicle) {
        for (Entity rider : vehicle.getPassengers()) {
            record(vehicle, rider, rider.position());
            captureTree(rider);
        }
    }

    /** setPos updates the game AABB and entity sections but leaves both interpolation endpoints intact. */
    public static void placeTickTree(Entity vehicle, float parentTurn) {
        placeTickTree(vehicle, parentTurn, null, heading(vehicle, 1));
    }

    public static void placeTickTree(Entity vehicle, float parentTurn, Matrix4dc basis, float heading) {
        for (Entity rider : vehicle.getPassengers()) {
            WorldMountOffset edge = attachment(vehicle, rider);
            if (edge == null) continue;
            float oldHeading = heading(rider, 1);
            rider.setPos(edge.position(vehicle.position(), heading, 1, basis));
            carryOrientation(vehicle, rider, parentTurn, false);
            placeTickTree(rider, Mth.wrapDegrees(heading(rider, 1) - oldHeading));
        }
    }

    /** Only carry angles the vanilla mount actually imposes. A standing horse does not steer its rider's eyes. */
    public static void carryOrientation(Entity parent, Entity rider, float turn, boolean frame) {
        if (turn == 0) return;
        if (parent instanceof Boat) {
            rider.setYRot(rider.getYRot() + turn);
            if (frame) rider.yRotO += turn;
            if (rider instanceof LivingEntity living) {
                living.yHeadRot += turn;
                if (frame) living.yHeadRotO += turn;
            }
        } else if (!(parent instanceof AbstractHorse horse && horse.getStandAnim(0) > 0)) return;
        if (rider instanceof LivingEntity living) {
            living.yBodyRot += turn;
            if (frame) living.yBodyRotO += turn;
        }
    }

    public static float heading(Entity vehicle, float partialTick) {
        return vehicle instanceof LivingEntity living ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot)
            : Mth.rotLerp(partialTick, vehicle.yRotO, vehicle.getYRot());
    }

    public Vec3 position(Vec3 renderedVehicle, float heading, float partialTick) {
        return position(renderedVehicle, heading, partialTick, null);
    }

    public Vec3 position(Vec3 origin, float heading, float t, Matrix4dc basis) {
        Vec3 local = previous.lerp(current, t).yRot(-heading * Mth.DEG_TO_RAD);
        if (basis == null) return origin.add(local);
        Vector3d offset = basis.transformDirection(local.x, local.y, local.z, new Vector3d());
        return origin.add(offset.x, offset.y, offset.z);
    }

    public static float seatHeading(Entity parent, Quaterniondc rotation, float body, float head, float pitch) {
        if (!(parent instanceof LivingEntity)) return body;
        Vec3 look = Vec3.directionFromRotation(pitch, head);
        Vector3d local = rotation.transformInverse(new Vector3d(look.x, look.y, look.z));
        return (float) Math.toDegrees(-Math.atan2(local.x, local.z)) + Mth.wrapDegrees(body - head);
    }
}
