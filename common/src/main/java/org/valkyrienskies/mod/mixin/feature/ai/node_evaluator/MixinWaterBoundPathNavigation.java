package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
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

// WaterBoundPathNavigation overrides isStableDestination as "the cell itself is not air"; the base-class wrap in MixinPathNavigation doesn't propagate to overrides, so water mobs in ship-mounted water tanks see vanilla air at the world cell and bail.
@Mixin(WaterBoundPathNavigation.class)
public abstract class MixinWaterBoundPathNavigation {

    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = "isStableDestination",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$shipAwareCell(
        final Level instance, final BlockPos blockPos, final Operation<BlockState> original
    ) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return original.call(instance, blockPos);
        final BlockState originalState = original.call(instance, blockPos);
        if (!originalState.isAir()) return originalState;

        final Vector3d center = VS$CENTER.get().set(
            blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        final AABBd probe = VS$PROBE.get()
            .setMin(blockPos.getX(), blockPos.getY(), blockPos.getZ())
            .setMax(blockPos.getX() + 1.0, blockPos.getY() + 1.0, blockPos.getZ() + 1.0);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(instance, probe)) {
            if (!ship.getWorldAABB().containsPoint(center)) continue;
            final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
                center, VS$LOCAL.get()
            );
            final BlockPos shipLocalPos = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            final BlockState shipBlock = original.call(instance, shipLocalPos);
            if (!shipBlock.isAir()) return shipBlock;
        }
        return originalState;
    }
}
