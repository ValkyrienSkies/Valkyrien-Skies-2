package org.valkyrienskies.mod.mixin.feature.mob_spawning.per_mob;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Slime.class)
public abstract class MixinSlimeShipSpawn {

    @WrapOperation(
        method = "checkSlimeSpawnRules",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;getY()I"
        )
    )
    private static int vs$shipProjectedY(
        final BlockPos pos, final Operation<Integer> original,
        @Local(argsOnly = true) final LevelAccessor level
    ) {
        return VSGameUtilsKt.shipProjectedWorldY(level, pos, original.call(pos));
    }

    @WrapOperation(
        method = "checkSlimeSpawnRules",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;)I"
        )
    )
    private static int vs$shipAwareBrightness(
        final LevelAccessor level, final BlockPos pos, final Operation<Integer> original
    ) {
        final int orig = original.call(level, pos);
        if (!(level instanceof ServerLevel serverLevel)) return orig;
        return VSGameUtilsKt.shipAwareCombinedBrightness(serverLevel, pos, serverLevel.getSkyDarken(), orig);
    }

    @WrapOperation(
        method = "checkSlimeSpawnRules",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/ChunkPos;"
        )
    )
    private static ChunkPos vs$slimeChunkUsingWorldFrame(
        final BlockPos pos, final Operation<ChunkPos> original,
        @Local(argsOnly = true) final LevelAccessor level
    ) {
        if (!(level instanceof ServerLevel serverLevel)) return original.call(pos);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(serverLevel, pos);
        if (ship == null) return original.call(pos);
        final Vector3d world = ship.getTransform().getShipToWorld().transformPosition(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new Vector3d()
        );
        return original.call(BlockPos.containing(world.x, world.y, world.z));
    }
}
