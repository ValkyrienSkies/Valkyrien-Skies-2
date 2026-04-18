package org.valkyrienskies.mod.mixin.feature.ai.goal.bees;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Comparator;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Bee.BeeLocateHiveGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(BeeLocateHiveGoal.class)
public class MixinLocateHiveGoal {
    @Unique
    private Bee vs$bee;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void vs$captureBee(final Bee bee, final CallbackInfo ci) {
        this.vs$bee = bee;
    }

    @WrapOperation(method = "findNearbyHivesWithSpace", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;sorted(Ljava/util/Comparator;)Ljava/util/stream/Stream;"))
    private Stream<BlockPos> onComparingDouble(Stream<BlockPos> instance, Comparator<BlockPos> comparator,
        Operation<Stream<BlockPos>> original, @Local BlockPos blockPos) {
        return original.call(instance, Comparator.comparingDouble(pos -> BlockPos.containing(VSGameUtilsKt.toWorldCoordinates(this.vs$bee.level(), (BlockPos) pos)).distSqr(blockPos)));
    }
}
