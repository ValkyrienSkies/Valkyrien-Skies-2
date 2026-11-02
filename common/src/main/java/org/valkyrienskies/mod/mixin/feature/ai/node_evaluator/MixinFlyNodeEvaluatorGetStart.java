package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.pathfinding.PathfindingFrame;

// Anchor the start node in ship-local coords so the search shares a grid with the rest of the InShip pathfind.
@Mixin(FlyNodeEvaluator.class)
public abstract class MixinFlyNodeEvaluatorGetStart extends WalkNodeEvaluator {

    @Invoker("iteratePathfindingStartNodeCandidatePositions")
    abstract Iterable<BlockPos> vs$invokeIterateStartCandidates(Mob mob);

    @Inject(method = "getStart", at = @At("HEAD"), cancellable = true)
    private void vs$shipLocalStart(final CallbackInfoReturnable<Node> cir) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return;
        if (this.mob == null) return;
        final PathfindingFrame frame = PathfindingFrame.current(this.mob);
        if (!(frame instanceof PathfindingFrame.InShip inShip)) return;

        try {
            final Ship ship = inShip.getShip();
            final Vector3d local = ship.getTransform().getWorldToShip().transformPosition(
                new Vector3d(this.mob.getX(), this.mob.getY(), this.mob.getZ()), new Vector3d()
            );
            final int sx = Mth.floor(local.x);
            final int sz = Mth.floor(local.z);
            final int sy;
            if (this.canFloat() && this.mob.isInWater()) {
                int y = Mth.floor(local.y);
                final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(sx, y, sz);
                while (this.level.getBlockState(probe).is(Blocks.WATER)) {
                    probe.set(sx, ++y, sz);
                }
                sy = y;
            } else {
                sy = Mth.floor(local.y + 0.5);
            }

            final BlockPos start = new BlockPos(sx, sy, sz);
            if (!canStartAt(start)) {
                for (final BlockPos candidate : vs$invokeIterateStartCandidates(this.mob)) {
                    if (canStartAt(candidate)) {
                        cir.setReturnValue(getStartNode(candidate));
                        return;
                    }
                }
            }
            cir.setReturnValue(getStartNode(start));
        } catch (final Throwable t) {
            // Silent fallback to vanilla.
        }
    }
}
