package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SculkShriekerBlockEntity.class)
public abstract class MixinSculkShriekerCrossFrameSummon {

    @WrapOperation(
        method = "trySummonWarden",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/SpawnUtil;trySpawnMob(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;IIILnet/minecraft/util/SpawnUtil$Strategy;)Ljava/util/Optional;"
        )
    )
    private Optional<?> vs$summonAcrossFrames(
        final EntityType<?> type, final MobSpawnType spawnType, final ServerLevel level,
        final BlockPos pos, final int attempts, final int rangeXZ, final int rangeY,
        final SpawnUtil.Strategy strategy, final Operation<Optional<?>> original
    ) {
        final Optional<?> first = original.call(type, spawnType, level, pos, attempts, rangeXZ, rangeY, strategy);
        if (first.isPresent()) return first;

        final Ship sourceShip = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (sourceShip != null) {
            final Vector3d world = sourceShip.getTransform().getShipToWorld().transformPosition(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new Vector3d()
            );
            final BlockPos worldPos = BlockPos.containing(world.x, world.y, world.z);
            return original.call(type, spawnType, level, worldPos, attempts, rangeXZ, rangeY, strategy);
        }

        final AABB searchBox = AABB.ofSize(
            Vec3.atCenterOf(pos),
            rangeXZ * 2 + 1, rangeY * 2 + 1, rangeXZ * 2 + 1
        );
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, searchBox)) {
            final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new Vector3d()
            );
            final BlockPos shipPos = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            final Optional<?> second = original.call(type, spawnType, level, shipPos, attempts, rangeXZ, rangeY, strategy);
            if (second.isPresent()) return second;
        }
        return first;
    }
}
