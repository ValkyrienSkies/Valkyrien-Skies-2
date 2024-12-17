package org.valkyrienskies.mod.mixin.feature.ai;

import static net.minecraft.world.entity.ai.util.LandRandomPos.generateRandomPosTowardDirection;
import static net.minecraft.world.entity.ai.util.LandRandomPos.movePosUpOutOfSolid;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * @author Tomato
 * Should allow for mobs to pathfind on ships.
 */
@Mixin(LandRandomPos.class)
public class MixinLandRandomPos {

    @Inject(
        method = "generateRandomPosTowardDirection",
        at = @At(
            value = "HEAD"
        ),
        cancellable = true
    )
    private static void postGenerateRandomPosTowardDirection(PathfinderMob pathfinderMob, int i, boolean bl,
        BlockPos blockPos, CallbackInfoReturnable<BlockPos> cir) {
        if (pathfinderMob.level != null) {
            final BlockPos blockPos3 = RandomPos.generateRandomPosTowardDirection(pathfinderMob, i, pathfinderMob.getRandom(), blockPos);
            AABB checker = new AABB(blockPos3);
            Iterable<LoadedShip> ships = VSGameUtilsKt.getShipObjectWorld(pathfinderMob.level).getLoadedShips().getIntersecting(VectorConversionsMCKt.toJOML(checker), VSGameUtilsKt.getDimensionId(pathfinderMob.level));
            if (ships.iterator().hasNext()) {
                for (LoadedShip ship : ships) {
                    Vector3d posInShip = ship.getWorldToShip()
                        .transformPosition(VectorConversionsMCKt.toJOMLD(blockPos3), new Vector3d());
                    BlockPos blockPosInShip = new BlockPos(VectorConversionsMCKt.toMinecraft(posInShip));
                    if (!GoalUtils.isRestricted(bl, pathfinderMob, blockPosInShip) &&
                        !GoalUtils.isNotStable(pathfinderMob.getNavigation(), blockPosInShip) &&
                        !GoalUtils.hasMalus(pathfinderMob, blockPosInShip)) {
                        cir.setReturnValue(blockPosInShip);
                        break;
                    }
                }
            }
        }
    }

    @WrapOperation(method = "getPosInDirection", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/util/RandomPos;generateRandomPos(Lnet/minecraft/world/entity/PathfinderMob;Ljava/util/function/Supplier;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 redirectGetPosInDirection(PathfinderMob arg, Supplier<BlockPos> supplier,
        Operation<Vec3> original) {
        Vec3 result = original.call(arg, supplier);
        if (result != null) {
            return VSGameUtilsKt.toWorldCoordinates(arg.level, result);
        }
        return null;
    }

    @Inject(method = "getPos(Lnet/minecraft/world/entity/PathfinderMob;IILjava/util/function/ToDoubleFunction;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private static void preGetPos(PathfinderMob pathfinderMob, int i, int j,
        ToDoubleFunction<BlockPos> toDoubleFunction, CallbackInfoReturnable<Vec3> cir) {
        boolean bl = GoalUtils.mobRestricted(pathfinderMob, i);
        Vec3 randomPos = RandomPos.generateRandomPos(() -> {
            BlockPos blockPos = RandomPos.generateRandomDirection(pathfinderMob.getRandom(), i, j);
            BlockPos blockPos2 = generateRandomPosTowardDirection(pathfinderMob, i, bl, blockPos);
            return blockPos2 == null ? null : movePosUpOutOfSolid(pathfinderMob, blockPos2);
        }, toDoubleFunction);

        if (randomPos != null) {
            cir.setReturnValue(VSGameUtilsKt.toWorldCoordinates(pathfinderMob.level, randomPos));
        }
        cir.setReturnValue(null);
    }
}
