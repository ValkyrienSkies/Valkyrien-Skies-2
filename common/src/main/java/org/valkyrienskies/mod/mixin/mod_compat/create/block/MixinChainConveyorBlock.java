package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.compat.create.ChainConveyorFrames;

// Chains refunded on sneak-wrench for connections between frames: what was paid (see ChainConveyorFrames).
@Mixin(ChainConveyorBlock.class)
public abstract class MixinChainConveyorBlock {
    @WrapOperation(
        method = "lambda$onSneakWrenched$1",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;getChainCost(Lnet/minecraft/core/BlockPos;)I")
    )
    private static int vs$actualCost(final BlockPos connection, final Operation<Integer> original,
        @Local(argsOnly = true) final ChainConveyorBlockEntity be) {
        return ChainConveyorFrames.refund(be, connection);
    }
}
