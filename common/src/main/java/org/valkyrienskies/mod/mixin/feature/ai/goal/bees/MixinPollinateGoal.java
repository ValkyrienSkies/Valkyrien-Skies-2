package org.valkyrienskies.mod.mixin.feature.ai.goal.bees;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Bee.BeePollinateGoal.class)
public abstract class MixinPollinateGoal {
    @Shadow
    @Final
    Bee field_20377;

    @Shadow
    protected abstract Optional<BlockPos> findNearestBlock(Predicate<BlockState> predicate, double d);

    @Shadow
    @Final
    private Predicate<BlockState> VALID_POLLINATION_BLOCKS;
    @Unique
    private BlockPos modifiedBeePosition = field_20377.blockPosition();

    @Inject(method = "findNearbyFlower", at = @At("HEAD"), cancellable = true)
    private void preFindNearbyFlower(CallbackInfoReturnable<Optional<BlockPos>> cir) {
        Optional<BlockPos> res = VSGameUtilsKt.transformToNearbyShipsAndWorld(this.field_20377.level, modifiedBeePosition.getX(), modifiedBeePosition.getY(), modifiedBeePosition.getZ(), 5.0).stream().flatMap(pos -> {
            this.modifiedBeePosition = new BlockPos(pos.x, pos.y, pos.z);
            return findNearestBlock(VALID_POLLINATION_BLOCKS, 5.0).stream();
        }).min(Comparator.comparingDouble(pos -> VSGameUtilsKt.squaredDistanceBetweenInclShips(this.field_20377.level, modifiedBeePosition.getX(), modifiedBeePosition.getY(), modifiedBeePosition.getZ(), pos.getX(), pos.getY(), pos.getZ())));
        modifiedBeePosition = field_20377.blockPosition();
        cir.setReturnValue(res);
    }

    @WrapOperation(method = "findNearestBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Bee;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos onBlockPosition(Bee instance, Operation<BlockPos> original) {
        return modifiedBeePosition;
    }
}
