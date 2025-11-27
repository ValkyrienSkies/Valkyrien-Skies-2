package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(PathNavigation.class)
public class MixinPathNavigation {
    @Shadow
    @Final
    protected Level level;

    @WrapOperation(method = "moveTo(DDDD)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;createPath(DDDI)Lnet/minecraft/world/level/pathfinder/Path;"))
    private Path onMoveToCreatePath(PathNavigation instance, double d, double e, double f, int i,
        Operation<Path> original) {
        Vec3 transformedPos = VSGameUtilsKt.toWorldCoordinates(this.level, new Vec3(d, e, f));
        return original.call(instance, transformedPos.x, transformedPos.y, transformedPos.z, i);
    }

    @WrapOperation(
        method = "isStableDestination",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$getBlockStateIsNotStable(
        Level instance, BlockPos blockPos, Operation<BlockState> original) {
        BlockState originalState = original.call(instance, blockPos);
        if (originalState.isSolidRender(instance, blockPos)) return originalState;
        Iterable<Vector3d> candidates = ValkyrienSkies.positionToNearbyShips(instance, blockPos.above().getX(), blockPos.above().getY(), blockPos.above().getZ());
        for (Vector3d candidate : candidates) {
            Ship ship = ValkyrienSkies.getShipManagingBlock(instance, candidate);
            if (ship == null) continue;
            Vector3dc upVector = ship.getTransform().getShipToWorld().transformDirection(VectorConversionsMCKt.toJOMLD(Direction.UP.getNormal())).normalize();
            Direction closestDirection = Direction.getNearest(upVector.x(), upVector.y(), upVector.z()).getOpposite();
            BlockPos candidatePos = BlockPos.containing(ValkyrienSkies.toMinecraft(candidate)).relative(closestDirection);
            BlockState candidateState = original.call(instance, candidatePos);
            if (candidateState.isSolidRender(instance, candidatePos)) {
                return candidateState;
            }
        }
        return originalState;
    }
}
