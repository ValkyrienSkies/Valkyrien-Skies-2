package org.valkyrienskies.mod.mixin.feature.ai.piglin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.piglin.PiglinBruteAi;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// PiglinBruteAi.initMemories stores the brute's guard post as a HOME GlobalPos at finalizeSpawn from brute.blockPosition() — for a ship-spawned brute the world-coord HOME goes stale once the ship moves, and the brute walks off the deck back toward where the ship used to be. Substitute a shipyard BlockPos so the existing MixinStrollToPoi/MixinStrollAroundPoi shipyard→world reprojection per tick keeps the guard post tracking the moving ship.
@Mixin(PiglinBruteAi.class)
public abstract class MixinPiglinBruteAi {

    @ModifyExpressionValue(
        method = "initMemories(Lnet/minecraft/world/entity/monster/piglin/PiglinBrute;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/GlobalPos;of(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/GlobalPos;"
        )
    )
    private static GlobalPos vs$projectHomeToShipyard(
        final GlobalPos original,
        @Local(argsOnly = true) final PiglinBrute brute
    ) {
        // Spawn-time: dragger isn't populated yet, use the geometric "standing on a ship" check.
        final Ship ship = VSGameUtilsKt.getShipStoodOn(brute);
        if (ship == null) return original;
        final Vector3d shipyard = ship.getTransform().getWorldToShip().transformPosition(
            new Vector3d(brute.getX(), brute.getY(), brute.getZ()), new Vector3d()
        );
        return GlobalPos.of(original.dimension(), BlockPos.containing(shipyard.x, shipyard.y, shipyard.z));
    }
}
