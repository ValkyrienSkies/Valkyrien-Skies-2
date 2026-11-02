package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Without world-frame projection, freshEntityInShipyard moves the trader to world but its pinned targets stay shipyard, trader walks off in a straight line forever.
@Mixin(WanderingTraderSpawner.class)
public abstract class MixinWanderingTraderTargetWorldFrame {

    @Unique
    private static BlockPos vs$projectShipyardCellToWorld(final Level level, final BlockPos pos) {
        final Vec3 world = VSGameUtilsKt.toWorldCoordinates(level, Vec3.atCenterOf(pos));
        return BlockPos.containing(world);
    }

    @WrapOperation(
        method = "spawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/npc/WanderingTrader;setWanderTarget(Lnet/minecraft/core/BlockPos;)V"
        )
    )
    private void vs$worldFrameWanderTarget(
        final WanderingTrader trader, final BlockPos pos, final Operation<Void> original
    ) {
        original.call(trader, vs$projectShipyardCellToWorld(trader.level(), pos));
    }

    @WrapOperation(
        method = "spawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/npc/WanderingTrader;restrictTo(Lnet/minecraft/core/BlockPos;I)V"
        )
    )
    private void vs$worldFrameRestrictTo(
        final WanderingTrader trader, final BlockPos pos, final int radius, final Operation<Void> original
    ) {
        original.call(trader, vs$projectShipyardCellToWorld(trader.level(), pos), radius);
    }
}
