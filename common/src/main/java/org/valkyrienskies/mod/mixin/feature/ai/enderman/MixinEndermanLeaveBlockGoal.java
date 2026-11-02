package org.valkyrienskies.mod.mixin.feature.ai.enderman;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// EnderMan$EndermanLeaveBlockGoal.tick reads the random target cell + the ground cell
// below + writes via setBlock — all world-only, so an enderman on a ship never finds
// solid ground in the world frame and never puts its carried block down. Wrap the two
// reads to fall back to ships, capture the target's ship-local pos in a per-tick
// ThreadLocal (HEAD-inject reset, first-call-only capture so the ground read can't
// override), and redirect the paired setBlock to that pos. Mirror of MixinEndermanTakeBlockGoal.
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public abstract class MixinEndermanLeaveBlockGoal {

    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);
    @Unique
    private static final ThreadLocal<BlockPos> VS$SHIP_PLACEMENT_POS = new ThreadLocal<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void vs$resetShipLocalCapture(final CallbackInfo ci) {
        VS$SHIP_PLACEMENT_POS.remove();
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readBlockStateIncludingShips(
        final Level instance, final BlockPos pos, final Operation<BlockState> original
    ) {
        final BlockState worldState = original.call(instance, pos);
        if (!worldState.isAir()) return worldState;
        final double cx = pos.getX() + 0.5;
        final double cy = pos.getY() + 0.5;
        final double cz = pos.getZ() + 0.5;
        final Vector3d worldCenter = VS$CENTER.get().set(cx, cy, cz);
        final AABBd probe = VS$PROBE.get()
            .setMin(cx - 0.5, cy - 0.5, cz - 0.5)
            .setMax(cx + 0.5, cy + 0.5, cz + 0.5);
        final Vector3d shipLocal = VS$LOCAL.get();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(instance, probe)) {
            if (!ship.getWorldAABB().containsPoint(worldCenter)) continue;
            ship.getTransform().getWorldToShip().transformPosition(worldCenter, shipLocal);
            final BlockPos shipLocalPos = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            // First wrap call this tick is for the target cell; capture for the eventual
            // setBlock redirect. Subsequent calls (the ground read at pos.below()) don't
            // override.
            if (VS$SHIP_PLACEMENT_POS.get() == null) {
                VS$SHIP_PLACEMENT_POS.set(shipLocalPos);
            }
            final BlockState shipState = instance.getBlockState(shipLocalPos);
            if (!shipState.isAir()) return shipState;
        }
        return worldState;
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
        )
    )
    private boolean vs$setBlockAtShipPosIfApplicable(
        final Level instance, final BlockPos pos, final BlockState state, final int flags,
        final Operation<Boolean> original
    ) {
        final BlockPos shipPos = VS$SHIP_PLACEMENT_POS.get();
        VS$SHIP_PLACEMENT_POS.remove();
        return original.call(instance, shipPos != null ? shipPos : pos, state, flags);
    }
}
