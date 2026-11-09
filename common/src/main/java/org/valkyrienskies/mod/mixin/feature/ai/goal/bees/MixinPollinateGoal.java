package org.valkyrienskies.mod.mixin.feature.ai.goal.bees;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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

    // ThreadLocal so the search-center override is scoped strictly to the per-ship
    // findNearestBlock call inside preFindNearbyFlower; outside that call, the wrap
    // returns the bee's current blockPosition.
    @Unique
    private static final ThreadLocal<BlockPos> vs$searchCenterOverride = new ThreadLocal<>();

    @Inject(method = "findNearbyFlower", at = @At("RETURN"), cancellable = true)
    private void preFindNearbyFlower(CallbackInfoReturnable<Optional<BlockPos>> cir) {
        if (!cir.getReturnValue().isEmpty()) return;
        final BlockPos beePos = field_20377.blockPosition();
        try {
            final Optional<BlockPos> res = VSGameUtilsKt.transformToNearbyShipsAndWorld(
                this.field_20377.level(), beePos.getX(), beePos.getY(), beePos.getZ(), 5.0
            ).stream().flatMap(pos -> {
                vs$searchCenterOverride.set(BlockPos.containing(pos.x, pos.y, pos.z));
                return findNearestBlock(VALID_POLLINATION_BLOCKS, 5.0).stream();
            }).min(Comparator.comparingDouble(pos ->
                VSGameUtilsKt.squaredDistanceBetweenInclShips(this.field_20377.level(),
                    beePos.getX(), beePos.getY(), beePos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ())));
            cir.setReturnValue(res);
        } finally {
            vs$searchCenterOverride.remove();
        }
    }

    // Re-center the in-loop search on the per-ship reprojected position set by
    // preFindNearbyFlower. No-op when the override isn't set.
    @WrapOperation(method = "findNearestBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Bee;blockPosition()Lnet/minecraft/core/BlockPos;", ordinal = 0))
    private BlockPos onBlockPosition(Bee instance, Operation<BlockPos> original) {
        final BlockPos override = vs$searchCenterOverride.get();
        return override != null ? override : original.call(instance);
    }

    // savedFlowerPos is shipyard for ship-mounted flowers; project the hover-target
    // vector to world directly (no BlockPos round-trip — flooring there can leave
    // the bee outside the 0.1-block hover threshold on rotated ships).
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 vs$atBottomCenterOfProjected(final Vec3i pos, final Operation<Vec3> original) {
        return VSGameUtilsKt.toWorldCoordinates(this.field_20377.level(), original.call(pos));
    }
}
