package org.valkyrienskies.mod.mixin.feature.ai.evoker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipBlockQueries;

// Lets evokers spawn EvokerFangs on ship decks instead of silently no-op'ing the attack.
@Mixin(targets = "net.minecraft.world.entity.monster.Evoker$EvokerAttackSpellGoal")
public abstract class MixinEvokerAttackSpellGoal {

    @WrapOperation(
        method = "createSpellEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readShipBlockStateForFang(
        final Level instance, final BlockPos pos, final Operation<BlockState> original
    ) {
        final BlockState worldState = original.call(instance, pos);
        if (!worldState.isAir()) return worldState;
        final BlockState shipState = ShipBlockQueries.findShipBlock(instance, pos, s -> !s.isAir());
        return shipState != null ? shipState : worldState;
    }

    @WrapOperation(
        method = "createSpellEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isEmptyBlock(Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean vs$isEmptyShipAwareForFang(
        final Level instance, final BlockPos pos, final Operation<Boolean> original
    ) {
        if (!original.call(instance, pos)) return false;
        return ShipBlockQueries.findShipBlock(instance, pos, s -> !s.isAir()) == null;
    }
}
