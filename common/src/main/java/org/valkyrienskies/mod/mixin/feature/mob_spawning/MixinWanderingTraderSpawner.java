package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(WanderingTraderSpawner.class)
public abstract class MixinWanderingTraderSpawner {

    @WrapOperation(
        method = "spawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$shipPlayerSeed(final Player player, final Operation<BlockPos> original) {
        if (!VSGameConfig.SERVER.getAllowMobSpawns()) return original.call(player);
        return VSGameUtilsKt.shipMountedSpawnSeedPos(player);
    }
}
