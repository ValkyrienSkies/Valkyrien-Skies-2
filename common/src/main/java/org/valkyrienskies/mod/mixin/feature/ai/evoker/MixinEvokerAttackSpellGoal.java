package org.valkyrienskies.mod.mixin.feature.ai.evoker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Lets evokers spawn EvokerFangs on ship decks instead of silently no-op'ing the attack.
@Mixin(targets = "net.minecraft.world.entity.monster.Evoker$EvokerAttackSpellGoal")
public abstract class MixinEvokerAttackSpellGoal {

    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = "createSpellEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readShipBlockStateForFang(
        final Level instance, final BlockPos pos, final Operation<BlockState> original
    ) {
        final BlockState worldState = original.call(instance, pos);
        if (!worldState.isAir()) return worldState;
        final BlockState shipState = vs$shipBlockAt(instance, pos);
        return shipState != null ? shipState : worldState;
    }

    @WrapOperation(
        method = "createSpellEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isEmptyBlock(Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean vs$isEmptyShipAwareForFang(
        final Level instance, final BlockPos pos, final Operation<Boolean> original
    ) {
        if (!original.call(instance, pos)) return false;
        return vs$shipBlockAt(instance, pos) == null;
    }

    @Unique
    private static BlockState vs$shipBlockAt(final Level level, final BlockPos worldPos) {
        final double cx = worldPos.getX() + 0.5;
        final double cy = worldPos.getY() + 0.5;
        final double cz = worldPos.getZ() + 0.5;
        final Vector3d worldCenter = VS$CENTER.get().set(cx, cy, cz);
        final AABBd probe = VS$PROBE.get()
            .setMin(cx - 0.5, cy - 0.5, cz - 0.5)
            .setMax(cx + 0.5, cy + 0.5, cz + 0.5);
        final Vector3d shipLocal = VS$LOCAL.get();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            if (!ship.getWorldAABB().containsPoint(worldCenter)) continue;
            ship.getTransform().getWorldToShip().transformPosition(worldCenter, shipLocal);
            final BlockPos shipLocalPos = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            final BlockState shipState = level.getBlockState(shipLocalPos);
            if (!shipState.isAir()) return shipState;
        }
        return null;
    }
}
