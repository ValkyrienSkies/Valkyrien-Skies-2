package org.valkyrienskies.mod.mixin.world.level.material;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LavaFluid.class)
public abstract class LavaFluidMixin extends FlowingFluid {
    @Unique
    private boolean isModifyingFireTick = false;

    @Inject(method = "randomTick", at = @At("TAIL"))
    public void fireTickMixin(final Level level, final BlockPos pos, final FluidState state, final Random random,
        final CallbackInfo ci) {
        if (isModifyingFireTick) {
            return;
        }

        isModifyingFireTick = true;

        final double origX = pos.getX();
        final double origY = pos.getY();
        final double origZ = pos.getZ();

        VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX, origY, origZ, 3, (x, y, z) -> {
            randomTick(level, new BlockPos(x, y, z), state, random);
        });

        isModifyingFireTick = false;

    }

}
