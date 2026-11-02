package org.valkyrienskies.mod.mixin.feature.ai.ender_dragon;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// EnderDragon.checkWalls scans every cell in each body-part AABB and reads/writes only at
// world coords; ship structures the dragon clips through stay invisible to it. Wrap the
// per-cell getBlockState to fall back to ships when the world cell is empty/transparent,
// and stash the projected ship-local pos so the paired removeBlock writes to the ship's
// chunk. Same pattern as MixinSilverfishWakeUpFriendsGoal / MixinEndermanTakeBlockGoal.
@Mixin(EnderDragon.class)
public abstract class MixinEnderDragonCheckWalls {

    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);
    @Unique
    private static final ThreadLocal<BlockPos> VS$SHIP_REMOVE_POS = new ThreadLocal<>();

    @WrapOperation(
        method = "checkWalls",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readBlockStateIncludingShips(
        final Level instance, final BlockPos worldPos, final Operation<BlockState> original
    ) {
        VS$SHIP_REMOVE_POS.remove();
        final BlockState worldState = original.call(instance, worldPos);
        if (!worldState.isAir() && !worldState.is(BlockTags.DRAGON_TRANSPARENT)) {
            return worldState;
        }
        final double cx = worldPos.getX() + 0.5;
        final double cy = worldPos.getY() + 0.5;
        final double cz = worldPos.getZ() + 0.5;
        final Vector3d worldCenter = VS$CENTER.get().set(cx, cy, cz);
        final AABBd probe = VS$PROBE.get()
            .setMin(cx - 0.5, cy - 0.5, cz - 0.5)
            .setMax(cx + 0.5, cy + 0.5, cz + 0.5);
        final Vector3d shipLocal = VS$LOCAL.get();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(instance, probe)) {
            ship.getTransform().getWorldToShip().transformPosition(worldCenter, shipLocal);
            final BlockPos shipLocalPos = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            final BlockState shipState = instance.getBlockState(shipLocalPos);
            if (!shipState.isAir() && !shipState.is(BlockTags.DRAGON_TRANSPARENT)) {
                VS$SHIP_REMOVE_POS.set(shipLocalPos);
                return shipState;
            }
        }
        return worldState;
    }

    @WrapOperation(
        method = "checkWalls",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"
        )
    )
    private boolean vs$removeBlockAtShipPosIfApplicable(
        final Level instance, final BlockPos worldPos, final boolean isMoving,
        final Operation<Boolean> original
    ) {
        final BlockPos shipPos = VS$SHIP_REMOVE_POS.get();
        VS$SHIP_REMOVE_POS.remove();
        return original.call(instance, shipPos != null ? shipPos : worldPos, isMoving);
    }
}
