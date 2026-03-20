package org.valkyrienskies.mod.mixin.feature.air_pockets.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(AbstractCauldronBlock.class)
public abstract class MixinAbstractCauldronBlock {

    @Shadow
    protected abstract double getContentHeight(BlockState state);

    @Inject(method = "isEntityInsideContent", at = @At("HEAD"), cancellable = true)
    private void valkyrienair$shipyardCauldronContentCheck(final BlockState state, final BlockPos pos,
        final Entity entity, final CallbackInfoReturnable<Boolean> cir) {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return;

        final Level level = entity.level();
        if (level.isClientSide) return;
        if (!VSGameUtilsKt.isBlockInShipyard(level, pos)) return;

        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) return;

        final ShipTransform shipTransform = ship.getTransform();
        final Matrix4dc worldToShip = shipTransform.getWorldToShip();

        final double minX = entity.getBoundingBox().minX;
        final double minY = entity.getBoundingBox().minY;
        final double minZ = entity.getBoundingBox().minZ;
        final double maxX = entity.getBoundingBox().maxX;
        final double maxY = entity.getBoundingBox().maxY;
        final double maxZ = entity.getBoundingBox().maxZ;

        // Compute the entity's ship-space AABB bounds by transforming the 8 world-space AABB corners.
        double shipMinX = Double.POSITIVE_INFINITY;
        double shipMinY = Double.POSITIVE_INFINITY;
        double shipMinZ = Double.POSITIVE_INFINITY;
        double shipMaxX = Double.NEGATIVE_INFINITY;
        double shipMaxY = Double.NEGATIVE_INFINITY;
        double shipMaxZ = Double.NEGATIVE_INFINITY;

        for (int xi = 0; xi < 2; xi++) {
            final double wx = xi == 0 ? minX : maxX;
            for (int yi = 0; yi < 2; yi++) {
                final double wy = yi == 0 ? minY : maxY;
                for (int zi = 0; zi < 2; zi++) {
                    final double wz = zi == 0 ? minZ : maxZ;

                    final double sx = worldToShip.m00() * wx + worldToShip.m10() * wy + worldToShip.m20() * wz + worldToShip.m30();
                    final double sy = worldToShip.m01() * wx + worldToShip.m11() * wy + worldToShip.m21() * wz + worldToShip.m31();
                    final double sz = worldToShip.m02() * wx + worldToShip.m12() * wy + worldToShip.m22() * wz + worldToShip.m32();

                    if (sx < shipMinX) shipMinX = sx;
                    if (sy < shipMinY) shipMinY = sy;
                    if (sz < shipMinZ) shipMinZ = sz;
                    if (sx > shipMaxX) shipMaxX = sx;
                    if (sy > shipMaxY) shipMaxY = sy;
                    if (sz > shipMaxZ) shipMaxZ = sz;
                }
            }
        }

        // Entity#checkInsideBlocks can invoke block callbacks for nearby ship blocks due to ship rotation / rounding.
        // Make sure the entity actually overlaps this cauldron's block volume in ship-space before applying the
        // vanilla Y-only "inside content" test.
        final double eps = 1.0e-7;
        final int blockX = pos.getX();
        final int blockZ = pos.getZ();
        final double wx = entity.getX();
        final double wy = entity.getY();
        final double wz = entity.getZ();
        final double shipEntityX = worldToShip.m00() * wx + worldToShip.m10() * wy + worldToShip.m20() * wz + worldToShip.m30();
        final double shipEntityY = worldToShip.m01() * wx + worldToShip.m11() * wy + worldToShip.m21() * wz + worldToShip.m31();
        final double shipEntityZ = worldToShip.m02() * wx + worldToShip.m12() * wy + worldToShip.m22() * wz + worldToShip.m32();

        // Use entity position (not ship-space AABB) to avoid false positives from rotated AABB expansion.
        if (shipEntityX <= blockX + eps || shipEntityX >= blockX + 1.0 - eps || shipEntityZ <= blockZ + eps ||
            shipEntityZ >= blockZ + 1.0 - eps) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        final double contentTopY = pos.getY() + this.getContentHeight(state);
        final boolean inside = shipEntityY < contentTopY && shipMaxY > pos.getY() + 0.25;
        cir.setReturnValue(inside);
        cir.cancel();
    }
}
