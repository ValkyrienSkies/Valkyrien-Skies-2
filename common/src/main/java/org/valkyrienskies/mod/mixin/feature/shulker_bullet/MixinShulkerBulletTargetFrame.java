package org.valkyrienskies.mod.mixin.feature.shulker_bullet;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.WorldEntityHandler;

// ShulkerBullet's selectNextMoveDirection picks a cardinal in WORLD axes; for a target on a rotated ship those world cardinals are misaligned with the ship's grid and the bullet bounces through world cells next to the rendered ship instead of threading through ship-aligned corridors. Do the entire selection in the target's ship-local frame: pick a cardinal there (where isEmptyBlock reads resolve to actual ship blocks), then transform the velocity goal back to world via the ship's rotation. The bullet stays a world entity; only the velocity DIRECTION is in the target's frame.
@Mixin(ShulkerBullet.class)
public abstract class MixinShulkerBulletTargetFrame {

    @Shadow private Entity finalTarget;
    @Shadow private Direction currentMoveDirection;
    @Shadow private int flightSteps;
    @Shadow private double targetDeltaX;
    @Shadow private double targetDeltaY;
    @Shadow private double targetDeltaZ;

    @Shadow protected abstract void setMoveDirection(Direction direction);

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$TARGET_LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$BULLET_LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$AUX = ThreadLocal.withInitial(Vector3d::new);

    @Inject(method = "selectNextMoveDirection", at = @At("HEAD"), cancellable = true)
    private void vs$selectInTargetFrame(final Direction.Axis avoid, final CallbackInfo ci) {
        final ShulkerBullet self = (ShulkerBullet) (Object) this;
        final Level level = self.level();

        // Constructor calls selectNextMoveDirection BEFORE the first tick, so MixinProjectile.returnFromShipyard hasn't migrated us out of shipyard yet — without this eager migrate the first targetDelta is computed from a shipyard-coord bullet pos and produces garbage velocity until the next reselection.
        final Ship bulletShip = VSGameUtilsKt.getShipManagingPos(level, self.position());
        if (bulletShip != null) {
            WorldEntityHandler.INSTANCE.moveEntityFromShipyardToWorld(self, bulletShip);
        }

        final LoadedShip ship = vs$resolveTargetShip(this.finalTarget);
        if (ship == null) return;  // vanilla world-frame logic runs

        final Quaterniondc shipRot = ship.getTransform().getShipToWorldRotation();
        final Matrix4dc worldToShip = ship.getTransform().getWorldToShip();

        // finalTarget non-null: vs$resolveTargetShip returns null otherwise.
        final double yOffset = this.finalTarget.getBbHeight() * 0.5;
        final Vector3d targetLocal = worldToShip.transformPosition(
            VS$IN.get().set(this.finalTarget.getX(), this.finalTarget.getY() + yOffset, this.finalTarget.getZ()),
            VS$TARGET_LOCAL.get()
        );
        final BlockPos targetPos = BlockPos.containing(targetLocal.x, targetLocal.y, targetLocal.z);

        double targetCx = targetPos.getX() + 0.5;
        double targetCy = targetPos.getY() + yOffset;
        double targetCz = targetPos.getZ() + 0.5;

        final Vector3d bulletLocal = worldToShip.transformPosition(
            VS$IN.get().set(self.getX(), self.getY(), self.getZ()),
            VS$BULLET_LOCAL.get()
        );
        final Vec3 bulletLocalVec = new Vec3(bulletLocal.x, bulletLocal.y, bulletLocal.z);

        Direction picked = null;
        if (!targetPos.closerToCenterThan(bulletLocalVec, 2.0)) {
            final BlockPos bulletPos = BlockPos.containing(bulletLocal.x, bulletLocal.y, bulletLocal.z);
            final List<Direction> candidates = Lists.newArrayList();
            if (avoid != Direction.Axis.X) {
                if (bulletPos.getX() < targetPos.getX() && level.isEmptyBlock(bulletPos.east())) {
                    candidates.add(Direction.EAST);
                } else if (bulletPos.getX() > targetPos.getX() && level.isEmptyBlock(bulletPos.west())) {
                    candidates.add(Direction.WEST);
                }
            }
            if (avoid != Direction.Axis.Y) {
                if (bulletPos.getY() < targetPos.getY() && level.isEmptyBlock(bulletPos.above())) {
                    candidates.add(Direction.UP);
                } else if (bulletPos.getY() > targetPos.getY() && level.isEmptyBlock(bulletPos.below())) {
                    candidates.add(Direction.DOWN);
                }
            }
            if (avoid != Direction.Axis.Z) {
                if (bulletPos.getZ() < targetPos.getZ() && level.isEmptyBlock(bulletPos.south())) {
                    candidates.add(Direction.SOUTH);
                } else if (bulletPos.getZ() > targetPos.getZ() && level.isEmptyBlock(bulletPos.north())) {
                    candidates.add(Direction.NORTH);
                }
            }

            picked = Direction.getRandom(self.level().getRandom());
            if (candidates.isEmpty()) {
                int safety = 5;
                while (safety > 0 && !level.isEmptyBlock(bulletPos.relative(picked))) {
                    picked = Direction.getRandom(self.level().getRandom());
                    safety--;
                }
            } else {
                picked = candidates.get(self.level().getRandom().nextInt(candidates.size()));
            }

            // Cardinal velocity goal: bullet + direction.normal (unit), so scaled below to direction.normal × 0.15 (axis-aligned hop).
            targetCx = bulletLocal.x + picked.getStepX();
            targetCy = bulletLocal.y + picked.getStepY();
            targetCz = bulletLocal.z + picked.getStepZ();
        }

        this.setMoveDirection(picked);

        final double dxLocal = targetCx - bulletLocal.x;
        final double dyLocal = targetCy - bulletLocal.y;
        final double dzLocal = targetCz - bulletLocal.z;
        final double dist = Math.sqrt(dxLocal * dxLocal + dyLocal * dyLocal + dzLocal * dzLocal);
        if (dist == 0.0) {
            this.targetDeltaX = 0.0;
            this.targetDeltaY = 0.0;
            this.targetDeltaZ = 0.0;
        } else {
            // Direction-only transform (no translation) — targetDelta is a velocity vector.
            final Vector3d worldDelta = shipRot.transform(
                VS$IN.get().set(dxLocal / dist * 0.15, dyLocal / dist * 0.15, dzLocal / dist * 0.15),
                VS$AUX.get()
            );
            this.targetDeltaX = worldDelta.x;
            this.targetDeltaY = worldDelta.y;
            this.targetDeltaZ = worldDelta.z;
        }

        self.hasImpulse = true;
        this.flightSteps = 10 + self.level().getRandom().nextInt(5) * 2;
        ci.cancel();
    }

    @Unique
    private LoadedShip vs$resolveTargetShip(final Entity target) {
        final Ship ship = VSGameUtilsKt.getEnclosingShip(target);
        return ship instanceof LoadedShip loaded ? loaded : null;
    }

    // Project both bullet and target blockPosition() reads inside ShulkerBullet.tick into the target's ship frame, so .relative(currentMoveDirection) (where currentMoveDirection is a ship-local cardinal we set in selectNextMoveDirection) reads from the ship's chunks and the per-axis equality with targetPos compares coordinates in the same frame as the move plan. Two distinct CP entries: bullet's blockPosition resolves through ShulkerBullet.blockPosition; target's through Entity.blockPosition.
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ShulkerBullet;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$bulletBlockPosInTargetFrame(
        final ShulkerBullet self, final Operation<BlockPos> original
    ) {
        final BlockPos worldPos = original.call(self);
        final LoadedShip ship = vs$resolveTargetShip(this.finalTarget);
        if (ship == null) return worldPos;
        return vs$projectToShipLocal(ship, worldPos);
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$targetBlockPosInTargetFrame(
        final Entity target, final Operation<BlockPos> original
    ) {
        final BlockPos worldPos = original.call(target);
        final LoadedShip ship = vs$resolveTargetShip(this.finalTarget);
        if (ship == null) return worldPos;
        return vs$projectToShipLocal(ship, worldPos);
    }

    @Unique
    private static BlockPos vs$projectToShipLocal(final LoadedShip ship, final BlockPos worldPos) {
        final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
            VS$IN.get().set(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5),
            VS$AUX.get()
        );
        return BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
    }
}
