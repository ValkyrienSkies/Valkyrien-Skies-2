package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Target;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(PathFinder.class)
public class MixinPathFinder {
    @Shadow
    @Final
    private NodeEvaluator nodeEvaluator;

    @WrapOperation(
        method = "findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Object onCollectPath(Stream<BlockPos> instance, Collector<BlockPos, ?, Map<Target, BlockPos>> arCollector, Operation<Map<Target, BlockPos>> original, @Local
    Mob mob) {
        return original.call(instance, Collectors.<BlockPos, Target, BlockPos>toMap(blockPos ->  {
            BlockPos transformedPos = new BlockPos(VSGameUtilsKt.toWorldCoordinates(mob.level, blockPos));
            return this.nodeEvaluator.getGoal(transformedPos.getX(), transformedPos.getY(), transformedPos.getZ());
        }, Function.identity()));
    }
}
