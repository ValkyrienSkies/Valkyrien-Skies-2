package org.valkyrienskies.mod.mixin.feature.ai.path_follow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.pathfinding.PathPerFrameRegistry;
import org.valkyrienskies.mod.common.pathfinding.PathfindingFrame;
import org.valkyrienskies.mod.mixin.accessors.world.level.pathfinder.PathAccessor;

/**
 * Re-projects ship-local path nodes through the ship's live shipToWorld so vanilla
 * path-follow getters return world coords. Out-of-claim nodes use the snapshot
 * transform so their world position stays fixed for the path's lifetime.
 */
@Mixin(Path.class)
public abstract class MixinPath {

    @Unique
    private static final ThreadLocal<Vector3d> VS$SCRATCH =
        ThreadLocal.withInitial(Vector3d::new);

    @Inject(method = "getNextEntityPos", at = @At("HEAD"), cancellable = true)
    private void vs$reprojectNextEntityPos(final Entity entity, final CallbackInfoReturnable<Vec3> cir) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return;
        final Path self = (Path) (Object) this;
        if (self.isDone()) return;
        final int nodeIndex = self.getNextNodeIndex();
        final Node node = self.getNode(nodeIndex);
        final PathfindingFrame frame = PathPerFrameRegistry.getFrameAtNodeIndex(self, nodeIndex);
        if (!(frame instanceof PathfindingFrame.InShip inShip)) return;

        try {
            cir.setReturnValue(vs$shipLocalNodeToWorld(inShip, node));
        } catch (final Throwable t) {
            // fall through to vanilla
        }
    }

    @Inject(method = "getEntityPosAtNode", at = @At("HEAD"), cancellable = true)
    private void vs$reprojectEntityPosAtNode(final Entity entity, final int index,
        final CallbackInfoReturnable<Vec3> cir) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return;
        final Path self = (Path) (Object) this;
        if (index < 0 || index >= self.getNodeCount()) return;
        final Node node = self.getNode(index);
        final PathfindingFrame frame = PathPerFrameRegistry.getFrameAtNodeIndex(self, index);
        if (!(frame instanceof PathfindingFrame.InShip inShip)) return;

        try {
            cir.setReturnValue(vs$shipLocalNodeToWorld(inShip, node));
        } catch (final Throwable t) {
            // fall through to vanilla
        }
    }

    @Inject(method = "getNextNodePos", at = @At("HEAD"), cancellable = true)
    private void vs$reprojectNextNodePos(final CallbackInfoReturnable<Vec3i> cir) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return;
        final Path self = (Path) (Object) this;
        if (self.isDone()) return;
        final int nodeIndex = self.getNextNodeIndex();
        final Node node = self.getNode(nodeIndex);
        final PathfindingFrame frame = PathPerFrameRegistry.getFrameAtNodeIndex(self, nodeIndex);
        if (!(frame instanceof PathfindingFrame.InShip inShip)) return;

        try {
            // Must be BlockPos, not bare Vec3i — downstream callers cast to BlockPos.
            cir.setReturnValue(vs$shipLocalNodeToWorldBlockPos(inShip, node));
        } catch (final Throwable t) {
            // fall through to vanilla
        }
    }

    @Inject(method = "getNodePos", at = @At("HEAD"), cancellable = true)
    private void vs$reprojectNodePos(final int index, final CallbackInfoReturnable<BlockPos> cir) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return;
        final Path self = (Path) (Object) this;
        if (index < 0 || index >= self.getNodeCount()) return;
        final Node node = self.getNode(index);
        final PathfindingFrame frame = PathPerFrameRegistry.getFrameAtNodeIndex(self, index);
        if (!(frame instanceof PathfindingFrame.InShip inShip)) return;

        try {
            cir.setReturnValue(vs$shipLocalNodeToWorldBlockPos(inShip, node));
        } catch (final Throwable t) {
            // fall through to vanilla
        }
    }

    @Inject(method = "getTarget", at = @At("HEAD"), cancellable = true)
    private void vs$reprojectTarget(final CallbackInfoReturnable<BlockPos> cir) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return;
        final Path self = (Path) (Object) this;
        final int n = self.getNodeCount();
        if (n == 0) return;
        final int lastIndex = n - 1;
        final PathfindingFrame frame = PathPerFrameRegistry.getFrameAtNodeIndex(self, lastIndex);
        if (!(frame instanceof PathfindingFrame.InShip inShip)) return;

        try {
            // Read raw field via accessor — calling getTarget() would re-enter this mixin.
            final BlockPos shipLocal = ((PathAccessor) (Object) this).vs$getRawTarget();
            if (shipLocal == null) return;
            final Matrix4dc shipToWorld = vs$projectionForBlockPos(inShip, shipLocal);
            final Vector3d scratch = VS$SCRATCH.get().set(
                shipLocal.getX() + 0.5, shipLocal.getY() + 0.5, shipLocal.getZ() + 0.5
            );
            shipToWorld.transformPosition(scratch);
            cir.setReturnValue(BlockPos.containing(scratch.x, scratch.y, scratch.z));
        } catch (final Throwable t) {
            // fall through to vanilla
        }
    }

    @Unique
    private static Matrix4dc vs$projectionForBlockPos(final PathfindingFrame.InShip inShip, final BlockPos pos) {
        if (inShip.getShip().getChunkClaim().contains(pos.getX() >> 4, pos.getZ() >> 4)) {
            return inShip.getShip().getTransform().getShipToWorld();
        }
        return inShip.getShipToWorldSnapshot();
    }

    @Unique
    private static Matrix4dc vs$projectionFor(final PathfindingFrame.InShip inShip, final Node node) {
        if (inShip.getShip().getChunkClaim().contains(node.x >> 4, node.z >> 4)) {
            return inShip.getShip().getTransform().getShipToWorld();
        }
        return inShip.getShipToWorldSnapshot();
    }

    private static BlockPos vs$shipLocalNodeToWorldBlockPos(final PathfindingFrame.InShip inShip, final Node node) {
        final Matrix4dc shipToWorld = vs$projectionFor(inShip, node);
        final Vector3d scratch = VS$SCRATCH.get().set(node.x + 0.5, node.y, node.z + 0.5);
        shipToWorld.transformPosition(scratch);
        return BlockPos.containing(scratch.x, scratch.y, scratch.z);
    }

    private static Vec3 vs$shipLocalNodeToWorld(final PathfindingFrame.InShip inShip, final Node node) {
        final Matrix4dc shipToWorld = vs$projectionFor(inShip, node);
        final Vector3d scratch = VS$SCRATCH.get().set(node.x + 0.5, node.y, node.z + 0.5);
        shipToWorld.transformPosition(scratch);
        return new Vec3(scratch.x, scratch.y, scratch.z);
    }
}
