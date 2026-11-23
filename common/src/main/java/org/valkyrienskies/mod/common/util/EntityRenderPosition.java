package org.valkyrienskies.mod.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.mod.mixin.accessors.entity.EntityAccessor;

public final class EntityRenderPosition {

    private EntityRenderPosition() {
    }

    public static Snapshot capture(final Entity entity) {
        return new Snapshot(
            entity.position(),
            ((EntityAccessor) entity).getBlockPosition(),
            entity.getBoundingBox(),
            entity.xo,
            entity.yo,
            entity.zo,
            entity.xOld,
            entity.yOld,
            entity.zOld,
            entity.getXRot(), entity.xRotO, entity.getYRot(), entity.yRotO,
            entity instanceof LivingEntity living ? living.yHeadRot : 0,
            entity instanceof LivingEntity living ? living.yHeadRotO : 0,
            entity instanceof LivingEntity living ? living.yBodyRot : 0,
            entity instanceof LivingEntity living ? living.yBodyRotO : 0
        );
    }

    public static void setWithoutSectionUpdate(final Entity entity, final double x, final double y, final double z) {
        final EntityAccessor accessor = (EntityAccessor) entity;
        AABB box = entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
        accessor.setPosNoUpdates(new Vec3(x, y, z));
        accessor.setBlockPosition(BlockPos.containing(x, y, z));
        accessor.setFeetBlockState(null);
        entity.setBoundingBox(box);
    }

    public static AABB seatedBounds(AABB local, org.joml.Matrix4dc basis, org.joml.Vector3dc origin) {
        org.joml.primitives.AABBd box = VectorConversionsMCKt.toJOML(local);
        // Do not apply the shipyard translation to a *directional* offset about the seat.
        box.transform(new org.joml.Matrix4d(basis).setTranslation(origin));
        return VectorConversionsMCKt.toMinecraft(box);
    }

    /** Preserve the entity's own distance rule. Measure from the nearest point of the visual
     * extent and compensate for scaling, since vanilla derives its limit from the native box. */
    public static double seatedCullDistance(AABB local, org.joml.Matrix4dc basis, org.joml.Vector3dc seat,
        Vec3 camera, double nativeSize) {
        return seatedCullDistance(seatedBounds(local, basis, seat), camera, nativeSize);
    }

    public static double seatedCullDistance(AABB visual, Vec3 camera, double nativeSize) {
        double ratio = nativeSize > 0 ? Math.max(1, visual.getSize() / nativeSize) : 1;
        return visual.distanceToSqr(camera) / (ratio * ratio);
    }

    public record Snapshot(
        Vec3 position,
        BlockPos blockPosition,
        AABB boundingBox,
        double xo,
        double yo,
        double zo,
        double xOld,
        double yOld,
        double zOld,
        float pitch, float oldPitch, float yaw, float oldYaw, float head, float oldHead, float body, float oldBody
    ) {
        public void applyYaw(final Entity entity, float correction) {
            entity.setYRot(yaw + correction);
            entity.yRotO = oldYaw + correction;
            if (entity instanceof LivingEntity living) {
                living.yHeadRot = head + correction;
                living.yHeadRotO = oldHead + correction;
                living.yBodyRot = body + correction;
                living.yBodyRotO = oldBody + correction;
            }
        }

        public void restore(final Entity entity) {
            final EntityAccessor accessor = (EntityAccessor) entity;
            accessor.setPosNoUpdates(position);
            accessor.setBlockPosition(blockPosition);
            accessor.setFeetBlockState(null);
            entity.setBoundingBox(boundingBox);
            entity.xo = xo;
            entity.yo = yo;
            entity.zo = zo;
            entity.xOld = xOld;
            entity.yOld = yOld;
            entity.zOld = zOld;
            applyYaw(entity, 0);
            entity.setXRot(pitch);
            entity.xRotO = oldPitch;
        }
    }
}
