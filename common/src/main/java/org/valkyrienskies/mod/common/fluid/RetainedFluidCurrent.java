package org.valkyrienskies.mod.common.fluid;

import net.minecraft.world.phys.Vec3;

final class RetainedFluidCurrent {
    private RetainedFluidCurrent() { }

    static Vec3 push(final Vec3 movement, final Vec3 velocity, final double fraction, final double motionScale) {
        final double speed = velocity.length() / 20;
        if (!(speed > 0) || !Double.isFinite(speed) || !(motionScale > 0) || !Double.isFinite(motionScale)) return Vec3.ZERO;
        final Vec3 direction = velocity.scale(1 / (speed * 20));
        final double remaining = Math.max(0, speed - movement.dot(direction));
        final double impulse = Math.min(motionScale, Math.min(speed, remaining) * Math.min(1, motionScale * 20));
        return direction.scale(impulse * Math.max(0, Math.min(1, fraction)));
    }
}
