package org.valkyrienskies.mod.mixin.feature.ai.path_follow;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.pathfinding.PathPerFrameRegistry;
import org.valkyrienskies.mod.common.pathfinding.PathfindingFrame;

/**
 * Re-runs {@link PathNavigation#followThePath()}'s waypoint-advance check in ship-local coords for
 * InShip-frame paths so the axis-aligned distance box lines up with the ship's grid regardless of
 * ship rotation. Stuck detection stays in world coords — ship drag isn't path progress.
 */
@Mixin(PathNavigation.class)
public abstract class MixinPathNavigationFollow {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @Shadow protected Mob mob;
    @Shadow protected Path path;
    @Shadow protected float maxDistanceToWaypoint;

    @Shadow protected abstract Vec3 getTempMobPos();
    @Shadow protected abstract void doStuckDetection(Vec3 vec3);
    @Shadow protected abstract boolean shouldTargetNextNodeInDirection(Vec3 vec3);
    @Shadow protected abstract boolean canMoveDirectly(Vec3 fromWorld, Vec3 toWorld);
    @Shadow public abstract boolean canCutCorner(net.minecraft.world.level.pathfinder.BlockPathTypes type);

    @Inject(method = "followThePath", at = @At("HEAD"), cancellable = true)
    private void vs$followInShipFrame(final CallbackInfo ci) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return;
        final Path currentPath = this.path;
        if (currentPath == null || currentPath.isDone()) return;
        final int nextIndex = currentPath.getNextNodeIndex();
        if (nextIndex >= currentPath.getNodeCount()) return;

        final Node nextNode = currentPath.getNode(nextIndex);
        final PathfindingFrame frame = PathPerFrameRegistry.getFrameAtNodeIndex(currentPath, nextIndex);
        if (!(frame instanceof PathfindingFrame.InShip inShip)) return;

        try {
            final Vec3 worldVec = this.getTempMobPos();

            this.maxDistanceToWaypoint = this.mob.getBbWidth() > 0.75F
                ? this.mob.getBbWidth() / 2.0F
                : 0.75F - this.mob.getBbWidth() / 2.0F;

            final Matrix4dc worldToShip = inShip.getShip().getTransform().getWorldToShip();
            final Vector3d shipLocalMob = worldToShip.transformPosition(
                VS$IN.get().set(worldVec.x, worldVec.y, worldVec.z), VS$OUT.get()
            );

            final double dx = Math.abs(shipLocalMob.x - ((double) nextNode.x + 0.5));
            final double dy = Math.abs(shipLocalMob.y - (double) nextNode.y);
            final double dz = Math.abs(shipLocalMob.z - ((double) nextNode.z + 0.5));
            final boolean withinDist =
                dx < (double) this.maxDistanceToWaypoint
                && dz < (double) this.maxDistanceToWaypoint
                && dy < 1.0;

            if (withinDist
                || (this.canCutCorner(currentPath.getNextNode().type)
                && vs$shouldTargetNextNodeInShipFrame(worldVec, shipLocalMob, inShip, currentPath))) {
                currentPath.advance();
            }

            this.doStuckDetection(worldVec);
            ci.cancel();
        } catch (final Throwable t) {
            // Leave ci uncanceled so vanilla followThePath runs as a fallback.
        }
    }

    // Ship-local form of shouldTargetNextNodeInDirection. canMoveDirectly stays in world coords — the VS2 collision overlay already handles ship blocks at world positions.
    @Unique
    private boolean vs$shouldTargetNextNodeInShipFrame(
        final Vec3 mobWorldVec,
        final Vector3d mobShipLocal,
        final PathfindingFrame.InShip inShip,
        final Path currentPath
    ) {
        final int nextIdx = currentPath.getNextNodeIndex();
        if (nextIdx + 1 >= currentPath.getNodeCount()) return false;

        final Node nextNode = currentPath.getNode(nextIdx);
        final double nextCx = nextNode.x + 0.5;
        final double nextCy = nextNode.y;
        final double nextCz = nextNode.z + 0.5;

        final double dx0 = mobShipLocal.x - nextCx;
        final double dy0 = mobShipLocal.y - nextCy;
        final double dz0 = mobShipLocal.z - nextCz;
        if (dx0 * dx0 + dy0 * dy0 + dz0 * dz0 >= 4.0) return false;

        if (this.canMoveDirectly(mobWorldVec, currentPath.getNextEntityPos(this.mob))) return true;

        final Node afterNode = currentPath.getNode(nextIdx + 1);
        final double afterCx = afterNode.x + 0.5;
        final double afterCy = afterNode.y;
        final double afterCz = afterNode.z + 0.5;

        final double v34x = nextCx - mobShipLocal.x;
        final double v34y = nextCy - mobShipLocal.y;
        final double v34z = nextCz - mobShipLocal.z;
        final double l4 = v34x * v34x + v34y * v34y + v34z * v34z;

        final double v35x = afterCx - mobShipLocal.x;
        final double v35y = afterCy - mobShipLocal.y;
        final double v35z = afterCz - mobShipLocal.z;
        final double l5 = v35x * v35x + v35y * v35y + v35z * v35z;

        final boolean shorterToAfter = l5 < l4;
        final boolean veryCloseToNext = l4 < 0.5;
        if (!shorterToAfter && !veryCloseToNext) return false;

        if (l4 == 0.0 || l5 == 0.0) return false;
        final double dotRaw = v34x * v35x + v34y * v35y + v34z * v35z;
        return dotRaw < 0.0;
    }
}
