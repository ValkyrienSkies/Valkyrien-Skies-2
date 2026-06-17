package org.valkyrienskies.mod.mixin.feature.ai.goat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.LongJumpToRandomPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;

// LongJumpToRandomPos (goat long-jump): vanilla world-only candidate enumeration misses ship-mounted targets, the chosen target needs world projection for the jump-vector calc, the canStillUse position-equals check fails under EntityDragger drift, and the trajectory clearance check needs ship-block awareness.
@Mixin(LongJumpToRandomPos.class)
public abstract class MixinLongJumpToRandomPos {

    @Shadow
    @Final
    protected int maxLongJumpWidth;

    @Shadow
    @Final
    protected int maxLongJumpHeight;

    @Shadow
    protected List<LongJumpToRandomPos.PossibleJump> jumpCandidates;

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @Inject(
        method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)V",
        at = @At("TAIL")
    )
    private void vs$appendShipFrameCandidates(
        final ServerLevel level, final Mob mob, final long time, final CallbackInfo ci
    ) {
        final double mx = mob.getX(), my = mob.getY(), mz = mob.getZ();
        final int W = maxLongJumpWidth, H = maxLongJumpHeight;
        final AABBd probe = new AABBd(
            mx - W - 1, my - H - 1, mz - W - 1,
            mx + W + 1, my + H + 1, mz + W + 1
        );
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            final Vector3d shipLocalSelf = ship.getTransform().getWorldToShip().transformPosition(
                new Vector3d(mx, my, mz), new Vector3d()
            );
            final BlockPos seed = BlockPos.containing(shipLocalSelf.x, shipLocalSelf.y, shipLocalSelf.z);
            final int sx = seed.getX(), sy = seed.getY(), sz = seed.getZ();
            BlockPos.betweenClosedStream(sx - W, sy - H, sz - W, sx + W, sy + H, sz + W)
                .forEach(p -> {
                    final double distSqr = seed.distSqr(p);
                    // Vanilla's MIN_PATHFIND_DISTANCE_TO_VALID_JUMP = 4 → squared = 16.
                    if (distSqr < 16.0) return;
                    jumpCandidates.add(new LongJumpToRandomPos.PossibleJump(
                        p.immutable(), Mth.ceil(distSqr)
                    ));
                });
        }
    }

    @WrapOperation(
        method = "pickCandidate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 vs$projectShipyardJumpTargetToWorld(
        final Vec3i pos, final Operation<Vec3> original,
        @Local(argsOnly = true) final ServerLevel level
    ) {
        if (!(pos instanceof BlockPos blockPos)) return original.call(pos);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, blockPos);
        if (ship == null) return original.call(pos);
        final Vector3d worldVec = ship.getTransform().getShipToWorld().transformPosition(
            VS$IN.get().set(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5),
            VS$OUT.get()
        );
        return new Vec3(worldVec.x, worldVec.y, worldVec.z);
    }

    @WrapOperation(
        method = "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;equals(Ljava/lang/Object;)Z"
        )
    )
    private boolean vs$bypassPositionCheckOnShip(
        final Vec3 initial, final Object current, final Operation<Boolean> original,
        @Local(argsOnly = true) final Mob mob
    ) {
        if (original.call(initial, current)) return true;
        // Ship-mounted goats see EntityDragger shift their world pos every tick (ship motion + FP drift), so vanilla's exact-equals never holds during prepare. Bypass ONLY in the prepare phase: once LONG_JUMP_MID_JUMP is set, vanilla's false return is what hands control to MID_JUMP — keeping the bypass past that point re-fires setDeltaMovement every tick and the goat accelerates upward into the sky.
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.LONG_JUMP_MID_JUMP)) return false;
        return VSGameUtilsKt.getEnclosingShip(mob) != null;
    }

    // isClearTransition steps the mob's bbox along the jump arc; vanilla world-only would accept arcs that pass through ship hulls.
    @WrapOperation(
        method = "isClearTransition",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"
        )
    )
    private boolean vs$noCollisionIncludingShipsForTrajectory(
        final Level level, final Entity entity, final AABB aabb,
        final Operation<Boolean> original
    ) {
        return ShipAwareCollisionUtil.noCollisionIncludingShips(level, entity, aabb);
    }
}
