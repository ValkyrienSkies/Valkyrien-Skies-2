package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.pathfinding.PathfindingFrame;

// Anchor the start node in ship-local coords so the search shares a grid with the rest of the InShip pathfind.
@Mixin(SwimNodeEvaluator.class)
public abstract class MixinSwimNodeEvaluatorGetStart extends NodeEvaluator {

    @Inject(method = "getStart", at = @At("HEAD"), cancellable = true)
    private void vs$shipLocalStart(final CallbackInfoReturnable<Node> cir) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return;
        if (this.mob == null) return;
        final PathfindingFrame frame = PathfindingFrame.current(this.mob);
        if (!(frame instanceof PathfindingFrame.InShip inShip)) return;

        try {
            final Ship ship = inShip.getShip();
            final AABB bb = this.mob.getBoundingBox();
            final Vector3d local = ship.getTransform().getWorldToShip().transformPosition(
                new Vector3d(bb.minX, bb.minY + 0.5, bb.minZ), new Vector3d()
            );
            cir.setReturnValue(this.getNode(Mth.floor(local.x), Mth.floor(local.y), Mth.floor(local.z)));
        } catch (final Throwable t) {
            // fall through to vanilla
        }
    }
}
