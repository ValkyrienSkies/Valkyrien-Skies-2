package org.valkyrienskies.mod.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
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
            entity.zOld
        );
    }

    public static void setWithoutSectionUpdate(final Entity entity, final double x, final double y, final double z) {
        final EntityAccessor accessor = (EntityAccessor) entity;
        accessor.setPosNoUpdates(new Vec3(x, y, z));
        accessor.setBlockPosition(BlockPos.containing(x, y, z));
        accessor.setFeetBlockState(null);
        entity.setBoundingBox(entity.getDimensions(entity.getPose()).makeBoundingBox(x, y, z));
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
        double zOld
    ) {
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
        }
    }
}
