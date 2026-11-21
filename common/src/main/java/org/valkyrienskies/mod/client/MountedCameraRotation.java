package org.valkyrienskies.mod.client;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

public final class MountedCameraRotation {
    private Object mount;
    private long shipId;
    private final Quaterniond shipRotation = new Quaterniond();
    private final Quaterniond rotation = new Quaterniond();
    private double localYaw;
    private double localPitch;
    private float worldYaw;
    private float worldPitch;

    public void update(final Object mount, final long shipId, final Quaterniondc shipRotation,
        final float yaw, final float pitch) {
        if (this.mount != mount || this.shipId != shipId) {
            this.mount = mount;
            this.shipId = shipId;
            this.worldYaw = yaw;
            this.localYaw = 0.0;
            this.setLocalAngles(shipRotation, yaw, pitch);
        } else if (Math.abs(wrapDegrees(yaw - this.worldYaw)) > 1.0E-4
            || Math.abs(pitch - this.worldPitch) > 1.0E-4) {
            this.setLocalAngles(this.shipRotation, yaw, pitch);
        }
        this.shipRotation.set(shipRotation);
        this.updateWorldAngles();
    }

    public void turn(final double yaw, final double pitch) {
        this.localYaw = wrapDegrees(this.localYaw + (float) yaw * 0.15F);
        this.localPitch = Math.max(-90.0, Math.min(90.0, this.localPitch + (float) pitch * 0.15F));
        this.updateWorldAngles();
    }

    public void clear() {
        this.mount = null;
    }

    public boolean isMounted() {
        return this.mount != null;
    }

    public float getWorldYaw() {
        return this.worldYaw;
    }

    public float getWorldPitch() {
        return this.worldPitch;
    }

    public Quaterniondc getRotation() {
        return this.rotation;
    }

    private void setLocalAngles(final Quaterniondc shipRotation, final float yaw, final float pitch) {
        final Vector3d forward = new Quaterniond().rotationYXZ(Math.toRadians(-yaw), Math.toRadians(pitch), 0.0)
            .transform(new Vector3d(0.0, 0.0, 1.0));
        shipRotation.transformInverse(forward);
        final double horizontal = Math.hypot(forward.x, forward.z);
        if (horizontal > 1.0E-6) {
            this.localYaw = Math.toDegrees(Math.atan2(-forward.x, forward.z));
        }
        this.localPitch = Math.toDegrees(Math.atan2(-forward.y, horizontal));
    }

    private void updateWorldAngles() {
        this.rotation.rotationYXZ(Math.toRadians(-this.localYaw), Math.toRadians(this.localPitch), 0.0)
            .premul(this.shipRotation);
        final Vector3d forward = this.rotation.transform(new Vector3d(0.0, 0.0, 1.0));
        final double horizontal = Math.hypot(forward.x, forward.z);
        if (horizontal > 1.0E-6) {
            final double yaw = Math.toDegrees(Math.atan2(-forward.x, forward.z));
            this.worldYaw += (float) wrapDegrees(yaw - this.worldYaw);
        }
        this.worldPitch = (float) Math.toDegrees(Math.atan2(-forward.y, horizontal));
    }

    private static double wrapDegrees(final double angle) {
        return angle - Math.floor((angle + 180.0) / 360.0) * 360.0;
    }
}
