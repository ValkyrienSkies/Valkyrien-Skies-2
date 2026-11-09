package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Trader was moved to world coords by freshEntityInShipyard; project back to ship-local so the llama search hits ship cells.
@Mixin(WanderingTraderSpawner.class)
public abstract class MixinWanderingTraderLlamaSeed {

    @WrapOperation(
        method = "tryToSpawnLlamaFor",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/npc/WanderingTrader;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$shipLlamaSeed(final WanderingTrader trader, final Operation<BlockPos> original) {
        return VSGameUtilsKt.shipMountedSpawnSeedPos(trader);
    }
}
