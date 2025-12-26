package org.valkyrienskies.mod.mixin.mod_compat.create;

import static com.simibubi.create.content.equipment.toolbox.ToolboxHandler.distance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.equipment.toolbox.ToolboxHandler;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.CompatUtil;

@Mixin(ToolboxHandler.class)
public abstract class MixinToolboxHandler {

    // We need access to a Level to transform toolbox position to world. While getNearest itself has it accessible
    // through a Player, distance checks are called inside lambdas which do not have access to this variable.
    // We instead replace lambdas themselves, granted they are rather simple.
    @Redirect(
        method = "getNearest",
        at = @At(
            value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;",
            ordinal = 0 // .filter is called twice, only the first one operates on BlockPos
        )
    )
    private static Stream<BlockPos> replaceFilteringLambda(
        Stream<BlockPos> instance, Predicate<? super BlockPos> predicate,
        @Local(argsOnly = true) Player player, @Local Vec3 location, @Local double maxRange
    ) {
        return instance.filter(p -> distance(
            CompatUtil.INSTANCE.toSameSpaceAs(player.level(), location, p), p
        ) < maxRange * maxRange);
    }

    @Redirect(
        method = "getNearest",
        at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;sorted(Ljava/util/Comparator;)Ljava/util/stream/Stream;")
    )
    private static Stream<BlockPos> replaceSortingLambda(
        Stream<BlockPos> instance, Comparator<? super BlockPos> comparator,
        @Local(argsOnly = true) Player player, @Local Vec3 location
    ) {
        return instance.sorted(
            // Functionally identical to (p1, p2) -> Double.compare(..., ...)
            // Suggested by IJ IDEA as a better option. I guess Eclipse doesn't do that...
            Comparator.comparingDouble(
                p -> distance(CompatUtil.INSTANCE.toSameSpaceAs(player.level(), location, p), p))
        );
    }

    @WrapOperation(
        method = "withinRange",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/equipment/toolbox/ToolboxHandler;distance(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;)D")
    )
    private static double adjustToolboxPos(
        Vec3 playerPos, BlockPos toolboxPos, Operation<Double> original,
        @Local(argsOnly = true) Player player
    ) {
        // We cannot transform toolbox position to world because the original method accepts a Vec3 and a BlockPos.
        return original.call(
            CompatUtil.INSTANCE.toSameSpaceAs(player.level(), playerPos, toolboxPos),
            toolboxPos
        );
    }
}
