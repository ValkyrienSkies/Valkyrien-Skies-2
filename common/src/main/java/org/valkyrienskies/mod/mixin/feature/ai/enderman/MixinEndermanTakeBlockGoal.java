package org.valkyrienskies.mod.mixin.feature.ai.enderman;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipBlockQueries;

// EnderMan$EndermanTakeBlockGoal.tick reads getBlockState at a random nearby cell, runs a
// clip-based visibility check, then removeBlock — all world-only, so an enderman next to
// a ship can never pick up the ship's deck blocks. Wrap the read to fall back to ships
// (capturing the target's ship-local pos in a per-tick TL), wrap the visibility equals
// to also accept the captured pos (since MixinLevel.clip returns shipyard BlockPos for
// ship-block hits), and redirect removeBlock to the captured pos. Mirror of MixinEndermanLeaveBlockGoal.
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class MixinEndermanTakeBlockGoal {

    @Unique
    private static final ThreadLocal<BlockPos> VS$SHIP_TARGET_POS = new ThreadLocal<>();

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readBlockStateIncludingShips(
        final Level instance, final BlockPos pos, final Operation<BlockState> original
    ) {
        VS$SHIP_TARGET_POS.remove();
        final BlockState worldState = original.call(instance, pos);
        if (worldState.is(BlockTags.ENDERMAN_HOLDABLE)) return worldState;
        final BlockPos shipLocalPos =
            ShipBlockQueries.findShipBlockPos(instance, pos, s -> s.is(BlockTags.ENDERMAN_HOLDABLE));
        if (shipLocalPos != null) {
            VS$SHIP_TARGET_POS.set(shipLocalPos);
            return instance.getBlockState(shipLocalPos);
        }
        return worldState;
    }

    // Vanilla compares hit.getBlockPos().equals(candidateWorldPos). VS2's MixinLevel.clip
    // returns the SHIPYARD BlockPos when it lands on a ship block (getLocation is world,
    // getBlockPos is shipyard), so the world-vs-shipyard equals always rejects. Accept
    // when the hit's BlockPos matches our captured ship-local target — that's the same
    // ship block we want to pick up; if the clip hit a different ship block (occluder),
    // the equality fails and visibility correctly stays false.
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;equals(Ljava/lang/Object;)Z"
        )
    )
    private boolean vs$visibilityAllowingShipMatch(
        final BlockPos hitBlockPos, final Object candidatePos,
        final Operation<Boolean> original
    ) {
        if (original.call(hitBlockPos, candidatePos)) return true;
        final BlockPos shipPos = VS$SHIP_TARGET_POS.get();
        return shipPos != null && shipPos.equals(hitBlockPos);
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"
        )
    )
    private boolean vs$removeBlockAtShipPosIfApplicable(
        final Level instance, final BlockPos pos, final boolean isMoving,
        final Operation<Boolean> original
    ) {
        final BlockPos shipPos = VS$SHIP_TARGET_POS.get();
        VS$SHIP_TARGET_POS.remove();
        return original.call(instance, shipPos != null ? shipPos : pos, isMoving);
    }
}
