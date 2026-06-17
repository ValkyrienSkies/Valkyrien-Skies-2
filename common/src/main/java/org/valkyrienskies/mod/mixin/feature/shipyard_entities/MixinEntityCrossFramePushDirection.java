package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Allows entities in shipyard to push entities in world space and vice-versa.
@Mixin(Entity.class)
public abstract class MixinEntityCrossFramePushDirection {

    // All scratches used non-recursively across vs$obbAabbOverlap (called first) and
    // vs$obbFaceNormalPush (called second); push(DDD) inside the inject body doesn't
    // re-enter this mixin (vanilla push(Entity) wrap, not push(DDD)).
    @Unique
    private static final ThreadLocal<Vector3d> VS$AXIS_X = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$AXIS_Z = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$RESULT = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$AABB = ThreadLocal.withInitial(AABBd::new);

    @Shadow public abstract double getX();
    @Shadow public abstract double getY();
    @Shadow public abstract double getZ();
    @Shadow public abstract Level level();
    @Shadow public abstract AABB getBoundingBox();
    @Shadow public abstract boolean isPassengerOfSameVehicle(Entity entity);
    @Shadow public abstract boolean isVehicle();
    @Shadow public abstract void push(double dx, double dy, double dz);
    @Shadow public boolean noPhysics;

    @Inject(
        method = "push(Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void vs$crossFramePushOverride(final Entity other, final CallbackInfo ci) {
        final Entity self = (Entity) (Object) this;
        if (self.isPassengerOfSameVehicle(other)) return;
        if (self.noPhysics || other.noPhysics) return;

        final Level level = self.level();
        if (level == null) return;

        final Ship selfShipyard = VSGameUtilsKt.getShipManagingPos(level, self.blockPosition());
        final Ship otherShipyard = VSGameUtilsKt.getShipManagingPos(level, other.blockPosition());
        if (selfShipyard == otherShipyard) return;

        final Entity onShip;
        final Entity offShip;
        final Ship ship;
        if (otherShipyard != null) {
            onShip = other;
            offShip = self;
            ship = otherShipyard;
        } else {
            onShip = self;
            offShip = other;
            ship = selfShipyard;
        }

        if (!vs$obbAabbOverlap(onShip, offShip, ship)) {
            ci.cancel();
            return;
        }

        final Vector3d push = vs$obbFaceNormalPush(onShip, offShip, ship);
        if (push == null) {
            ci.cancel();
            return;
        }

        // Vanilla sign convention: self.push(-dx, 0, -dz), entity.push(dx, 0, dz), where
        // (dx, dz) points from self toward other. Our push points from onShip toward
        // offShip — i.e., the direction offShip should move.
        if (!offShip.isVehicle()) offShip.push(push.x, 0.0, push.z);
        if (!onShip.isVehicle()) onShip.push(-push.x, 0.0, -push.z);
        ci.cancel();
    }

    // Push delta to apply to offShip in world frame. Direction = the ship-local X/Z axis
    // whose projection onto the world-frame delta is largest, signed toward offShip.
    // Magnitude = 0.05/tick (vanilla). Returns null if entities are too far apart for
    // vanilla's d >= 0.01 threshold.
    @Unique
    private static Vector3d vs$obbFaceNormalPush(
        final Entity onShip, final Entity offShip, final Ship ship
    ) {
        final Matrix4dc shipToWorld = ship.getShipToWorld();
        // Bbox center, not entity position — for an asymmetric bbox (Shulker peeking
        // sideways) entity position is at one corner; centers keep direction selection
        // consistent with the SAT overlap test.
        final AABB onBbox = onShip.getBoundingBox();
        final AABB offBbox = offShip.getBoundingBox();
        final Vector3d onWorldCenter = shipToWorld.transformPosition(VS$CENTER.get().set(
            (onBbox.minX + onBbox.maxX) * 0.5,
            (onBbox.minY + onBbox.maxY) * 0.5,
            (onBbox.minZ + onBbox.maxZ) * 0.5));
        final double onWorldX = onWorldCenter.x;
        final double onWorldZ = onWorldCenter.z;
        final double offCx = (offBbox.minX + offBbox.maxX) * 0.5;
        final double offCz = (offBbox.minZ + offBbox.maxZ) * 0.5;
        final double dx = offCx - onWorldX;
        final double dz = offCz - onWorldZ;
        if (Math.max(Math.abs(dx), Math.abs(dz)) < 0.01) return null;

        // OBB X and Z axes in world frame (Y axis irrelevant — vanilla flattens).
        final Vector3d axisX = shipToWorld.transformDirection(VS$AXIS_X.get().set(1, 0, 0));
        axisX.y = 0.0;
        if (axisX.lengthSquared() < 1.0e-12) return null;
        axisX.normalize();
        final Vector3d axisZ = shipToWorld.transformDirection(VS$AXIS_Z.get().set(0, 0, 1));
        axisZ.y = 0.0;
        if (axisZ.lengthSquared() < 1.0e-12) return null;
        axisZ.normalize();

        final double projX = dx * axisX.x + dz * axisX.z;
        final double projZ = dx * axisZ.x + dz * axisZ.z;
        final Vector3d normal = VS$RESULT.get();
        if (Math.abs(projX) >= Math.abs(projZ)) {
            normal.set(axisX);
            if (projX < 0) normal.mul(-1.0);
        } else {
            normal.set(axisZ);
            if (projZ < 0) normal.mul(-1.0);
        }
        normal.mul(0.05);
        return normal;
    }

    // 2D OBB-vs-AABB overlap in X/Z plus a plain AABB Y check. On-ship side is treated
    // as an OBB (axis-aligned in shipyard, transformed by shipToWorld); off-ship side
    // is a plain world AABB. 4-axis SAT is sufficient in 2D — both rectangles' face
    // normals; cross-products collapse to the same set.
    @Unique
    private static boolean vs$obbAabbOverlap(
        final Entity onShip, final Entity offShip, final Ship ship
    ) {
        final AABB onBbox = onShip.getBoundingBox();
        final AABB offBbox = offShip.getBoundingBox();
        final Matrix4dc shipToWorld = ship.getShipToWorld();

        // Y check: encapsulating Y range of the on-ship bbox in world frame.
        final AABBd onWorldEnc = VS$AABB.get().setMin(onBbox.minX, onBbox.minY, onBbox.minZ)
            .setMax(onBbox.maxX, onBbox.maxY, onBbox.maxZ).transform(shipToWorld);
        if (offBbox.maxY < onWorldEnc.minY || offBbox.minY > onWorldEnc.maxY) return false;

        final Vector3d ax = shipToWorld.transformDirection(VS$AXIS_X.get().set(1, 0, 0));
        ax.y = 0.0;
        if (ax.lengthSquared() < 1.0e-12) return true;
        ax.normalize();
        final Vector3d az = shipToWorld.transformDirection(VS$AXIS_Z.get().set(0, 0, 1));
        az.y = 0.0;
        if (az.lengthSquared() < 1.0e-12) return true;
        az.normalize();

        final double ohx = (onBbox.maxX - onBbox.minX) * 0.5;
        final double ohz = (onBbox.maxZ - onBbox.minZ) * 0.5;
        final Vector3d oCtr = shipToWorld.transformPosition(VS$CENTER.get().set(
            (onBbox.minX + onBbox.maxX) * 0.5,
            (onBbox.minY + onBbox.maxY) * 0.5,
            (onBbox.minZ + onBbox.maxZ) * 0.5));

        final double ahx = (offBbox.maxX - offBbox.minX) * 0.5;
        final double ahz = (offBbox.maxZ - offBbox.minZ) * 0.5;
        final double aCx = (offBbox.minX + offBbox.maxX) * 0.5;
        final double aCz = (offBbox.minZ + offBbox.maxZ) * 0.5;

        final double tx = oCtr.x - aCx;
        final double tz = oCtr.z - aCz;

        if (Math.abs(tx) > ahx + ohx * Math.abs(ax.x) + ohz * Math.abs(az.x)) return false;
        if (Math.abs(tz) > ahz + ohx * Math.abs(ax.z) + ohz * Math.abs(az.z)) return false;
        final double tProjX = tx * ax.x + tz * ax.z;
        if (Math.abs(tProjX) > ohx + ahx * Math.abs(ax.x) + ahz * Math.abs(ax.z)) return false;
        final double tProjZ = tx * az.x + tz * az.z;
        return Math.abs(tProjZ) <= ohz + ahx * Math.abs(az.x) + ahz * Math.abs(az.z);
    }
}
