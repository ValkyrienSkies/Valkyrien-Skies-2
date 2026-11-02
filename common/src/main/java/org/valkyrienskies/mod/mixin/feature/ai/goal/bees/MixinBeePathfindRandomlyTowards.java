package org.valkyrienskies.mod.mixin.feature.ai.goal.bees;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Bee.pathfindRandomlyTowards uses Vec3.atBottomCenterOf(target) and
// blockPosition().distManhattan(target) to steer; for ship-mounted hives/flowers the
// shipyard target leaves the steered direction pointed ~10⁷ blocks away and the bee
// never moves. Project both reads through the owning ship's shipToWorld.
@Mixin(Bee.class)
public abstract class MixinBeePathfindRandomlyTowards {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        method = "pathfindRandomlyTowards",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 vs$atBottomCenterOfProjected(final Vec3i pos, final Operation<Vec3> original) {
        if (pos instanceof BlockPos bp) {
            final Ship ship = vs$shipFor(bp);
            if (ship != null) {
                // Project the cell's bottom-face center directly so a rotated ship's hive
                // entrance lands on its actual world point rather than corner-then-floor.
                final Vector3d world = ship.getTransform().getShipToWorld().transformPosition(
                    VS$IN.get().set(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5),
                    VS$OUT.get()
                );
                return new Vec3(world.x, world.y, world.z);
            }
        }
        return original.call(pos);
    }

    @WrapOperation(
        method = "pathfindRandomlyTowards",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distManhattan(Lnet/minecraft/core/Vec3i;)I"
        )
    )
    private int vs$distManhattanProjected(
        final BlockPos receiver, final Vec3i other, final Operation<Integer> original
    ) {
        // receiver is the bee's own blockPosition (world); other is the target (shipyard
        // for ship targets) — project so the distance is in world space.
        if (other instanceof BlockPos bp) {
            final Ship ship = vs$shipFor(bp);
            if (ship != null) {
                final Vector3d world = ship.getTransform().getShipToWorld().transformPosition(
                    VS$IN.get().set(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5),
                    VS$OUT.get()
                );
                return original.call(receiver, BlockPos.containing(world.x, world.y, world.z));
            }
        }
        return original.call(receiver, other);
    }

    @Unique
    private Ship vs$shipFor(final BlockPos bp) {
        final Level level = ((Bee) (Object) this).level();
        return level == null ? null : VSGameUtilsKt.getShipManagingPos(level, bp);
    }
}
