package org.valkyrienskies.mod.mixin.feature.ai.swim;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// RandomStroll.getTargetSwimPos validates each swim candidate by reading the world fluid
// at the candidate cell; for a fish in ship-mounted water the world cell is air so the
// loop bails to null and the wander behavior fails. Project the world cell to ship-local
// on miss and return the ship-frame fluid.
@Mixin(RandomStroll.class)
public abstract class MixinRandomStrollSwim {

    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$VEC = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        method = "getTargetSwimPos",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        )
    )
    private static FluidState vs$shipAwareSwimFluid(
        final Level level, final BlockPos worldPos, final Operation<FluidState> original
    ) {
        final FluidState vanilla = original.call(level, worldPos);
        if (!vanilla.isEmpty()) return vanilla;

        final AABBd probe = VS$PROBE.get()
            .setMin(worldPos.getX(), worldPos.getY(), worldPos.getZ())
            .setMax(worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0);
        final Vector3d scratch = VS$VEC.get();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            ship.getTransform().getWorldToShip().transformPosition(
                scratch.set(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5));
            final FluidState shipFluid = level.getFluidState(BlockPos.containing(scratch.x, scratch.y, scratch.z));
            if (!shipFluid.isEmpty()) return shipFluid;
        }
        return vanilla;
    }
}
