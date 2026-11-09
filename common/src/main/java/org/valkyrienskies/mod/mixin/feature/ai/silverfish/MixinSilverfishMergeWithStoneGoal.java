package org.valkyrienskies.mod.mixin.feature.ai.silverfish;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Silverfish hide-into-stone goal reads the adjacent cell + setBlock-converts to InfestedBlock; for a silverfish on a ship's apparent surface the world cell is air. Project to ship-local on miss; cross-wrap ThreadLocal carries the projected pos from the getBlockState wrap to the setBlock wrap so the conversion writes to the actual ship cell. Three CP entries: Level.getBlockState in canUse (class dispatch) plus LevelAccessor.getBlockState/setBlock in start (interface dispatch).
@Mixin(targets = "net.minecraft.world.entity.monster.Silverfish$SilverfishMergeWithStoneGoal")
public abstract class MixinSilverfishMergeWithStoneGoal {

    @Unique
    private static final ThreadLocal<BlockPos> vs$shipLocalPosForWrite = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = "canUse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readInCanUse(
        final Level instance, final BlockPos worldPos, final Operation<BlockState> original
    ) {
        return vs$projectReadIfHostMissing(instance, worldPos, original.call(instance, worldPos));
    }

    @WrapOperation(
        method = "start",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readInStart(
        final LevelAccessor instance, final BlockPos worldPos, final Operation<BlockState> original
    ) {
        return vs$projectReadIfHostMissing(instance, worldPos, original.call(instance, worldPos));
    }

    @WrapOperation(
        method = "start",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
        )
    )
    private boolean vs$setBlockAtShipPosIfApplicable(
        final LevelAccessor instance, final BlockPos worldPos, final BlockState state,
        final int flags, final Operation<Boolean> original
    ) {
        final BlockPos shipPos = vs$shipLocalPosForWrite.get();
        vs$shipLocalPosForWrite.remove();
        if (shipPos != null) {
            return original.call(instance, shipPos, state, flags);
        }
        return original.call(instance, worldPos, state, flags);
    }

    @Unique
    private BlockState vs$projectReadIfHostMissing(
        final LevelAccessor instance, final BlockPos worldPos, final BlockState worldState
    ) {
        vs$shipLocalPosForWrite.remove();
        if (InfestedBlock.isCompatibleHostBlock(worldState)) return worldState;
        if (!(instance instanceof Level level)) return worldState;
        final Vector3d worldCenter = VS$CENTER.get().set(
            worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);
        final AABBd probe = VS$PROBE.get()
            .setMin(worldPos.getX(), worldPos.getY(), worldPos.getZ())
            .setMax(worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
                worldCenter, VS$LOCAL.get()
            );
            final BlockPos shipLocalPos = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            final BlockState shipState = level.getBlockState(shipLocalPos);
            if (InfestedBlock.isCompatibleHostBlock(shipState)) {
                vs$shipLocalPosForWrite.set(shipLocalPos.immutable());
                return shipState;
            }
        }
        return worldState;
    }
}
