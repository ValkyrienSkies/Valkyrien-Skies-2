package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// No config gate: phantoms remain a sleep-debt threat on ships regardless of allowMobSpawns.
@Mixin(PhantomSpawner.class)
public abstract class MixinPhantomSpawner {

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$shipPlayerSeed(final ServerPlayer player, final Operation<BlockPos> original) {
        return VSGameUtilsKt.shipMountedSpawnSeedPos(player);
    }
}
