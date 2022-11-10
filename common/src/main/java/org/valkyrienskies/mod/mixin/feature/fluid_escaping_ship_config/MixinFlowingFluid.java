package org.valkyrienskies.mod.mixin.feature.fluid_escaping_ship_config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(FlowingFluid.class)
public class MixinFlowingFluid {

    @Inject(at = @At("HEAD"), method = "canSpreadTo", cancellable = true)
    private void beforeCanSpreadTo(final BlockGetter level, final BlockPos fromPos, final BlockState fromBlockState,
        final Direction direction, final BlockPos toPos, final BlockState toBlockState, final FluidState toFluidState,
        final Fluid fluid, final CallbackInfoReturnable<Boolean> cir) {

        if (VSGameConfig.SERVER.getPreventFluidEscapingShip() && level instanceof Level) {
            final Ship ship = VSGameUtilsKt.getShipManagingPos((Level) level, toPos);
            if (ship != null && ship.getShipVoxelAABB() != null) {
                final AABBic a = ship.getShipVoxelAABB();
                final int x = toPos.getX();
                final int y = toPos.getY();
                final int z = toPos.getZ();

                if (x <= a.minX() || y < a.minY() || z <= a.minZ() || x >= a.maxX() || y >= a.maxY() || z >= a.maxZ()) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
