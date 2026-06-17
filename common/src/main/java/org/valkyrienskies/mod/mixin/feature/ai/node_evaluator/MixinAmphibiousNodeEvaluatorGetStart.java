package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.pathfinding.PathfindingFrame;

// Project vanilla's water-mode bbox-derived start into ship-local coords for InShip-frame
// paths so MixinPathNavigationRegion's InShip branch doesn't misinterpret the world-coord
// BlockPos as ship-local. Dry InShip falls through to super (WalkNodeEvaluator), which has
// its own ship-aware start in MixinWalkNodeEvaluator.
@Mixin(AmphibiousNodeEvaluator.class)
public abstract class MixinAmphibiousNodeEvaluatorGetStart extends NodeEvaluator {

    @Inject(method = "getStart", at = @At("HEAD"), cancellable = true)
    private void vs$shipLocalStart(final CallbackInfoReturnable<Node> cir) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return;
        if (this.mob == null) return;
        if (!(PathfindingFrame.current(this.mob) instanceof PathfindingFrame.InShip inShip)) return;
        if (!this.mob.isInWater()) return;

        try {
            final AABB bbox = this.mob.getBoundingBox();
            final Vector3d local = inShip.getShip().getTransform().getWorldToShip().transformPosition(
                new Vector3d(bbox.minX, bbox.minY + 0.5, bbox.minZ), new Vector3d()
            );
            cir.setReturnValue(new Node(
                (int) Math.floor(local.x), (int) Math.floor(local.y), (int) Math.floor(local.z)
            ));
        } catch (final Throwable t) {
            // Silent fallback to vanilla.
        }
    }
}
