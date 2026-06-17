package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
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

// Suppress the air-adjust scan in createPath when the target cell is inside a ship's worldAABB; vanilla would walk Y down through empty world chunks and either land below the ship or bump to maxBuildHeight.
@Mixin(GroundPathNavigation.class)
public abstract class MixinGroundPathNavigation extends PathNavigation {

    public MixinGroundPathNavigation(final Mob mob, final Level level) {
        super(mob, level);
    }

    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z",
            ordinal = 0
        )
    )
    private boolean vs$gateIsAirOnShip(
        final BlockState instance, final Operation<Boolean> original,
        @Local(argsOnly = true) final BlockPos pos
    ) {
        final boolean vanilla = original.call(instance);
        if (!vanilla || !VSGameConfig.SERVER.getAiOnShips()) return vanilla;
        if (vs$cellInsideAnyShipAABB(this.level, pos)) return false;
        return vanilla;
    }

    @Unique
    private static boolean vs$cellInsideAnyShipAABB(final Level level, final BlockPos worldPos) {
        final Vector3d worldCenter = VS$CENTER.get().set(
            worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5
        );
        final AABBd probe = VS$PROBE.get()
            .setMin(worldPos.getX(), worldPos.getY(), worldPos.getZ())
            .setMax(worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            if (ship.getWorldAABB().containsPoint(worldCenter)) return true;
        }
        return false;
    }
}
