package org.valkyrienskies.mod.mixin.feature.ai.silverfish;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipBlockQueries;

// After a 20-tick cooldown set by notifyHurt, vanilla iterates a 21×11×21 cube around the silverfish's world blockPos and destroyBlock/setBlock-converts each InfestedBlock found; for a silverfish on a ship the surrounding world cells are air. Same overlay pattern as MixinSilverfishMergeWithStoneGoal: read tries world first then projects into each ship; on ship match a cross-wrap ThreadLocal carries the projected pos to the destroyBlock/setBlock wrap. Per-cell iteration means each iteration's read clears the TL at HEAD so leftover state from one cell never affects the next.
@Mixin(targets = "net.minecraft.world.entity.monster.Silverfish$SilverfishWakeUpFriendsGoal")
public abstract class MixinSilverfishWakeUpFriendsGoal {

    @Unique
    private static final ThreadLocal<BlockPos> vs$shipLocalPosForWrite = new ThreadLocal<>();

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readBlockStateIncludingShips(
        final Level instance, final BlockPos worldPos, final Operation<BlockState> original
    ) {
        vs$shipLocalPosForWrite.remove();
        final BlockState worldState = original.call(instance, worldPos);
        if (worldState.getBlock() instanceof InfestedBlock) return worldState;
        final BlockPos shipLocalPos =
            ShipBlockQueries.findShipBlockPos(instance, worldPos, s -> s.getBlock() instanceof InfestedBlock);
        if (shipLocalPos != null) {
            vs$shipLocalPosForWrite.set(shipLocalPos);
            return instance.getBlockState(shipLocalPos);
        }
        return worldState;
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"
        )
    )
    private boolean vs$destroyBlockAtShipPosIfApplicable(
        final Level instance, final BlockPos worldPos, final boolean dropBlock, final Entity entity,
        final Operation<Boolean> original
    ) {
        final BlockPos shipPos = vs$shipLocalPosForWrite.get();
        vs$shipLocalPosForWrite.remove();
        if (shipPos != null) {
            return original.call(instance, shipPos, dropBlock, entity);
        }
        return original.call(instance, worldPos, dropBlock, entity);
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
        )
    )
    private boolean vs$setBlockAtShipPosIfApplicable(
        final Level instance, final BlockPos worldPos, final BlockState state, final int flags,
        final Operation<Boolean> original
    ) {
        final BlockPos shipPos = vs$shipLocalPosForWrite.get();
        vs$shipLocalPosForWrite.remove();
        if (shipPos != null) {
            return original.call(instance, shipPos, state, flags);
        }
        return original.call(instance, worldPos, state, flags);
    }
}
