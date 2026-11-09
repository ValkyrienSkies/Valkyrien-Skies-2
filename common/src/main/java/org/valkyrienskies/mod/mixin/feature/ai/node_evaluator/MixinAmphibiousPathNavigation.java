package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

// AmphibiousPathNavigation overrides isStableDestination as "below is not air"; the
// base-class wrap in MixinPathNavigation doesn't propagate to overrides, so axolotls
// in ship water see vanilla air at the world cell and bail. Project the read into ship
// frame and return any non-air ship block as the below-state.
@Mixin(AmphibiousPathNavigation.class)
public abstract class MixinAmphibiousPathNavigation {

    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$SCRATCH = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = "isStableDestination",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$shipAwareBelow(
        final Level instance, final BlockPos blockPos, final Operation<BlockState> original
    ) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return original.call(instance, blockPos);
        try {
            return vs$shipAwareBelowImpl(instance, blockPos, original);
        } catch (final Throwable t) {
            return original.call(instance, blockPos);
        }
    }

    @Unique
    private static BlockState vs$shipAwareBelowImpl(
        final Level instance, final BlockPos blockPos, final Operation<BlockState> original
    ) {
        final BlockState originalState = original.call(instance, blockPos);
        if (!originalState.isAir()) return originalState;

        final double cx = blockPos.getX() + 0.5;
        final double cy = blockPos.getY() + 0.5;
        final double cz = blockPos.getZ() + 0.5;
        final Vector3d center = VS$CENTER.get().set(cx, cy, cz);
        final AABBd probe = VS$PROBE.get().setMin(cx - 0.5, cy - 0.5, cz - 0.5).setMax(cx + 0.5, cy + 0.5, cz + 0.5);
        final Vector3d scratch = VS$SCRATCH.get();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(instance, probe)) {
            if (!ship.getWorldAABB().containsPoint(center)) continue;
            ship.getTransform().getWorldToShip().transformPosition(center, scratch);
            final BlockPos shipLocalPos = BlockPos.containing(scratch.x, scratch.y, scratch.z);
            final BlockState shipBlock = original.call(instance, shipLocalPos);
            if (!shipBlock.isAir()) return shipBlock;
        }
        return originalState;
    }
}
