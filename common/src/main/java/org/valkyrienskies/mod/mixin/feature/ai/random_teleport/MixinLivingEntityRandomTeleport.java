package org.valkyrienskies.mod.mixin.feature.ai.random_teleport;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipBlockQueries;

// LivingEntity.randomTeleport (endermen, ender pearls, chorus fruit): walk-down sees ship decks as solid.
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityRandomTeleport {

    @WrapOperation(
        method = "randomTeleport(DDDZ)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readBlockStateForRandomTeleport(
        final Level instance, final BlockPos pos, final Operation<BlockState> original
    ) {
        final BlockState worldState = original.call(instance, pos);
        if (worldState.blocksMotion()) return worldState;
        final BlockState shipState = ShipBlockQueries.findShipBlock(instance, pos, BlockState::blocksMotion);
        return shipState != null ? shipState : worldState;
    }
}
