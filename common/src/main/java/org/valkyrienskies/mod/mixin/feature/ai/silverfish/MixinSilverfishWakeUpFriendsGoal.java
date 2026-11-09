package org.valkyrienskies.mod.mixin.feature.ai.silverfish;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// After a 20-tick cooldown set by notifyHurt, vanilla iterates a 21×11×21 cube around the silverfish's world blockPos and destroyBlock/setBlock-converts each InfestedBlock found; for a silverfish on a ship the surrounding world cells are air. Same overlay pattern as MixinSilverfishMergeWithStoneGoal: read tries world first then projects into each ship; on ship match a cross-wrap ThreadLocal carries the projected pos to the destroyBlock/setBlock wrap. Per-cell iteration means each iteration's read clears the TL at HEAD so leftover state from one cell never affects the next.
@Mixin(targets = "net.minecraft.world.entity.monster.Silverfish$SilverfishWakeUpFriendsGoal")
public abstract class MixinSilverfishWakeUpFriendsGoal {

    @Unique
    private static final ThreadLocal<BlockPos> vs$shipLocalPosForWrite = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

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
        final Vector3d worldCenter = VS$CENTER.get().set(
            worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);
        final AABBd probe = VS$PROBE.get()
            .setMin(worldPos.getX(), worldPos.getY(), worldPos.getZ())
            .setMax(worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(instance, probe)) {
            final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
                worldCenter, VS$LOCAL.get()
            );
            final BlockPos shipLocalPos = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            final BlockState shipState = instance.getBlockState(shipLocalPos);
            if (shipState.getBlock() instanceof InfestedBlock) {
                vs$shipLocalPosForWrite.set(shipLocalPos.immutable());
                return shipState;
            }
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
