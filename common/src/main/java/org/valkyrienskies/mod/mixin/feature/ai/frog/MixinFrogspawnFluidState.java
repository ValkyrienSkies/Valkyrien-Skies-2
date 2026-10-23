package org.valkyrienskies.mod.mixin.feature.ai.frog;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FrogspawnBlock;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.fluid.ShipFluidStateQueries;

/** The support check must see the same retained source water used by the frog's placement query. */
@Mixin(FrogspawnBlock.class)
public abstract class MixinFrogspawnFluidState {
    @WrapOperation(method = "mayPlaceOn", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
    private static FluidState vs$shipFluidSupport(final BlockGetter getter, final BlockPos pos,
        final Operation<FluidState> original) {
        if (getter instanceof Level level) {
            final var controlled = ShipFluidStateQueries.at(level, pos);
            if (controlled != null) return controlled.fluid();
        }
        return original.call(getter, pos);
    }
}
