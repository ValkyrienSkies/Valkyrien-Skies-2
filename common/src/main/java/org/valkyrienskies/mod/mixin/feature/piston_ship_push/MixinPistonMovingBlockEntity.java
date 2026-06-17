package org.valkyrienskies.mod.mixin.feature.piston_ship_push;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.PistonShipPush;

@Mixin(PistonMovingBlockEntity.class)
public class MixinPistonMovingBlockEntity {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void vs$pushShips(Level level, BlockPos blockPos, BlockState blockState,
                                     PistonMovingBlockEntity blockEntity, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        PistonShipPush.applyPushForces(serverLevel, blockPos, blockEntity);
    }
}
