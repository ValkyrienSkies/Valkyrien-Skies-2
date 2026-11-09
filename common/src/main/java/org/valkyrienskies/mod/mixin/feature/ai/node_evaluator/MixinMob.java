package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.mod.common.pathfinding.IPathfindingFrameProvider;
import org.valkyrienskies.mod.common.pathfinding.PathfindingFrame;

@Mixin(Mob.class)
public class MixinMob implements IPathfindingFrameProvider {

    @Unique
    private PathfindingFrame vs$pathfindingFrame = null;

    @Override
    public PathfindingFrame vs$getPathfindingFrame() {
        return vs$pathfindingFrame;
    }

    @Override
    public void vs$setPathfindingFrame(PathfindingFrame frame) {
        this.vs$pathfindingFrame = frame;
    }
}
