package org.valkyrienskies.mod.mixin.feature.ai.goat;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.PrepareRamNearestTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Two coordinated goat-ramming fixes: (1) walk the back-up search in the deck's local cardinals (vanilla world-cardinal walk goes diagonal on a rotated ship). (2) project the shipyard target to world only at the getEdgeOfBlock call site so RAM_TARGET ends up world-frame for the ramDirection calc — without re-projecting the per-tick "did target move?" equality check, which would oscillate from FP drift and reset the prepare-pose timer.
@Mixin(PrepareRamNearestTarget.class)
public abstract class MixinPrepareRamNearestTarget {

    @Shadow @Final private int maxRamDistance;
    @Shadow @Final private int minRamDistance;

    @Inject(method = "calculateRammingStartPosition", at = @At("HEAD"), cancellable = true)
    private void vs$shipFrameRamSearch(
        final PathfinderMob mob, final LivingEntity target,
        final CallbackInfoReturnable<Optional<BlockPos>> cir
    ) {
        final Level level = mob.level();
        if (level == null) return;

        final Long goatShipId = vs$shipIdOf(mob);
        if (goatShipId == null) return;
        final Long targetShipId = vs$shipIdOf(target);
        if (targetShipId == null || !goatShipId.equals(targetShipId)) return;

        final Ship ship = VSGameUtilsKt.getAllShips(level).getById(goatShipId);
        if (ship == null) return;

        // Shipyard-frame target uses raw blockPos directly; world-frame target projects via worldToShip.
        final BlockPos rawTargetBlockPos = target.blockPosition();
        final BlockPos shipyardTargetPos;
        if (ship.getChunkClaim().contains(rawTargetBlockPos.getX() >> 4, rawTargetBlockPos.getZ() >> 4)) {
            shipyardTargetPos = rawTargetBlockPos;
        } else {
            final Vector3d shipyard = ship.getTransform().getWorldToShip().transformPosition(
                new Vector3d(target.getX(), target.getY(), target.getZ()), new Vector3d()
            );
            shipyardTargetPos = BlockPos.containing(shipyard.x, shipyard.y, shipyard.z);
        }
        if (!vs$isWalkableBlock(mob, shipyardTargetPos)) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        final Vector3d goatShipyard = ship.getTransform().getWorldToShip().transformPosition(
            new Vector3d(mob.getX(), mob.getY(), mob.getZ()), new Vector3d()
        );
        final BlockPos shipyardGoatPos = BlockPos.containing(
            goatShipyard.x, goatShipyard.y, goatShipyard.z
        );

        // Direction.Plane.HORIZONTAL applied to shipyard coords gives the ship's LOCAL cardinals.
        final List<BlockPos> candidates = Lists.newArrayList();
        final BlockPos.MutableBlockPos cursor = shipyardTargetPos.mutable();
        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            cursor.set(shipyardTargetPos);
            for (int step = 0; step < maxRamDistance; step++) {
                if (!vs$isWalkableBlock(mob, cursor.move(direction))) {
                    cursor.move(direction.getOpposite());
                    break;
                }
            }
            if (cursor.distManhattan(shipyardTargetPos) >= minRamDistance) {
                candidates.add(cursor.immutable());
            }
        }

        final PathNavigation nav = mob.getNavigation();
        final Optional<BlockPos> chosenShipyard = candidates.stream()
            .sorted(Comparator.comparingDouble(p -> shipyardGoatPos.distSqr(p)))
            .filter(p -> {
                final Path path = nav.createPath(p, 0);
                return path != null && path.canReach();
            })
            .findFirst();

        if (chosenShipyard.isEmpty()) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        // Back-project to world for RamCandidate.startPosition; the goat then walks to it via ship-aware nav and the equality check goat.blockPosition().equals(start) succeeds.
        final BlockPos shipyardStart = chosenShipyard.get();
        final Vector3d worldStart = ship.getTransform().getShipToWorld().transformPosition(
            new Vector3d(shipyardStart.getX() + 0.5, shipyardStart.getY(), shipyardStart.getZ() + 0.5),
            new Vector3d()
        );
        cir.setReturnValue(Optional.of(BlockPos.containing(worldStart.x, worldStart.y, worldStart.z)));
    }

    // Project candidate.targetPosition to world ONLY at the getEdgeOfBlock call inside tick() so RAM_TARGET is world-frame for the ramDirection calc. Do NOT wrap blockPosition() reads — those feed vanilla's "did target move?" equality check, and a per-tick worldToShip→containing round-trip has FP drift that flips the result between adjacent cells, oscillating the equality and re-running chooseRamPosition every tick so the prepare-pose timer never reaches ramPrepareTime.
    @WrapOperation(
        method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/PathfinderMob;J)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/behavior/PrepareRamNearestTarget;getEdgeOfBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 vs$projectShipyardTargetForGetEdgeOfBlock(
        final PrepareRamNearestTarget self, final BlockPos goatBlockPos, final BlockPos rawTargetBlockPos,
        final Operation<Vec3> original,
        @Local(argsOnly = true) final ServerLevel level
    ) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, rawTargetBlockPos);
        if (ship == null) return original.call(self, goatBlockPos, rawTargetBlockPos);
        final Vector3d worldVec = ship.getTransform().getShipToWorld().transformPosition(
            new Vector3d(rawTargetBlockPos.getX() + 0.5, rawTargetBlockPos.getY(), rawTargetBlockPos.getZ() + 0.5),
            new Vector3d()
        );
        return original.call(self, goatBlockPos, BlockPos.containing(worldVec.x, worldVec.y, worldVec.z));
    }

    // Vanilla-equivalent walkability check (vanilla's isWalkableBlock is private).
    @Unique
    private static boolean vs$isWalkableBlock(final PathfinderMob mob, final BlockPos pos) {
        return mob.getNavigation().isStableDestination(pos)
            && mob.getPathfindingMalus(WalkNodeEvaluator.getBlockPathTypeStatic(mob.level(), pos.mutable())) == 0.0F;
    }

    // Resolve the entity's ship: chunk-claim (shipyard-frame) takes precedence over dragger (world-frame).
    @Unique
    private static Long vs$shipIdOf(final Entity entity) {
        final Ship byClaim = VSGameUtilsKt.getShipManaging(entity);
        if (byClaim != null) return byClaim.getId();
        final Ship byDragger = VSGameUtilsKt.getEnclosingShip(entity);
        return byDragger != null ? byDragger.getId() : null;
    }
}
