package org.valkyrienskies.mod.mixin.feature.fluid_escaping_ship_config;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.fluid.RetainedFarmWater;

@Mixin(SugarCaneBlock.class)
public abstract class MixinSugarCaneBlock {
    @Inject(method = "canSurvive", at = @At("RETURN"), cancellable = true)
    private void vs$retainedWaterSupportsCane(final BlockState state, final LevelReader level, final BlockPos pos,
        final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        final BlockPos below = pos.below();
        final BlockState substrate = level.getBlockState(below);
        if ((substrate.is(BlockTags.DIRT) || substrate.is(BlockTags.SAND))
            && RetainedFarmWater.supportsCane(level, below)) cir.setReturnValue(true);
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void vs$recheckCaneSupport(final BlockState state, final ServerLevel level, final BlockPos pos,
        final RandomSource random, final CallbackInfo ci) {
        if (VSGameUtilsKt.getShipManagingPos(level, pos) != null && !state.canSurvive(level, pos)) {
            level.scheduleTick(pos, state.getBlock(), 1);
            ci.cancel();
        }
    }
}
