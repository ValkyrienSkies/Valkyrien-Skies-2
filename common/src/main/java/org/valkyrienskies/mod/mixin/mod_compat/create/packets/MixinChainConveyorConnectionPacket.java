package org.valkyrienskies.mod.mixin.mod_compat.create.packets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionPacket;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.compat.create.ChainConveyorFrames;

// Connections between frames (see ChainConveyorFrames): connecting costs the chain's actual length; disconnecting
// refunds what was paid.
@Mixin(ChainConveyorConnectionPacket.class)
public abstract class MixinChainConveyorConnectionPacket {
    @WrapOperation(
        method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;)V",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;getChainCost(Lnet/minecraft/core/BlockPos;)I", ordinal = 0)
    )
    private int vs$actualCost(final BlockPos connection, final Operation<Integer> original,
        @Local(argsOnly = true) final ChainConveyorBlockEntity be) {
        return ChainConveyorFrames.chainCost(be.getLevel(), be.getBlockPos(), connection);
    }

    @WrapOperation(
        method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;)V",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;getChainCost(Lnet/minecraft/core/BlockPos;)I", ordinal = 1)
    )
    private int vs$refund(final BlockPos connection, final Operation<Integer> original,
        @Local(argsOnly = true) final ChainConveyorBlockEntity be) {
        return ChainConveyorFrames.refund(be, connection);
    }
}
