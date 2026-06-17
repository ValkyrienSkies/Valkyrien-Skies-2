package org.valkyrienskies.mod.mixin.feature.ai.goal.allay;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.AllayAi;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Vibration system hands AllayAi the world pos of the played note block; for ship-mounted
// notes the real NOTE_BLOCK lives in shipyard. Store the shipyard pos in memory so the
// downstream getBlockState(stored).is(NOTE_BLOCK) check fires, and project back to world
// when constructing the deposit-position tracker.
@Mixin(AllayAi.class)
public abstract class MixinAllayAi {

    @ModifyExpressionValue(
        method = "hearNoteblock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/GlobalPos;of(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/GlobalPos;"
        )
    )
    private static GlobalPos vs$projectGlobalPosToShipyard(
        final GlobalPos original,
        @Local(argsOnly = true) final LivingEntity entity,
        @Local(argsOnly = true) final BlockPos worldPos
    ) {
        final BlockPos shipLocal = vs$findShipLocalNoteBlock(entity.level(), worldPos);
        return shipLocal == null ? original : GlobalPos.of(original.dimension(), shipLocal);
    }

    @Unique
    private static BlockPos vs$findShipLocalNoteBlock(final Level level, final BlockPos worldPos) {
        final double cx = worldPos.getX() + 0.5;
        final double cy = worldPos.getY() + 0.5;
        final double cz = worldPos.getZ() + 0.5;
        final AABBd probe = new AABBd(cx - 0.5, cy - 0.5, cz - 0.5, cx + 0.5, cy + 0.5, cz + 0.5);
        final Vector3d worldCenter = new Vector3d(cx, cy, cz);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            if (!ship.getWorldAABB().containsPoint(worldCenter)) continue;
            final Vector3d local = ship.getTransform().getWorldToShip().transformPosition(
                worldCenter, new Vector3d()
            );
            final BlockPos localPos = BlockPos.containing(local.x, local.y, local.z);
            if (level.getBlockState(localPos).is(Blocks.NOTE_BLOCK)) {
                return localPos;
            }
        }
        return null;
    }

    @WrapOperation(
        method = "getItemDepositPosition",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;above()Lnet/minecraft/core/BlockPos;",
            ordinal = 0
        )
    )
    private static BlockPos vs$projectNoteBlockToWorld(
        final BlockPos instance, final Operation<BlockPos> original,
        @Local(argsOnly = true) final LivingEntity entity
    ) {
        // Vec3.atCenterOf + Vec3 overload of toWorldCoordinates is corner-safe; the BlockPos
        // overload projects the cell corner and lands on the wrong cell for rotated ships.
        final Vec3 noteWorldCenter = VSGameUtilsKt.toWorldCoordinates(entity.level(), Vec3.atCenterOf(instance));
        return original.call(BlockPos.containing(noteWorldCenter));
    }
}
