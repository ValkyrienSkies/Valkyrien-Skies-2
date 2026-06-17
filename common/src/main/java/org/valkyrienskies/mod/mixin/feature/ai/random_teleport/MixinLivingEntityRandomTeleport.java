package org.valkyrienskies.mod.mixin.feature.ai.random_teleport;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;

// LivingEntity.randomTeleport (endermen, ender pearls, chorus fruit): walk-down sees ship decks as solid; final landing-validity check rejects landings clipping into ship walls/ceilings.
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityRandomTeleport {

    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$LOCAL = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = "randomTeleport(DDDZ)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readBlockStateForRandomTeleport(
        final Level instance, final BlockPos pos, final Operation<BlockState> original
    ) {
        final BlockState worldState = original.call(instance, pos);
        if (worldState.blocksMotion()) return worldState;
        final BlockState shipState = vs$shipBlockAt(instance, pos);
        return shipState != null ? shipState : worldState;
    }

    @WrapOperation(
        method = "randomTeleport(DDDZ)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;)Z"
        )
    )
    private boolean vs$noCollisionIncludingShips(
        final Level level, final Entity entity, final Operation<Boolean> original
    ) {
        return ShipAwareCollisionUtil.noCollisionIncludingShips(level, entity, entity.getBoundingBox());
    }

    @Unique
    private static BlockState vs$shipBlockAt(final Level level, final BlockPos worldPos) {
        final Vector3d worldCenter = VS$CENTER.get().set(
            worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5
        );
        final AABBd probe = VS$PROBE.get()
            .setMin(worldPos.getX(), worldPos.getY(), worldPos.getZ())
            .setMax(worldPos.getX() + 1.0, worldPos.getY() + 1.0, worldPos.getZ() + 1.0);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            if (!ship.getWorldAABB().containsPoint(worldCenter)) continue;
            final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
                worldCenter, VS$LOCAL.get()
            );
            final BlockPos shipLocalPos = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            final BlockState shipState = level.getBlockState(shipLocalPos);
            if (shipState.blocksMotion()) return shipState;
        }
        return null;
    }
}
